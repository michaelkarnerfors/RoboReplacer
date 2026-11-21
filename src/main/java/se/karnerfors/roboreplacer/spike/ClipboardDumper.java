package se.karnerfors.roboreplacer.spike;

import java.awt.AWTError;
import java.awt.Image;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

/**
 * Reads every flavour that Java offers for the current clipboard contents and reports what came out.
 *
 * <p>Throw-away spike code. It reads <em>all</em> flavours, which the product must not do blindly (it forces
 * source applications to render delayed data), and it keeps excerpts of the content, which the product must
 * never log (NFR-002).
 */
@Log
@RequiredArgsConstructor
final class ClipboardDumper {

    /** Per flavour we read at most this many characters or bytes. */
    static final int MAX_READ = 1 << 20;

    /** Characters of content shown per flavour. */
    static final int PREVIEW_CHARS = 160;

    private static final int READ_ATTEMPTS = 5;
    private static final long RETRY_PAUSE_MS = 40;

    private final Clipboard clipboard;

    /**
     * Result of one pass over the clipboard.
     *
     * @param flavours one report per flavour, in the order Java lists them
     * @param attempts how many times we had to ask for the contents (the clipboard can be busy)
     * @param millis   how long the whole pass took
     * @param problem  set if the contents could not be obtained at all
     */
    record Snapshot(List<FlavourReport> flavours, int attempts, long millis, String problem) {
    }

    /** What we could extract from the object a flavour returned. */
    private record Content(String resultClass, long size, String unit, boolean truncated, String traits,
            String preview) {
    }

    Snapshot inspect() {
        long start = System.nanoTime();
        Transferable contents = null;
        String problem = null;
        int attempts = 0;
        while (contents == null && attempts < READ_ATTEMPTS) {
            attempts++;
            try {
                contents = clipboard.getContents(null);
            } catch (IllegalStateException e) {
                problem = "clipboard unavailable (" + e.getClass().getSimpleName() + ")";
                log.fine("Clipboard busy on attempt " + attempts);
                pause();
            }
        }
        if (contents == null) {
            return new Snapshot(List.of(), attempts, millisSince(start),
                    problem != null ? problem : "clipboard returned no contents");
        }

        List<FlavourReport> reports = new ArrayList<>();
        DataFlavor[] flavors = contents.getTransferDataFlavors();
        for (int i = 0; i < flavors.length; i++) {
            reports.add(inspectFlavour(i, contents, flavors[i]));
        }
        return new Snapshot(List.copyOf(reports), attempts, millisSince(start), null);
    }

    private FlavourReport inspectFlavour(int index, Transferable contents, DataFlavor flavor) {
        var report = FlavourReport.builder()
                .index(index)
                .mimeType(flavor.getMimeType())
                .humanName(flavor.getHumanPresentableName())
                .representationClass(flavor.getRepresentationClass().getName())
                .nativeFormats(nativesFor(flavor))
                .size(-1)
                .sizeUnit("")
                .traits("")
                .preview("");

        // Reading these would make the JDK deserialise an object that another process put on the clipboard.
        // DataFlavor.stringFlavor is the exception: its MIME type says "serialized object", but the JDK fills it
        // from the plain text formats, so it is the most important flavour to read.
        boolean serialized = flavor.isMimeTypeSerializedObject() && !DataFlavor.stringFlavor.equals(flavor);
        if (serialized || flavor.isRepresentationClassRemote()) {
            return report.outcome(FlavourReport.Outcome.SKIPPED)
                    .note("serialized or remote object: not read, because reading deserialises it")
                    .build();
        }

        try {
            Content content = describe(flavor, contents.getTransferData(flavor));
            return report.outcome(FlavourReport.Outcome.READ)
                    .resultClass(content.resultClass())
                    .size(content.size())
                    .sizeUnit(content.unit())
                    .truncated(content.truncated())
                    .traits(content.traits())
                    .preview(content.preview())
                    .build();
        } catch (UnsupportedFlavorException | IOException | RuntimeException e) {
            return report.outcome(FlavourReport.Outcome.FAILED)
                    .note(e.getClass().getSimpleName() + ": " + shorten(e.getMessage(), 100))
                    .build();
        }
    }

    private Content describe(DataFlavor flavor, Object data) throws IOException {
        if (data == null) {
            return new Content("null", -1, "", false, "", "");
        }
        String resultClass = data.getClass().getName();

        if (data instanceof String text) {
            return textContent(resultClass, text, false, text.length(), "chars", "");
        }
        if (data instanceof Reader reader) {
            try (reader) {
                return readChars(resultClass, reader);
            }
        }
        if (data instanceof InputStream in) {
            try (in) {
                byte[] bytes = in.readNBytes(MAX_READ + 1);
                boolean truncated = bytes.length > MAX_READ;
                byte[] kept = truncated ? Arrays.copyOf(bytes, MAX_READ) : bytes;
                return bytesContent(flavor, resultClass, kept, truncated, kept.length);
            }
        }
        if (data instanceof byte[] bytes) {
            return bytesContent(flavor, resultClass, bytes, false, bytes.length);
        }
        if (data instanceof ByteBuffer buffer) {
            int total = buffer.remaining();
            byte[] kept = new byte[Math.min(total, MAX_READ)];
            buffer.duplicate().get(kept);
            return bytesContent(flavor, resultClass, kept, total > MAX_READ, total);
        }
        if (data instanceof CharBuffer chars) {
            return textContent(resultClass, chars.toString(), false, chars.remaining(), "chars", "");
        }
        if (data instanceof char[] chars) {
            return textContent(resultClass, new String(chars), false, chars.length, "chars", "");
        }
        if (data instanceof RenderedImage image) {
            return new Content(resultClass, (long) image.getWidth() * image.getHeight(), "pixels", false, "",
                    image.getWidth() + " x " + image.getHeight());
        }
        if (data instanceof Image image) {
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            if (width < 0 || height < 0) {
                var icon = new ImageIcon(image); // loads the image synchronously
                width = icon.getIconWidth();
                height = icon.getIconHeight();
            }
            return new Content(resultClass, (long) width * height, "pixels", false, "", width + " x " + height);
        }
        if (data instanceof List<?> list) {
            String names = list.stream().limit(3)
                    .map(item -> item instanceof File file ? file.getName() : "?")
                    .collect(Collectors.joining(", "));
            return new Content(resultClass, list.size(), "items", false, "",
                    names + (list.size() > 3 ? ", ..." : ""));
        }
        // Unknown object: report its class only, never its toString().
        return new Content(resultClass, -1, "", false, "", "(" + resultClass + ")");
    }

    private Content readChars(String resultClass, Reader reader) throws IOException {
        StringBuilder text = new StringBuilder();
        char[] chunk = new char[8192];
        int read;
        while (text.length() <= MAX_READ && (read = reader.read(chunk)) != -1) {
            text.append(chunk, 0, read);
        }
        boolean truncated = text.length() > MAX_READ;
        if (truncated) {
            text.setLength(MAX_READ);
        }
        return textContent(resultClass, text.toString(), truncated, text.length(), "chars", "");
    }

    private Content bytesContent(DataFlavor flavor, String resultClass, byte[] bytes, boolean truncated, long size) {
        String bom = bom(bytes);
        if (flavor.isFlavorTextType()) {
            String charsetName = flavor.getParameter("charset");
            Charset charset = charsetOf(charsetName);
            String extra = join(
                    charset == null ? "charset-unknown=" + charsetName + "(decoded as UTF-8)" : null,
                    bom);
            Charset used = charset != null ? charset : StandardCharsets.UTF_8;
            return textContent(resultClass, new String(bytes, used), truncated, size, "bytes", extra);
        }
        int shown = Math.min(bytes.length, 24);
        String hex = HexFormat.ofDelimiter(" ").formatHex(bytes, 0, shown);
        return new Content(resultClass, size, "bytes", truncated, join(bom), "hex: " + hex + (bytes.length > shown ? " ..." : ""));
    }

    private Content textContent(String resultClass, String text, boolean truncated, long size, String unit,
            String extraTraits) {
        return new Content(resultClass, size, unit, truncated, join(extraTraits, traits(text)),
                "\"" + escape(text, PREVIEW_CHARS) + "\"");
    }

    /** Counts the characters that matter for the rules: line endings, tabs and the many kinds of space. */
    static String traits(String text) {
        int crlf = 0;
        int lf = 0;
        int cr = 0;
        int tab = 0;
        int nbsp = 0;
        int narrowNbsp = 0;
        int nul = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\r' -> {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                        crlf++;
                        i++;
                    } else {
                        cr++;
                    }
                }
                case '\n' -> lf++;
                case '\t' -> tab++;
                case ' ' -> nbsp++;
                case ' ' -> narrowNbsp++;
                case '\0' -> nul++;
                default -> {
                }
            }
        }
        StringJoiner out = new StringJoiner(" ");
        if (crlf > 0) {
            out.add("CRLF=" + crlf);
        }
        if (lf > 0) {
            out.add("LF=" + lf);
        }
        if (cr > 0) {
            out.add("lone-CR=" + cr);
        }
        if (tab > 0) {
            out.add("TAB=" + tab);
        }
        if (nbsp > 0) {
            out.add("NBSP=" + nbsp);
        }
        if (narrowNbsp > 0) {
            out.add("NARROW-NBSP=" + narrowNbsp);
        }
        if (nul > 0) {
            out.add("NUL=" + nul);
        }
        if (text.startsWith("Version:")) {
            out.add("CF_HTML-header");
        }
        if (text.endsWith("\n")) {
            out.add("ends-with-newline");
        } else if (text.endsWith(" ")) {
            out.add("ends-with-space");
        }
        return out.toString();
    }

    /** Makes control characters and look-alike spaces visible, and cuts the text at {@code limit} characters. */
    static String escape(String text, int limit) {
        int shown = Math.min(text.length(), limit);
        StringBuilder out = new StringBuilder(shown + 16);
        for (int i = 0; i < shown; i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\r' -> out.append("\\r");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '\\' -> out.append("\\\\");
                default -> {
                    boolean invisible = Character.isISOControl(c)
                            || (c != ' ' && Character.isSpaceChar(c))
                            || Character.getType(c) == Character.FORMAT; // BOM, zero-width space and friends
                    if (invisible) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        if (text.length() > shown) {
            out.append("...(+").append(text.length() - shown).append(" chars)");
        }
        return out.toString();
    }

    private static List<String> nativesFor(DataFlavor flavor) {
        try {
            var map = (SystemFlavorMap) SystemFlavorMap.getDefaultFlavorMap();
            return List.copyOf(map.getNativesForFlavor(flavor));
        } catch (RuntimeException | LinkageError | AWTError e) {
            return List.of("(unavailable: " + e.getClass().getSimpleName() + ")");
        }
    }

    private static Charset charsetOf(String name) {
        if (name == null) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException e) {
            return null; // illegal or unsupported name
        }
    }

    private static String bom(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xff) == 0xEF && (b[1] & 0xff) == 0xBB && (b[2] & 0xff) == 0xBF) {
            return "BOM=UTF-8";
        }
        if (b.length >= 2 && (b[0] & 0xff) == 0xFE && (b[1] & 0xff) == 0xFF) {
            return "BOM=UTF-16BE";
        }
        if (b.length >= 2 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xFE) {
            return "BOM=UTF-16LE";
        }
        return null;
    }

    private static String join(String... parts) {
        StringJoiner out = new StringJoiner(" ");
        for (String part : parts) {
            if (part != null && !part.isEmpty()) {
                out.add(part);
            }
        }
        return out.toString();
    }

    private static String shorten(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static void pause() {
        try {
            Thread.sleep(RETRY_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

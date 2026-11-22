package se.karnerfors.roboreplacer.spike;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Reads the plain-text flavour of the clipboard and nothing else (MVP decision of 2026-11-22: plain text in,
 * plain text out). No HTML, RTF, image or file-list flavour is ever asked for.
 *
 * <p>The result holds no clipboard content, only its length, a short fingerprint and its traits (line endings,
 * tabs, odd spaces), because the product must not log clipboard content (NFR-001, NFR-002) and the spike
 * rehearses that. The fingerprint is the first four bytes of a SHA-256: enough to tell two copies apart, and
 * still guessable for short texts, so the spike log is not safe for real data.
 */
final class PlainTextReader {

    static final int DEFAULT_ATTEMPTS = 5;
    static final long DEFAULT_PAUSE_MILLIS = 40;

    enum Outcome {
        /** Plain text was read. */
        TEXT,
        /** The clipboard holds something, but no plain text (an image, a file list ...). */
        NO_TEXT,
        /** The clipboard holds no formats at all. Excel leaves it like this for a moment during a copy. */
        EMPTY,
        /** Another application kept the clipboard locked for every attempt. */
        BUSY,
        FAILED
    }

    /** What a read found. */
    record Result(Outcome outcome, int attempts, long millis, int length, String fingerprint, String traits,
            String problem) {

        boolean sameContentAs(Result other) {
            return outcome == Outcome.TEXT && other != null && other.outcome == Outcome.TEXT
                    && length == other.length && fingerprint.equals(other.fingerprint);
        }

        String describe() {
            String what = switch (outcome) {
                case TEXT -> "TEXT length=" + length + " fp=" + fingerprint
                        + (traits.isEmpty() ? "" : " traits=[" + traits + "]");
                case NO_TEXT -> "NO_TEXT: something is on the clipboard, but no plain text";
                case EMPTY -> "EMPTY: no formats at all";
                case BUSY -> "BUSY: the clipboard stayed locked by another application";
                case FAILED -> "FAILED: " + problem;
            };
            return what + " | " + attempts + " attempt(s), " + millis + " ms";
        }
    }

    private final Clipboard clipboard;
    private final int maxAttempts;
    private final long pauseMillis;

    PlainTextReader(Clipboard clipboard) {
        this(clipboard, DEFAULT_ATTEMPTS, DEFAULT_PAUSE_MILLIS);
    }

    PlainTextReader(Clipboard clipboard, int maxAttempts, long pauseMillis) {
        this.clipboard = clipboard;
        this.maxAttempts = maxAttempts;
        this.pauseMillis = pauseMillis;
    }

    /** Reads once, retrying while the clipboard is locked by another application. */
    Result read() {
        long start = System.nanoTime();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    Object data = clipboard.getData(DataFlavor.stringFlavor);
                    if (data instanceof String text) {
                        return new Result(Outcome.TEXT, attempt, millisSince(start), text.length(),
                                fingerprint(text), ClipboardDumper.traits(text), "");
                    }
                    return failed(attempt, start,
                            "the String flavour gave " + (data == null ? "null" : data.getClass().getName()));
                }
                Outcome outcome = clipboard.getAvailableDataFlavors().length == 0 ? Outcome.EMPTY : Outcome.NO_TEXT;
                return new Result(outcome, attempt, millisSince(start), 0, "", "", "");
            } catch (IllegalStateException busy) {
                if (attempt < maxAttempts && !pause()) {
                    return failed(attempt, start, "interrupted while waiting for the clipboard");
                }
            } catch (UnsupportedFlavorException | IOException e) {
                return failed(attempt, start, e.getClass().getSimpleName());
            }
        }
        return new Result(Outcome.BUSY, maxAttempts, millisSince(start), 0, "", "", "");
    }

    private boolean pause() {
        try {
            Thread.sleep(pauseMillis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Result failed(int attempt, long start, String problem) {
        return new Result(Outcome.FAILED, attempt, millisSince(start), 0, "", "", problem);
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String fingerprint(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

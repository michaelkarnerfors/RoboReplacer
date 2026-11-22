package se.karnerfors.roboreplacer.spike;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The few {@code user32} clipboard calls that need no open clipboard, reached through the Foreign Function API
 * (java.lang.foreign, final since JDK 22). Because nothing here opens the clipboard, calling it cannot make
 * another application's copy fail.
 *
 * <ul>
 *   <li>{@code GetClipboardSequenceNumber}: changes on every clipboard change, whatever the formats are;</li>
 *   <li>{@code CountClipboardFormats}: how many native formats are on the clipboard, to compare with what AWT
 *       shows (H2);</li>
 *   <li>{@code IsClipboardFormatAvailable} for the formats that password managers use to say "do not record
 *       this" (H7), which AWT hides.</li>
 * </ul>
 *
 * <p>Restricted methods: run the JVM with {@code --enable-native-access=ALL-UNNAMED}, or JDK 24 and later
 * prints a warning (the jar's manifest only counts when started with {@code java -jar}).
 */
final class WindowsClipboard implements ClipboardSequence {

    /** The name of the flag that clipboard monitors must honour: its presence alone means "leave this copy alone". */
    static final String EXCLUDE_FROM_MONITORS = "ExcludeClipboardContentFromMonitorProcessing";

    /**
     * Registered format names that applications add to a copy. Only presence is checked here; the two
     * {@code Can...} flags matter when their value is zero, which needs the clipboard opened to read.
     */
    private static final List<String> FLAG_FORMATS = List.of(
            EXCLUDE_FROM_MONITORS,
            "CanIncludeInClipboardHistory",
            "CanUploadToCloudClipboard");

    private final MethodHandle getSequenceNumber;
    private final MethodHandle countFormats;
    private final MethodHandle isFormatAvailable;
    private final Map<String, Integer> flagFormatIds = new LinkedHashMap<>();

    WindowsClipboard() {
        Linker linker = Linker.nativeLinker();
        SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
        getSequenceNumber = linker.downcallHandle(
                find(user32, "GetClipboardSequenceNumber"), FunctionDescriptor.of(ValueLayout.JAVA_INT));
        countFormats = linker.downcallHandle(
                find(user32, "CountClipboardFormats"), FunctionDescriptor.of(ValueLayout.JAVA_INT));
        isFormatAvailable = linker.downcallHandle(
                find(user32, "IsClipboardFormatAvailable"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        MethodHandle registerFormat = linker.downcallHandle(
                find(user32, "RegisterClipboardFormatW"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        try (Arena arena = Arena.ofConfined()) {
            for (String name : FLAG_FORMATS) {
                flagFormatIds.put(name, register(registerFormat, arena, name));
            }
        }
    }

    @Override
    public long current() {
        try {
            return Integer.toUnsignedLong((int) getSequenceNumber.invokeExact());
        } catch (Throwable t) {
            throw rethrow("GetClipboardSequenceNumber", t);
        }
    }

    /** How many native formats the clipboard holds right now (0 when it is empty, or on failure). */
    int formatCount() {
        try {
            return (int) countFormats.invokeExact();
        } catch (Throwable t) {
            throw rethrow("CountClipboardFormats", t);
        }
    }

    /** The names, among {@link #FLAG_FORMATS}, that the current clipboard content carries. */
    List<String> flagsPresent() {
        List<String> present = new ArrayList<>();
        for (Map.Entry<String, Integer> flag : flagFormatIds.entrySet()) {
            if (available(flag.getValue())) {
                present.add(flag.getKey());
            }
        }
        return present;
    }

    private boolean available(int formatId) {
        try {
            return (int) isFormatAvailable.invokeExact(formatId) != 0;
        } catch (Throwable t) {
            throw rethrow("IsClipboardFormatAvailable", t);
        }
    }

    private static int register(MethodHandle registerFormat, Arena arena, String name) {
        byte[] wide = (name + "\0").getBytes(StandardCharsets.UTF_16LE);
        MemorySegment segment = arena.allocate(wide.length);
        MemorySegment.copy(wide, 0, segment, ValueLayout.JAVA_BYTE, 0, wide.length);
        try {
            return (int) registerFormat.invokeExact(segment);
        } catch (Throwable t) {
            throw rethrow("RegisterClipboardFormatW", t);
        }
    }

    private static MemorySegment find(SymbolLookup lookup, String function) {
        return lookup.find(function).orElseThrow(() -> new IllegalStateException("user32 has no " + function));
    }

    private static RuntimeException rethrow(String function, Throwable cause) {
        if (cause instanceof Error error) {
            throw error;
        }
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException(function + " failed", cause);
    }
}

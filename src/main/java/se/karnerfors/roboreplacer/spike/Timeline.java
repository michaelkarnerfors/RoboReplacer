package se.karnerfors.roboreplacer.spike;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * One time-ordered log for everything the trigger comparison sees, from several threads: each line starts with
 * the time of day and a source tag, so the listener, the poller, the reads and our own writes can be read side
 * by side. Lines go to a file (when one is given) and to a sink, normally the window.
 */
final class Timeline {

    private static final Logger LOG = Logger.getLogger(Timeline.class.getName());
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Path file;
    private final Consumer<String> sink;
    private boolean fileFailureReported;

    Timeline(Path file, Consumer<String> sink) {
        this.file = file;
        this.sink = sink;
    }

    /** One timestamped line: {@code 15:52:49.588  POLL      seq 41 -> 43}. */
    synchronized void line(String source, String text) {
        raw(LocalTime.now().format(TIME) + "  " + pad(source) + " " + text + "\n");
    }

    /** Text as it is, for session headers and markers. */
    synchronized void raw(String text) {
        if (file != null) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, text, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                if (!fileFailureReported) {
                    fileFailureReported = true;
                    LOG.warning("Could not write " + file + ": " + e);
                }
            }
        }
        sink.accept(text);
    }

    private static String pad(String source) {
        return source.length() >= 9 ? source : source + " ".repeat(9 - source.length());
    }
}

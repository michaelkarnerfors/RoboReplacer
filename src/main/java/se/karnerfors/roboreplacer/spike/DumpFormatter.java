package se.karnerfors.roboreplacer.spike;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Turns a {@link ClipboardDumper.Snapshot} into the text shown in the window and written to the log file. */
final class DumpFormatter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");

    private DumpFormatter() {
    }

    /**
     * @param trigger     what caused the dump ("FlavorListener" or "manual")
     * @param eventNumber number of the FlavorListener event, or {@code null} for a manual dump
     * @param when        when the trigger happened
     * @param sinceLastMs milliseconds since the previous FlavorListener event, or {@code null} for the first one
     */
    static String format(String trigger, Integer eventNumber, Instant when, Long sinceLastMs,
            ClipboardDumper.Snapshot snapshot) {
        StringBuilder out = new StringBuilder();
        out.append("=== ").append(eventNumber == null ? "manual dump" : "event #" + eventNumber)
                .append(" [").append(trigger).append("] ")
                .append(TIME.format(when.atZone(ZoneId.systemDefault())));
        if (sinceLastMs != null) {
            out.append("  (+").append(sinceLastMs).append(" ms since previous event)");
        }
        out.append('\n');

        if (snapshot.flavours().isEmpty()) {
            out.append("    no flavours: ")
                    .append(snapshot.problem() != null ? snapshot.problem() : "clipboard is empty")
                    .append(" (").append(snapshot.attempts()).append(" attempt(s), ")
                    .append(snapshot.millis()).append(" ms)\n\n");
            return out.toString();
        }

        Set<String> natives = new LinkedHashSet<>();
        int read = 0;
        int skipped = 0;
        int failed = 0;
        for (FlavourReport report : snapshot.flavours()) {
            natives.addAll(report.getNativeFormats());
            switch (report.getOutcome()) {
                case READ -> read++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }
        out.append("    ").append(snapshot.flavours().size()).append(" flavours (")
                .append(read).append(" read, ").append(skipped).append(" skipped, ").append(failed)
                .append(" failed) | took ").append(snapshot.millis()).append(" ms, ")
                .append(snapshot.attempts()).append(" attempt(s)\n");
        out.append("    native format candidates: ").append(String.join(", ", natives)).append('\n');

        Map<String, Integer> firstSeen = new HashMap<>();
        for (FlavourReport report : snapshot.flavours()) {
            out.append(String.format("[%2d] %s\n", report.getIndex(), report.getMimeType()));
            out.append("     ").append(report.getOutcome());
            switch (report.getOutcome()) {
                case READ -> {
                    out.append("  ").append(report.getResultClass());
                    if (report.getSize() >= 0) {
                        out.append(", ").append(report.isTruncated() ? ">= " : "").append(report.getSize())
                                .append(' ').append(report.getSizeUnit());
                    }
                    if (!report.getTraits().isEmpty()) {
                        out.append(" | ").append(report.getTraits());
                    }
                }
                case SKIPPED, FAILED -> out.append("  ").append(report.getNote());
            }
            out.append(" | maps to: ").append(String.join(", ", report.getNativeFormats())).append('\n');

            String preview = report.getPreview();
            if (report.getOutcome() == FlavourReport.Outcome.READ && !preview.isEmpty()) {
                Integer earlier = firstSeen.putIfAbsent(preview, report.getIndex());
                out.append("     ").append(earlier == null ? preview : "= same excerpt as [" + earlier + "]")
                        .append('\n');
            }
        }
        out.append('\n');
        return out.toString();
    }
}

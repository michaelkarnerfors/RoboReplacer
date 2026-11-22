package se.karnerfors.roboreplacer.spike;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

/**
 * Throw-away spike, round 2: which trigger is reliable enough for FR-001? It watches the clipboard in two ways
 * at once and writes both into one time-ordered log:
 *
 * <ul>
 *   <li>AWT's {@code FlavorListener}, which the first spike showed is silent when a copy has the same set of
 *       formats as the one before it (T2), yet fires twice for one Excel copy;</li>
 *   <li>a poller that looks at {@code GetClipboardSequenceNumber} every {@value #POLL_MILLIS_DEFAULT} ms,
 *       waits for a quiet period (so an Excel copy counts once, when it is complete), and then reads the
 *       plain-text flavour only.</li>
 * </ul>
 *
 * <p>It also rehearses two things the product needs: a loop guard (the "Write synthetic text" button copies a
 * text of our own and the log shows whether the burst it causes is recognised as ours), and the exclusion flags
 * that password managers set (a copy that carries one is not read at all).
 *
 * <p>Windows only; run it on the Windows JVM with {@code --enable-native-access=ALL-UNNAMED}. The log,
 * {@code target/spike/triggers.log}, holds no clipboard content, only sequence numbers, lengths, a short
 * fingerprint and traits. Tune with {@code -Dspike.pollMillis=} and {@code -Dspike.quietMillis=}.
 */
public final class TriggerCompareSpike {

    static final int POLL_MILLIS_DEFAULT = 20;
    private static final Logger LOG = Logger.getLogger(TriggerCompareSpike.class.getName());
    private static final Path LOG_FILE = Path.of("target", "spike", "triggers.log");
    private static final int POLL_MILLIS = Integer.getInteger("spike.pollMillis", POLL_MILLIS_DEFAULT);
    private static final int QUIET_MILLIS = Integer.getInteger("spike.quietMillis", 100);

    private final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    private final WindowsClipboard windows = new WindowsClipboard();
    private final JTextArea output = new JTextArea(32, 120);
    private final JLabel status = new JLabel(" ");
    private final Timeline timeline = new Timeline(LOG_FILE, this::appendToView);
    private final PlainTextReader reader = new PlainTextReader(clipboard);
    private final SequenceWatcher watcher = new SequenceWatcher(windows, QUIET_MILLIS, new WatcherEvents());
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "clipboard-sequence-poller");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicInteger listenerEvents = new AtomicInteger();
    private final AtomicInteger pollChanges = new AtomicInteger();
    private final AtomicInteger bursts = new AtomicInteger();
    private final AtomicInteger reads = new AtomicInteger();
    /** The sequence number right after our own latest write, or -1 before the first one. */
    private final AtomicLong ownSeq = new AtomicLong(-1);

    // Touched on the event dispatch thread only.
    private long lastListenerSeq = -1;

    // Touched on the poller thread only (every read and write is queued there).
    private PlainTextReader.Result previousText;
    private int ownWrites;
    private String lastError = "";

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("The trigger comparison needs a graphical environment.");
            System.exit(1);
        }
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            System.err.println("The trigger comparison needs Windows: it calls user32 for the clipboard sequence number.");
            System.exit(1);
        }
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> {
            try {
                new TriggerCompareSpike().show();
            } catch (RuntimeException | LinkageError e) {
                LOG.log(Level.SEVERE, "Could not start the trigger comparison", e);
                System.exit(1);
            }
        });
    }

    private void show() {
        JFrame frame = new JFrame("RoboReplacer trigger comparison spike (throw-away)");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JLabel warning = new JLabel("<html><body style='width: 880px'><b>Spike, round 2.</b> Compares the AWT "
                + "FlavorListener with polling the clipboard sequence number every " + POLL_MILLIS + " ms, with "
                + QUIET_MILLIS + " ms of quiet before one plain-text read. The log holds no clipboard content "
                + "(sequence numbers, lengths, a short fingerprint, traits), but copy synthetic data only. "
                + "Log: <code>" + LOG_FILE.toAbsolutePath() + "</code></html>");
        warning.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JTextField markerText = new JTextField("R1", 10);
        JButton mark = new JButton("Add marker");
        mark.setToolTipText("Writes a labelled line into the window and the log, to tell test cases apart");
        mark.addActionListener(_ -> addMarker(markerText.getText()));
        markerText.addActionListener(_ -> addMarker(markerText.getText()));
        JButton write = new JButton("Write synthetic text");
        write.setToolTipText("Loop-guard test: copies a text of our own and shows how the log sees it");
        write.addActionListener(_ -> poller.execute(guarded(this::writeSynthetic)));
        JButton readNow = new JButton("Read text now");
        readNow.addActionListener(_ -> poller.execute(guarded(() -> readAndLog("manual read"))));
        JButton clear = new JButton("Clear view");
        clear.addActionListener(_ -> output.setText(""));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(markerText);
        buttons.add(mark);
        buttons.add(write);
        buttons.add(readNow);
        buttons.add(clear);
        buttons.add(status);

        frame.add(warning, BorderLayout.NORTH);
        frame.add(new JScrollPane(output), BorderLayout.CENTER);
        frame.add(buttons, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        long startSeq = windows.current();
        timeline.raw(sessionHeader(startSeq));
        if (startSeq == 0) {
            timeline.line("WARNING", "the sequence number is 0: this process may not be allowed to look at the clipboard");
        }
        clipboard.addFlavorListener(_ -> onFlavorsChanged());
        poller.scheduleWithFixedDelay(this::tick, 0, POLL_MILLIS, TimeUnit.MILLISECONDS);
        new Timer(250, _ -> updateStatus()).start();
        LOG.info("Comparing triggers. The timeline is appended to " + LOG_FILE.toAbsolutePath());
    }

    /** AWT calls this on the event dispatch thread. */
    private void onFlavorsChanged() {
        int number = listenerEvents.incrementAndGet();
        long seq = windows.current();
        String since = lastListenerSeq < 0
                ? ""
                : " (+" + ClipboardSequence.distance(lastListenerSeq, seq) + " since the previous event)";
        lastListenerSeq = seq;
        timeline.line("LISTENER", "FlavorListener event #" + number + ", seq now " + seq + since
                + (seq == ownSeq.get() ? "  [our own write]" : ""));
    }

    private void tick() {
        try {
            watcher.tick(System.nanoTime());
        } catch (Throwable t) {
            // An exception must not stop the schedule: report it, once per kind, and carry on.
            reportOnce(t);
        }
    }

    /** Reads the plain text, after checking the exclusion flags. Poller thread only. */
    private void readAndLog(String why) {
        List<String> flags;
        int nativeFormats;
        try {
            flags = windows.flagsPresent();
            nativeFormats = windows.formatCount();
        } catch (RuntimeException e) {
            timeline.line("ERROR", "the native flag check failed: " + e.getClass().getSimpleName());
            flags = List.of();
            nativeFormats = -1;
        }
        String facts = "native formats=" + nativeFormats + ", flags=" + (flags.isEmpty() ? "none" : String.join(", ", flags));
        if (flags.contains(WindowsClipboard.EXCLUDE_FROM_MONITORS)) {
            timeline.line("SKIP", why + ": " + facts + ". The copy asks monitors to leave it alone, so it is not read.");
            return;
        }
        PlainTextReader.Result result = reader.read();
        reads.incrementAndGet();
        String comparison = "";
        if (result.outcome() == PlainTextReader.Outcome.TEXT) {
            if (previousText != null) {
                comparison = result.sameContentAs(previousText)
                        ? " | same content as the previous text read"
                        : " | different content from the previous text read";
            }
            previousText = result;
        }
        timeline.line("READ", why + ": " + facts + " | " + result.describe() + comparison);
    }

    /** Copies a text of our own, so the log can show how a self-made change looks. Poller thread only. */
    private void writeSynthetic() {
        int number = ++ownWrites;
        String text = "ROBO-SPIKE-" + number;
        long before = windows.current();
        long started = System.nanoTime();
        try {
            clipboard.setContents(new StringSelection(text), (_, _) -> timeline.line("OWNER",
                    "lostOwnership: our contents were replaced (by another application, or by our own next write)"));
        } catch (IllegalStateException busy) {
            timeline.line("WRITE", "own write #" + number + " failed: the clipboard was locked");
            return;
        }
        long after = windows.current();
        ownSeq.set(after);
        timeline.line("WRITE", "own write #" + number + " (" + text + "): seq " + before + " -> " + after
                + " (+" + ClipboardSequence.distance(before, after) + "), took "
                + (System.nanoTime() - started) / 1_000_000 + " ms");
    }

    private void addMarker(String label) {
        timeline.raw("### " + label + "\n");
    }

    private void updateStatus() {
        status.setText("seq=" + windows.current() + " | listener events=" + listenerEvents.get()
                + " | poll changes=" + pollChanges.get() + " | settled bursts=" + bursts.get()
                + " | reads=" + reads.get());
    }

    private void appendToView(String text) {
        SwingUtilities.invokeLater(() -> {
            output.append(text);
            output.setCaretPosition(output.getDocument().getLength());
        });
    }

    private Runnable guarded(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable t) {
                reportOnce(t);
            }
        };
    }

    private synchronized void reportOnce(Throwable t) {
        String kind = t.getClass().getName();
        if (!kind.equals(lastError)) {
            lastError = kind;
            timeline.line("ERROR", t.toString());
        }
    }

    private String sessionHeader(long startSeq) {
        return "# ---- trigger spike session started " + Instant.now()
                + " | java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")"
                + " | " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " | poll=" + POLL_MILLIS + " ms, quiet=" + QUIET_MILLIS + " ms"
                + " | seq at start=" + startSeq + "\n\n";
    }

    /** What the poller sees. Runs on the poller thread. */
    private final class WatcherEvents implements SequenceWatcher.Listener {

        private boolean anyChange;
        private long lastChangeNanos;

        @Override
        public void changed(SequenceWatcher.Change change) {
            pollChanges.incrementAndGet();
            String since = anyChange
                    ? ", +" + (change.atNanos() - lastChangeNanos) / 1_000_000 + " ms after the previous change"
                    : "";
            anyChange = true;
            lastChangeNanos = change.atNanos();
            timeline.line("POLL", "seq " + change.previous() + " -> " + change.current()
                    + " (+" + change.delta() + ")" + since
                    + (change.current() == ownSeq.get() ? "  [our own write]" : ""));
        }

        @Override
        public void settled(SequenceWatcher.Burst burst) {
            bursts.incrementAndGet();
            boolean own = burst.last() == ownSeq.get();
            timeline.line("SETTLED", burst.changes() + " change(s), seq " + burst.firstPrevious() + " -> "
                    + burst.last() + " (+" + burst.totalDelta() + ") within " + burst.spanMillis()
                    + " ms, then " + burst.quietMillis() + " ms quiet"
                    + (own ? "  [ends at our own write: not read, loop guard]" : ""));
            if (!own) {
                readAndLog("read after the burst");
            }
        }
    }
}

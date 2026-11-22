package se.karnerfors.roboreplacer.spike;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import lombok.extern.java.Log;

/**
 * Throw-away spike for the v0.2.0 clipboard question (OQ-1): listen to the system clipboard with AWT's
 * {@code FlavorListener} and, on every notification, dump every flavour Java offers, so we can see what
 * real applications put there and what the JDK makes of it.
 *
 * <p>Run it on the Windows JVM, not in WSL. Not production code: it shows excerpts of clipboard content in a
 * window and appends them to {@code target/spike/flavours.log}, which the product must never do (NFR-002).
 *
 * <p>Besides the automatic dump there is a "Dump clipboard now" button. If you copy something and nothing
 * shows up, press it: when the button shows new content, the clipboard changed without a notification.
 */
@Log
public final class ClipboardSpike {

    private static final Path LOG_FILE = Path.of("target", "spike", "flavours.log");

    private final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    private final ClipboardDumper dumper = new ClipboardDumper(clipboard);
    private final JTextArea output = new JTextArea(32, 120);
    private final JLabel status = new JLabel();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "clipboard-dumper");
        thread.setDaemon(true);
        return thread;
    });

    // Touched on the event dispatch thread only.
    private int events;
    private boolean anyEventYet;
    private long lastEventNanos;

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("The clipboard spike needs a graphical environment.");
            System.exit(1);
        }
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> new ClipboardSpike().show());
    }

    private void show() {
        JFrame frame = new JFrame("RoboReplacer clipboard spike (throw-away)");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JLabel warning = new JLabel("<html><body style='width: 880px'><b>Spike.</b> Every copy you make while this runs is excerpted "
                + "(first " + ClipboardDumper.PREVIEW_CHARS + " characters per format) in this window and appended to "
                + "<code>" + LOG_FILE.toAbsolutePath() + "</code>. Copy synthetic data only.</html>");
        warning.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JButton dumpNow = new JButton("Dump clipboard now");
        dumpNow.addActionListener(_ -> submit("manual", null, null));
        JButton clear = new JButton("Clear view");
        clear.addActionListener(_ -> output.setText(""));
        JTextField markerText = new JTextField("T1", 10);
        JButton mark = new JButton("Add marker");
        mark.setToolTipText("Writes a labelled line into the window and the log, to tell test cases apart");
        mark.addActionListener(_ -> publish("### " + markerText.getText() + "\n\n"));
        markerText.addActionListener(_ -> publish("### " + markerText.getText() + "\n\n"));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(dumpNow);
        buttons.add(clear);
        buttons.add(markerText);
        buttons.add(mark);
        buttons.add(status);

        frame.add(warning, BorderLayout.NORTH);
        frame.add(new JScrollPane(output), BorderLayout.CENTER);
        frame.add(buttons, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        writeToFile(sessionHeader());
        updateStatus();
        clipboard.addFlavorListener(_ -> onFlavorsChanged());
        log.info("Listening to the system clipboard. Dumps are appended to " + LOG_FILE.toAbsolutePath());
    }

    /** Called by AWT on the event dispatch thread. Keep it cheap: the reading happens on the worker thread. */
    private void onFlavorsChanged() {
        events++;
        long now = System.nanoTime();
        Long sinceLastMs = anyEventYet ? (now - lastEventNanos) / 1_000_000 : null;
        anyEventYet = true;
        lastEventNanos = now;
        updateStatus();
        submit("FlavorListener", events, sinceLastMs);
    }

    private void submit(String trigger, Integer eventNumber, Long sinceLastMs) {
        Instant when = Instant.now();
        worker.submit(() -> {
            String text;
            try {
                text = DumpFormatter.format(trigger, eventNumber, when, sinceLastMs, dumper.inspect());
            } catch (RuntimeException e) {
                text = "=== dump failed [" + trigger + "]: " + e + "\n\n";
            }
            publish(text);
        });
    }

    private void publish(String text) {
        writeToFile(text);
        SwingUtilities.invokeLater(() -> {
            output.append(text);
            output.setCaretPosition(output.getDocument().getLength());
        });
    }

    private void updateStatus() {
        status.setText("Listening. FlavorListener notifications so far: " + events);
    }

    private static String sessionHeader() {
        return "# ---- spike session started " + Instant.now()
                + " | java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")"
                + " | " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " | headless=" + GraphicsEnvironment.isHeadless() + "\n\n";
    }

    private void writeToFile(String text) {
        try {
            Files.createDirectories(LOG_FILE.getParent());
            Files.writeString(LOG_FILE, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warning("Could not write " + LOG_FILE + ": " + e);
        }
    }
}

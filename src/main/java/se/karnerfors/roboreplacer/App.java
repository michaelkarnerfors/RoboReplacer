package se.karnerfors.roboreplacer;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.GraphicsEnvironment;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Application entry point. */
public final class App {

    public static final String NAME = "RoboReplacer";

    private App() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println(NAME + " needs a graphical environment.");
            System.exit(1);
        }
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(App::showMainWindow);
    }

    private static void showMainWindow() {
        JFrame frame = new JFrame(NAME);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setContentPane(new MainPanel());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

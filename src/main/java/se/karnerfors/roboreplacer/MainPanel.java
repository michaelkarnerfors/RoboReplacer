package se.karnerfors.roboreplacer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Placeholder content for the main window. It is replaced by the real user
 * interface in later milestones. Kept free of windows so it can be tested
 * without a display.
 */
@SuppressWarnings("serial") // Swing components are never serialized here
final class MainPanel extends JPanel {

    private final JLabel title = new JLabel(App.NAME, SwingConstants.CENTER);

    MainPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        setPreferredSize(new Dimension(420, 160));
        add(title, BorderLayout.CENTER);
        add(new JLabel("Under construction", SwingConstants.CENTER), BorderLayout.SOUTH);
    }

    JLabel titleLabel() {
        return title;
    }
}

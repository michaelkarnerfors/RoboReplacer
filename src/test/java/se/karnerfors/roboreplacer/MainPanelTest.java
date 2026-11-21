package se.karnerfors.roboreplacer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Placeholder test that proves the test setup runs in CI, including on
 * machines without a display. Real tests arrive with the rule engine.
 */
class MainPanelTest {

    @Test
    void showsTheApplicationName() {
        MainPanel panel = new MainPanel();

        assertEquals("RoboReplacer", panel.titleLabel().getText());
    }
}

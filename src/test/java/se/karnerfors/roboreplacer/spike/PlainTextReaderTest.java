package se.karnerfors.roboreplacer.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import org.junit.jupiter.api.Test;

/** The plain-text reader against a private in-memory clipboard, so it runs anywhere, with or without a display. */
class PlainTextReaderTest {

    private final Clipboard clipboard = new Clipboard("test");
    private final PlainTextReader reader = new PlainTextReader(clipboard, 3, 1);

    @Test
    void readsPlainTextAndDescribesItWithoutRevealingIt() {
        clipboard.setContents(new StringSelection("1 234,56\nsecret-looking-word\n"), null);

        PlainTextReader.Result result = reader.read();

        assertEquals(PlainTextReader.Outcome.TEXT, result.outcome());
        assertEquals(29, result.length());
        assertEquals(1, result.attempts());
        assertTrue(result.traits().contains("LF=2"));
        assertFalse(result.describe().contains("secret-looking-word"));
        assertFalse(result.describe().contains("1 234,56"));
    }

    @Test
    void sameTextGivesTheSameFingerprintAndOtherTextDoesNot() {
        clipboard.setContents(new StringSelection("abc"), null);
        PlainTextReader.Result first = reader.read();
        clipboard.setContents(new StringSelection("abc"), null);
        PlainTextReader.Result again = reader.read();
        clipboard.setContents(new StringSelection("abd"), null);
        PlainTextReader.Result other = reader.read();

        assertTrue(first.sameContentAs(again));
        assertFalse(first.sameContentAs(other));
        assertNotEquals(first.fingerprint(), other.fingerprint());
    }

    @Test
    void anEmptyClipboardIsEmpty() {
        assertEquals(PlainTextReader.Outcome.EMPTY, reader.read().outcome());
    }

    @Test
    void somethingThatIsNotTextIsNoText() {
        clipboard.setContents(new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] {DataFlavor.imageFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.imageFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                throw new UnsupportedFlavorException(flavor);
            }
        }, null);

        assertEquals(PlainTextReader.Outcome.NO_TEXT, reader.read().outcome());
    }

    @Test
    void retriesWhileTheClipboardIsLocked() {
        BusyClipboard busy = new BusyClipboard(2);
        busy.setContents(new StringSelection("abc"), null);

        PlainTextReader.Result result = new PlainTextReader(busy, 5, 1).read();

        assertEquals(PlainTextReader.Outcome.TEXT, result.outcome());
        assertEquals(3, result.attempts());
    }

    @Test
    void givesUpWhenTheClipboardStaysLocked() {
        BusyClipboard busy = new BusyClipboard(100);
        busy.setContents(new StringSelection("abc"), null);

        PlainTextReader.Result result = new PlainTextReader(busy, 3, 1).read();

        assertEquals(PlainTextReader.Outcome.BUSY, result.outcome());
        assertEquals(3, result.attempts());
    }

    /** Throws the "cannot open system clipboard" error a few times before it lets the caller in. */
    private static final class BusyClipboard extends Clipboard {

        private int failuresLeft;

        BusyClipboard(int failures) {
            super("busy");
            this.failuresLeft = failures;
        }

        @Override
        public boolean isDataFlavorAvailable(DataFlavor flavor) {
            if (failuresLeft > 0) {
                failuresLeft--;
                throw new IllegalStateException("cannot open system clipboard");
            }
            return super.isDataFlavorAvailable(flavor);
        }
    }
}

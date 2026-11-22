package se.karnerfors.roboreplacer.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The watcher's bookkeeping, with a fake sequence number and a fake clock: no clipboard, no sleeping. */
class SequenceWatcherTest {

    private static final long QUIET_MILLIS = 100;

    private long sequence = 1000;
    private long now;
    private final List<SequenceWatcher.Change> changes = new ArrayList<>();
    private final List<SequenceWatcher.Burst> bursts = new ArrayList<>();
    private final SequenceWatcher watcher = new SequenceWatcher(() -> sequence, QUIET_MILLIS,
            new SequenceWatcher.Listener() {
                @Override
                public void changed(SequenceWatcher.Change change) {
                    changes.add(change);
                }

                @Override
                public void settled(SequenceWatcher.Burst burst) {
                    bursts.add(burst);
                }
            });

    private void tickAfter(long millis) {
        now += millis * 1_000_000;
        watcher.tick(now);
    }

    @Test
    void theFirstLookOnlyRecordsTheStartingValue() {
        tickAfter(0);
        tickAfter(500);

        assertTrue(changes.isEmpty());
        assertTrue(bursts.isEmpty());
        assertEquals(1000, watcher.lastSeen());
    }

    @Test
    void aSingleChangeIsReportedAndSettlesAfterTheQuietPeriod() {
        tickAfter(0);
        sequence = 1002;
        tickAfter(20);
        tickAfter(20);
        assertEquals(1, changes.size());
        assertEquals(1000, changes.get(0).previous());
        assertEquals(1002, changes.get(0).current());
        assertEquals(2, changes.get(0).delta());
        assertTrue(bursts.isEmpty());

        tickAfter(QUIET_MILLIS);

        assertEquals(1, bursts.size());
        assertEquals(1, bursts.get(0).changes());
        assertEquals(1000, bursts.get(0).firstPrevious());
        assertEquals(1002, bursts.get(0).last());
        assertEquals(2, bursts.get(0).totalDelta());
    }

    @Test
    void changesWithinTheQuietPeriodFormOneBurst() {
        tickAfter(0);
        sequence = 1001;
        tickAfter(20);
        sequence = 1003;
        tickAfter(20);
        tickAfter(60);
        assertTrue(bursts.isEmpty());

        tickAfter(60);

        assertEquals(2, changes.size());
        assertEquals(1, bursts.size());
        assertEquals(2, bursts.get(0).changes());
        assertEquals(3, bursts.get(0).totalDelta());
        assertEquals(1003, bursts.get(0).last());
        assertEquals(20, bursts.get(0).spanMillis());
    }

    @Test
    void aBurstSettlesOnlyOnce() {
        tickAfter(0);
        sequence = 1001;
        tickAfter(20);
        tickAfter(QUIET_MILLIS);
        tickAfter(QUIET_MILLIS);
        tickAfter(QUIET_MILLIS);

        assertEquals(1, bursts.size());
    }

    @Test
    void aLaterChangeStartsANewBurst() {
        tickAfter(0);
        sequence = 1001;
        tickAfter(20);
        tickAfter(QUIET_MILLIS);
        sequence = 1005;
        tickAfter(20);
        tickAfter(QUIET_MILLIS);

        assertEquals(2, bursts.size());
        assertEquals(1001, bursts.get(1).firstPrevious());
        assertEquals(4, bursts.get(1).totalDelta());
    }

    @Test
    void theWrapAroundAt32BitsIsNotANegativeJump() {
        sequence = 0xFFFF_FFFFL;
        tickAfter(0);
        sequence = 1;
        tickAfter(20);

        assertEquals(2, changes.get(0).delta());
    }

    @Test
    void noChangeMeansNoEvents() {
        tickAfter(0);
        for (int i = 0; i < 50; i++) {
            tickAfter(20);
        }

        assertTrue(changes.isEmpty());
        assertTrue(bursts.isEmpty());
    }
}

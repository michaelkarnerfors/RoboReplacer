package se.karnerfors.roboreplacer.spike;

/**
 * Turns repeated looks at the clipboard sequence number into "changed" and "settled" events. Not thread-safe by
 * design: one polling thread calls {@link #tick(long)}, and the clock is passed in so tests need no sleeping.
 *
 * <p>A <em>burst</em> is a run of changes that each follow the previous one within the quiet period. One copy in
 * Excel is such a burst (an empty clipboard, then the formats, a few milliseconds apart), and so is our own
 * write. The point of waiting for the burst to settle is to read the clipboard once, when it is complete.
 */
final class SequenceWatcher {

    /** One observed change of the sequence number. */
    record Change(long previous, long current, long delta, long atNanos) {
    }

    /** A settled burst: the changes since the clipboard was last quiet. */
    record Burst(long firstPrevious, long last, long totalDelta, int changes,
            long firstAtNanos, long lastAtNanos, long settledAtNanos) {

        long spanMillis() {
            return (lastAtNanos - firstAtNanos) / 1_000_000;
        }

        long quietMillis() {
            return (settledAtNanos - lastAtNanos) / 1_000_000;
        }
    }

    interface Listener {

        /** Every change, as soon as the poll sees it. */
        void changed(Change change);

        /** Once per burst, when no further change has come for the quiet period. */
        void settled(Burst burst);
    }

    private final ClipboardSequence source;
    private final long quietNanos;
    private final Listener listener;

    private boolean primed;
    private long lastSeen;
    private boolean inBurst;
    private long burstFirstPrevious;
    private long burstTotal;
    private int burstChanges;
    private long burstFirstAt;
    private long burstLastAt;

    SequenceWatcher(ClipboardSequence source, long quietMillis, Listener listener) {
        this.source = source;
        this.quietNanos = quietMillis * 1_000_000;
        this.listener = listener;
    }

    /** Looks at the sequence number once. The first call only records the starting value. */
    void tick(long nowNanos) {
        long current = source.current();
        if (!primed) {
            lastSeen = current;
            primed = true;
            return;
        }
        if (current != lastSeen) {
            long delta = ClipboardSequence.distance(lastSeen, current);
            if (!inBurst) {
                inBurst = true;
                burstFirstPrevious = lastSeen;
                burstTotal = 0;
                burstChanges = 0;
                burstFirstAt = nowNanos;
            }
            burstTotal += delta;
            burstChanges++;
            burstLastAt = nowNanos;
            Change change = new Change(lastSeen, current, delta, nowNanos);
            lastSeen = current;
            listener.changed(change);
        } else if (inBurst && nowNanos - burstLastAt >= quietNanos) {
            inBurst = false;
            listener.settled(new Burst(burstFirstPrevious, lastSeen, burstTotal, burstChanges,
                    burstFirstAt, burstLastAt, nowNanos));
        }
    }

    /** The sequence number as of the last tick. */
    long lastSeen() {
        return lastSeen;
    }
}

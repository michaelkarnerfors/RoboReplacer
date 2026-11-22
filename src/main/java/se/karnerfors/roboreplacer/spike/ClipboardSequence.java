package se.karnerfors.roboreplacer.spike;

/**
 * Source of the Windows clipboard sequence number. It is an interface so that {@link SequenceWatcher} can be
 * tested without a clipboard.
 */
interface ClipboardSequence {

    /**
     * The sequence number: an unsigned 32-bit value that Windows bumps whenever the clipboard changes. It is
     * returned as a {@code long} so that it never turns negative. Zero means the process may not look at the
     * clipboard at all (no WINSTA_ACCESSCLIPBOARD).
     */
    long current();

    /** Distance from {@code previous} to {@code current}, allowing for the wrap-around at 2^32. */
    static long distance(long previous, long current) {
        return (current - previous) & 0xFFFF_FFFFL;
    }
}

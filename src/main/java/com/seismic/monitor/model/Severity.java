package com.seismic.monitor.model;

/**
 * Alert severity levels (FR-05), declared in ascending order of priority so that
 * {@link #compareTo} and {@link #ordinal()} reflect urgency.
 */
public enum Severity {
    LOW,
    MEDIUM,
    CRITICAL;

    /** Returns the next-higher severity, capped at {@link #CRITICAL}. */
    public Severity escalate() {
        Severity[] all = values();
        return all[Math.min(ordinal() + 1, all.length - 1)];
    }

    /** Returns whichever of the two severities is higher. */
    public static Severity max(Severity a, Severity b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}

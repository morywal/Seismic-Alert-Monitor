package com.seismic.monitor.rules;

import com.seismic.monitor.model.SeismicEvent;
import com.seismic.monitor.model.Severity;

import java.util.Locale;
import java.util.Optional;

/** Triggers when an event's magnitude is at or above a threshold (default M4.5, FR-04). */
public class MagnitudeRule implements AlertRule {

    public static final double DEFAULT_MIN_MAGNITUDE = 4.5;

    private final double minMagnitude;

    public MagnitudeRule() {
        this(DEFAULT_MIN_MAGNITUDE);
    }

    public MagnitudeRule(double minMagnitude) {
        this.minMagnitude = minMagnitude;
    }

    @Override
    public Optional<String> evaluate(SeismicEvent event) {
        // NaN (unknown magnitude) never satisfies >=, so it is never flagged.
        if (event == null || !(event.magnitude() >= minMagnitude)) {
            return Optional.empty();
        }
        return Optional.of(String.format(Locale.ROOT, "Magnitude M%.1f >= M%.1f", event.magnitude(), minMagnitude));
    }

    @Override
    public Severity getSeverity() {
        return Severity.MEDIUM;
    }

    public double getMinMagnitude() {
        return minMagnitude;
    }
}

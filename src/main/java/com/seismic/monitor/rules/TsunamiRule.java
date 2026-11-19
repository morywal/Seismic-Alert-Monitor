package com.seismic.monitor.rules;

import com.seismic.monitor.model.SeismicEvent;
import com.seismic.monitor.model.Severity;

import java.util.Optional;

/** Triggers when USGS has set the event's tsunami flag ({@code tsunami == 1}, FR-04). */
public class TsunamiRule implements AlertRule {

    @Override
    public Optional<String> evaluate(SeismicEvent event) {
        if (event == null || !event.tsunamiWarning()) {
            return Optional.empty();
        }
        return Optional.of("Tsunami flag set by USGS");
    }

    @Override
    public Severity getSeverity() {
        return Severity.CRITICAL;
    }
}

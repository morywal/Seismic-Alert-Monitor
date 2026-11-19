package com.seismic.monitor.model;

import java.util.List;
import java.util.Objects;

/**
 * A flagged seismic event together with its classified severity and the
 * human-readable reasons produced by every rule it triggered (FR-05).
 *
 * @param event            the event that triggered one or more rules
 * @param severity         the overall severity assigned by the {@code RuleEngine}
 * @param triggeredReasons one reason per triggered rule, in rule order
 */
public record Alert(SeismicEvent event, Severity severity, List<String> triggeredReasons) {

    public Alert {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(severity, "severity");
        triggeredReasons = List.copyOf(triggeredReasons == null ? List.of() : triggeredReasons);
    }
}

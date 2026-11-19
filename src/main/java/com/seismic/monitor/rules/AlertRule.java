package com.seismic.monitor.rules;

import com.seismic.monitor.model.SeismicEvent;
import com.seismic.monitor.model.Severity;

import java.util.Optional;

/**
 * Strategy interface for alert conditions (FR-04, NFR-02).
 *
 * <p>New conditions are added by implementing this interface and passing the instance
 * to {@code RuleEngine}; the engine itself never needs to change.
 */
public interface AlertRule {

    /**
     * @return a human-readable reason if the event triggers this rule, otherwise empty
     */
    Optional<String> evaluate(SeismicEvent event);

    /** @return the severity this rule contributes when triggered */
    Severity getSeverity();
}

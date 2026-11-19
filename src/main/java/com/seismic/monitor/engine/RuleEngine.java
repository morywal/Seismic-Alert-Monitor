package com.seismic.monitor.engine;

import com.seismic.monitor.model.Alert;
import com.seismic.monitor.model.SeismicEvent;
import com.seismic.monitor.model.Severity;
import com.seismic.monitor.rules.AlertRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Evaluate stage: runs every event through each {@link AlertRule} (Strategy Pattern)
 * and produces severity-classified {@link Alert}s (FR-04, FR-05).
 *
 * <h2>Severity classification (FR-05)</h2>
 * <ol>
 *   <li>The base severity is the highest {@link AlertRule#getSeverity()} among the
 *       rules the event triggered (single highest-priority rule).</li>
 *   <li>If two or more rules triggered (a rule combination), the severity is escalated
 *       one level, capped at {@link Severity#CRITICAL}.</li>
 * </ol>
 * With the default rules (Proximity = LOW, Magnitude = MEDIUM, Tsunami = CRITICAL):
 * proximity alone → LOW; magnitude alone → MEDIUM; proximity + magnitude → CRITICAL;
 * tsunami (alone or combined) → CRITICAL.
 *
 * <p>The engine depends only on the {@code AlertRule} interface, so new rules can be
 * added without modifying this class (NFR-02).
 */
public class RuleEngine {

    /** Orders alerts most-severe first, then by magnitude (unknown last), then newest first. */
    public static final Comparator<Alert> SEVERITY_RANKING = Comparator
            .comparing(Alert::severity, Comparator.reverseOrder())
            .thenComparing((Alert a) -> Double.isNaN(a.event().magnitude())
                    ? Double.NEGATIVE_INFINITY : a.event().magnitude(), Comparator.reverseOrder())
            .thenComparing((Alert a) -> a.event().timestampEpochMs(), Comparator.reverseOrder());

    private final List<AlertRule> rules;

    public RuleEngine(List<AlertRule> rules) {
        Objects.requireNonNull(rules, "rules");
        this.rules = List.copyOf(rules);
    }

    public List<Alert> evaluateAll(List<SeismicEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<Alert> alerts = new ArrayList<>();
        for (SeismicEvent event : events) {
            evaluate(event).ifPresent(alerts::add);
        }
        alerts.sort(SEVERITY_RANKING);
        return List.copyOf(alerts);
    }

    /** Evaluates a single event; empty if no rule was triggered. */
    public Optional<Alert> evaluate(SeismicEvent event) {
        if (event == null) {
            return Optional.empty();
        }
        List<String> reasons = new ArrayList<>();
        Severity highest = null;

        for (AlertRule rule : rules) {
            Optional<String> reason = rule.evaluate(event);
            if (reason.isPresent()) {
                reasons.add(reason.get());
                highest = highest == null ? rule.getSeverity() : Severity.max(highest, rule.getSeverity());
            }
        }

        if (highest == null) {
            return Optional.empty();
        }
        Severity severity = reasons.size() >= 2 ? highest.escalate() : highest;
        return Optional.of(new Alert(event, severity, reasons));
    }

    public List<AlertRule> getRules() {
        return rules;
    }
}

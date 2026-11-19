package com.seismic.monitor.engine;

import com.seismic.monitor.model.Alert;
import com.seismic.monitor.model.SeismicEvent;
import com.seismic.monitor.model.Severity;
import com.seismic.monitor.rules.AlertRule;
import com.seismic.monitor.rules.MagnitudeRule;
import com.seismic.monitor.rules.ProximityRule;
import com.seismic.monitor.rules.TsunamiRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR-04 / FR-05 / NFR-02: rule evaluation, severity escalation, and extensibility. */
class RuleEngineTest {

    // Reference points relative to the default San Diego target.
    private static final double NEAR_LAT = 33.0;     // ~40 km from San Diego
    private static final double NEAR_LON = -116.9;
    private static final double FAR_LAT = 35.6762;   // Tokyo
    private static final double FAR_LON = 139.6503;

    private final RuleEngine engine = new RuleEngine(List.of(
            new MagnitudeRule(), new ProximityRule(), new TsunamiRule()));

    private static SeismicEvent event(String id, double mag, double lat, double lon, boolean tsunami) {
        return new SeismicEvent(id, "place " + id, mag, lat, lon, 10.0, tsunami, 1_000L);
    }

    @Test
    void testIndividualRules() {
        assertTrue(new MagnitudeRule().evaluate(event("a", 4.5, FAR_LAT, FAR_LON, false)).isPresent(), "4.5 >= 4.5");
        assertFalse(new MagnitudeRule().evaluate(event("b", 4.49, FAR_LAT, FAR_LON, false)).isPresent());
        assertFalse(new MagnitudeRule().evaluate(event("c", Double.NaN, FAR_LAT, FAR_LON, false)).isPresent());
        assertTrue(new TsunamiRule().evaluate(event("d", 2.0, FAR_LAT, FAR_LON, true)).isPresent());
        assertFalse(new TsunamiRule().evaluate(event("e", 9.0, FAR_LAT, FAR_LON, false)).isPresent());
    }

    @Test
    void testEventsTriggeringNoRulesProduceNoAlert() {
        List<Alert> alerts = engine.evaluateAll(List.of(
                event("quiet", 2.1, FAR_LAT, FAR_LON, false),
                event("unknownMag", Double.NaN, FAR_LAT, FAR_LON, false)));

        assertTrue(alerts.isEmpty());
    }

    @Test
    void testSeverityEscalation() {
        // Single rules -> that rule's own severity
        assertEquals(Severity.LOW, severityOf(event("prox", 2.0, NEAR_LAT, NEAR_LON, false)));
        assertEquals(Severity.MEDIUM, severityOf(event("mag", 5.0, FAR_LAT, FAR_LON, false)));
        assertEquals(Severity.CRITICAL, severityOf(event("tsu", 3.0, FAR_LAT, FAR_LON, true)));

        // Combination of rules -> escalate one level above the highest triggered rule
        assertEquals(Severity.CRITICAL, severityOf(event("magProx", 5.2, NEAR_LAT, NEAR_LON, false)));

        // Already critical stays capped at CRITICAL
        assertEquals(Severity.CRITICAL, severityOf(event("magTsu", 7.1, FAR_LAT, FAR_LON, true)));
        assertEquals(Severity.CRITICAL, severityOf(event("all", 7.5, NEAR_LAT, NEAR_LON, true)));
    }

    @Test
    void testTwoLowRulesEscalateToMedium() {
        AlertRule alwaysLow = new FixedRule("always", Severity.LOW);
        RuleEngine twoLow = new RuleEngine(List.of(new ProximityRule(), alwaysLow));

        Alert alert = twoLow.evaluate(event("x", 1.0, NEAR_LAT, NEAR_LON, false)).orElseThrow();

        assertEquals(Severity.MEDIUM, alert.severity());
    }

    @Test
    void testTriggeredReasonsCollectedInRuleOrder() {
        Alert alert = engine.evaluate(event("all", 7.5, NEAR_LAT, NEAR_LON, true)).orElseThrow();

        assertEquals(3, alert.triggeredReasons().size());
        assertTrue(alert.triggeredReasons().get(0).startsWith("Magnitude M7.5"));
        assertTrue(alert.triggeredReasons().get(1).startsWith("Within "));
        assertEquals("Tsunami flag set by USGS", alert.triggeredReasons().get(2));
    }

    @Test
    void testAlertsRankedBySeverityThenMagnitude() {
        List<Alert> alerts = engine.evaluateAll(List.of(
                event("lowNear", 2.0, NEAR_LAT, NEAR_LON, false),
                event("med5", 5.0, FAR_LAT, FAR_LON, false),
                event("tsunami", 4.0, FAR_LAT, FAR_LON, true),
                event("med6", 6.0, FAR_LAT, FAR_LON, false),
                event("ignored", 1.0, FAR_LAT, FAR_LON, false)));

        assertEquals(List.of("tsunami", "med6", "med5", "lowNear"),
                alerts.stream().map(a -> a.event().id()).toList());
    }

    @Test
    void testNullAndEmptyInputsAreSafe() {
        assertTrue(engine.evaluateAll(null).isEmpty());
        assertTrue(engine.evaluateAll(List.of()).isEmpty());
        assertTrue(engine.evaluateAll(new ArrayList<>(Arrays.asList(null, null))).isEmpty());
    }

    @Test
    void testNewRuleAddedWithoutModifyingEngine() {
        // NFR-02: a brand-new condition plugs in purely by implementing AlertRule.
        AlertRule shallowQuake = new AlertRule() {
            @Override
            public Optional<String> evaluate(SeismicEvent e) {
                return e.depthKm() < 15 ? Optional.of("Shallow focus") : Optional.empty();
            }

            @Override
            public Severity getSeverity() {
                return Severity.MEDIUM;
            }
        };
        RuleEngine extended = new RuleEngine(List.of(shallowQuake));

        List<Alert> alerts = extended.evaluateAll(List.of(event("s", 1.0, FAR_LAT, FAR_LON, false)));

        assertEquals(1, alerts.size());
        assertEquals(Severity.MEDIUM, alerts.get(0).severity());
        assertEquals(List.of("Shallow focus"), alerts.get(0).triggeredReasons());
    }

    @Test
    void testEngineWithNoRulesFlagsNothing() {
        assertTrue(new RuleEngine(List.of()).evaluateAll(List.of(event("big", 9.0, NEAR_LAT, NEAR_LON, true)))
                .isEmpty());
    }

    private Severity severityOf(SeismicEvent e) {
        return engine.evaluate(e).orElseThrow(() -> new AssertionError("expected alert for " + e.id())).severity();
    }

    private record FixedRule(String reason, Severity severity) implements AlertRule {
        @Override
        public Optional<String> evaluate(SeismicEvent event) {
            return Optional.of(reason);
        }

        @Override
        public Severity getSeverity() {
            return severity;
        }
    }
}

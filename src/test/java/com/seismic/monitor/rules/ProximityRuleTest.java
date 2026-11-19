package com.seismic.monitor.rules;

import com.seismic.monitor.model.SeismicEvent;
import com.seismic.monitor.model.Severity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR-04: Haversine distance accuracy and proximity rule behaviour. */
class ProximityRuleTest {

    private final ProximityRule rule = new ProximityRule();

    private static SeismicEvent eventAt(double lat, double lon) {
        return new SeismicEvent("t", "test", 3.0, lat, lon, 10.0, false, 0L);
    }

    @Test
    void testHaversineAccuracy() {
        // Identical points
        assertEquals(0.0, rule.calculateHaversineKm(32.7157, -117.1611, 32.7157, -117.1611), 1e-9);

        // One degree of latitude along a meridian = R * pi / 180 ≈ 111.195 km
        assertEquals(Math.PI * ProximityRule.EARTH_RADIUS_KM / 180.0,
                rule.calculateHaversineKm(0, 0, 1, 0), 1e-6);

        // Antipodal points = half the circumference ≈ 20,015 km (numerically stable)
        assertEquals(Math.PI * ProximityRule.EARTH_RADIUS_KM,
                rule.calculateHaversineKm(0, 0, 0, 180), 1e-6);

        // Well-known city pairs (published great-circle distances)
        assertEquals(343.5, rule.calculateHaversineKm(51.5074, -0.1278, 48.8566, 2.3522), 1.0); // London–Paris
        assertEquals(179.4, rule.calculateHaversineKm(32.7157, -117.1611, 34.0522, -118.2437), 1.0); // SD–LA
        assertEquals(3936.0, rule.calculateHaversineKm(34.0522, -118.2437, 40.7128, -74.0060), 5.0); // LA–NYC

        // Symmetry and crossing the antimeridian
        assertEquals(rule.calculateHaversineKm(10, 20, -30, 40), rule.calculateHaversineKm(-30, 40, 10, 20), 1e-9);
        assertEquals(111.195, rule.calculateHaversineKm(0, 179.5, 0, -179.5), 0.01);
    }

    @Test
    void testDefaultsToSanDiego250Km() {
        assertEquals(32.7157, rule.getTargetLat(), 0.0);
        assertEquals(-117.1611, rule.getTargetLon(), 0.0);
        assertEquals(250.0, rule.getMaxRadiusKm(), 0.0);
        assertEquals(Severity.LOW, rule.getSeverity());
    }

    @Test
    void testEventsInsideAndOutsideRadius() {
        assertTrue(rule.evaluate(eventAt(34.0522, -118.2437)).isPresent(), "Los Angeles (~179 km) is inside");
        assertTrue(rule.evaluate(eventAt(32.7157, -117.1611)).isPresent(), "epicenter at target is inside");
        assertFalse(rule.evaluate(eventAt(37.7749, -122.4194)).isPresent(), "San Francisco (~740 km) is outside");
        assertFalse(rule.evaluate(eventAt(35.6762, 139.6503)).isPresent(), "Tokyo is outside");
    }

    @Test
    void testRadiusBoundaryIsInclusive() {
        ProximityRule probe = new ProximityRule(0, 0, 1000);
        double exact = probe.calculateHaversineKm(0, 0, 1, 0);

        ProximityRule boundary = new ProximityRule(0, 0, exact);
        assertTrue(boundary.evaluate(eventAt(1, 0)).isPresent(), "distance == radius must trigger (<=)");

        ProximityRule justShort = new ProximityRule(0, 0, exact - 0.001);
        assertFalse(justShort.evaluate(eventAt(1, 0)).isPresent());
    }

    @Test
    void testReasonMentionsDistance() {
        String reason = rule.evaluate(eventAt(34.0522, -118.2437)).orElseThrow();
        assertTrue(reason.startsWith("Within 179."), reason);
    }

    @Test
    void testInvalidConfigurationRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProximityRule(91, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> new ProximityRule(0, -181, 100));
        assertThrows(IllegalArgumentException.class, () -> new ProximityRule(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ProximityRule(0, 0, Double.NaN));
    }

    @Test
    void testNullEventIsNotFlagged() {
        assertFalse(rule.evaluate(null).isPresent());
    }
}

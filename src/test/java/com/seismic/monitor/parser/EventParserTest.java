package com.seismic.monitor.parser;

import com.seismic.monitor.model.SeismicEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR-02, FR-03, FR-07: GeoJSON mapping, noise filtering, and fault-tolerant parsing. */
class EventParserTest {

    private final EventParser parser = new EventParser();

    private static String feature(String id, String type, String coords, String extraProps) {
        return """
                {"type":"Feature","id":"%s",
                 "properties":{"type":"%s"%s},
                 "geometry":{"type":"Point","coordinates":%s}}
                """.formatted(id, type, extraProps.isEmpty() ? "" : "," + extraProps, coords);
    }

    private static String collection(String... features) {
        return "{\"type\":\"FeatureCollection\",\"features\":[" + String.join(",", features) + "]}";
    }

    @Test
    void testGeoJsonCoordinateOrder() {
        // GeoJSON order is [longitude, latitude, depth] — deliberately asymmetric values.
        String json = collection(feature("ci40000001", "earthquake", "[-116.7915, 33.4962, 12.34]",
                "\"mag\":4.8,\"place\":\"10 km NE of Aguanga, CA\",\"time\":1763467200000,\"tsunami\":1"));

        List<SeismicEvent> events = parser.parseEvents(json);

        assertEquals(1, events.size());
        SeismicEvent e = events.get(0);
        assertEquals(33.4962, e.latitude(), 1e-9, "latitude must come from coordinates[1]");
        assertEquals(-116.7915, e.longitude(), 1e-9, "longitude must come from coordinates[0]");
        assertEquals(12.34, e.depthKm(), 1e-9, "depth must come from coordinates[2]");
        assertEquals("ci40000001", e.id());
        assertEquals("10 km NE of Aguanga, CA", e.place());
        assertEquals(4.8, e.magnitude(), 1e-9);
        assertEquals(1763467200000L, e.timestampEpochMs());
        assertTrue(e.tsunamiWarning());
    }

    @Test
    void testFiltersNonEarthquakeEvents() {
        String json = collection(
                feature("eq1", "earthquake", "[-117.0, 33.0, 5.0]", "\"mag\":3.1"),
                feature("qb1", "quarry blast", "[-117.1, 33.1, 0.0]", "\"mag\":1.9"),
                feature("ex1", "explosion", "[-117.2, 33.2, 0.0]", "\"mag\":2.0"),
                feature("ice1", "ice quake", "[-150.0, 61.0, 1.0]", "\"mag\":1.2"),
                feature("eq2", "earthquake", "[139.7, 35.6, 40.0]", "\"mag\":5.6"));

        List<SeismicEvent> events = parser.parseEvents(json);

        assertEquals(List.of("eq1", "eq2"), events.stream().map(SeismicEvent::id).toList());
    }

    @Test
    void testFeatureWithMissingTypeIsFiltered() {
        String json = collection("""
                {"type":"Feature","id":"x","properties":{"mag":4.0},
                 "geometry":{"type":"Point","coordinates":[-117,33,5]}}""");

        assertTrue(parser.parseEvents(json).isEmpty());
    }

    @Test
    void testEmptyAndMalformedPayloads() {
        // HTTP 200 with zero events
        assertTrue(parser.parseEvents("{\"type\":\"FeatureCollection\",\"features\":[]}").isEmpty());
        // null / blank bodies
        assertTrue(parser.parseEvents(null).isEmpty());
        assertTrue(parser.parseEvents("").isEmpty());
        assertTrue(parser.parseEvents("   \n").isEmpty());
        // syntactically broken JSON
        assertDoesNotThrow(() -> parser.parseEvents("{\"features\": [ {\"id\": "));
        assertTrue(parser.parseEvents("{\"features\": [ {\"id\": ").isEmpty());
        assertTrue(parser.parseEvents("not json at all").isEmpty());
        // valid JSON but wrong shape
        assertTrue(parser.parseEvents("{\"type\":\"FeatureCollection\"}").isEmpty());
        assertTrue(parser.parseEvents("{\"features\":null}").isEmpty());
        assertTrue(parser.parseEvents("{\"features\":\"oops\"}").isEmpty());
        assertTrue(parser.parseEvents("[]").isEmpty());
        assertTrue(parser.parseEvents("null").isEmpty());
        // features array containing junk entries
        assertTrue(parser.parseEvents("{\"features\":[null, 42, \"x\", {}]}").isEmpty());
    }

    @Test
    void testNullAndMissingPropertiesUseSafeDefaults() {
        String json = collection("""
                {"type":"Feature",
                 "properties":{"type":"earthquake","mag":null,"place":null,"time":null,"tsunami":null},
                 "geometry":{"type":"Point","coordinates":[-117.5, 32.9]}}""");

        List<SeismicEvent> events = assertDoesNotThrow(() -> parser.parseEvents(json));

        assertEquals(1, events.size());
        SeismicEvent e = events.get(0);
        assertEquals(EventParser.UNKNOWN_ID, e.id());
        assertEquals(EventParser.UNKNOWN_PLACE, e.place());
        assertTrue(Double.isNaN(e.magnitude()));
        assertTrue(Double.isNaN(e.depthKm()), "missing depth should be NaN");
        assertEquals(0L, e.timestampEpochMs());
        assertFalse(e.tsunamiWarning());
        assertEquals(32.9, e.latitude(), 1e-9);
        assertEquals(-117.5, e.longitude(), 1e-9);
    }

    @Test
    void testFeaturesWithUnusableGeometryAreSkippedNotFatal() {
        String json = collection(
                "{\"type\":\"Feature\",\"id\":\"noGeom\",\"properties\":{\"type\":\"earthquake\",\"mag\":5}}",
                "{\"type\":\"Feature\",\"id\":\"nullGeom\",\"properties\":{\"type\":\"earthquake\"},\"geometry\":null}",
                feature("short", "earthquake", "[-117.0]", "\"mag\":5"),
                feature("text", "earthquake", "[\"a\", \"b\", 1]", "\"mag\":5"),
                feature("good", "earthquake", "[-117.0, 33.0, 8.0]", "\"mag\":5"));

        List<SeismicEvent> events = parser.parseEvents(json);

        assertEquals(List.of("good"), events.stream().map(SeismicEvent::id).toList());
    }

    @Test
    void testTsunamiFlagZeroIsFalse() {
        String json = collection(feature("t0", "earthquake", "[-117, 33, 5]", "\"mag\":6.0,\"tsunami\":0"));

        assertFalse(parser.parseEvents(json).get(0).tsunamiWarning());
    }
}

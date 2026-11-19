package com.seismic.monitor.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seismic.monitor.model.SeismicEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parse stage: converts a raw USGS GeoJSON {@code FeatureCollection} into
 * {@link SeismicEvent} records (FR-02) and drops non-earthquake noise (FR-03).
 *
 * <p>Fault tolerance (FR-07): null, blank, or malformed payloads yield an empty list;
 * missing or null properties fall back to safe defaults; a feature without usable
 * coordinates is skipped rather than aborting the whole batch.
 */
public class EventParser {

    static final String EARTHQUAKE_TYPE = "earthquake";
    static final String UNKNOWN_ID = "unknown";
    static final String UNKNOWN_PLACE = "Unknown location";

    private final ObjectMapper mapper;

    public EventParser() {
        this(new ObjectMapper());
    }

    public EventParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<SeismicEvent> parseEvents(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Collections.emptyList();
        }

        JsonNode root;
        try {
            root = mapper.readTree(rawJson);
        } catch (JsonProcessingException e) {
            System.err.println("[EventParser] Malformed JSON payload: " + e.getOriginalMessage());
            return Collections.emptyList();
        }

        JsonNode features = root == null ? null : root.get("features");
        if (features == null || !features.isArray()) {
            return Collections.emptyList();
        }

        List<SeismicEvent> events = new ArrayList<>();
        for (JsonNode feature : features) {
            SeismicEvent event = parseFeature(feature);
            if (event != null) {
                events.add(event);
            }
        }
        return Collections.unmodifiableList(events);
    }

    /** Returns the parsed event, or {@code null} if the feature is noise or unusable. */
    private SeismicEvent parseFeature(JsonNode feature) {
        if (feature == null || !feature.isObject()) {
            return null;
        }
        JsonNode props = feature.path("properties");

        // FR-03: retain only records whose type is exactly "earthquake".
        if (!EARTHQUAKE_TYPE.equals(textOrNull(props.get("type")))) {
            return null;
        }

        // FR-02: GeoJSON coordinate order is [longitude, latitude, depth].
        JsonNode coords = feature.path("geometry").path("coordinates");
        if (!coords.isArray() || coords.size() < 2
                || !coords.get(0).isNumber() || !coords.get(1).isNumber()) {
            return null;
        }
        double longitude = coords.get(0).asDouble();
        double latitude = coords.get(1).asDouble();
        double depthKm = coords.size() > 2 && coords.get(2).isNumber() ? coords.get(2).asDouble() : Double.NaN;

        String id = textOrNull(feature.get("id"));
        String place = textOrNull(props.get("place"));
        JsonNode mag = props.get("mag");
        JsonNode tsunami = props.get("tsunami");
        JsonNode time = props.get("time");

        return new SeismicEvent(
                id == null ? UNKNOWN_ID : id,
                place == null ? UNKNOWN_PLACE : place,
                mag != null && mag.isNumber() ? mag.asDouble() : Double.NaN,
                latitude,
                longitude,
                depthKm,
                tsunami != null && tsunami.isNumber() && tsunami.asInt() == 1,
                time != null && time.isNumber() ? time.asLong() : 0L);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isValueNode()) {
            return null;
        }
        String text = node.asText();
        return text.isBlank() ? null : text;
    }
}

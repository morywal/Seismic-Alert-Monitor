package com.seismic.monitor;

import com.seismic.monitor.client.UsgsApiClient;
import com.seismic.monitor.dispatch.AlertDispatcher;
import com.seismic.monitor.engine.RuleEngine;
import com.seismic.monitor.model.Alert;
import com.seismic.monitor.model.SeismicEvent;
import com.seismic.monitor.parser.EventParser;
import com.seismic.monitor.rules.MagnitudeRule;
import com.seismic.monitor.rules.ProximityRule;
import com.seismic.monitor.rules.TsunamiRule;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/**
 * Entry point wiring the Intake → Parse → Evaluate → Act pipeline.
 *
 * <pre>
 * Usage: java -jar seismic-alert-monitor.jar [hoursBack] [targetLat targetLon radiusKm]
 *   hoursBack   look-back window in hours (default 24)
 *   targetLat   proximity target latitude  (default 32.7157, San Diego)
 *   targetLon   proximity target longitude (default -117.1611, San Diego)
 *   radiusKm    proximity radius in km     (default 250)
 * </pre>
 */
public class Main {

    public static void main(String[] args) {
        int hoursBack = UsgsApiClient.DEFAULT_HOURS_BACK;
        double targetLat = ProximityRule.DEFAULT_TARGET_LAT;
        double targetLon = ProximityRule.DEFAULT_TARGET_LON;
        double radiusKm = ProximityRule.DEFAULT_MAX_RADIUS_KM;
        ProximityRule proximityRule;

        try {
            if (args.length >= 1) {
                hoursBack = Integer.parseInt(args[0]);
            }
            if (args.length >= 4) {
                targetLat = Double.parseDouble(args[1]);
                targetLon = Double.parseDouble(args[2]);
                radiusKm = Double.parseDouble(args[3]);
            } else if (args.length > 1) {
                throw new IllegalArgumentException("target requires all of: targetLat targetLon radiusKm");
            }
            if (hoursBack <= 0) {
                throw new IllegalArgumentException("hoursBack must be positive");
            }
            proximityRule = new ProximityRule(targetLat, targetLon, radiusKm);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid arguments: " + e.getMessage());
            System.err.println("Usage: [hoursBack] [targetLat targetLon radiusKm]");
            System.exit(2);
            return;
        }

        UsgsApiClient client = new UsgsApiClient();
        EventParser parser = new EventParser();
        RuleEngine engine = new RuleEngine(List.of(
                new MagnitudeRule(),
                proximityRule,
                new TsunamiRule()));
        AlertDispatcher dispatcher = new AlertDispatcher();

        System.out.printf("Fetching USGS earthquakes from the past %d hour(s)...%n", hoursBack);
        String rawJson = client.fetchRawGeoJson(hoursBack);
        List<SeismicEvent> events = parser.parseEvents(rawJson);
        System.out.printf("Parsed %d earthquake event(s).%n", events.size());

        List<Alert> alerts = engine.evaluateAll(events);
        dispatcher.printConsoleSummary(alerts);

        Path report = AlertDispatcher.timestampedReportPath(Path.of("."), Clock.systemDefaultZone());
        try {
            dispatcher.writeCsvReport(alerts, report);
            System.out.println("\nCSV report written to " + report.toAbsolutePath().normalize());
        } catch (IOException e) {
            System.err.println("Failed to write CSV report: " + e.getMessage());
        }
    }
}

package com.seismic.monitor.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.seismic.monitor.model.Alert;
import com.seismic.monitor.model.SeismicEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Act stage: renders alerts as a console table and exports them to CSV (FR-06)
 * and to a JSON feed consumed by the Leaflet map frontend.
 */
public class AlertDispatcher {

    public static final String[] CSV_HEADER = {
            "severity", "event_id", "time_utc", "magnitude", "latitude", "longitude",
            "depth_km", "tsunami", "place", "reasons"
    };

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
    private static final int PLACE_WIDTH = 40;

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final PrintStream out;

    public AlertDispatcher() {
        this(System.out);
    }

    public AlertDispatcher(PrintStream out) {
        this.out = out;
    }

    /** Builds {@code alerts_report_YYYYMMDD_HHMMSS.csv} inside {@code directory}. */
    public static Path timestampedReportPath(Path directory, Clock clock) {
        String stamp = LocalDateTime.now(clock).format(FILE_STAMP);
        return directory.resolve("alerts_report_" + stamp + ".csv");
    }

    public void printConsoleSummary(List<Alert> alerts) {
        List<Alert> safe = alerts == null ? List.of() : alerts;
        String rowFormat = "%-8s | %-5s | %-20s | %-" + PLACE_WIDTH + "s | %-8s | %-7s | %s%n";

        out.println();
        out.println("=== Seismic Alert Summary: " + safe.size() + " flagged event(s) ===");
        if (safe.isEmpty()) {
            out.println("No events met any alert rule.");
            return;
        }
        out.printf(rowFormat, "SEVERITY", "MAG", "TIME (UTC)", "PLACE", "DEPTH", "TSUNAMI", "REASONS");
        out.println("-".repeat(130));
        for (Alert alert : safe) {
            SeismicEvent e = alert.event();
            out.printf(rowFormat,
                    alert.severity(),
                    formatNumber(e.magnitude(), "%.1f", "n/a"),
                    DISPLAY_TIME.format(Instant.ofEpochMilli(e.timestampEpochMs())),
                    truncate(e.place(), PLACE_WIDTH),
                    formatNumber(e.depthKm(), "%.1f km", "n/a"),
                    e.tsunamiWarning() ? "YES" : "no",
                    String.join("; ", alert.triggeredReasons()));
        }
    }

    public void writeCsvReport(List<Alert> alerts, Path outputPath) throws IOException {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write(String.join(",", CSV_HEADER));
            writer.newLine();
            if (alerts == null) {
                return;
            }
            for (Alert alert : alerts) {
                SeismicEvent e = alert.event();
                String[] row = {
                        alert.severity().name(),
                        e.id(),
                        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(e.timestampEpochMs())),
                        formatNumber(e.magnitude(), "%.2f", ""),
                        formatNumber(e.latitude(), "%.4f", ""),
                        formatNumber(e.longitude(), "%.4f", ""),
                        formatNumber(e.depthKm(), "%.2f", ""),
                        Boolean.toString(e.tsunamiWarning()),
                        e.place(),
                        String.join("; ", alert.triggeredReasons())
                };
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) {
                        line.append(',');
                    }
                    line.append(escapeCsv(row[i]));
                }
                writer.write(line.toString());
                writer.newLine();
            }
        }
    }

    /** Writes the JSON map feed, stamping {@code generatedAtUtc} with the current UTC time. */
    public void writeJsonReport(List<Alert> alerts, Path outputPath) throws IOException {
        writeJsonReport(alerts, outputPath, Clock.systemUTC());
    }

    /**
     * Writes {@code {"generatedAtUtc": ..., "alerts": [...]}} to {@code outputPath},
     * creating parent directories as needed. Unknown numeric values ({@code NaN}) are
     * written as JSON {@code null}, since NaN is not valid JSON.
     */
    public void writeJsonReport(List<Alert> alerts, Path outputPath, Clock clock) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        root.put("generatedAtUtc",
                DateTimeFormatter.ISO_INSTANT.format(clock.instant().truncatedTo(ChronoUnit.SECONDS)));
        ArrayNode array = root.putArray("alerts");
        for (Alert alert : alerts == null ? List.<Alert>of() : alerts) {
            SeismicEvent e = alert.event();
            ObjectNode node = array.addObject();
            node.put("place", e.place());
            putNumber(node, "magnitude", e.magnitude());
            putNumber(node, "latitude", e.latitude());
            putNumber(node, "longitude", e.longitude());
            putNumber(node, "depthKm", e.depthKm());
            node.put("timestampEpochMs", e.timestampEpochMs());
            node.put("severity", alert.severity().name());
            ArrayNode reasons = node.putArray("reasons");
            alert.triggeredReasons().forEach(reasons::add);
        }

        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        JSON.writeValue(outputPath.toFile(), root);
    }

    private static void putNumber(ObjectNode node, String field, double value) {
        if (Double.isNaN(value)) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    /** RFC 4180 escaping: quote fields containing commas, quotes, or line breaks. */
    static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String formatNumber(double value, String format, String fallback) {
        return Double.isNaN(value) ? fallback : String.format(Locale.ROOT, format, value);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}

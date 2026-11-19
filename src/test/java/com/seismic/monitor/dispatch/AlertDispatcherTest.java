package com.seismic.monitor.dispatch;

import com.seismic.monitor.model.Alert;
import com.seismic.monitor.model.SeismicEvent;
import com.seismic.monitor.model.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR-06: console summary and timestamped CSV export. */
class AlertDispatcherTest {

    @TempDir
    Path tempDir;

    private static final Alert CRITICAL_ALERT = new Alert(
            new SeismicEvent("ci123", "5 km SW of Ocotillo, CA", 5.4, 32.7012, -116.0567, 8.75, true,
                    Instant.parse("2026-11-18T07:30:00Z").toEpochMilli()),
            Severity.CRITICAL,
            List.of("Magnitude M5.4 >= M4.5", "Tsunami flag set by USGS"));

    private static final Alert LOW_ALERT = new Alert(
            new SeismicEvent("ci456", "Somewhere \"quoted\", CA", Double.NaN, 33.0, -117.0, Double.NaN, false, 0L),
            Severity.LOW,
            List.of("Within 40.0 km of target"));

    @Test
    void testCsvReportGeneration() throws IOException {
        Path report = tempDir.resolve("alerts_report_20261118_073000.csv");

        new AlertDispatcher(new PrintStream(new ByteArrayOutputStream())).writeCsvReport(
                List.of(CRITICAL_ALERT, LOW_ALERT), report);

        assertTrue(Files.exists(report));
        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);
        assertEquals(3, lines.size(), "header + 2 rows");
        assertEquals("severity,event_id,time_utc,magnitude,latitude,longitude,depth_km,tsunami,place,reasons",
                lines.get(0));
        assertEquals("CRITICAL,ci123,2026-11-18T07:30:00Z,5.40,32.7012,-116.0567,8.75,true,"
                        + "\"5 km SW of Ocotillo, CA\",Magnitude M5.4 >= M4.5; Tsunami flag set by USGS",
                lines.get(1));
        // Unknown numbers are blank; embedded quotes are doubled and the field is quoted.
        assertEquals("LOW,ci456,1970-01-01T00:00:00Z,,33.0000,-117.0000,,false,"
                        + "\"Somewhere \"\"quoted\"\", CA\",Within 40.0 km of target",
                lines.get(2));
    }

    @Test
    void testCsvWithNoAlertsHasHeaderOnly() throws IOException {
        Path report = tempDir.resolve("nested/dir/empty.csv");

        new AlertDispatcher().writeCsvReport(List.of(), report);

        assertEquals(List.of(String.join(",", AlertDispatcher.CSV_HEADER)), Files.readAllLines(report));
    }

    @Test
    void testTimestampedReportFileName() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-18T21:05:09Z"), ZoneOffset.UTC);

        Path path = AlertDispatcher.timestampedReportPath(tempDir, clock);

        assertEquals("alerts_report_20261118_210509.csv", path.getFileName().toString());
        assertEquals(tempDir, path.getParent());
        assertTrue(path.getFileName().toString().matches("alerts_report_\\d{8}_\\d{6}\\.csv"));
    }

    @Test
    void testConsoleSummaryTable() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        AlertDispatcher dispatcher = new AlertDispatcher(new PrintStream(buffer, true, StandardCharsets.UTF_8));

        dispatcher.printConsoleSummary(List.of(CRITICAL_ALERT, LOW_ALERT));

        String output = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("2 flagged event(s)"), output);
        assertTrue(output.contains("SEVERITY"), output);
        assertTrue(output.contains("CRITICAL"), output);
        assertTrue(output.contains("5 km SW of Ocotillo, CA"), output);
        assertTrue(output.contains("2026-11-18 07:30:00Z"), output);
        assertTrue(output.contains("n/a"), "unknown magnitude/depth rendered as n/a");
        assertTrue(output.indexOf("CRITICAL") < output.indexOf("LOW  "), "order preserved");
    }

    @Test
    void testConsoleSummaryWithNoAlerts() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        new AlertDispatcher(new PrintStream(buffer, true, StandardCharsets.UTF_8)).printConsoleSummary(List.of());

        assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("No events met any alert rule."));
    }

    @Test
    void testEscapeCsv() {
        assertEquals("plain", AlertDispatcher.escapeCsv("plain"));
        assertEquals("\"a,b\"", AlertDispatcher.escapeCsv("a,b"));
        assertEquals("\"line\nbreak\"", AlertDispatcher.escapeCsv("line\nbreak"));
        assertEquals("", AlertDispatcher.escapeCsv(null));
    }
}

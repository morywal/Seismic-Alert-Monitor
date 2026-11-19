# Seismic Alert & Proximity Monitor

Java 17 / Maven implementation of `docs/REQUIREMENTS.md` and `docs/ARCHITECTURE.md`.
Pulls the last N hours of earthquakes from the public USGS API (no key needed), runs them through
the alert rules, prints a ranked table, and writes `alerts_report_YYYYMMDD_HHMMSS.csv`.

## Build & run

```bash
mvn clean test                                   # 5 test classes (JUnit 5)
mvn -q compile exec:java                         # last 24 h, San Diego, 250 km
mvn -q compile exec:java -Dexec.args="48"        # last 48 h
mvn -q compile exec:java -Dexec.args="24 34.0522 -118.2437 150"   # custom target: LA, 150 km
```

Arguments: `[hoursBack] [targetLat targetLon radiusKm]`. The CSV is written to the working directory.

## Pipeline (Intake → Parse → Evaluate → Act)

| Stage    | Class                                     | Package                           |
|----------|-------------------------------------------|-----------------------------------|
| Intake   | `UsgsApiClient` (`java.net.http.HttpClient`) | `com.seismic.monitor.client`   |
| Parse    | `EventParser` → `SeismicEvent` (record)   | `…parser`, `…model`               |
| Evaluate | `RuleEngine` + `AlertRule` strategies     | `…engine`, `…rules`               |
| Act      | `AlertDispatcher` → console + CSV         | `…dispatch`                       |

Supporting types: `Alert` (record), `Severity` (enum). The UML references `Severity` but does not
list it as a class, so it's added here.

## Rules and severity (FR-04 / FR-05)

| Rule            | Trigger                                              | Severity  |
|-----------------|------------------------------------------------------|-----------|
| `ProximityRule` | ≤ 250 km (Haversine, R = 6371 km) from 32.7157, -117.1611 | LOW  |
| `MagnitudeRule` | magnitude ≥ 4.5                                      | MEDIUM    |
| `TsunamiRule`   | USGS `tsunami == 1`                                  | CRITICAL  |

An event's severity is the highest severity among the rules it triggers. If **two or more** rules
trigger, it is escalated one level, capped at CRITICAL. For example, an M5 quake within 250 km of
San Diego is **CRITICAL**. Alerts are ranked by severity, then magnitude, then most recent first.

To add a rule, implement `AlertRule` and pass it to `RuleEngine`'s constructor. `RuleEngine`
itself doesn't change (NFR-02).

## Fault tolerance (FR-07)

* `UsgsApiClient` never throws for network problems. Timeouts, I/O errors, interrupts and non-200
  responses are logged to stderr and return an empty `FeatureCollection`.
* `EventParser` returns an empty list for null, blank, malformed or wrongly shaped JSON. A missing
  `mag` or depth becomes `NaN` (never flagged by the magnitude rule, shown as `n/a` or a blank cell).
  A missing `place` or `id` gets a placeholder, and a missing `tsunami` flag is treated as `false`.
  Features without usable coordinates are skipped without dropping the rest of the batch.

## Requirements traceability

| Req   | Implementation                                  | Tests |
|-------|-------------------------------------------------|-------|
| FR-01 | `UsgsApiClient`                                 | `UsgsApiClientTest` (mocked `HttpClient`: 200, timeout, I/O error, 503, interrupt) |
| FR-02 | `EventParser`, `SeismicEvent`                   | `EventParserTest#testGeoJsonCoordinateOrder` |
| FR-03 | `EventParser`                                   | `EventParserTest#testFiltersNonEarthquakeEvents` |
| FR-04 | `RuleEngine`, `MagnitudeRule`, `ProximityRule`, `TsunamiRule` | `RuleEngineTest`, `ProximityRuleTest#testHaversineAccuracy` |
| FR-05 | `RuleEngine`, `Alert`                           | `RuleEngineTest#testSeverityEscalation` |
| FR-06 | `AlertDispatcher`                               | `AlertDispatcherTest#testCsvReportGeneration` |
| FR-07 | `UsgsApiClient`, `EventParser`                  | `EventParserTest#testEmptyAndMalformedPayloads` (+ client failure tests) |

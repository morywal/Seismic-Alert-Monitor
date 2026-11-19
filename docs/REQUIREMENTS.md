# Software Requirements Specification (SRS)
## Project: Seismic Alert & Proximity Monitor

### 1. Overview
The Seismic Alert & Proximity Monitor is a standalone Java application that ingests live geospatial earthquake data from the public USGS Earthquake Catalog API, evaluates each event against configurable mission rules, and dispatches severity-ranked alerts to both the console and a timestamped CSV report.

---

### 2. Functional Requirements

| ID | Requirement |
| :--- | :--- |
| **FR-01** | **Data Ingestion:** The system shall query the USGS GeoJSON API (`https://earthquake.usgs.gov/fdsnws/event/1/query`) for seismic events occurring within a configurable time window (default: past 24 hours). |
| **FR-02** | **GeoJSON Parsing:** The system shall parse raw GeoJSON responses into strongly typed `SeismicEvent` domain objects, accurately mapping the GeoJSON coordinate array `[longitude, latitude, depth]` to their respective fields. |
| **FR-03** | **Noise Filtering:** The system shall filter out non-earthquake seismic events (e.g., quarry blasts, explosions) by retaining only records where `type == "earthquake"`. |
| **FR-04** | **Rule Evaluation:** The system shall evaluate every valid `SeismicEvent` against a modular set of rules: **Magnitude** ($\ge 4.5$), **Proximity** ($\le 250\text{ km}$ from a configurable target coordinate using the Haversine formula), and **Tsunami Flag** (`tsunami == 1`). |
| **FR-05** | **Severity Classification:** The system shall assign an alert severity level (`LOW`, `MEDIUM`, or `CRITICAL`) to each flagged event based on the highest-priority triggered rule or rule combination. |
| **FR-06** | **Alert Dispatch & Reporting:** The system shall display a formatted summary table of flagged alerts in the console and export the results to a timestamped CSV file (`alerts_report_YYYYMMDD_HHMMSS.csv`). |
| **FR-07** | **Fault Tolerance:** The system shall gracefully handle `HTTP 200` responses containing zero events, missing/null JSON properties, and network timeouts without throwing unhandled exceptions. |

---

### 3. Non-Functional Requirements

| ID | Requirement |
| :--- | :--- |
| **NFR-01** | **Portability & Zero-Auth:** The application shall run on Java 17+ using Maven and require no API keys, external databases, or paid services. |
| **NFR-02** | **Extensibility (Open/Closed Principle):** New alert conditions shall be addable by implementing the `AlertRule` interface without modifying existing `RuleEngine` logic. |
| **NFR-03** | **Automated Verification:** Core parsing, Haversine distance calculations, and rule evaluation logic shall be verified via automated JUnit 5 unit tests. |

---

### 4. Requirements Traceability Matrix (RTM)

| Req ID | Implementing Class(es) | Verification / Test Case |
| :--- | :--- | :--- |
| **FR-01** | `UsgsApiClient` | `UsgsApiClientTest` (mock HTTP response & timeout handling) |
| **FR-02** | `EventParser`, `SeismicEvent` | `EventParserTest#testGeoJsonCoordinateOrder` |
| **FR-03** | `EventParser` | `EventParserTest#testFiltersNonEarthquakeEvents` |
| **FR-04** | `RuleEngine`, `MagnitudeRule`, `ProximityRule`, `TsunamiRule` | `RuleEngineTest`, `ProximityRuleTest#testHaversineAccuracy` |
| **FR-05** | `RuleEngine`, `Alert` | `RuleEngineTest#testSeverityEscalation` |
| **FR-06** | `AlertDispatcher` | `AlertDispatcherTest#testCsvReportGeneration` |
| **FR-07** | `UsgsApiClient`, `EventParser` | `EventParserTest#testEmptyAndMalformedPayloads` |
# Software Architecture & Design Document
## Project: Seismic Alert & Proximity Monitor

### 1. System Pipeline
The application follows a unidirectional **Intake → Parse → Evaluate → Act** architecture to keep networking, data transformation, business logic, and output strictly decoupled:

1. **Intake (`UsgsApiClient`):** Executes HTTP GET requests to the USGS API using Java's standard `java.net.http.HttpClient`.
2. **Parse (`EventParser`):** Deserializes GeoJSON into immutable `SeismicEvent` records and filters out non-earthquake noise.
3. **Evaluate (`RuleEngine`):** Passes each event through a list of `AlertRule` implementations (Strategy Pattern) and aggregates triggered alerts.
4. **Act (`AlertDispatcher`):** Formats alerts for terminal output and writes them to a timestamped CSV file.

---

### 2. UML Class Diagram

```mermaid
classDiagram
    class Main {
        +main(String[] args) void
    }

    class UsgsApiClient {
        -HttpClient httpClient
        -String baseUrl
        +fetchRawGeoJson(int hoursBack) String
    }

    class EventParser {
        +parseEvents(String rawJson) List~SeismicEvent~
    }

    class SeismicEvent {
        +String id
        +String place
        +double magnitude
        +double latitude
        +double longitude
        +double depthKm
        +boolean tsunamiWarning
        +long timestampEpochMs
    }

    class RuleEngine {
        -List~AlertRule~ rules
        +evaluateAll(List~SeismicEvent~ events) List~Alert~
    }

    class AlertRule {
        <<interface>>
        +evaluate(SeismicEvent event) Optional~String~
        +getSeverity() Severity
    }

    class MagnitudeRule {
        -double minMagnitude
        +evaluate(SeismicEvent event) Optional~String~
        +getSeverity() Severity
    }

    class ProximityRule {
        -double targetLat
        -double targetLon
        -double maxRadiusKm
        +evaluate(SeismicEvent event) Optional~String~
        +calculateHaversineKm(double lat1, double lon1, double lat2, double lon2) double
        +getSeverity() Severity
    }

    class TsunamiRule {
        +evaluate(SeismicEvent event) Optional~String~
        +getSeverity() Severity
    }

    class Alert {
        +SeismicEvent event
        +Severity severity
        +List~String~ triggeredReasons
    }

    class AlertDispatcher {
        +printConsoleSummary(List~Alert~ alerts) void
        +writeCsvReport(List~Alert~ alerts, Path outputPath) void
    }

    Main --> UsgsApiClient
    Main --> EventParser
    Main --> RuleEngine
    Main --> AlertDispatcher
    EventParser ..> SeismicEvent : creates
    RuleEngine o-- AlertRule : evaluates
    RuleEngine ..> Alert : produces
    AlertRule <|.. MagnitudeRule
    AlertRule <|.. ProximityRule
    AlertRule <|.. TsunamiRule
    Alert --> SeismicEvent
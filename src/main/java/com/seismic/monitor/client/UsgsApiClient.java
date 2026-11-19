package com.seismic.monitor.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Intake stage: fetches raw GeoJSON from the public USGS FDSN event service (FR-01)
 * using {@link java.net.http.HttpClient}. No API key is required (NFR-01).
 *
 * <p>Fault tolerance (FR-07): network failures, timeouts, interruptions and non-200
 * responses never propagate as exceptions. Instead the client logs the problem to
 * {@code System.err} and returns {@link #EMPTY_FEATURE_COLLECTION}, which downstream
 * stages treat as "zero events".
 */
public class UsgsApiClient {

    public static final String DEFAULT_BASE_URL = "https://earthquake.usgs.gov/fdsnws/event/1/query";
    public static final int DEFAULT_HOURS_BACK = 24;
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    /** Valid GeoJSON with no features; returned whenever a fetch fails. */
    public static final String EMPTY_FEATURE_COLLECTION = "{\"type\":\"FeatureCollection\",\"features\":[]}";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration requestTimeout;
    private final Clock clock;

    public UsgsApiClient() {
        this(HttpClient.newBuilder()
                        .connectTimeout(DEFAULT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                DEFAULT_BASE_URL, DEFAULT_TIMEOUT, Clock.systemUTC());
    }

    public UsgsApiClient(HttpClient httpClient, String baseUrl, Duration requestTimeout, Clock clock) {
        if (httpClient == null || baseUrl == null || baseUrl.isBlank() || requestTimeout == null || clock == null) {
            throw new IllegalArgumentException("httpClient, baseUrl, requestTimeout and clock are required");
        }
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.requestTimeout = requestTimeout;
        this.clock = clock;
    }

    /**
     * Fetches all events whose origin time falls within the last {@code hoursBack} hours.
     *
     * @param hoursBack size of the look-back window in hours; must be positive
     * @return the raw GeoJSON body, or {@link #EMPTY_FEATURE_COLLECTION} on any failure
     */
    public String fetchRawGeoJson(int hoursBack) {
        if (hoursBack <= 0) {
            throw new IllegalArgumentException("hoursBack must be positive, got " + hoursBack);
        }

        URI uri = buildQueryUri(hoursBack);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/geo+json, application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.printf("[UsgsApiClient] USGS returned HTTP %d; treating as zero events.%n",
                        response.statusCode());
                return EMPTY_FEATURE_COLLECTION;
            }
            String body = response.body();
            return (body == null || body.isBlank()) ? EMPTY_FEATURE_COLLECTION : body;
        } catch (HttpTimeoutException e) {
            System.err.println("[UsgsApiClient] Request timed out: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("[UsgsApiClient] Network error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[UsgsApiClient] Request interrupted.");
        } catch (RuntimeException e) {
            System.err.println("[UsgsApiClient] Unexpected error: " + e);
        }
        return EMPTY_FEATURE_COLLECTION;
    }

    /** Builds the USGS query URL for the window {@code [now - hoursBack, now]}. */
    URI buildQueryUri(int hoursBack) {
        Instant end = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant start = end.minus(Duration.ofHours(hoursBack));
        return URI.create(baseUrl
                + "?format=geojson"
                + "&eventtype=earthquake"
                + "&orderby=time"
                + "&starttime=" + start
                + "&endtime=" + end);
    }
}

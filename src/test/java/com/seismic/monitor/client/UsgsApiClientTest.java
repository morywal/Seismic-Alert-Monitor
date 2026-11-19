package com.seismic.monitor.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR-01 / FR-07: verifies request construction and fault tolerance using a mocked HttpClient. */
class UsgsApiClientTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC);
    private static final String SAMPLE_BODY =
            "{\"type\":\"FeatureCollection\",\"features\":[{\"id\":\"ci1\"}]}";

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    private static UsgsApiClient clientWith(StubHttpClient stub) {
        return new UsgsApiClient(stub, UsgsApiClient.DEFAULT_BASE_URL, Duration.ofSeconds(5), FIXED_CLOCK);
    }

    @Test
    void testReturnsBodyOnHttp200() {
        StubHttpClient stub = StubHttpClient.responding(200, SAMPLE_BODY);

        String result = clientWith(stub).fetchRawGeoJson(24);

        assertEquals(SAMPLE_BODY, result);
    }

    @Test
    void testBuildsUsgsQueryForTimeWindow() {
        StubHttpClient stub = StubHttpClient.responding(200, SAMPLE_BODY);

        clientWith(stub).fetchRawGeoJson(24);

        HttpRequest sent = stub.lastRequest;
        assertNotNull(sent, "a request should have been sent");
        assertEquals("GET", sent.method());
        URI uri = sent.uri();
        assertEquals("https://earthquake.usgs.gov/fdsnws/event/1/query",
                uri.getScheme() + "://" + uri.getHost() + uri.getPath());
        String query = uri.getQuery();
        assertTrue(query.contains("format=geojson"), query);
        assertTrue(query.contains("starttime=2026-11-17T12:00:00Z"), query);
        assertTrue(query.contains("endtime=2026-11-18T12:00:00Z"), query);
        assertEquals(Optional.of(Duration.ofSeconds(5)), sent.timeout());
    }

    @Test
    void testCustomHoursBackWindow() {
        StubHttpClient stub = StubHttpClient.responding(200, SAMPLE_BODY);

        clientWith(stub).fetchRawGeoJson(6);

        assertTrue(stub.lastRequest.uri().getQuery().contains("starttime=2026-11-18T06:00:00Z"));
    }

    @Test
    void testTimeoutReturnsEmptyCollection() {
        StubHttpClient stub = StubHttpClient.throwing(new HttpTimeoutException("request timed out"));

        String result = clientWith(stub).fetchRawGeoJson(24);

        assertEquals(UsgsApiClient.EMPTY_FEATURE_COLLECTION, result);
    }

    @Test
    void testNetworkErrorReturnsEmptyCollection() {
        StubHttpClient stub = StubHttpClient.throwing(new IOException("connection refused"));

        assertEquals(UsgsApiClient.EMPTY_FEATURE_COLLECTION, clientWith(stub).fetchRawGeoJson(24));
    }

    @Test
    void testInterruptedRestoresFlagAndReturnsEmptyCollection() {
        StubHttpClient stub = StubHttpClient.throwing(new InterruptedException());

        String result = clientWith(stub).fetchRawGeoJson(24);

        assertEquals(UsgsApiClient.EMPTY_FEATURE_COLLECTION, result);
        assertTrue(Thread.currentThread().isInterrupted(), "interrupt flag should be restored");
    }

    @Test
    void testNon200ReturnsEmptyCollection() {
        StubHttpClient stub = StubHttpClient.responding(503, "Service Unavailable");

        assertEquals(UsgsApiClient.EMPTY_FEATURE_COLLECTION, clientWith(stub).fetchRawGeoJson(24));
    }

    @Test
    void testBlankBodyReturnsEmptyCollection() {
        StubHttpClient stub = StubHttpClient.responding(200, "   ");

        assertEquals(UsgsApiClient.EMPTY_FEATURE_COLLECTION, clientWith(stub).fetchRawGeoJson(24));
    }

    @Test
    void testRejectsNonPositiveWindow() {
        UsgsApiClient client = clientWith(StubHttpClient.responding(200, SAMPLE_BODY));

        assertThrows(IllegalArgumentException.class, () -> client.fetchRawGeoJson(0));
        assertThrows(IllegalArgumentException.class, () -> client.fetchRawGeoJson(-5));
    }

    @Test
    void testNoExceptionEscapesOnUnexpectedRuntimeError() {
        StubHttpClient stub = StubHttpClient.throwing(new IllegalStateException("boom"));

        String result = clientWith(stub).fetchRawGeoJson(24);

        assertFalse(result.isEmpty());
        assertEquals(UsgsApiClient.EMPTY_FEATURE_COLLECTION, result);
    }

    // --------------------------------------------------------------------------------------------
    // Hand-rolled HttpClient mock: no extra test dependencies required.
    // --------------------------------------------------------------------------------------------

    private static final class StubHttpClient extends HttpClient {
        private final int status;
        private final String body;
        private final Exception failure;
        HttpRequest lastRequest;

        private StubHttpClient(int status, String body, Exception failure) {
            this.status = status;
            this.body = body;
            this.failure = failure;
        }

        static StubHttpClient responding(int status, String body) {
            return new StubHttpClient(status, body, null);
        }

        static StubHttpClient throwing(Exception failure) {
            return new StubHttpClient(0, null, failure);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws IOException, InterruptedException {
            lastRequest = request;
            if (failure instanceof IOException io) {
                throw io;
            }
            if (failure instanceof InterruptedException ie) {
                throw ie;
            }
            if (failure instanceof RuntimeException re) {
                throw re;
            }
            return (HttpResponse<T>) new StubResponse(request, status, body);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h,
                                                                HttpResponse.PushPromiseHandler<T> p) {
            throw new UnsupportedOperationException();
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return null; }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
    }

    private record StubResponse(HttpRequest request, int statusCode, String body) implements HttpResponse<String> {
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}

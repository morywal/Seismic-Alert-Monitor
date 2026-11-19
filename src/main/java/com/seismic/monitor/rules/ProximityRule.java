package com.seismic.monitor.rules;

import com.seismic.monitor.model.SeismicEvent;
import com.seismic.monitor.model.Severity;

import java.util.Locale;
import java.util.Optional;

/**
 * Triggers when an event's epicenter lies within {@code maxRadiusKm} of a target
 * coordinate, measured with the Haversine great-circle formula (FR-04).
 * Defaults to San Diego, CA (32.7157, -117.1611) with a 250 km radius.
 */
public class ProximityRule implements AlertRule {

    public static final double DEFAULT_TARGET_LAT = 32.7157;
    public static final double DEFAULT_TARGET_LON = -117.1611;
    public static final double DEFAULT_MAX_RADIUS_KM = 250.0;

    /** Mean Earth radius in kilometres (IUGG). */
    public static final double EARTH_RADIUS_KM = 6371.0;

    private final double targetLat;
    private final double targetLon;
    private final double maxRadiusKm;

    public ProximityRule() {
        this(DEFAULT_TARGET_LAT, DEFAULT_TARGET_LON, DEFAULT_MAX_RADIUS_KM);
    }

    public ProximityRule(double targetLat, double targetLon, double maxRadiusKm) {
        if (targetLat < -90 || targetLat > 90) {
            throw new IllegalArgumentException("targetLat out of range: " + targetLat);
        }
        if (targetLon < -180 || targetLon > 180) {
            throw new IllegalArgumentException("targetLon out of range: " + targetLon);
        }
        if (!(maxRadiusKm > 0)) {
            throw new IllegalArgumentException("maxRadiusKm must be positive: " + maxRadiusKm);
        }
        this.targetLat = targetLat;
        this.targetLon = targetLon;
        this.maxRadiusKm = maxRadiusKm;
    }

    @Override
    public Optional<String> evaluate(SeismicEvent event) {
        if (event == null) {
            return Optional.empty();
        }
        double distanceKm = calculateHaversineKm(targetLat, targetLon, event.latitude(), event.longitude());
        if (!(distanceKm <= maxRadiusKm)) {
            return Optional.empty();
        }
        return Optional.of(String.format(Locale.ROOT, "Within %.1f km of target (%.4f, %.4f) <= %.0f km",
                distanceKm, targetLat, targetLon, maxRadiusKm));
    }

    /** Great-circle distance in kilometres between two points given in decimal degrees. */
    public double calculateHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        // Clamp guards against tiny floating-point overshoot for antipodal points.
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0, 1 - a)));
        return EARTH_RADIUS_KM * c;
    }

    @Override
    public Severity getSeverity() {
        return Severity.LOW;
    }

    public double getTargetLat() {
        return targetLat;
    }

    public double getTargetLon() {
        return targetLon;
    }

    public double getMaxRadiusKm() {
        return maxRadiusKm;
    }
}

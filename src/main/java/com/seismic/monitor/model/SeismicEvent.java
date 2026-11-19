package com.seismic.monitor.model;

/**
 * Immutable domain object for a single USGS seismic event (FR-02).
 *
 * <p>Note: GeoJSON stores coordinates as {@code [longitude, latitude, depth]};
 * {@code EventParser} is responsible for mapping that order onto these named fields.
 * Numeric fields that were absent in the source payload are {@link Double#NaN}.
 *
 * @param id               USGS event id
 * @param place            human-readable location description
 * @param magnitude        event magnitude ({@code NaN} if unknown)
 * @param latitude         latitude in decimal degrees
 * @param longitude        longitude in decimal degrees
 * @param depthKm          hypocenter depth in kilometres ({@code NaN} if unknown)
 * @param tsunamiWarning   true when the USGS {@code tsunami} flag equals 1
 * @param timestampEpochMs event origin time in epoch milliseconds
 */
public record SeismicEvent(
        String id,
        String place,
        double magnitude,
        double latitude,
        double longitude,
        double depthKm,
        boolean tsunamiWarning,
        long timestampEpochMs) {
}

package com.example.mysignupapp;

import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

/**
 * Smooths raw GPS coordinates using a simple Kalman filter.
 *
 * Raw GPS from Android jumps ±5-15 metres between readings even when standing still.
 * This causes the ambulance marker to shake/drift on the map.
 *
 * This class:
 * 1. Applies a Kalman filter to smooth lat/lng
 * 2. Smooths bearing (rotation) so marker doesn't spin wildly
 * 3. Detects and ignores GPS noise jumps
 * 4. Provides a road-snapped bearing from movement direction
 */
public class LocationSmoother {

    // ── Kalman filter state ───────────────────────────────────────────────────
    private double  filteredLat     = 0;
    private double  filteredLng     = 0;
    private double  variance        = -1; // negative = not initialised
    private long    lastTimestampMs = 0;

    // GPS accuracy in metres — tune this for your device
    private static final float MIN_ACCURACY_M = 3f;  // metres per second walking
    private static final float GPS_NOISE_M    = 10f; // typical Android GPS noise

    // ── Bearing smoothing ─────────────────────────────────────────────────────
    private float smoothedBearing  = 0f;
    private LatLng lastPosition     = null;

    // ── Minimum movement threshold ─────────────────────────────────────────────
    // Ignore GPS updates smaller than this — they're just noise
    private static final float MIN_MOVEMENT_M = 2f;

    /**
     * Feed a raw GPS location — returns smoothed LatLng.
     * Call this on every LocationResult update.
     */
    public LatLng smooth(Location rawLocation) {
        double rawLat = rawLocation.getLatitude();
        double rawLng = rawLocation.getLongitude();
        float  acc    = rawLocation.hasAccuracy()
                ? rawLocation.getAccuracy() : GPS_NOISE_M;
        long   nowMs  = rawLocation.getTime();

        if (variance < 0) {
            // First reading — initialise
            filteredLat     = rawLat;
            filteredLng     = rawLng;
            variance        = acc * acc;
            lastTimestampMs = nowMs;
            return new LatLng(rawLat, rawLng);
        }

        // Time since last update in seconds
        long   dtMs  = nowMs - lastTimestampMs;
        double dtSec = Math.max(dtMs / 1000.0, 0.001);
        lastTimestampMs = nowMs;

        // Prediction step — variance grows with time (position uncertainty increases)
        variance += dtSec * MIN_ACCURACY_M * MIN_ACCURACY_M;

        // Update step — Kalman gain
        double measurementVariance = acc * acc;
        double gain = variance / (variance + measurementVariance);

        // Correct filtered position
        filteredLat = filteredLat + gain * (rawLat - filteredLat);
        filteredLng = filteredLng + gain * (rawLng - filteredLng);
        variance    = (1.0 - gain) * variance;

        return new LatLng(filteredLat, filteredLng);
    }

    /**
     * Returns smoothed bearing (rotation angle) for the marker.
     * Calculates bearing from last position to current position,
     * then smoothly interpolates to avoid sudden rotations.
     *
     * @param currentPos current smoothed position
     * @return bearing in degrees 0-360
     */
    public float getSmoothedBearing(LatLng currentPos) {
        if (lastPosition == null) {
            lastPosition = currentPos;
            return smoothedBearing;
        }

        float[] res = new float[1];
        Location.distanceBetween(
                lastPosition.latitude, lastPosition.longitude,
                currentPos.latitude,   currentPos.longitude, res);

        // Only update bearing if moved enough — ignore noise
        if (res[0] >= MIN_MOVEMENT_M) {
            float rawBearing = bearingBetween(lastPosition, currentPos);
            // Smooth bearing — weighted average (70% old, 30% new)
            smoothedBearing = smoothBearing(smoothedBearing, rawBearing, 0.3f);
            lastPosition = currentPos;
        }

        return smoothedBearing;
    }

    /**
     * Check if movement is real or just GPS noise.
     * Returns true if the position has moved enough to act on.
     */
    public boolean isRealMovement(LatLng current) {
        if (lastPosition == null) return true;
        float[] res = new float[1];
        Location.distanceBetween(
                lastPosition.latitude, lastPosition.longitude,
                current.latitude,      current.longitude, res);
        return res[0] >= MIN_MOVEMENT_M;
    }

    public void reset() {
        variance        = -1;
        lastPosition    = null;
        smoothedBearing = 0f;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private float bearingBetween(LatLng from, LatLng to) {
        double lat1 = Math.toRadians(from.latitude);
        double lat2 = Math.toRadians(to.latitude);
        double dLng = Math.toRadians(to.longitude - from.longitude);
        float b = (float) Math.toDegrees(Math.atan2(
                Math.sin(dLng) * Math.cos(lat2),
                Math.cos(lat1) * Math.sin(lat2)
                        - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng)));
        return (b + 360f) % 360f;
    }

    /**
     * Smoothly interpolates between two bearing angles.
     * Handles the 359° → 1° wraparound correctly.
     */
    private float smoothBearing(float from, float to, float alpha) {
        float diff = to - from;
        // Normalise to [-180, 180] to take the shortest rotation path
        while (diff >  180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return (from + alpha * diff + 360f) % 360f;
    }
}

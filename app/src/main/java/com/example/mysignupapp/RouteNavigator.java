package com.example.mysignupapp;

import android.graphics.Color;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Production motion engine — replaces raw GPS marker animation.
 *
 * What this does that raw GPS animation does NOT:
 *
 * 1. Route-snapped movement — car follows the route polyline geometry,
 *    not raw GPS coordinates. Corners look natural, no cutting diagonals.
 *
 * 2. Prediction engine — at 30fps, car moves continuously between GPS
 *    updates using the route polyline as the path. No more freeze-jump-freeze.
 *
 * 3. Future-bearing — bearing is calculated from the NEXT few route points
 *    ahead, not from last→current GPS. Icon anticipates turns correctly.
 *
 * 4. Map matching — car snaps to the nearest point ON the route polyline,
 *    so it stays glued to the road, not floating beside it.
 *
 * 5. No polyline blinking — the route polyline is NEVER removed during
 *    movement. Only the "travelled" portion is trimmed from the front.
 *
 * 6. Frame-based rendering — continuous 30fps Handler loop, not GPS-event-
 *    based. Movement is smooth regardless of GPS update frequency.
 *
 * Usage:
 *   navigator = new RouteNavigator(googleMa, markerIcon);
 *   navigator.setRoute(routePoints);          // when route is fetched
 *   navigator.updateGps(smoothedLatLng);      // on each GPS update
 *   navigator.start();                        // begin rendering loop
 *   navigator.stop();                         // on fragment destroy
 */
public class RouteNavigator {

    private static final String TAG         = "RouteNavigator";
    private static final int    FPS         = 30;
    private static final long   FRAME_MS    = 1000L / FPS;      // ~33ms per frame
    private static final float  SNAP_RADIUS = 40f;              // metres — snap to route if within
    private static final float  SPEED_MPS   = 8.33f;            // 30 km/h assumed speed
    private static final int    LOOK_AHEAD  = 5;                // route points ahead for bearing
    private static final float  TRIM_BEHIND = 20f;              // trim route points > 20m behind

    // ── Map objects ───────────────────────────────────────────────────────────
    private final GoogleMap    googleMap;
    private final int          markerIconRes;
    private final android.content.Context context;
    /** Pre-converted Bitmap from vector drawable — cached so we don't convert every frame */
    private com.google.android.gms.maps.model.BitmapDescriptor cachedIcon = null;
    private Marker          marker;
    private Polyline        routePolyline;

    // ── Route state ───────────────────────────────────────────────────────────
    private List<LatLng>    routePoints     = new ArrayList<>();
    private int             routeIndex      = 0;
    private LatLng          snappedPos      = null;
    private float           smoothBearing   = 0f;
    /** True once a real Directions API route has been set (not just straight line) */
    private boolean         hasRealRoute    = false;

    // ── GPS input ─────────────────────────────────────────────────────────────
    private LatLng          lastGpsPos      = null;
    private long            lastGpsTimeMs   = 0;

    // ── Rendering loop ────────────────────────────────────────────────────────
    private final Handler   handler         = new Handler(Looper.getMainLooper());
    private boolean         running         = false;
    private long            lastFrameMs     = 0;

    // ── Destination ───────────────────────────────────────────────────────────
    private LatLng          destination     = null;
    private LatLng          userLatLng      = null; // for camera framing

    // ── Callbacks ─────────────────────────────────────────────────────────────
    public interface OnEtaUpdate {
        void onUpdate(float distanceM, int etaSec);
    }
    private OnEtaUpdate etaCallback;

    // ══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ══════════════════════════════════════════════════════════════════════════

    public RouteNavigator(GoogleMap map, int markerIconRes, android.content.Context ctx) {
        this.googleMap    = map;
        this.markerIconRes = markerIconRes;
        this.context      = ctx;
        // Pre-convert vector drawable to bitmap once on construction
        this.cachedIcon   = vectorToBitmapDescriptor(ctx, markerIconRes);
    }

    /** Backward-compatible constructor — pass null context to use fromResource (PNG only) */
    public RouteNavigator(GoogleMap map, int markerIconRes) {
        this.googleMap    = map;
        this.markerIconRes = markerIconRes;
        this.context      = null;
        this.cachedIcon   = null;
    }

    public void setEtaCallback(OnEtaUpdate cb) { this.etaCallback = cb; }
    public void setUserLatLng(LatLng u)         { this.userLatLng  = u; }
    public void setDestination(LatLng dest)      { this.destination = dest; }

    // ══════════════════════════════════════════════════════════════════════════
    // ROUTE — two modes:
    //   setRouteStraight() — called immediately with just from+to points
    //   setRoute()         — called when Directions API returns full polyline
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Draw a straight line IMMEDIATELY while waiting for the Directions API.
     * Call this as soon as you know origin and destination.
     * The marker will already start moving along this line.
     */
    public void setRouteStraight(LatLng from, LatLng to) {
        if (from == null || to == null) return;
        List<LatLng> straight = new ArrayList<>();
        straight.add(from);
        straight.add(to);
        hasRealRoute = false;
        applyRoute(straight, Color.parseColor("#3B608C"), 10f);
        Log.d(TAG, "Straight route drawn immediately — waiting for API");
    }

    /**
     * Replace the straight line with the full Directions API polyline.
     * Call this from RouteHelper's onRouteDrawn callback.
     */
    public void setRoute(List<LatLng> points) {
        if (points == null || points.isEmpty()) return;
        hasRealRoute = true;
        // Reset index — re-snap to new route from current position
        routeIndex = 0;
        if (snappedPos != null) {
            routeIndex = findNearestIndex(snappedPos, points);
        }
        routePoints = new ArrayList<>(points);
        applyRoute(points, Color.parseColor("#3B608C"), 14f);
        Log.d(TAG, "Real Directions route applied: " + points.size() + " points");
    }

    /** Shared route drawing — updates polyline in-place if possible, no flash */
    private void applyRoute(List<LatLng> points, int color, float width) {
        if (googleMap == null) return;

        if (routePoints != points) {
            routePoints = new ArrayList<>(points);
        }

        if (routePolyline == null) {
            routePolyline = googleMap.addPolyline(new PolylineOptions()
                    .addAll(routePoints)
                    .width(width)
                    .color(color)
                    .geodesic(false));
        } else {
            // Mutate in place — zero visual flash
            routePolyline.setPoints(routePoints);
            routePolyline.setWidth(width);
            routePolyline.setColor(color);
        }
    }

    /** Find nearest index in a given point list — used when real route replaces straight line */
    private int findNearestIndex(LatLng pos, List<LatLng> points) {
        float  best = Float.MAX_VALUE;
        int    idx  = 0;
        for (int i = 0; i < Math.min(points.size(), 100); i++) {
            float[] d = new float[1];
            Location.distanceBetween(pos.latitude, pos.longitude,
                    points.get(i).latitude, points.get(i).longitude, d);
            if (d[0] < best) { best = d[0]; idx = i; }
        }
        return idx;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GPS INPUT — call on each smoothed GPS update
    // ══════════════════════════════════════════════════════════════════════════

    public void updateGps(LatLng gpsPos) {
        if (gpsPos == null) return;

        // ── Step 1: snap GPS to nearest route point ───────────────────────────
        LatLng snapped = snapToRoute(gpsPos);

        // ── Step 2: advance route index to snapped position ───────────────────
        int snappedIndex = findNearestIndex(snapped);
        if (snappedIndex > routeIndex) {
            routeIndex = snappedIndex;
            trimBehind(); // remove passed segments from polyline
        }

        snappedPos    = snapped;
        lastGpsPos    = snapped;
        lastGpsTimeMs = System.currentTimeMillis();

        // ── Step 3: update ETA ───────────────────────────────────────────────
        if (etaCallback != null && destination != null) {
            float[] res = new float[1];
            Location.distanceBetween(snapped.latitude, snapped.longitude,
                    destination.latitude, destination.longitude, res);
            int eta = (int)(res[0] / SPEED_MPS);
            etaCallback.onUpdate(res[0], eta);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RENDERING LOOP — 30fps continuous motion
    // ══════════════════════════════════════════════════════════════════════════

    public void start() {
        if (running) return;
        running      = true;
        lastFrameMs  = System.currentTimeMillis();
        handler.post(renderLoop);
        Log.d(TAG, "Navigator started");
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(renderLoop);
        Log.d(TAG, "Navigator stopped");
    }

    private final Runnable renderLoop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            long nowMs = System.currentTimeMillis();
            float dtSec = (nowMs - lastFrameMs) / 1000f;
            lastFrameMs = nowMs;

            tick(dtSec);

            handler.postDelayed(this, FRAME_MS);
        }
    };

    // ── One frame of motion ───────────────────────────────────────────────────

    private void tick(float dtSec) {
        if (routePoints.isEmpty() || routeIndex >= routePoints.size()) return;

        float distanceThisFrame = SPEED_MPS * dtSec;
        LatLng targetPos = advanceAlongRoute(distanceThisFrame);
        if (targetPos == null) return;

        float targetBearing = futureBearing();
        smoothBearing = lerpBearing(smoothBearing, targetBearing, 0.12f);

        if (marker == null && googleMap != null) {
            // MUST use cached Bitmap — fromResource() crashes on Vector Drawables
            com.google.android.gms.maps.model.BitmapDescriptor icon =
                    cachedIcon != null
                            ? cachedIcon
                            : BitmapDescriptorFactory.fromResource(markerIconRes);
            marker = googleMap.addMarker(new MarkerOptions()
                    .position(targetPos)
                    .flat(true)
                    .rotation(smoothBearing)
                    .anchor(0.5f, 0.5f)
                    .icon(icon));
        } else if (marker != null) {
            marker.setPosition(targetPos);
            marker.setRotation(smoothBearing);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ROUTE TRAVERSAL — advance position along polyline
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Advances the current route position by 'distanceM' metres along the
     * polyline and returns the new position.
     * This is how the car moves along the road, not raw GPS coordinates.
     */
    private LatLng advanceAlongRoute(float distanceM) {
        if (routePoints.isEmpty()) return null;

        // If we have a very recent GPS update, snap to it immediately
        long gpsAge = System.currentTimeMillis() - lastGpsTimeMs;
        if (gpsAge < 500 && lastGpsPos != null) {
            // Fresh GPS — snap directly (within last 500ms)
            return lastGpsPos;
        }

        // Predict along route polyline between GPS updates
        int    idx      = Math.min(routeIndex, routePoints.size() - 1);
        LatLng current  = snappedPos != null ? snappedPos : routePoints.get(idx);
        float  remaining = distanceM;

        while (remaining > 0 && idx < routePoints.size() - 1) {
            LatLng next = routePoints.get(idx + 1);
            float[] seg = new float[1];
            Location.distanceBetween(current.latitude, current.longitude,
                    next.latitude, next.longitude, seg);
            float segLen = seg[0];

            if (remaining >= segLen) {
                remaining -= segLen;
                current = next;
                idx++;
            } else {
                // Interpolate within this segment
                float t = remaining / segLen;
                current = new LatLng(
                        current.latitude  + t * (next.latitude  - current.latitude),
                        current.longitude + t * (next.longitude - current.longitude));
                remaining = 0;
            }
        }

        snappedPos = current;
        return current;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FUTURE BEARING — look AHEAD on polyline for smooth turns
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Calculates bearing from current route position to a point LOOK_AHEAD
     * segments ahead on the polyline. This makes the icon "anticipate" turns
     * rather than reacting after the fact.
     */
    private float futureBearing() {
        if (routePoints.size() < 2) return smoothBearing;
        int from = Math.min(routeIndex, routePoints.size() - 1);
        int to   = Math.min(routeIndex + LOOK_AHEAD, routePoints.size() - 1);
        if (from == to) return smoothBearing;

        LatLng pFrom = routePoints.get(from);
        LatLng pTo   = routePoints.get(to);

        double lat1 = Math.toRadians(pFrom.latitude);
        double lat2 = Math.toRadians(pTo.latitude);
        double dLng = Math.toRadians(pTo.longitude - pFrom.longitude);
        float b = (float) Math.toDegrees(Math.atan2(
                Math.sin(dLng) * Math.cos(lat2),
                Math.cos(lat1) * Math.sin(lat2)
                        - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng)));
        return (b + 360f) % 360f;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ROUTE SNAPPING — snap GPS position to nearest point on polyline
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Finds the closest point on the route polyline to the raw GPS position.
     * Returns the snapped point (on the road) rather than the GPS point (beside it).
     */
    private LatLng snapToRoute(LatLng gps) {
        if (routePoints.isEmpty()) return gps;

        LatLng  bestSnap = gps;
        float   bestDist = Float.MAX_VALUE;
        int     bestIdx  = 0;

        // Search from current index forward (don't snap backward)
        int startIdx = Math.max(0, routeIndex - 2);
        for (int i = startIdx; i < routePoints.size() - 1; i++) {
            LatLng snapped = closestPointOnSegment(gps, routePoints.get(i),
                    routePoints.get(i + 1));
            float[] d = new float[1];
            Location.distanceBetween(gps.latitude, gps.longitude,
                    snapped.latitude, snapped.longitude, d);
            if (d[0] < bestDist) {
                bestDist = d[0];
                bestSnap = snapped;
                bestIdx  = i;
            }
            // Stop searching if we've gone too far ahead
            if (i > routeIndex + 30) break;
        }

        // Only snap if within SNAP_RADIUS — otherwise trust raw GPS
        if (bestDist <= SNAP_RADIUS) {
            if (bestIdx > routeIndex) routeIndex = bestIdx;
            return bestSnap;
        }
        return gps;
    }

    /** Closest point on segment AB to point P */
    private LatLng closestPointOnSegment(LatLng p, LatLng a, LatLng b) {
        double ax = a.longitude, ay = a.latitude;
        double bx = b.longitude, by = b.latitude;
        double px = p.longitude, py = p.latitude;
        double abx = bx - ax, aby = by - ay;
        double len2 = abx * abx + aby * aby;
        if (len2 == 0) return a;
        double t = Math.max(0, Math.min(1, ((px-ax)*abx + (py-ay)*aby) / len2));
        return new LatLng(ay + t*aby, ax + t*abx);
    }

    private int findNearestIndex(LatLng pos) {
        float  best = Float.MAX_VALUE;
        int    idx  = routeIndex;
        int    end  = Math.min(routePoints.size(), routeIndex + 50);
        for (int i = routeIndex; i < end; i++) {
            float[] d = new float[1];
            Location.distanceBetween(pos.latitude, pos.longitude,
                    routePoints.get(i).latitude, routePoints.get(i).longitude, d);
            if (d[0] < best) { best = d[0]; idx = i; }
        }
        return idx;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TRIM — remove already-passed route segments from polyline
    // Avoids the polyline-remove-redraw flash. Just shrinks from the front.
    // ══════════════════════════════════════════════════════════════════════════

    private void trimBehind() {
        if (routePolyline == null || routePoints.isEmpty()) return;
        // Don't trim a straight line (2 points) — only trim real Directions routes
        if (!hasRealRoute || routePoints.size() <= 2) return;
        if (routeIndex <= 0) return;

        int keepFrom = Math.max(0, routeIndex - 1);
        if (keepFrom >= routePoints.size()) return;

        List<LatLng> remaining = routePoints.subList(keepFrom, routePoints.size());
        if (remaining.size() >= 2) {
            routePolyline.setPoints(remaining); // in-place — no flash
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BEARING SMOOTHING
    // ══════════════════════════════════════════════════════════════════════════

    /** Smooth bearing interpolation handling 359°→1° wraparound */
    private float lerpBearing(float from, float to, float alpha) {
        float diff = to - from;
        while (diff >  180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return (from + alpha * diff + 360f) % 360f;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CAMERA — smooth follow without constant jarring resets
    // ══════════════════════════════════════════════════════════════════════════

    private long   lastCameraMs  = 0;
    private static final long CAMERA_INTERVAL_MS = 3000; // update camera every 3s max

    /**
     * Call this from the GPS update (not every frame) to avoid camera jumping.
     * Keeps driver marker + destination in view.
     */
    public void updateCamera(LatLng driverPos, LatLng destPos) {
        if (googleMap == null || driverPos == null) return;
        long now = System.currentTimeMillis();
        if (now - lastCameraMs < CAMERA_INTERVAL_MS) return;
        lastCameraMs = now;

        if (destPos != null) {
            try {
                LatLngBounds bounds = new LatLngBounds.Builder()
                        .include(driverPos).include(destPos).build();
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140));
            } catch (Exception e) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(driverPos, 16));
            }
        } else {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(driverPos, 17));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VECTOR DRAWABLE → BITMAP DESCRIPTOR
    // BitmapDescriptorFactory.fromResource() cannot decode Vector Drawables.
    // This method converts them to Bitmap first, which Maps API accepts.
    // ══════════════════════════════════════════════════════════════════════════

    public static com.google.android.gms.maps.model.BitmapDescriptor
    vectorToBitmapDescriptor(android.content.Context ctx, int drawableRes) {
        try {
            android.graphics.drawable.Drawable drawable =
                    androidx.core.content.ContextCompat.getDrawable(ctx, drawableRes);
            if (drawable == null) return BitmapDescriptorFactory.defaultMarker();

            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                    drawable.getIntrinsicWidth()  > 0 ? drawable.getIntrinsicWidth()  : 96,
                    drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 144,
                    android.graphics.Bitmap.Config.ARGB_8888);

            android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);

            return BitmapDescriptorFactory.fromBitmap(bmp);
        } catch (Exception e) {
            Log.e(TAG, "vectorToBitmapDescriptor failed: " + e.getMessage());
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
        }
    }

    public void destroy() {
        stop();
        if (marker       != null) { marker.remove();       marker       = null; }
        if (routePolyline != null) { routePolyline.remove(); routePolyline = null; }
        routePoints.clear();
        routeIndex  = 0;
        snappedPos  = null;
        lastGpsPos  = null;
    }

    public Marker   getMarker()       { return marker;        }
    public Polyline getRoutePolyline() { return routePolyline; }
    public boolean  hasRoute()         { return !routePoints.isEmpty(); }
}
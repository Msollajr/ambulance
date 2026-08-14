package com.example.mysignupapp;

import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Production-grade routing helper.
 *
 * Per-caller cache: "user" and "driver" maintain SEPARATE caches so they
 * never interfere with each other. This was the root cause of the user
 * side not drawing a route — the driver's cached route was being reused.
 *
 * Smart rerouting: only calls Directions API when driver deviates >50m
 * from the current route AND 10 seconds have passed since last call.
 */
public class RouteHelper {

    private static final String TAG              = "RouteHelper";
    private static final String API_KEY          = "AIzaSyBTbhvmNJnZT4HhivOR990rlmYvZ3YKCT4";
    private static final float  REROUTE_M        = 50f;
    private static final long   DEBOUNCE_MS      = 10_000L;

    // ── Per-caller caches keyed by "user" or "driver" ─────────────────────────
    private static final HashMap<String, List<LatLng>> C_POINTS   = new HashMap<>();
    private static final HashMap<String, LatLng>       C_DEST     = new HashMap<>();
    private static final HashMap<String, Long>         C_TIME     = new HashMap<>();
    private static final HashMap<String, Boolean>      C_FETCHING = new HashMap<>();

    public interface RouteCallback { void onRouteDrawn(Polyline polyline); }
    public interface EtaCallback   { void onEta(int seconds, double distanceMetres); }

    // ══════════════════════════════════════════════════════════════════════════
    // PRIMARY API
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Smart update — only reroutes when necessary.
     * @param caller "user" or "driver" — each has its own cache
     */
    public static void updateRoute(String caller,
                                   GoogleMap map, LatLng from, LatLng to,
                                   Polyline oldPolyline,
                                   RouteCallback callback,
                                   EtaCallback etaCallback) {
        if (map == null || from == null || to == null) return;

        List<LatLng> cached     = C_POINTS.get(caller);
        LatLng       cachedDest = C_DEST.get(caller);
        long         lastTime   = C_TIME.containsKey(caller) ? C_TIME.get(caller) : 0L;
        boolean      fetching   = Boolean.TRUE.equals(C_FETCHING.get(caller));
        boolean      hasCache   = cached != null && !cached.isEmpty();
        boolean      destChange = !same(to, cachedDest);

        if (destChange || !hasCache) {
            clearCache(caller);
            C_DEST.put(caller, to);
            fetch(caller, map, from, to, oldPolyline, callback, etaCallback);
            return;
        }

        float devM     = deviationFromRoute(from, cached);
        boolean okTime = (System.currentTimeMillis() - lastTime) >= DEBOUNCE_MS;

        if (devM > REROUTE_M && okTime && !fetching) {
            Log.d(TAG, "[" + caller + "] reroute deviation=" + devM + "m");
            fetch(caller, map, from, to, oldPolyline, callback, etaCallback);
        } else if (hasCache && oldPolyline == null) {
            redraw(caller, map, callback);
        }
    }

    /** Force fresh route — ignores cache. Use when trip starts or destination changes. */
    public static void drawRoute(String caller,
                                 GoogleMap map, LatLng from, LatLng to,
                                 Polyline oldPolyline, RouteCallback callback) {
        clearCache(caller);
        C_DEST.put(caller, to);
        fetch(caller, map, from, to, oldPolyline, callback, null);
    }

    // ── Backward-compatible overloads (default caller = "user") ───────────────

    public static void drawRoute(GoogleMap map, LatLng from, LatLng to,
                                 Polyline old, RouteCallback cb) {
        drawRoute("user", map, from, to, old, cb);
    }

    public static void updateRoute(GoogleMap map, LatLng from, LatLng to,
                                   Polyline old, RouteCallback cb, EtaCallback eta) {
        updateRoute("user", map, from, to, old, cb, eta);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FETCH
    // ══════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("deprecation")
    private static void fetch(String caller,
                              GoogleMap map, LatLng from, LatLng to,
                              Polyline oldPolyline,
                              RouteCallback callback,
                              EtaCallback etaCallback) {
        if (Boolean.TRUE.equals(C_FETCHING.get(caller))) return;
        C_FETCHING.put(caller, true);
        C_TIME.put(caller, System.currentTimeMillis());

        if (oldPolyline != null) oldPolyline.remove();

        new AsyncTask<Void, Void, RouteResult>() {
            @Override
            protected RouteResult doInBackground(Void... v) {
                return callApi(from, to);
            }

            @Override
            protected void onPostExecute(RouteResult result) {
                C_FETCHING.put(caller, false);
                if (result == null || result.points.isEmpty() || map == null) return;

                C_POINTS.put(caller, result.points);

                Polyline poly = map.addPolyline(new PolylineOptions()
                        .addAll(result.points)
                        .width(14f)
                        .color(Color.parseColor("#3B608C"))
                        .geodesic(false));

                if (callback    != null) callback.onRouteDrawn(poly);
                if (etaCallback != null)
                    etaCallback.onEta(result.etaSeconds, result.distanceMetres);
            }
        }.execute();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DIRECTIONS API
    // ══════════════════════════════════════════════════════════════════════════

    private static RouteResult callApi(LatLng from, LatLng to) {
        String url = "https://maps.googleapis.com/maps/api/directions/json"
                + "?origin="      + from.latitude + "," + from.longitude
                + "&destination=" + to.latitude   + "," + to.longitude
                + "&mode=driving"
                + "&key="         + API_KEY;

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                Log.e(TAG, "HTTP " + conn.getResponseCode());
                return straight(from, to);
            }

            BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            conn.disconnect();

            String raw = sb.toString();
            Log.d(TAG, "API[" + raw.substring(0, Math.min(200, raw.length())) + "]");
            return parse(raw, from, to);

        } catch (Exception e) {
            Log.e(TAG, "API error: " + e.getMessage());
            return straight(from, to);
        }
    }

    private static RouteResult parse(String json, LatLng from, LatLng to) {
        try {
            JSONObject root   = new JSONObject(json);
            String     status = root.getString("status");
            if (!"OK".equals(status)) {
                Log.e(TAG, "status=" + status + " | " + root.optString("error_message"));
                return straight(from, to);
            }

            JSONArray routes = root.getJSONArray("routes");
            if (routes.length() == 0) return straight(from, to);

            JSONObject route = routes.getJSONObject(0);
            JSONArray  legs  = route.getJSONArray("legs");

            int    eta  = -1;
            double dist = 0;

            if (legs.length() > 0) {
                JSONObject leg = legs.getJSONObject(0);
                if (leg.has("duration"))
                    eta  = leg.getJSONObject("duration").getInt("value");
                if (leg.has("distance"))
                    dist = leg.getJSONObject("distance").getDouble("value");
            }

            // Decode step-level polylines for maximum road accuracy
            List<LatLng> pts = new ArrayList<>();
            for (int i = 0; i < legs.length(); i++) {
                JSONArray steps = legs.getJSONObject(i).getJSONArray("steps");
                for (int j = 0; j < steps.length(); j++) {
                    pts.addAll(decodePolyline(steps.getJSONObject(j)
                            .getJSONObject("polyline").getString("points")));
                }
            }

            if (pts.isEmpty()) {
                pts = decodePolyline(route.getJSONObject("overview_polyline")
                        .getString("points"));
            }

            RouteResult res = new RouteResult();
            res.points = pts; res.etaSeconds = eta; res.distanceMetres = dist;
            return res;

        } catch (Exception e) {
            Log.e(TAG, "parse error: " + e.getMessage());
            return straight(from, to);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DEVIATION — how far is 'point' from the current route polyline?
    // ══════════════════════════════════════════════════════════════════════════

    private static float deviationFromRoute(LatLng point, List<LatLng> route) {
        if (route == null || route.size() < 2) return Float.MAX_VALUE;
        float min = Float.MAX_VALUE;
        for (int i = 0; i < route.size() - 1; i++) {
            float d = segDist(point, route.get(i), route.get(i + 1));
            if (d < min) min = d;
        }
        return min;
    }

    private static float segDist(LatLng p, LatLng a, LatLng b) {
        double ax = a.longitude, ay = a.latitude;
        double bx = b.longitude, by = b.latitude;
        double px = p.longitude, py = p.latitude;
        double abx = bx - ax, aby = by - ay;
        double len2 = abx * abx + aby * aby;
        float[] res = new float[1];
        if (len2 == 0) {
            android.location.Location.distanceBetween(py, px, ay, ax, res);
            return res[0];
        }
        double t = Math.max(0, Math.min(1, ((px-ax)*abx + (py-ay)*aby) / len2));
        android.location.Location.distanceBetween(py, px, ay+t*aby, ax+t*abx, res);
        return res[0];
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private static void redraw(String caller, GoogleMap map, RouteCallback cb) {
        List<LatLng> pts = C_POINTS.get(caller);
        if (map == null || pts == null || pts.isEmpty()) return;
        Polyline p = map.addPolyline(new PolylineOptions()
                .addAll(pts).width(14f).color(Color.parseColor("#3B608C")).geodesic(false));
        if (cb != null) cb.onRouteDrawn(p);
    }

    private static RouteResult straight(LatLng from, LatLng to) {
        RouteResult r = new RouteResult();
        r.points = new ArrayList<>();
        r.points.add(from); r.points.add(to);
        float[] res = new float[1];
        android.location.Location.distanceBetween(
                from.latitude, from.longitude, to.latitude, to.longitude, res);
        r.distanceMetres = res[0];
        r.etaSeconds     = (int)(res[0] / 8.3f);
        return r;
    }

    private static boolean same(LatLng a, LatLng b) {
        if (a == null || b == null) return false;
        return Math.abs(a.latitude  - b.latitude)  < 0.0001
                && Math.abs(a.longitude - b.longitude) < 0.0001;
    }

    /** Returns the most recently decoded route points for a caller — used by RouteNavigator */
    public static List<LatLng> getLastDecodedPoints(String caller) {
        List<LatLng> pts = C_POINTS.get(caller);
        return pts != null ? new ArrayList<>(pts) : null;
    }

    public static void clearCache(String caller) {
        C_POINTS.remove(caller);
        C_DEST.remove(caller);
        C_TIME.remove(caller);
        C_FETCHING.put(caller, false);
    }

    public static void clearCache() {
        C_POINTS.clear(); C_DEST.clear(); C_TIME.clear(); C_FETCHING.clear();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POLYLINE DECODER
    // ══════════════════════════════════════════════════════════════════════════

    public static List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length(), lat = 0, lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do { b = encoded.charAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; }
            while (b >= 0x20);
            lat += ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            shift = 0; result = 0;
            do { b = encoded.charAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; }
            while (b >= 0x20);
            lng += ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            poly.add(new LatLng(lat / 1e5, lng / 1e5));
        }
        return poly;
    }

    static class RouteResult {
        List<LatLng> points         = new ArrayList<>();
        int          etaSeconds     = -1;
        double       distanceMetres = 0;
    }
}
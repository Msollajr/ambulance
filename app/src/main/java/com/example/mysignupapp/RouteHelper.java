package com.example.mysignupapp;

import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches a real road-following route from the Google Directions API
 * and draws it as a polyline on a GoogleMap.
 *
 * Usage:
 *   RouteHelper.drawRoute(googleMap, fromLatLng, toLatLng, existingPolyline, callback);
 *
 * The callback gives back the new Polyline so you can store/remove it later.
 */
public class RouteHelper {

    private static final String TAG     = "RouteHelper";
    // Your project's Maps API key
    private static final String API_KEY = "AIzaSyDVaottvsjR30jVvaS_yJH6chzSu5ACmBw";

    public interface RouteCallback {
        /** Called on the main thread once the route polyline is drawn. */
        void onRouteDrawn(Polyline polyline);
    }

    /**
     * Fetches the route in a background thread, then draws it on the map
     * on the main thread.
     *
     * @param map      The GoogleMap to draw on
     * @param from     Origin (driver position)
     * @param to       Destination (patient / user position)
     * @param old      The previous polyline to remove before drawing the new one (may be null)
     * @param callback Receives the newly drawn Polyline
     */
    public static void drawRoute(GoogleMap map, LatLng from, LatLng to,
                                  Polyline old, RouteCallback callback) {
        if (map == null || from == null || to == null) return;

        // Remove old route immediately
        if (old != null) old.remove();

        new FetchRouteTask(map, callback).execute(from, to);
    }

    // ── AsyncTask ─────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private static class FetchRouteTask extends AsyncTask<LatLng, Void, List<LatLng>> {

        private final GoogleMap      map;
        private final RouteCallback  callback;

        FetchRouteTask(GoogleMap map, RouteCallback callback) {
            this.map      = map;
            this.callback = callback;
        }

        @Override
        protected List<LatLng> doInBackground(LatLng... points) {
            LatLng from = points[0];
            LatLng to   = points[1];

            String urlStr = "https://maps.googleapis.com/maps/api/directions/json"
                    + "?origin="      + from.latitude + "," + from.longitude
                    + "&destination=" + to.latitude   + "," + to.longitude
                    + "&mode=driving"
                    + "&key="         + API_KEY;

            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                return decodeRoute(sb.toString());

            } catch (Exception e) {
                Log.e(TAG, "Directions API error: " + e.getMessage());
                // Fall back to straight line
                List<LatLng> fallback = new ArrayList<>();
                fallback.add(from);
                fallback.add(to);
                return fallback;
            }
        }

        @Override
        protected void onPostExecute(List<LatLng> points) {
            if (points == null || points.isEmpty() || map == null) return;

            Polyline polyline = map.addPolyline(new PolylineOptions()
                    .addAll(points)
                    .width(12f)
                    .color(Color.parseColor("#3B608C"))
                    .geodesic(false));   // false = follow the decoded path exactly

            if (callback != null) callback.onRouteDrawn(polyline);
        }
    }

    // ── Decode the overview_polyline from Directions API response ─────────────

    private static List<LatLng> decodeRoute(String json) {
        List<LatLng> points = new ArrayList<>();
        try {
            JSONObject root   = new JSONObject(json);
            JSONArray  routes = root.getJSONArray("routes");
            if (routes.length() == 0) return points;

            JSONObject route        = routes.getJSONObject(0);
            JSONObject overviewPoly = route.getJSONObject("overview_polyline");
            String     encoded      = overviewPoly.getString("points");

            points = decodePolyline(encoded);

        } catch (Exception e) {
            Log.e(TAG, "JSON parse error: " + e.getMessage());
        }
        return points;
    }

    /**
     * Standard Google encoded polyline decoder.
     * Converts a compressed string into a list of LatLng points.
     */
    public static List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dLat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dLat;

            shift = 0; result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dLng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dLng;

            poly.add(new LatLng(lat / 1e5, lng / 1e5));
        }
        return poly;
    }
}

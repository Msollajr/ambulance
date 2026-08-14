package com.example.mysignupapp;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class AdminMap extends Fragment implements OnMapReadyCallback {

    private String adminPhone;
    private String trackDriverPhone = null;
    private String trackUserPhone   = null;

    /** Cached ambulance icon bitmap — computed once in onMapReady, reused for all markers */
    private com.google.android.gms.maps.model.BitmapDescriptor ambulanceIcon = null;

    private GoogleMap googleMap;

    // Driver markers on map keyed by driver phone
    private final HashMap<String, Marker>   driverMarkers  = new HashMap<>();
    private final HashMap<String, Marker>   patientMarkers = new HashMap<>();
    private final HashMap<String, Polyline> routeLines     = new HashMap<>();
    private final HashMap<String, LatLng>   driverPositions = new HashMap<>();
    /** Tracks active driversavailable GPS listeners so we can remove stale ones */
    private final HashMap<String, ValueEventListener> driverGpsListeners = new HashMap<>();

    private ValueEventListener requestsListener;
    private View tvNoActive;  // LinearLayout in XML, not TextView

    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            adminPhone       = getArguments().getString("phone", "");
            trackDriverPhone = getArguments().getString("trackDriver", null);
            trackUserPhone   = getArguments().getString("trackUser",   null);
        }
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvNoActive = view.findViewById(R.id.tv_no_active_routes);
        SupportMapFragment mapFrag = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.admin_map);
        if (mapFrag != null) mapFrag.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap gMap) {
        googleMap = gMap;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        // Pre-convert vector drawable to bitmap ONCE — reused for every driver marker
        try {
            if (getContext() != null)
                ambulanceIcon = RouteNavigator.vectorToBitmapDescriptor(
                        getContext(), R.drawable.ic_ambulance_marker);
        } catch (Exception e) {
            ambulanceIcon = null; // fallback to default marker
        }
        listenToActiveRoutes();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (requestsListener != null)
            FirebaseDatabase.getInstance().getReference("requests")
                    .removeEventListener(requestsListener);
        // Clean up all driver GPS listeners
        for (java.util.Map.Entry<String, ValueEventListener> entry
                : driverGpsListeners.entrySet()) {
            FirebaseDatabase.getInstance().getReference("driversavailable")
                    .child(entry.getKey()).child("l")
                    .removeEventListener(entry.getValue());
        }
        driverGpsListeners.clear();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LISTEN TO ALL ACTIVE REQUESTS FOR THIS ADMIN'S DRIVERS
    // ══════════════════════════════════════════════════════════════════════════

    private void listenToActiveRoutes() {
        // First get all driver phones for this admin
        FirebaseDatabase.getInstance().getReference("driver")
                .orderByChild("adminNo").equalTo(adminPhone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        // Collect driver phones — stored as adminNo = phone
                        // But adminNo might be UID, try phone field search too
                        if (!ds.exists() || ds.getChildrenCount() == 0) {
                            // Try by org_name / direct read
                            loadAllDriversAndWatch();
                            return;
                        }
                        loadAllDriversAndWatch();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        loadAllDriversAndWatch();
                    }
                });
    }

    private void loadAllDriversAndWatch() {
        // Load all drivers, filter by adminNo matching this admin
        FirebaseDatabase.getInstance().getReference("driver")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        java.util.List<String> myDrivers = new java.util.ArrayList<>();
                        for (DataSnapshot d : ds.getChildren()) {
                            String aN = d.child("adminNo").getValue(String.class);
                            String ph = d.child("phone").getValue(String.class);
                            // Match by phone (adminNo in driver = admin's phone)
                            if (adminPhone.equals(aN) || adminPhone.equals(ph)) {
                                String dPhone = d.getKey();
                                if (dPhone != null) myDrivers.add(dPhone);
                            }
                        }
                        watchRequestsForDrivers(myDrivers);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void watchRequestsForDrivers(List<String> driverPhones) {
        requestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Collect which drivers are still active BEFORE clearing markers
                java.util.Set<String> stillActiveDrivers = new java.util.HashSet<>();
                for (DataSnapshot reqSnap : snapshot.getChildren()) {
                    String assigned = reqSnap.child("assignedDriver").getValue(String.class);
                    String status   = reqSnap.child("status").getValue(String.class);
                    if (assigned != null && status != null
                            && ("en_route".equals(status) || "going_to_hospital".equals(status))) {
                        stillActiveDrivers.add(assigned);
                    }
                }

                // Remove GPS listeners for drivers that are no longer active
                java.util.Iterator<String> it = driverGpsListeners.keySet().iterator();
                while (it.hasNext()) {
                    String drPhone = it.next();
                    if (!stillActiveDrivers.contains(drPhone)) {
                        ValueEventListener l = driverGpsListeners.get(drPhone);
                        if (l != null) {
                            FirebaseDatabase.getInstance().getReference("driversavailable")
                                    .child(drPhone).child("l").removeEventListener(l);
                        }
                        it.remove();
                    }
                }

                for (Marker m : driverMarkers.values())  m.remove();
                for (Marker m : patientMarkers.values()) m.remove();
                for (Polyline p : routeLines.values())   p.remove();
                driverMarkers.clear();
                patientMarkers.clear();
                routeLines.clear();

                int activeCount = 0;

                for (DataSnapshot reqSnap : snapshot.getChildren()) {
                    String userPhone    = reqSnap.getKey();
                    String assigned     = reqSnap.child("assignedDriver").getValue(String.class);
                    String assignedAdmin= reqSnap.child("assignedAdmin").getValue(String.class);
                    String status       = reqSnap.child("status").getValue(String.class);

                    if (status == null) continue;

                    // Match: either admin directly assigned, or driver belongs to this admin
                    boolean isMyRequest = adminPhone.equals(assignedAdmin)
                            || (assigned != null && driverPhones.contains(assigned));
                    if (!isMyRequest) continue;

                    // Only show ACTIVE routes
                    if (!"en_route".equals(status) && !"going_to_hospital".equals(status)
                            && !"admin_assigned".equals(status)) continue;

                    activeCount++;

                    // Only track if driver is assigned
                    if (assigned != null && !assigned.isEmpty()) {
                        trackDriverOnMap(assigned, userPhone, reqSnap, status);
                    }
                }

                if (tvNoActive != null)
                    tvNoActive.setVisibility(activeCount == 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        FirebaseDatabase.getInstance().getReference("requests")
                .addValueEventListener(requestsListener);
    }

    private void trackDriverOnMap(String driverPhone, String userPhone,
                                  DataSnapshot reqSnap, String status) {
        String driverName  = reqSnap.child("driverName").getValue(String.class);
        String plate       = reqSnap.child("ambulancePlate").getValue(String.class);
        String severity    = reqSnap.child("severity").getValue(String.class);

        DatabaseReference gpsRef = FirebaseDatabase.getInstance()
                .getReference("driversavailable").child(driverPhone).child("l");

        // CRITICAL: Remove any existing listener for this driver before attaching new one.
        // Without this, every request update (status/name/etc change) attaches ANOTHER
        // listener, and multiple listeners write to the same marker/route simultaneously,
        // causing routes to flicker, freeze, or fail to draw — especially noticeable
        // during going_to_hospital where status flips and re-triggers this constantly.
        ValueEventListener existing = driverGpsListeners.get(driverPhone);
        if (existing != null) {
            gpsRef.removeEventListener(existing);
        }

        ValueEventListener gpsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot ds) {
                if (!ds.exists() || googleMap == null) return;
                List<Object> c = (List<Object>) ds.getValue();
                if (c == null || c.size() < 2) return;

                double dLat = Double.parseDouble(c.get(0).toString());
                double dLng = Double.parseDouble(c.get(1).toString());
                LatLng driverPos = new LatLng(dLat, dLng);
                driverPositions.put(driverPhone, driverPos);

                // Update or create driver marker
                String label = (driverName != null ? driverName : driverPhone)
                        + (plate != null ? " · " + plate : "");
                if (driverMarkers.containsKey(driverPhone)) {
                    driverMarkers.get(driverPhone).setPosition(driverPos);
                } else {
                    // Use pre-cached bitmap (converted once in onMapReady)
                    com.google.android.gms.maps.model.BitmapDescriptor icon =
                            ambulanceIcon != null
                                    ? ambulanceIcon
                                    : BitmapDescriptorFactory.defaultMarker(
                                    BitmapDescriptorFactory.HUE_ORANGE);
                    Marker m = googleMap.addMarker(new MarkerOptions()
                            .position(driverPos)
                            .title(label)
                            .snippet(status.replace("_", " ").toUpperCase())
                            .flat(true)
                            .icon(icon));
                    if (m != null) driverMarkers.put(driverPhone, m);
                }

                // Get patient/destination position and draw route
                getDestinationAndDrawRoute(driverPhone, userPhone, driverPos, status);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };

        gpsRef.addValueEventListener(gpsListener);
        driverGpsListeners.put(driverPhone, gpsListener);
    }

    @SuppressLint("MissingPermission")
    private void getDestinationAndDrawRoute(String driverPhone, String userPhone,
                                            LatLng driverPos, String status) {
        if ("going_to_hospital".equals(status)) {
            // Destination = hospital (stored in admin node via driver's adminNo)
            FirebaseDatabase.getInstance().getReference("driver").child(driverPhone)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot ds) {
                            String aN = ds.child("adminNo").getValue(String.class);
                            if (aN == null) return;
                            loadHospitalAndRoute(driverPhone, driverPos, aN);
                        }
                        @Override public void onCancelled(@NonNull DatabaseError e) {}
                    });
        } else {
            // Destination = patient location from Requests GeoFire
            FirebaseDatabase.getInstance().getReference("Requests")
                    .child(userPhone).child("l")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot ds) {
                            if (!ds.exists()) return;
                            List<Object> c = (List<Object>) ds.getValue();
                            if (c == null || c.size() < 2) return;
                            double pLat = Double.parseDouble(c.get(0).toString());
                            double pLng = Double.parseDouble(c.get(1).toString());
                            LatLng patientPos = new LatLng(pLat, pLng);

                            // Patient marker
                            updateOrAddMarker(patientMarkers, userPhone, patientPos,
                                    "Patient · " + userPhone, BitmapDescriptorFactory.HUE_RED);

                            // Draw route
                            drawAdminRoute(driverPhone, driverPos, patientPos);
                        }
                        @Override public void onCancelled(@NonNull DatabaseError e) {}
                    });
        }
    }

    private void loadHospitalAndRoute(String driverPhone, LatLng driverPos, String adminNo) {
        // Try direct key, then by phone
        FirebaseDatabase.getInstance().getReference("admin").child(adminNo)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        Object lat = ds.child("hospitalLat").getValue();
                        Object lng = ds.child("hospitalLng").getValue();
                        String nm  = ds.child("hospitalName").getValue(String.class);
                        if (lat != null && lng != null) {
                            LatLng hospPos = new LatLng(
                                    Double.parseDouble(lat.toString()),
                                    Double.parseDouble(lng.toString()));
                            updateOrAddMarker(patientMarkers, driverPhone + "_hosp", hospPos,
                                    nm != null ? nm : "Hospital",
                                    BitmapDescriptorFactory.HUE_BLUE);
                            drawAdminRoute(driverPhone, driverPos, hospPos);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void drawAdminRoute(String key, LatLng from, LatLng to) {
        if (googleMap == null) return;
        // Remove old route for this driver
        if (routeLines.containsKey(key)) {
            routeLines.get(key).remove();
            routeLines.remove(key);
        }
        RouteHelper.updateRoute("admin_" + key, googleMap, from, to, null,
                polyline -> {
                    routeLines.put(key, polyline);
                    // If this is the driver we're tracking from requests page, zoom to it
                    if (key.equals(trackDriverPhone) && googleMap != null) {
                        try {
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                                    new LatLngBounds.Builder().include(from).include(to).build(),
                                    120));
                            trackDriverPhone = null; // done — don't zoom again
                        } catch (Exception ignored) {}
                    }
                }, null);
    }

    private void updateOrAddMarker(HashMap<String, Marker> map, String key,
                                   LatLng pos, String title, float hue) {
        if (googleMap == null) return;
        if (map.containsKey(key)) {
            map.get(key).setPosition(pos);
        } else {
            Marker m = googleMap.addMarker(new MarkerOptions()
                    .position(pos).title(title)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue)));
            if (m != null) map.put(key, m);
        }
    }
}
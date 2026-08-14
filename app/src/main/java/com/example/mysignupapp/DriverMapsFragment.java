package com.example.mysignupapp;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import com.bumptech.glide.Glide;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
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
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class DriverMapsFragment extends Fragment implements OnMapReadyCallback {

    private static final int    RC_LOCATION   = 2001;
    private static final String CHANNEL_ID    = "driver_channel";
    private static final int    NOTIF_ID      = 99;
    private static final float  AUTO_ARRIVE_M = 100f;

    // ── Driver identity ────────────────────────────────────────────────────────
    private String phone, driverName, driverHospital, adminNo;
    private String assignedAmbulanceId = "";
    private String ambulancePlate      = "";
    private String ambulanceType       = "";
    private double ambulanceCost       = 0;
    private double hospitalLat = 0, hospitalLng = 0;
    private String hospitalName = "";

    // ── Trip state ─────────────────────────────────────────────────────────────
    private boolean isOnActiveTrip   = false;
    private boolean autoArrivedFired = false;
    private boolean isHospitalPhase  = false;
    private String  activeUserPhone  = "";
    private String  activeSeverity   = "";
    private String  activeAmbType    = "";
    private LatLng  patientLatLng    = null;

    // ── Map & location ─────────────────────────────────────────────────────────
    private GoogleMap                   googleMap;
    private FusedLocationProviderClient fusedClient;
    private LocationCallback            locationCallback;
    private LatLng                      driverLatLng;
    private Marker                      driverMarker;
    private Marker                      patientMarker;
    private Polyline                    routePolyline;
    private final HashMap<String, Marker> requestPins = new HashMap<>();

    // ── Production navigation ──────────────────────────────────────────────────
    private final LocationSmoother locationSmoother  = new LocationSmoother();
    private       RouteNavigator   routeNavigator;

    // ── GeoFire ────────────────────────────────────────────────────────────────
    private GeoFire           geoFire;
    private DatabaseReference geoRef;

    // ── Firebase ───────────────────────────────────────────────────────────────
    private ValueEventListener allRequestsListener;
    private ValueEventListener activeTripStatusListener;
    private DatabaseReference  activeTripRef;

    // ── Views ──────────────────────────────────────────────────────────────────
    private ScrollView     incomingRequestPanel;
    private LinearLayout   activeTripPanel;
    private LinearLayout   idlePanel;
    private TextView       tvReqSeverity, tvReqType, tvReqDistance, tvReqEta,
            tvReqDescription;
    private TextView       tvTripTitle, tvTripSubtitle, tvTripTypeBadge;
    private TextView       tvTripDistance, tvTripSeverity, tvTripPatientPhone;
    private MaterialButton btnAccept, btnReject;
    private MaterialButton btnOnTheWay, btnArrivedBtn, btnGoingHospital, btnEndRoute;
    private TextView       tvOnlineStatus;
    private ImageView      ivReqPhoto;

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    private static final String PREFS             = "ambulance_driver_state";
    private static final String KEY_ACTIVE        = "is_active";
    private static final String KEY_USER_PHONE    = "user_phone";
    private static final String KEY_SEVERITY      = "severity";
    private static final String KEY_AMB_TYPE      = "amb_type";
    private static final String KEY_PAT_LAT       = "pat_lat";
    private static final String KEY_PAT_LNG       = "pat_lng";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) phone = getArguments().getString("phone");
        fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        createNotificationChannel();
        loadDriverProfile();
    }

    private void saveTripState() {
        if (getActivity() == null) return;
        getActivity().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACTIVE,     isOnActiveTrip)
                .putString(KEY_USER_PHONE,  activeUserPhone)
                .putString(KEY_SEVERITY,    activeSeverity)
                .putString(KEY_AMB_TYPE,    activeAmbType)
                .putFloat(KEY_PAT_LAT,
                        patientLatLng != null ? (float) patientLatLng.latitude  : 0f)
                .putFloat(KEY_PAT_LNG,
                        patientLatLng != null ? (float) patientLatLng.longitude : 0f)
                .apply();
    }

    private void clearTripState() {
        if (getActivity() == null) return;
        getActivity().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .edit().clear().apply();
    }

    private void restoreTripState() {
        if (getActivity() == null) return;
        android.content.SharedPreferences p = getActivity()
                .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        if (!p.getBoolean(KEY_ACTIVE, false)) return;

        String userPh  = p.getString(KEY_USER_PHONE, "");
        String sev     = p.getString(KEY_SEVERITY,   "—");
        String ambType = p.getString(KEY_AMB_TYPE,   "basic");
        float  pLat    = p.getFloat(KEY_PAT_LAT,     0f);
        float  pLng    = p.getFloat(KEY_PAT_LNG,     0f);

        if (userPh == null || userPh.isEmpty()) return;

        // Always verify with Firebase before restoring — prevent stale state
        FirebaseDatabase.getInstance().getReference("requests").child(userPh)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        if (!ds.exists()) { clearTripState(); return; }
                        String status = ds.child("status").getValue(String.class);

                        // Terminal states — do NOT restore
                        if (status == null
                                || "cancelled".equals(status)
                                || "rejected".equals(status)
                                || "trip_ended".equals(status)) {
                            clearTripState();
                            return;
                        }

                        // Restore active trip state, including going_to_hospital.
                        // Treating going_to_hospital as terminal was wrong: it kills
                        // route drawing after rotation/reopen.
                        if (!"en_route".equals(status)
                                && !"arrived".equals(status)
                                && !"going_to_hospital".equals(status)) {
                            clearTripState();
                            return;
                        }

                        isOnActiveTrip   = true;
                        autoArrivedFired = false;
                        activeUserPhone  = userPh;
                        activeSeverity   = sev;
                        activeAmbType    = ambType;
                        if (pLat != 0f) patientLatLng = new LatLng(pLat, pLng);

                        if (idlePanel != null) {
                            idlePanel.setVisibility(View.GONE);
                            incomingRequestPanel.setVisibility(View.GONE);
                            activeTripPanel.setVisibility(View.VISIBLE);
                            tvTripTitle.setText("Trip Resumed");
                            tvTripSubtitle.setText("Follow the route to patient");
                            tvTripSeverity.setText(sev);
                            tvTripPatientPhone.setText(userPh);
                            tvTripTypeBadge.setText(
                                    "advanced".equalsIgnoreCase(ambType) ? "ALS" : "BLS");
                            btnOnTheWay.setEnabled(true);
                            btnArrivedBtn.setEnabled(true);
                            btnGoingHospital.setEnabled(true);
                            showTripButtons("arrived_waiting"); // resume from on_the_way phase
                        }

                        if ("going_to_hospital".equals(status)) {
                            isHospitalPhase = true;
                            showTripButtons("going_to_hospital");
                            tvTripTitle.setText("Going to Hospital 🏥");
                            tvTripSubtitle.setText("Restoring hospital route…");
                            beginDriverHospitalRouteFromFirebase();
                        }
                        watchActiveTripStatus();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        clearTripState();
                    }
                });
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_driver_maps, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupClickListeners();
        SupportMapFragment mapFrag = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFrag != null) mapFrag.getMapAsync(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedClient != null && locationCallback != null)
            fusedClient.removeLocationUpdates(locationCallback);
        cleanupListeners();
        if (routeNavigator != null) routeNavigator.destroy();
        RouteHelper.clearCache();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOAD PROFILE + AMBULANCE + HOSPITAL COORDS
    // ══════════════════════════════════════════════════════════════════════════

    private void loadDriverProfile() {
        if (phone == null) return;
        FirebaseDatabase.getInstance().getReference("driver").child(phone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        driverName     = ds.child("name").getValue(String.class);
                        driverHospital = ds.child("org_name").getValue(String.class);
                        adminNo        = ds.child("adminNo").getValue(String.class);
                        String ambId   = ds.child("assignedAmbulance").getValue(String.class);
                        if (ambId != null && !ambId.isEmpty()) {
                            assignedAmbulanceId = ambId;
                            loadAmbulanceInfo(ambId);
                        }
                        // adminNo in DB = admin's phone number (e.g. "0612345678")
                        // Admin node uses Firebase UID as key — must search by phone to find UID
                        if (adminNo != null && !adminNo.isEmpty()) {
                            resolveAdminUidAndLoadHospital(adminNo);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    /**
     * adminNo stored in driver record is the admin's PHONE NUMBER.
     * Admin records are keyed by Firebase UID.
     * So we search admin node by phone to find the right record and load hospital coords.
     */
    private void resolveAdminUidAndLoadHospital(String adminPhone) {
        // First try: if adminPhone happens to be a UID (direct key match)
        FirebaseDatabase.getInstance().getReference("admin").child(adminPhone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        if (ds.exists() && ds.child("hospitalLat").getValue() != null) {
                            // Direct key match found with hospital data
                            applyHospitalCoords(ds);
                            // Also set up live listener for admin updates
                            listenToAdminHospitalCoords(adminPhone);
                        } else {
                            // Not found by key — search by phone field
                            searchAdminByPhoneForHospital(adminPhone);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        searchAdminByPhoneForHospital(adminPhone);
                    }
                });
    }

    private void searchAdminByPhoneForHospital(String adminPhone) {
        FirebaseDatabase.getInstance().getReference("admin")
                .orderByChild("phone").equalTo(adminPhone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String adminUid = ds.getKey();
                            applyHospitalCoords(ds);
                            // Store the UID so we can use it for live updates
                            if (adminUid != null) {
                                adminNo = adminUid; // update to UID for future use
                                listenToAdminHospitalCoords(adminUid);
                            }
                            return;
                        }
                        android.util.Log.w("HOSPITAL",
                                "No admin found with phone: " + adminPhone);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        android.util.Log.e("HOSPITAL", "Search failed: " + e.getMessage());
                    }
                });
    }

    /** Live listener — updates hospital coords when admin changes them */
    private void listenToAdminHospitalCoords(String adminUid) {
        FirebaseDatabase.getInstance().getReference("admin").child(adminUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        applyHospitalCoords(ds);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void loadAmbulanceInfo(String ambId) {
        FirebaseDatabase.getInstance().getReference("ambulances").child(ambId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        ambulancePlate = ds.child("plateNo").getValue(String.class);
                        ambulanceType  = ds.child("type").getValue(String.class);
                        Object c = ds.child("costPerTrip").getValue();
                        if (c != null) ambulanceCost = Double.parseDouble(c.toString());
                        if (ambulancePlate == null) ambulancePlate = "";
                        if (ambulanceType  == null) ambulanceType  = "BLS";
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    /**
     * Load hospital coordinates from admin node.
     * Admin can be stored under UID (Firebase Auth) or phone number.
     * We try both — first by exact key match, then by orderByChild("phone").
     */
    private void loadHospitalCoords(String adminIdentifier) {
        if (adminIdentifier == null || adminIdentifier.isEmpty()) return;

        // Try direct key lookup first (works if adminNo = UID or phone as key)
        FirebaseDatabase.getInstance().getReference("admin").child(adminIdentifier)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        if (ds.exists()) {
                            applyHospitalCoords(ds);
                        } else {
                            // Not found by key — try searching by phone field
                            searchAdminByPhone(adminIdentifier);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        android.util.Log.e("HOSPITAL", "loadHospitalCoords error: " + e.getMessage());
                    }
                });
    }

    private void searchAdminByPhone(String phone) {
        FirebaseDatabase.getInstance().getReference("admin")
                .orderByChild("phone").equalTo(phone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            applyHospitalCoords(ds);
                            return; // take first match
                        }
                        android.util.Log.w("HOSPITAL",
                                "No admin found with identifier: " + phone);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void applyHospitalCoords(@NonNull DataSnapshot ds) {
        Object lat  = ds.child("hospitalLat").getValue();
        Object lng  = ds.child("hospitalLng").getValue();
        String name = ds.child("hospitalName").getValue(String.class);
        if (lat  != null) hospitalLat  = Double.parseDouble(lat.toString());
        if (lng  != null) hospitalLng  = Double.parseDouble(lng.toString());
        if (name != null && !name.isEmpty()) hospitalName = name;
        android.util.Log.d("HOSPITAL",
                "Loaded coords: lat=" + hospitalLat + " lng=" + hospitalLng
                        + " name=" + hospitalName);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BIND + CLICK
    // ══════════════════════════════════════════════════════════════════════════

    private void bindViews(View v) {
        incomingRequestPanel = v.findViewById(R.id.incoming_request_panel);
        activeTripPanel      = v.findViewById(R.id.active_trip_panel);
        idlePanel            = v.findViewById(R.id.idle_panel);
        tvReqSeverity        = v.findViewById(R.id.tv_req_severity);
        tvReqType            = v.findViewById(R.id.tv_req_type);
        tvReqDistance        = v.findViewById(R.id.tv_req_distance);
        tvReqEta             = v.findViewById(R.id.tv_req_eta);
        tvReqDescription     = v.findViewById(R.id.tv_req_description);
        tvTripTitle          = v.findViewById(R.id.tv_trip_title);
        tvTripSubtitle       = v.findViewById(R.id.tv_trip_subtitle);
        tvTripTypeBadge      = v.findViewById(R.id.tv_trip_type_badge);
        tvTripDistance       = v.findViewById(R.id.tv_trip_distance);
        tvTripSeverity       = v.findViewById(R.id.tv_trip_severity);
        tvTripPatientPhone   = v.findViewById(R.id.tv_trip_patient_phone);
        btnAccept            = v.findViewById(R.id.btn_accept);
        btnReject            = v.findViewById(R.id.btn_reject);
        btnOnTheWay          = v.findViewById(R.id.btn_on_the_way);
        btnArrivedBtn        = v.findViewById(R.id.btn_arrived);
        btnGoingHospital     = v.findViewById(R.id.btn_going_hospital);
        btnEndRoute          = v.findViewById(R.id.btn_end_route);
        tvOnlineStatus       = v.findViewById(R.id.tv_online_status);
        ivReqPhoto           = v.findViewById(R.id.iv_req_photo);
    }

    private void setupClickListeners() {
        btnAccept.setOnClickListener(v        -> acceptRequest());
        btnReject.setOnClickListener(v        -> rejectRequest());
        btnOnTheWay.setOnClickListener(v      -> updateTripStatus("on_the_way"));
        btnArrivedBtn.setOnClickListener(v    -> updateTripStatus("arrived"));
        btnGoingHospital.setOnClickListener(v -> updateTripStatus("going_to_hospital"));
        btnEndRoute.setOnClickListener(v      -> updateTripStatus("end_route"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAP READY
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void onMapReady(@NonNull GoogleMap gMap) {
        googleMap = gMap;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setTiltGesturesEnabled(true);

        // Init navigator with new directional ambulance marker
        routeNavigator = new RouteNavigator(googleMap, R.drawable.ic_ambulance_marker,
                requireContext());

        googleMap.setOnMarkerClickListener(marker -> {
            String tag = marker.getTag() != null ? marker.getTag().toString() : null;
            if (tag != null && requestPins.containsKey(tag) && !isOnActiveTrip) {
                fetchAndShowRequest(tag);
                return true;
            }
            return false;
        });

        requestLocationPermission();
        listenForMyRequests();
        restoreTripState();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOCATION — smoothed GPS + smart rerouting
    // ══════════════════════════════════════════════════════════════════════════

    private void requestLocationPermission() {
        boolean hasFine = ActivityCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasFine) {
            // Request foreground location first
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, RC_LOCATION);
            return;
        }

        // Android 10+ also needs background location for real device GPS in background
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            boolean hasBg = ActivityCompat.checkSelfPermission(requireActivity(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            if (!hasBg) {
                // Must ask separately on Android 10+ — can't bundle with foreground
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        RC_LOCATION + 1);
                // Still start updates — foreground location works now
            }
        }

        // Check GPS is actually turned on (critical for real devices)
        checkGpsAndStart();
    }

    private void checkGpsAndStart() {
        com.google.android.gms.location.LocationSettingsRequest settingsReq =
                new com.google.android.gms.location.LocationSettingsRequest.Builder()
                        .addLocationRequest(new LocationRequest.Builder(
                                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                                3000L).build())
                        .setAlwaysShow(true)
                        .build();

        com.google.android.gms.location.SettingsClient client =
                com.google.android.gms.location.LocationServices
                        .getSettingsClient(requireActivity());

        client.checkLocationSettings(settingsReq)
                .addOnSuccessListener(response -> {
                    // GPS is on — start updates
                    startLocationUpdates();
                })
                .addOnFailureListener(e -> {
                    // GPS is off — show system dialog to turn it on
                    if (e instanceof com.google.android.gms.common.api.ResolvableApiException) {
                        try {
                            com.google.android.gms.common.api.ResolvableApiException resolvable =
                                    (com.google.android.gms.common.api.ResolvableApiException) e;
                            resolvable.startResolutionForResult(requireActivity(), 9002);
                        } catch (android.content.IntentSender.SendIntentException ex) {
                            // Can't resolve — just try starting anyway
                            startLocationUpdates();
                        }
                    } else {
                        startLocationUpdates();
                    }
                });
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        geoRef  = FirebaseDatabase.getInstance().getReference("driversavailable");
        geoFire = new GeoFire(geoRef);

        // Publish last known location immediately so driver appears online right away
        if (phone != null && !phone.isEmpty()) {
            fusedClient.getLastLocation().addOnSuccessListener(last -> {
                if (last != null && geoFire != null) {
                    geoFire.setLocation(phone,
                            new GeoLocation(last.getLatitude(), last.getLongitude()));
                    driverLatLng = new LatLng(last.getLatitude(), last.getLongitude());
                    android.util.Log.d("DriverMap",
                            "Seeded GeoFire: " + last.getLatitude() + "," + last.getLongitude());
                }
            });
        }

        // ── Modern LocationRequest (works on real devices Android 12+) ─────────
        // LocationRequest.create() is deprecated and unreliable on real hardware
        LocationRequest req = new LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(1500L)
                .setMinUpdateDistanceMeters(2f)
                .setWaitForAccurateLocation(false)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc == null) return;

                // ── Smooth GPS with Kalman filter ────────────────────────────
                LatLng smoothedPos = locationSmoother.smooth(loc);

                // ── ALWAYS publish to GeoFire — even first update ─────────────
                // Do NOT filter with isRealMovement before publishing.
                // isRealMovement only gates UI/route updates, not location publishing.
                if (phone != null && !phone.isEmpty()) {
                    geoFire.setLocation(phone,
                            new GeoLocation(smoothedPos.latitude, smoothedPos.longitude));
                }
                driverLatLng = smoothedPos;

                // Update driver marker on map
                if (googleMap != null) {
                    if (routeNavigator != null) {
                        routeNavigator.updateGps(smoothedPos);
                        if (isOnActiveTrip && patientLatLng != null) {
                            routeNavigator.updateCamera(smoothedPos, patientLatLng);
                        }
                    }
                }

                // ── Active trip route + auto-arrived ─────────────────────────
                if (isOnActiveTrip && patientLatLng != null && googleMap != null) {
                    float[] res = new float[1];
                    Location.distanceBetween(smoothedPos.latitude, smoothedPos.longitude,
                            patientLatLng.latitude, patientLatLng.longitude, res);
                    float distM = res[0];

                    if (tvTripDistance != null)
                        tvTripDistance.setText(
                                String.format(Locale.getDefault(), "%.1f km", distM / 1000f));

                    // Hospital phase must NEVER depend only on isRealMovement.
                    // If the driver presses GOING TO HOSPITAL while standing still,
                    // the old movement gate can block route drawing forever.
                    if (isHospitalPhase && hospitalLat != 0) {
                        renderDriverHospitalRoute(smoothedPos, patientLatLng, routePolyline == null);
                    } else if (locationSmoother.isRealMovement(smoothedPos)) {
                        if (hospitalLat != 0) {
                            renderDriverHospitalRoute(smoothedPos, patientLatLng, false);
                        } else {
                            RouteHelper.updateRoute("driver",
                                    googleMap, smoothedPos, patientLatLng,
                                    routePolyline,
                                    polyline -> {
                                        if (!isAdded()) return;
                                        if (routePolyline != null) routePolyline.remove();
                                        routePolyline = polyline;
                                    },
                                    (etaSec, distanceM) -> {
                                        if (tvTripDistance != null)
                                            tvTripDistance.setText(String.format(
                                                    Locale.getDefault(), "%.1f km", distanceM / 1000f));
                                    });
                        }
                    }

                    if (distM <= AUTO_ARRIVE_M && !autoArrivedFired) {
                        autoArrivedFired = true;
                        triggerAutoArrived();
                    }
                }
            }
        };
        fusedClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == RC_LOCATION) {
            if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
                checkGpsAndStart(); // Check GPS settings then start
            } else {
                Toast.makeText(getActivity(),
                        "Location permission required — please allow in Settings",
                        Toast.LENGTH_LONG).show();
            }
        }
        // RC_LOCATION+1 is background location — GPS already started, no action needed
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 9002) {
            // GPS settings dialog result
            if (resultCode == android.app.Activity.RESULT_OK) {
                startLocationUpdates(); // User turned GPS on
            } else {
                Toast.makeText(getActivity(),
                        "GPS is off — location will not update",
                        Toast.LENGTH_LONG).show();
                startLocationUpdates(); // Try anyway — may work with network location
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LISTEN — only requests assigned to THIS driver
    // ══════════════════════════════════════════════════════════════════════════

    private void listenForMyRequests() {
        if (phone == null || phone.isEmpty()) {
            android.util.Log.e("DriverMap", "phone is null — cannot listen for requests");
            return;
        }

        DatabaseReference reqsRef = FirebaseDatabase.getInstance().getReference("requests");
        allRequestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (Marker m : requestPins.values()) m.remove();
                requestPins.clear();

                for (DataSnapshot reqSnap : snapshot.getChildren()) {
                    String userPhone = reqSnap.getKey();
                    String status    = reqSnap.child("status").getValue(String.class);
                    String assigned  = reqSnap.child("assignedDriver").getValue(String.class);
                    if (status == null || userPhone == null || assigned == null) continue;

                    // ── Patient cancelled during active trip ──────────────────
                    if (isOnActiveTrip
                            && userPhone.equals(activeUserPhone)
                            && "cancelled".equals(status)) {
                        handlePatientCancelled();
                        continue;
                    }

                    // ── Only care about requests assigned to THIS driver ───────
                    if (!phone.trim().equals(assigned.trim())) continue;

                    // Driver sees request ONLY when admin has assigned them
                    // "admin_assigned" = admin chose this driver
                    // Do NOT show on "searching" or "assigned" — those go to admin only
                    boolean isNewTask = "admin_assigned".equals(status)
                            && !isOnActiveTrip
                            && !userPhone.equals(activeUserPhone);

                    if (isNewTask) {
                        fetchRequestPin(userPhone, reqSnap);
                        fetchAndShowRequest(userPhone);
                    }

                    // ── En route — keep pin showing destination ───────────────
                    if ("en_route".equals(status) && isOnActiveTrip
                            && userPhone.equals(activeUserPhone)) {
                        fetchRequestPin(userPhone, reqSnap);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                android.util.Log.e("DriverMap", "requests listener cancelled: " + e.getMessage());
            }
        };
        reqsRef.addValueEventListener(allRequestsListener);
    }

    private void fetchRequestPin(String userPhone, DataSnapshot reqSnap) {
        FirebaseDatabase.getInstance().getReference("Requests")
                .child(userPhone).child("l")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        if (!ds.exists() || googleMap == null) return;
                        List<Object> c = (List<Object>) ds.getValue();
                        if (c == null || c.size() < 2) return;
                        double lat = Double.parseDouble(c.get(0).toString());
                        double lng = Double.parseDouble(c.get(1).toString());
                        String sev = reqSnap.child("severity").getValue(String.class);
                        float hue = "Critical".equalsIgnoreCase(sev) ? BitmapDescriptorFactory.HUE_RED
                                : "High".equalsIgnoreCase(sev)        ? BitmapDescriptorFactory.HUE_ORANGE
                                  : "Medium".equalsIgnoreCase(sev)      ? BitmapDescriptorFactory.HUE_YELLOW
                                    : BitmapDescriptorFactory.HUE_GREEN;
                        Marker pin = googleMap.addMarker(new MarkerOptions()
                                .position(new LatLng(lat, lng))
                                .title("Patient · " + (sev != null ? sev : ""))
                                .icon(BitmapDescriptorFactory.defaultMarker(hue)));
                        if (pin != null) { pin.setTag(userPhone); requestPins.put(userPhone, pin); }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void fetchAndShowRequest(String userPhone) {
        FirebaseDatabase.getInstance().getReference("requests").child(userPhone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        if (!ds.exists()) return;
                        String status = ds.child("status").getValue(String.class);
                        if ("en_route".equals(status) || "arrived".equals(status)
                                || "going_to_hospital".equals(status)
                                || "cancelled".equals(status) || "rejected".equals(status)
                                || "trip_ended".equals(status)) return;

                        String severity  = ds.child("severity").getValue(String.class);
                        String ambType   = ds.child("ambulanceType").getValue(String.class);
                        String desc      = ds.child("description").getValue(String.class);
                        String uName     = ds.child("userName").getValue(String.class);
                        String uPhone    = ds.child("userPhone").getValue(String.class);
                        String photoUrl  = ds.child("photoUrl").getValue(String.class);
                        // Insurance
                        Object insObj    = ds.child("hasInsurance").getValue();
                        boolean hasIns   = insObj != null && Boolean.parseBoolean(insObj.toString());
                        String insComp   = ds.child("insuranceCompany").getValue(String.class);
                        String insNum    = ds.child("insuranceNumber").getValue(String.class);

                        if (uName  == null || uName.isEmpty())  uName  = userPhone;
                        if (uPhone == null || uPhone.isEmpty()) uPhone = userPhone;

                        final String displayName  = uName;
                        final String displayPhone = uPhone;
                        final String finalPhoto   = photoUrl;
                        final boolean finalHasIns = hasIns;
                        final String  finalInsComp= insComp;
                        final String  finalInsNum = insNum;

                        fetchPatientLocation(userPhone, severity, ambType, desc,
                                displayName, displayPhone, finalPhoto,
                                finalHasIns, finalInsComp, finalInsNum);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void fetchPatientLocation(String userPhone, String severity,
                                      String ambType, String desc,
                                      String userName, String userPhoneDisplay,
                                      String photoUrl, boolean hasInsurance,
                                      String insCompany, String insNumber) {
        FirebaseDatabase.getInstance().getReference("Requests")
                .child(userPhone).child("l")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        double lat = 0, lng = 0;
                        if (ds.exists()) {
                            List<Object> c = (List<Object>) ds.getValue();
                            if (c != null && c.size() >= 2) {
                                lat = Double.parseDouble(c.get(0).toString());
                                lng = Double.parseDouble(c.get(1).toString());
                            }
                        }
                        patientLatLng = new LatLng(lat, lng);
                        String dist = "—", eta = "—";
                        if (driverLatLng != null && lat != 0) {
                            float[] res = new float[1];
                            Location.distanceBetween(driverLatLng.latitude,
                                    driverLatLng.longitude, lat, lng, res);
                            float km = res[0] / 1000f;
                            dist = String.format(Locale.getDefault(), "%.1f km", km);
                            eta  = Math.max(1, (int)(km / 0.5f)) + " min";
                        }
                        showIncomingCard(userPhone, severity, ambType, desc,
                                dist, eta, userName, userPhoneDisplay,
                                photoUrl, hasInsurance, insCompany, insNumber);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        showIncomingCard(userPhone, severity, ambType, desc,
                                "—", "—", userName, userPhoneDisplay,
                                null, false, null, null);
                    }
                });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INCOMING CARD
    // ══════════════════════════════════════════════════════════════════════════

    private void showIncomingCard(String userPhone, String severity, String ambType,
                                  String desc, String dist, String eta,
                                  String userName, String userPhoneDisplay,
                                  String photoUrl, boolean hasInsurance,
                                  String insCompany, String insNumber) {
        if (isOnActiveTrip) return;

        activeUserPhone = userPhone;
        activeSeverity  = severity != null ? severity : "—";
        activeAmbType   = ambType  != null ? ambType  : "basic";

        idlePanel.setVisibility(View.GONE);
        incomingRequestPanel.setVisibility(View.VISIBLE);

        tvReqSeverity.setText(activeSeverity.toUpperCase());
        setSeverityBadge(tvReqSeverity, activeSeverity);
        tvReqType.setText("advanced".equalsIgnoreCase(activeAmbType)
                ? "Advanced (ALS)" : "Basic (BLS)");
        tvReqDistance.setText(dist);
        tvReqEta.setText(eta);

        // Patient name + phone with call button
        if (tvTripPatientPhone != null) {
            String display = userName != null && !userName.equals(userPhoneDisplay)
                    ? userName + "\n" + userPhoneDisplay
                    : userPhoneDisplay;
            tvTripPatientPhone.setText(display);
            tvTripPatientPhone.setCompoundDrawablesWithIntrinsicBounds(
                    0, 0, android.R.drawable.sym_action_call, 0);
            tvTripPatientPhone.setOnClickListener(v -> {
                android.content.Intent dial = new android.content.Intent(
                        android.content.Intent.ACTION_DIAL,
                        android.net.Uri.parse("tel:" + userPhoneDisplay));
                startActivity(dial);
            });
        }

        // Description
        tvReqDescription.setVisibility(
                desc != null && !desc.isEmpty() && !"No description".equals(desc)
                        ? View.VISIBLE : View.GONE);
        if (tvReqDescription.getVisibility() == View.VISIBLE)
            tvReqDescription.setText("📋  " + desc);

        // Insurance info — shown to driver
        TextView tvInsurance = incomingRequestPanel.findViewById(R.id.tv_req_insurance_driver);
        if (tvInsurance != null) {
            if (hasInsurance) {
                tvInsurance.setVisibility(View.VISIBLE);
                tvInsurance.setText("🏥 Insurance: "
                        + (insCompany != null ? insCompany : "—")
                        + "  |  " + (insNumber != null ? insNumber : "—"));
            } else {
                tvInsurance.setVisibility(View.GONE);
            }
        }

        // Patient photo
        if (ivReqPhoto != null) ivReqPhoto.setVisibility(View.GONE);
        if (photoUrl != null && !photoUrl.isEmpty() && ivReqPhoto != null && isAdded()) {
            Glide.with(requireActivity()).load(photoUrl)
                    .placeholder(android.R.color.darker_gray)
                    .centerCrop().into(ivReqPhoto);
            ivReqPhoto.setVisibility(View.VISIBLE);
        }

        if (patientLatLng != null && patientLatLng.latitude != 0) {
            if (patientMarker != null) patientMarker.remove();
            patientMarker = googleMap.addMarker(new MarkerOptions()
                    .position(patientLatLng).title("Patient")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            if (driverLatLng != null)
                try {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                            new LatLngBounds.Builder()
                                    .include(driverLatLng).include(patientLatLng).build(), 120));
                } catch (Exception e) {
                    googleMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(patientLatLng, 14));
                }
        }

        sendPushNotification("New Request 🆘", activeSeverity + " · " + dist);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ACCEPT / REJECT
    // ══════════════════════════════════════════════════════════════════════════

    private void acceptRequest() {
        isOnActiveTrip   = true;
        autoArrivedFired = false;
        RouteHelper.clearCache("driver");

        DatabaseReference reqRef = FirebaseDatabase.getInstance()
                .getReference("requests").child(activeUserPhone);
        reqRef.child("status").setValue("en_route");
        reqRef.child("assignedDriver").setValue(phone);
        reqRef.child("driverName").setValue(driverName != null ? driverName : phone);
        reqRef.child("driverHospital").setValue(driverHospital != null ? driverHospital : "—");
        reqRef.child("driverPhone").setValue(phone);
        reqRef.child("ambulancePlate").setValue(ambulancePlate != null ? ambulancePlate : "");
        reqRef.child("ambulanceType").setValue(ambulanceType != null ? ambulanceType : "BLS");
        reqRef.child("costPerTrip").setValue(ambulanceCost);
        reqRef.child("assignedAmbulanceId").setValue(assignedAmbulanceId);
        reqRef.child("acceptedAt").setValue(System.currentTimeMillis());

        if (assignedAmbulanceId != null && !assignedAmbulanceId.isEmpty())
            FirebaseDatabase.getInstance().getReference("ambulances")
                    .child(assignedAmbulanceId).child("status").setValue("on_trip");

        Marker pin = requestPins.remove(activeUserPhone);
        if (pin != null) pin.remove();

        incomingRequestPanel.setVisibility(View.GONE);
        activeTripPanel.setVisibility(View.VISIBLE);
        tvTripTypeBadge.setText("advanced".equalsIgnoreCase(activeAmbType) ? "ALS" : "BLS");
        tvTripSeverity.setText(activeSeverity);
        setSeverityColor(tvTripSeverity, activeSeverity);
        tvTripPatientPhone.setText(activeUserPhone);
        tvTripTitle.setText("En Route to Patient");
        tvTripSubtitle.setText("Follow the route");
        // NEW FLOW: accept → show ON THE WAY only
        showTripButtons("on_the_way");

        // Start route navigator
        if (routeNavigator != null) {
            routeNavigator.setDestination(patientLatLng);
            routeNavigator.setEtaCallback((distM, etaSec) -> {
                if (tvTripDistance != null)
                    tvTripDistance.setText(String.format(
                            Locale.getDefault(), "%.1f km", distM / 1000f));
            });
            routeNavigator.start();

            // Draw straight line immediately — only if BOTH positions are valid
            if (driverLatLng != null && patientLatLng != null
                    && patientLatLng.latitude != 0 && patientLatLng.longitude != 0
                    && googleMap != null) {
                routeNavigator.setRouteStraight(driverLatLng, patientLatLng);
                try {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                            new LatLngBounds.Builder()
                                    .include(driverLatLng)
                                    .include(patientLatLng).build(), 120));
                } catch (Exception e) {
                    // Bounds can fail if points are identical — fallback to center
                    googleMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(patientLatLng, 15));
                }
            }
        }

        // Fetch real Directions API route in background
        if (driverLatLng != null && patientLatLng != null
                && patientLatLng.latitude != 0 && googleMap != null) {
            RouteHelper.drawRoute("driver", googleMap, driverLatLng, patientLatLng,
                    null, polyline -> {
                        List<LatLng> pts = RouteHelper.getLastDecodedPoints("driver");
                        if (routeNavigator != null && pts != null && !pts.isEmpty()) {
                            polyline.remove();
                            routeNavigator.setRoute(pts);
                        }
                    });
        }

        watchActiveTripStatus();
        saveTripState();
        Toast.makeText(getActivity(), "Accepted — navigate to patient",
                Toast.LENGTH_SHORT).show();
    }

    private void rejectRequest() {
        FirebaseDatabase.getInstance().getReference("requests")
                .child(activeUserPhone).child("status").setValue("rejected");
        // Reset activeUserPhone before cleanupTrip so it clears properly
        String rejectedPhone = activeUserPhone;
        activeUserPhone = "";
        cleanupTrip();
        Toast.makeText(getActivity(), "Request declined", Toast.LENGTH_SHORT).show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AUTO-ARRIVED
    // ══════════════════════════════════════════════════════════════════════════

    private void triggerAutoArrived() {
        DatabaseReference reqRef = FirebaseDatabase.getInstance()
                .getReference("requests").child(activeUserPhone);
        reqRef.child("status").setValue("arrived");
        reqRef.child("driverStatus").setValue("arrived");
        reqRef.child("arrivedAt").setValue(System.currentTimeMillis());

        if (assignedAmbulanceId != null && !assignedAmbulanceId.isEmpty())
            FirebaseDatabase.getInstance().getReference("ambulances")
                    .child(assignedAmbulanceId).child("status").setValue("available");

        tvTripTitle.setText("Arrived at Patient ✅");
        tvTripSubtitle.setText("Assess patient — go to hospital or end route");
        // Show GOING TO HOSPITAL + END ROUTE
        showTripButtons("arrived_at_client");

        if (routePolyline != null) { routePolyline.remove(); routePolyline = null; }
        if (routeNavigator != null) routeNavigator.stop();
        RouteHelper.clearCache("driver");

        sendPushNotification("Arrived ✅", "Ambulance is at your location.");
        Toast.makeText(getActivity(), "Auto-arrived — within 100m",
                Toast.LENGTH_LONG).show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3 STATUS BUTTONS
    // ══════════════════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════════════════
    // BUTTON STATE MANAGER
    // Controls which buttons are visible based on current trip phase
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * phase:
     *   "on_the_way"       → show [ON THE WAY] only (accept → heading to patient)
     *   "arrived_at_client"→ show [GOING TO HOSPITAL] + [END ROUTE]
     *   "going_to_hospital"→ show [END ROUTE] only
     *   "none"             → hide all
     */
    private void showTripButtons(String phase) {
        if (btnOnTheWay == null) return;
        btnOnTheWay.setVisibility(View.GONE);
        btnArrivedBtn.setVisibility(View.GONE);
        btnGoingHospital.setVisibility(View.GONE);
        btnEndRoute.setVisibility(View.GONE);

        switch (phase) {
            case "on_the_way":
                btnOnTheWay.setVisibility(View.VISIBLE);
                btnOnTheWay.setEnabled(true);
                break;
            case "arrived_waiting":
                // Driver said on the way, show ARRIVED AT PATIENT
                btnArrivedBtn.setVisibility(View.VISIBLE);
                btnArrivedBtn.setEnabled(true);
                break;
            case "arrived_at_client":
                // Driver arrived — show going to hospital + end route
                btnGoingHospital.setVisibility(View.VISIBLE);
                btnGoingHospital.setEnabled(true);
                btnEndRoute.setVisibility(View.VISIBLE);
                btnEndRoute.setEnabled(true);
                break;
            case "going_to_hospital":
                // Heading to hospital — show end route only
                btnEndRoute.setVisibility(View.VISIBLE);
                btnEndRoute.setEnabled(true);
                break;
        }
    }

    private void updateTripStatus(String newStatus) {
        if (activeUserPhone == null || activeUserPhone.isEmpty()) return;
        DatabaseReference reqRef = FirebaseDatabase.getInstance()
                .getReference("requests").child(activeUserPhone);

        switch (newStatus) {

            case "on_the_way":
                reqRef.child("status").setValue("en_route");
                reqRef.child("driverStatus").setValue("on_the_way");
                isHospitalPhase = false;
                tvTripTitle.setText("On The Way 🚑");
                tvTripSubtitle.setText("Patient has been notified");
                showTripButtons("arrived_waiting");
                sendPushNotification("Ambulance On The Way", "Driver is heading to you.");
                break;

            case "arrived":
                reqRef.child("status").setValue("arrived");
                reqRef.child("driverStatus").setValue("arrived");
                reqRef.child("arrivedAt").setValue(System.currentTimeMillis());
                isHospitalPhase = false;
                tvTripTitle.setText("Arrived at Patient ✅");
                tvTripSubtitle.setText("Assess patient — then go to hospital or end route");
                showTripButtons("arrived_at_client");
                if (routePolyline != null) { routePolyline.remove(); routePolyline = null; }
                if (routeNavigator != null) routeNavigator.stop();
                RouteHelper.clearCache("driver");
                RouteHelper.clearCache("driver_hospital");
                sendPushNotification("Ambulance Arrived", "Help has arrived!");
                break;

            case "going_to_hospital":
                // CRITICAL: local UI state is NOT enough. We write Firebase, then rebuild
                // the route from Firebase truth exactly like admin/user now do.
                reqRef.child("status").setValue("going_to_hospital");
                reqRef.child("driverStatus").setValue("going_to_hospital");

                if (assignedAmbulanceId != null && !assignedAmbulanceId.isEmpty()) {
                    FirebaseDatabase.getInstance().getReference("ambulances")
                            .child(assignedAmbulanceId).child("status").setValue("available");
                }

                isOnActiveTrip = true;
                isHospitalPhase = true;
                autoArrivedFired = false;

                tvTripTitle.setText("Going to Hospital 🏥");
                tvTripSubtitle.setText("Loading hospital route…");
                showTripButtons("going_to_hospital");
                sendPushNotification("Going to Hospital 🏥", "Patient is being transported.");

                // Stop the patient navigation completely. If RouteNavigator remains alive,
                // it can keep showing the old patient route even after our polyline changes.
                if (routeNavigator != null) routeNavigator.stop();
                if (routePolyline != null) { routePolyline.remove(); routePolyline = null; }
                RouteHelper.clearCache("driver");
                RouteHelper.clearCache("driver_hospital");

                beginDriverHospitalRouteFromFirebase();
                saveTripState();
                break;

            case "end_route":
                reqRef.child("status").setValue("trip_ended");
                reqRef.child("driverStatus").setValue("trip_ended");
                if (assignedAmbulanceId != null && !assignedAmbulanceId.isEmpty()) {
                    FirebaseDatabase.getInstance().getReference("ambulances")
                            .child(assignedAmbulanceId).child("status").setValue("available");
                }
                sendPushNotification("Trip Ended", "The ambulance trip has ended.");
                showTripButtons("none");
                cleanupTrip();
                Toast.makeText(getActivity(), "Route ended ✓", Toast.LENGTH_SHORT).show();
                break;
        }
    }




    // ══════════════════════════════════════════════════════════════════════════
    // WATCH ACTIVE TRIP
    // ══════════════════════════════════════════════════════════════════════════

    private void loadHospitalFromDriverRecordAndRoute() {
        if (phone == null || phone.isEmpty()) {
            Toast.makeText(getActivity(), "Driver phone missing — cannot load hospital.", Toast.LENGTH_LONG).show();
            showTripButtons("arrived_at_client");
            return;
        }

        FirebaseDatabase.getInstance().getReference("driver").child(phone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        String aNo = ds.child("adminNo").getValue(String.class);
                        if (aNo == null || aNo.isEmpty()) {
                            Toast.makeText(getActivity(),
                                    "Driver adminNo missing. Contact dispatcher.",
                                    Toast.LENGTH_LONG).show();
                            showTripButtons("arrived_at_client");
                            return;
                        }
                        adminNo = aNo;
                        fetchAdminCoordsAndRoute(aNo);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        Toast.makeText(getActivity(),
                                "Could not load driver admin: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                        showTripButtons("arrived_at_client");
                    }
                });
    }

    private void fetchAdminCoordsAndRoute(String adminIdentifier) {
        // Try direct UID key first
        FirebaseDatabase.getInstance().getReference("admin").child(adminIdentifier)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        if (ds.exists() && ds.child("hospitalLat").getValue() != null) {
                            buildHospitalRoute(ds);
                        } else {
                            // Not found by UID — search by phone field
                            FirebaseDatabase.getInstance().getReference("admin")
                                    .orderByChild("phone").equalTo(adminIdentifier)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot snap) {
                                            boolean found = false;
                                            for (DataSnapshot child : snap.getChildren()) {
                                                if (child.child("hospitalLat").getValue() != null) {
                                                    buildHospitalRoute(child);
                                                    found = true;
                                                    break;
                                                }
                                            }
                                            if (!found) {
                                                if (getActivity() != null)
                                                    Toast.makeText(getActivity(),
                                                            "Hospital GPS not set.\nAsk admin to set it in Settings → Hospital Location.",
                                                            Toast.LENGTH_LONG).show();
                                                if (btnGoingHospital != null)
                                                    btnGoingHospital.setEnabled(true);
                                            }
                                        }
                                        @Override public void onCancelled(@NonNull DatabaseError e) {
                                            if (btnGoingHospital != null)
                                                btnGoingHospital.setEnabled(true);
                                        }
                                    });
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                        if (getActivity() != null)
                            Toast.makeText(getActivity(),
                                    "Error loading hospital: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        if (btnGoingHospital != null) btnGoingHospital.setEnabled(true);
                    }
                });
    }

    /** Called once we have a valid admin DataSnapshot with hospital coords */
    private void buildHospitalRoute(@NonNull DataSnapshot ds) {
        Object latVal = ds.child("hospitalLat").getValue();
        Object lngVal = ds.child("hospitalLng").getValue();
        String hospNm = ds.child("hospitalName").getValue(String.class);

        if (latVal == null || lngVal == null) {
            if (getActivity() != null) {
                Toast.makeText(getActivity(),
                        "Hospital GPS not set. Ask admin to set it in Settings.",
                        Toast.LENGTH_LONG).show();
            }
            if (btnGoingHospital != null) btnGoingHospital.setEnabled(true);
            return;
        }

        LatLng hospitalLatLng = new LatLng(
                Double.parseDouble(latVal.toString()),
                Double.parseDouble(lngVal.toString()));
        String resolvedName = hospNm != null && !hospNm.isEmpty() ? hospNm : "Hospital";
        startDriverHospitalNavigation(hospitalLatLng, resolvedName);

        android.util.Log.d("HOSPITAL", "Hospital route started: " + resolvedName
                + " lat=" + hospitalLatLng.latitude + " lng=" + hospitalLatLng.longitude);
    }


    private LatLng parseGeoFireLatLng(@NonNull DataSnapshot snap) {
        if (!snap.exists()) return null;
        try {
            List<Object> c = (List<Object>) snap.getValue();
            if (c == null || c.size() < 2) return null;
            double lat = Double.parseDouble(c.get(0).toString());
            double lng = Double.parseDouble(c.get(1).toString());
            if (Double.isNaN(lat) || Double.isNaN(lng) || lat == 0 || lng == 0) return null;
            return new LatLng(lat, lng);
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Driver-side hospital routing must use Firebase truth, not stale local fields.
     * Source order:
     * 1) requests/{activeUserPhone}/hospitalLat,hospitalLng if already written
     * 2) driver/{phone}/adminNo -> admin/{adminNo} or admin where phone == adminNo
     */
    private void beginDriverHospitalRouteFromFirebase() {
        if (activeUserPhone == null || activeUserPhone.isEmpty()) {
            Toast.makeText(getActivity(), "Active patient missing — cannot route to hospital.", Toast.LENGTH_LONG).show();
            showTripButtons("arrived_at_client");
            return;
        }

        FirebaseDatabase.getInstance().getReference("requests").child(activeUserPhone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot reqSnap) {
                        Object rLat = reqSnap.child("hospitalLat").getValue();
                        Object rLng = reqSnap.child("hospitalLng").getValue();
                        String rName = reqSnap.child("hospitalName").getValue(String.class);

                        if (rLat != null && rLng != null) {
                            LatLng hospital = new LatLng(
                                    Double.parseDouble(rLat.toString()),
                                    Double.parseDouble(rLng.toString()));
                            startDriverHospitalNavigation(hospital,
                                    rName != null && !rName.isEmpty() ? rName : "Hospital");
                            return;
                        }

                        loadHospitalFromDriverRecordAndRoute();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        loadHospitalFromDriverRecordAndRoute();
                    }
                });
    }

    private void startDriverHospitalNavigation(@NonNull LatLng hospitalLatLng,
                                               @NonNull String resolvedHospitalName) {
        if (!isAdded() || googleMap == null) return;

        hospitalLat = hospitalLatLng.latitude;
        hospitalLng = hospitalLatLng.longitude;
        hospitalName = resolvedHospitalName;
        patientLatLng = hospitalLatLng;
        isOnActiveTrip = true;
        isHospitalPhase = true;
        autoArrivedFired = false;

        tvTripTitle.setText("Going to Hospital 🏥");
        tvTripSubtitle.setText("Route to " + hospitalName);
        showTripButtons("going_to_hospital");

        if (activeUserPhone != null && !activeUserPhone.isEmpty()) {
            DatabaseReference req = FirebaseDatabase.getInstance()
                    .getReference("requests").child(activeUserPhone);
            req.child("hospitalLat").setValue(hospitalLat);
            req.child("hospitalLng").setValue(hospitalLng);
            req.child("hospitalName").setValue(hospitalName);
        }

        if (patientMarker != null) patientMarker.remove();
        patientMarker = googleMap.addMarker(new MarkerOptions()
                .position(hospitalLatLng)
                .title(hospitalName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        if (routeNavigator != null) routeNavigator.stop();
        if (routePolyline != null) { routePolyline.remove(); routePolyline = null; }
        RouteHelper.clearCache("driver");
        RouteHelper.clearCache("driver_hospital");

        // 1) Draw immediately from in-memory GPS if available.
        if (driverLatLng != null) {
            renderDriverHospitalRoute(driverLatLng, hospitalLatLng, true);
        }

        // 2) Read the exact GeoFire value that admin/user use. This is the key fix.
        if (phone != null && !phone.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("driversavailable")
                    .child(phone).child("l")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot snap) {
                            LatLng p = parseGeoFireLatLng(snap);
                            if (p != null) {
                                driverLatLng = p;
                                renderDriverHospitalRoute(p, hospitalLatLng, true);
                            } else {
                                tryLastKnownLocationForHospital(hospitalLatLng);
                            }
                        }
                        @Override public void onCancelled(@NonNull DatabaseError e) {
                            tryLastKnownLocationForHospital(hospitalLatLng);
                        }
                    });
        } else {
            tryLastKnownLocationForHospital(hospitalLatLng);
        }

        saveTripState();
    }

    @SuppressLint("MissingPermission")
    private void tryLastKnownLocationForHospital(@NonNull LatLng hospitalLatLng) {
        if (fusedClient == null || getActivity() == null) return;
        boolean hasFine = ActivityCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ActivityCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasFine && !hasCoarse) return;

        fusedClient.getLastLocation().addOnSuccessListener(last -> {
            if (last == null) return;
            LatLng p = new LatLng(last.getLatitude(), last.getLongitude());
            driverLatLng = p;
            renderDriverHospitalRoute(p, hospitalLatLng, true);
        });
    }

    private void renderDriverHospitalRoute(@NonNull LatLng from, @NonNull LatLng to, boolean forceFresh) {
        if (!isAdded() || googleMap == null) return;

        if (routePolyline == null || forceFresh) {
            if (routePolyline != null) routePolyline.remove();
            routePolyline = googleMap.addPolyline(
                    new com.google.android.gms.maps.model.PolylineOptions()
                            .add(from, to)
                            .width(14f)
                            .color(android.graphics.Color.parseColor("#EA6D35"))
                            .geodesic(true));
            try {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                        new LatLngBounds.Builder().include(from).include(to).build(), 140));
            } catch (Exception e) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(to, 13));
            }
            RouteHelper.drawRoute("driver_hospital", googleMap, from, to,
                    null, roadPolyline -> {
                        if (!isAdded() || googleMap == null) return;
                        if (routePolyline != null) routePolyline.remove();
                        routePolyline = roadPolyline;
                        routePolyline.setColor(android.graphics.Color.parseColor("#EA6D35"));
                        routePolyline.setWidth(14f);
                    });
        } else {
            RouteHelper.updateRoute("driver_hospital", googleMap, from, to,
                    routePolyline,
                    polyline -> {
                        if (!isAdded()) return;
                        if (routePolyline != null) routePolyline.remove();
                        routePolyline = polyline;
                        routePolyline.setColor(android.graphics.Color.parseColor("#EA6D35"));
                        routePolyline.setWidth(14f);
                    },
                    (etaSec, distanceM) -> {
                        if (tvTripDistance != null)
                            tvTripDistance.setText(String.format(
                                    Locale.getDefault(), "%.1f km", distanceM / 1000f));
                    });
        }
    }

    private void watchActiveTripStatus() {
        if (activeUserPhone == null || activeUserPhone.isEmpty()) return;

        // Listen to the whole request, not only /status. Hospital coordinates may be
        // written shortly after status changes, and driver must retry route building.
        activeTripRef = FirebaseDatabase.getInstance()
                .getReference("requests").child(activeUserPhone);

        activeTripStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot ds) {
                String status = ds.child("status").getValue(String.class);
                if (status == null) return;

                if ("cancelled".equals(status)) {
                    handlePatientCancelled();
                    return;
                }

                if ("trip_ended".equals(status)) {
                    cleanupTrip();
                    return;
                }

                if ("going_to_hospital".equals(status)) {
                    // Do not rely on button click only. If the UI missed the click path,
                    // rotation happened, or GPS was late, this listener rebuilds the route.
                    if (!isHospitalPhase || routePolyline == null || patientLatLng == null) {
                        isOnActiveTrip = true;
                        isHospitalPhase = true;
                        autoArrivedFired = false;
                        tvTripTitle.setText("Going to Hospital 🏥");
                        tvTripSubtitle.setText("Loading hospital route…");
                        showTripButtons("going_to_hospital");
                        if (routeNavigator != null) routeNavigator.stop();
                        beginDriverHospitalRouteFromFirebase();
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        activeTripRef.addValueEventListener(activeTripStatusListener);
    }


    private void handlePatientCancelled() {
        if (!isOnActiveTrip) return;
        Toast.makeText(getActivity(), "Patient cancelled", Toast.LENGTH_LONG).show();
        sendPushNotification("Request Cancelled", "The patient has cancelled.");
        if (!assignedAmbulanceId.isEmpty())
            FirebaseDatabase.getInstance().getReference("ambulances")
                    .child(assignedAmbulanceId).child("status").setValue("available");
        cleanupTrip(); // cleanupTrip now sets isOnActiveTrip=false and shows idlePanel
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ══════════════════════════════════════════════════════════════════════════

    private void cleanupTrip() {
        if (patientMarker != null) { patientMarker.remove(); patientMarker = null; }
        if (routePolyline != null) { routePolyline.remove(); routePolyline = null; }

        // Stop active trip status watcher ONLY — NOT the main requests listener
        if (activeTripRef != null && activeTripStatusListener != null) {
            activeTripRef.removeEventListener(activeTripStatusListener);
            activeTripRef = null;
        }

        if (routeNavigator != null) routeNavigator.stop();

        activeUserPhone  = "";
        activeSeverity   = "";
        activeAmbType    = "";
        patientLatLng    = null;
        autoArrivedFired = false;
        isHospitalPhase  = false;
        hospitalLat = 0;
        hospitalLng = 0;
        isOnActiveTrip   = false;

        RouteHelper.clearCache("driver");
        locationSmoother.reset();
        clearTripState();

        // Return to idle — driver is now ready for next request
        if (activeTripPanel      != null) activeTripPanel.setVisibility(View.GONE);
        if (incomingRequestPanel != null) incomingRequestPanel.setVisibility(View.GONE);
        if (idlePanel            != null) idlePanel.setVisibility(View.VISIBLE);
    }

    private void cleanupListeners() {
        DatabaseReference r = FirebaseDatabase.getInstance().getReference("requests");
        if (allRequestsListener != null) r.removeEventListener(allRequestsListener);
        if (activeTripRef != null && activeTripStatusListener != null)
            activeTripRef.removeEventListener(activeTripStatusListener);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SMOOTH MARKER ANIMATION
    // ══════════════════════════════════════════════════════════════════════════

    private void animateMarker(Marker marker, LatLng toPos, float toBearing) {
        LatLng from  = marker.getPosition();
        float  fromB = marker.getRotation();
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(800);
        anim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            marker.setPosition(new LatLng(
                    from.latitude  + f * (toPos.latitude  - from.latitude),
                    from.longitude + f * (toPos.longitude - from.longitude)));
            marker.setRotation(fromB + f * (toBearing - fromB));
        });
        anim.start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SEVERITY + NOTIFICATIONS
    // ══════════════════════════════════════════════════════════════════════════

    private void setSeverityBadge(TextView v, String sev) {
        v.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(sColor(sev)));
    }
    private void setSeverityColor(TextView v, String sev) { v.setTextColor(sColor(sev)); }
    private int sColor(String sev) {
        if (sev == null) return Color.parseColor("#27AE60");
        switch (sev.toLowerCase()) {
            case "critical": return Color.parseColor("#E74C3C");
            case "high":     return Color.parseColor("#E67E22");
            case "medium":   return Color.parseColor("#F1C40F");
            default:         return Color.parseColor("#27AE60");
        }
    }

    private void sendPushNotification(String title, String body) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(requireActivity(),
                android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(getActivity(), Drv_Home.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(getActivity(), 0, intent, flags);
        NotificationManagerCompat.from(requireActivity()).notify(NOTIF_ID,
                new NotificationCompat.Builder(requireActivity(), CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title).setContentText(body)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true).setContentIntent(pi).build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Driver Alerts", NotificationManager.IMPORTANCE_HIGH);
            requireActivity().getSystemService(NotificationManager.class)
                    .createNotificationChannel(ch);
        }
    }
}
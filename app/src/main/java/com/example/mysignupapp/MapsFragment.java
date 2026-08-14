package com.example.mysignupapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MapsFragment extends Fragment implements OnMapReadyCallback {

    private static final int    RC_LOCATION   = 1001;
    private static final int    RC_PICK_IMAGE = 1002;
    private static final int    RC_CAMERA     = 1003;
    private static final String CHANNEL_ID    = "ambulance_channel";
    private static final int    NOTIF_ID      = 42;
    private static final float  AUTO_ARRIVE_M = 100f;

    private static final int STATUS_IDLE      = 0;
    private static final int STATUS_SEARCHING = 1;
    private static final int STATUS_ASSIGNED  = 2;
    private static final int STATUS_ENROUTE   = 3;
    private static final int STATUS_ARRIVED   = 4;

    // ── State ─────────────────────────────────────────────────────────────────
    private String  phone;
    private String  userName            = "";
    private int     currentStatus       = STATUS_IDLE;
    private String  selectedAmbType     = "basic";
    private String  selectedSeverity    = "High";
    private String  assignedAdminPhone  = "";   // admin who received the request
    private boolean autoArrivedFired    = false;
    private boolean hospitalRedirectDone = false; // prevents re-triggering hospital route on every snapshot
    private boolean driverAssignedPopupShown = false; // show assignment dialog only once per trip

    // ── Insurance ─────────────────────────────────────────────────────────────
    private boolean hasInsurance     = false;
    private String  insuranceCompany = "";
    private String  insuranceNumber  = "";

    // ── Map & location ────────────────────────────────────────────────────────
    private GoogleMap                   googleMap;
    private FusedLocationProviderClient fusedClient;
    private LocationCallback            locationCallback;
    private LatLng                      userLatLng;
    private Marker                      userMarker;
    private Marker                      driverMarker;
    private LatLng                      lastDriverLatLng;
    private Polyline                    routePolyline;
    private final HashMap<String, Marker> orgMarkers = new HashMap<>();

    // ── GeoFire ───────────────────────────────────────────────────────────────
    private GeoFire requestGeoFire;

    // ── Firebase ──────────────────────────────────────────────────────────────
    private ValueEventListener responseListener;
    private DatabaseReference  responseRef;
    private ValueEventListener driverLiveListener;
    private DatabaseReference  driverLiveRef;

    // ── Organization picker ───────────────────────────────────────────────────
    /** One org per admin in Firebase */
    private static class OrgItem {
        String adminUid, adminPhone, orgName, photoUrl;
        int    ambulanceCount;
    }
    private final List<OrgItem> orgList = new ArrayList<>();
    private AlertDialog orgPickerDialog;

    // ── Photo upload ──────────────────────────────────────────────────────────
    private String         uploadedPhotoUrl = null;
    private MaterialButton btnAttachPhoto;
    private ImageView      ivPhotoPreview;
    private TextView       tvPhotoLabel;

    // ── Views ─────────────────────────────────────────────────────────────────
    private MaterialButton btnFindDriver;
    private LinearLayout   statusBarPanel;
    private TextView       tvStatusLabel;
    private View           dotSearching, dotAssigned, dotEnroute, dotArrived;
    private View           line1, line2, line3;
    private LinearLayout   etaPanel;
    private TextView       tvEta, tvDistance;
    private LinearLayout   notificationPanel;
    private TextView       tvNotificationMsg;
    private MaterialButton btnOkay;
    private LinearLayout   driverInfoPanel;
    private TextView       tvDriverName, tvDriverHospital, tvDriverPhone,
            tvAmbTypeBadge, tvSeverityDisplay, tvAmbPlate, tvAmbCost;
    private ImageView      ivDriverPhoto;
    private TextView       tvDriverInsurance;
    private MaterialButton btnCancelRequest;
    private View           requestModal;
    private LinearLayout   btnTypeBasic, btnTypeAdvanced;
    private MaterialButton sevLow, sevMedium, sevHigh, sevCritical;
    private EditText       etDescription;
    private MaterialButton btnConfirmRequest;
    private RadioGroup     rgInsurance;
    private RadioButton    rbInsuranceNo;
    private LinearLayout   insuranceDetailsPanel;
    private EditText       etInsuranceCompany, etInsuranceNumber;

    // ── Prefs ─────────────────────────────────────────────────────────────────
    private static final String PREFS         = "ambulance_user_state";
    private static final String KEY_STATUS    = "status";
    private static final String KEY_ADMIN_PH  = "admin_phone";
    private static final String KEY_DRIVER_PH = "driver_phone";
    private static final String KEY_AMB_TYPE  = "amb_type";
    private static final String KEY_SEVERITY  = "severity";
    private int    pendingRestoreStatus = STATUS_IDLE;
    private String pendingRestoreDriver = "",  driverPhone;

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) phone = getArguments().getString("phone");
        fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        createNotificationChannel();
        restoreTripState();
        // Load user name
        com.google.firebase.auth.FirebaseUser fbUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser != null) {
            FirebaseDatabase.getInstance().getReference("users")
                    .child(fbUser.getUid()).child("name")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot ds) {
                            String n = ds.getValue(String.class);
                            if (n != null && !n.isEmpty()) userName = n;
                        }
                        @Override public void onCancelled(@NonNull DatabaseError e) {}
                    });
        }
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_maps, container, false);
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
        stopDriverLiveTracking();
        RouteHelper.clearCache();
        if (requestGeoFire != null && phone != null)
            requestGeoFire.removeLocation(phone);
        if (responseRef != null && responseListener != null)
            responseRef.removeEventListener(responseListener);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BIND + CLICK
    // ══════════════════════════════════════════════════════════════════════════

    private void bindViews(View v) {
        btnFindDriver         = v.findViewById(R.id.btn_find_driver);
        statusBarPanel        = v.findViewById(R.id.status_bar_panel);
        tvStatusLabel         = v.findViewById(R.id.tv_status_label);
        dotSearching          = v.findViewById(R.id.dot_searching);
        dotAssigned           = v.findViewById(R.id.dot_assigned);
        dotEnroute            = v.findViewById(R.id.dot_enroute);
        dotArrived            = v.findViewById(R.id.dot_arrived);
        line1                 = v.findViewById(R.id.line_1);
        line2                 = v.findViewById(R.id.line_2);
        line3                 = v.findViewById(R.id.line_3);
        etaPanel              = v.findViewById(R.id.eta_panel);
        tvEta                 = v.findViewById(R.id.tv_eta);
        tvDistance            = v.findViewById(R.id.tv_distance);
        notificationPanel     = v.findViewById(R.id.notification_panel);
        tvNotificationMsg     = v.findViewById(R.id.tv_notification_msg);
        btnOkay               = v.findViewById(R.id.btn_okay);
        driverInfoPanel       = v.findViewById(R.id.driver_info_panel);
        tvDriverName          = v.findViewById(R.id.tv_driver_name);
        tvDriverHospital      = v.findViewById(R.id.tv_driver_hospital);
        tvDriverPhone         = v.findViewById(R.id.tv_driver_phone);
        tvAmbTypeBadge        = v.findViewById(R.id.tv_amb_type_badge);
        tvSeverityDisplay     = v.findViewById(R.id.tv_severity_display);
        tvAmbPlate            = v.findViewById(R.id.tv_amb_plate);
        tvAmbCost             = v.findViewById(R.id.tv_amb_cost);
        ivDriverPhoto         = v.findViewById(R.id.iv_driver_photo);
        tvDriverInsurance     = v.findViewById(R.id.tv_driver_insurance);
        btnCancelRequest      = v.findViewById(R.id.btn_cancel_request);
        requestModal          = v.findViewById(R.id.request_modal);
        btnTypeBasic          = v.findViewById(R.id.btn_type_basic);
        btnTypeAdvanced       = v.findViewById(R.id.btn_type_advanced);
        sevLow                = v.findViewById(R.id.sev_low);
        sevMedium             = v.findViewById(R.id.sev_medium);
        sevHigh               = v.findViewById(R.id.sev_high);
        sevCritical           = v.findViewById(R.id.sev_critical);
        etDescription         = v.findViewById(R.id.et_description);
        btnConfirmRequest     = v.findViewById(R.id.btn_confirm_request);
        btnAttachPhoto        = v.findViewById(R.id.btn_attach_photo);
        ivPhotoPreview        = v.findViewById(R.id.iv_photo_preview);
        tvPhotoLabel          = v.findViewById(R.id.tv_photo_label);
        rgInsurance           = v.findViewById(R.id.rg_insurance);
        rbInsuranceNo         = v.findViewById(R.id.rb_insurance_no);
        insuranceDetailsPanel = v.findViewById(R.id.insurance_details_panel);
        etInsuranceCompany    = v.findViewById(R.id.et_insurance_company);
        etInsuranceNumber     = v.findViewById(R.id.et_insurance_number);
    }

    private void setupClickListeners() {
        btnFindDriver.setOnClickListener(v -> {
            if (userLatLng == null) {
                Toast.makeText(getActivity(), "Getting your location…", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadedPhotoUrl = null;
            hasInsurance = false; insuranceCompany = ""; insuranceNumber = "";
            if (ivPhotoPreview != null) ivPhotoPreview.setVisibility(View.GONE);
            if (tvPhotoLabel   != null) tvPhotoLabel.setText("Add photo (optional)");
            if (insuranceDetailsPanel != null) insuranceDetailsPanel.setVisibility(View.GONE);
            if (rbInsuranceNo != null) rbInsuranceNo.setChecked(true);
            requestModal.setVisibility(View.VISIBLE);
            btnFindDriver.setVisibility(View.GONE);
        });

        if (btnAttachPhoto != null)
            btnAttachPhoto.setOnClickListener(v -> showPhotoSourceDialog());

        if (rgInsurance != null)
            rgInsurance.setOnCheckedChangeListener((g, id) -> {
                hasInsurance = (id == R.id.rb_insurance_yes);
                if (insuranceDetailsPanel != null)
                    insuranceDetailsPanel.setVisibility(hasInsurance ? View.VISIBLE : View.GONE);
            });

        btnTypeBasic.setOnClickListener(v    -> selectAmbType("basic"));
        btnTypeAdvanced.setOnClickListener(v -> selectAmbType("advanced"));
        sevLow.setOnClickListener(v      -> selectSeverity("Low",      sevLow));
        sevMedium.setOnClickListener(v   -> selectSeverity("Medium",   sevMedium));
        sevHigh.setOnClickListener(v     -> selectSeverity("High",     sevHigh));
        sevCritical.setOnClickListener(v -> selectSeverity("Critical", sevCritical));

        btnConfirmRequest.setOnClickListener(v -> {
            if (hasInsurance) {
                if (etInsuranceCompany != null)
                    insuranceCompany = etInsuranceCompany.getText().toString().trim();
                if (etInsuranceNumber != null)
                    insuranceNumber  = etInsuranceNumber.getText().toString().trim();
            }
            requestModal.setVisibility(View.GONE);
            loadOrganizations(); // NEW: load orgs for user to pick
        });

        btnOkay.setOnClickListener(v -> notificationPanel.setVisibility(View.GONE));
        btnCancelRequest.setOnClickListener(v -> confirmCancel());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAP READY
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void onMapReady(@NonNull GoogleMap gMap) {
        googleMap = gMap;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        requestLocationPermission();
        resumeActiveTrip();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOCATION
    // ══════════════════════════════════════════════════════════════════════════

    private void requestLocationPermission() {
        boolean hasFine = ActivityCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasFine) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, RC_LOCATION);
            return;
        }
        checkGpsAndStart();
    }

    private void checkGpsAndStart() {
        com.google.android.gms.location.LocationSettingsRequest req =
                new com.google.android.gms.location.LocationSettingsRequest.Builder()
                        .addLocationRequest(new LocationRequest.Builder(
                                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                                4000L).build())
                        .setAlwaysShow(true).build();
        com.google.android.gms.location.LocationServices.getSettingsClient(requireActivity())
                .checkLocationSettings(req)
                .addOnSuccessListener(r -> startLocationUpdates())
                .addOnFailureListener(e -> {
                    if (e instanceof com.google.android.gms.common.api.ResolvableApiException) {
                        try { ((com.google.android.gms.common.api.ResolvableApiException) e)
                                .startResolutionForResult(requireActivity(), 9003);
                        } catch (Exception ex) { startLocationUpdates(); }
                    } else startLocationUpdates();
                });
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        LocationRequest req = new LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 4000L)
                .setMinUpdateIntervalMillis(2000L).setWaitForAccurateLocation(false).build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc == null || googleMap == null) return;
                userLatLng = new LatLng(loc.getLatitude(), loc.getLongitude());
                if (userMarker == null) {
                    userMarker = googleMap.addMarker(new MarkerOptions()
                            .position(userLatLng).title("You")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));
                } else userMarker.setPosition(userLatLng);
            }
        };
        fusedClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == RC_LOCATION && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED)
            checkGpsAndStart();
        else if (code == RC_LOCATION)
            Toast.makeText(getActivity(), "Location permission required", Toast.LENGTH_LONG).show();
    }

    // ── Single onActivityResult handles GPS dialog + photo picker ─────────────
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 9003) { startLocationUpdates(); return; }
        if (resultCode != Activity.RESULT_OK) return;
        Bitmap bmp = null;
        try {
            if (requestCode == RC_PICK_IMAGE && data != null)
                bmp = android.provider.MediaStore.Images.Media.getBitmap(
                        requireActivity().getContentResolver(), data.getData());
            else if (requestCode == RC_CAMERA && data != null)
                bmp = (Bitmap) data.getExtras().get("data");
        } catch (Exception e) {
            Toast.makeText(getActivity(), "Could not load image", Toast.LENGTH_SHORT).show();
            return;
        }
        if (bmp == null) return;
        final Bitmap finalBmp = bmp;
        if (ivPhotoPreview != null) { ivPhotoPreview.setImageBitmap(finalBmp); ivPhotoPreview.setVisibility(View.VISIBLE); }
        if (tvPhotoLabel   != null) { tvPhotoLabel.setText("Uploading…"); tvPhotoLabel.setTextColor(Color.parseColor("#888888")); }
        ImageUploader.uploadIncidentFromBitmap(finalBmp, phone,
                url -> { if (!isAdded()) return; uploadedPhotoUrl = url;
                    if (tvPhotoLabel != null) { tvPhotoLabel.setText("Photo attached ✓"); tvPhotoLabel.setTextColor(Color.parseColor("#27AE60")); }},
                err -> { if (!isAdded()) return; uploadedPhotoUrl = null;
                    if (tvPhotoLabel != null) { tvPhotoLabel.setText("Upload failed"); tvPhotoLabel.setTextColor(Color.parseColor("#E74C3C")); }});
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHOTO
    // ══════════════════════════════════════════════════════════════════════════

    private void showPhotoSourceDialog() {
        String[] opts = uploadedPhotoUrl != null
                ? new String[]{"Take a photo","Choose from gallery","Remove photo"}
                : new String[]{"Take a photo","Choose from gallery"};
        new AlertDialog.Builder(requireContext()).setTitle("Attach photo")
                .setItems(opts, (d, w) -> {
                    if (w == 0) startActivityForResult(
                            new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), RC_CAMERA);
                    else if (w == 1) checkStoragePermAndPick();
                    else { uploadedPhotoUrl = null;
                        if (ivPhotoPreview != null) ivPhotoPreview.setVisibility(View.GONE);
                        if (tvPhotoLabel   != null) tvPhotoLabel.setText("Add photo (optional)"); }
                }).show();
    }

    private void checkStoragePermAndPick() {
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ActivityCompat.checkSelfPermission(requireActivity(), perm) == PackageManager.PERMISSION_GRANTED)
            startActivityForResult(new Intent(Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI), RC_PICK_IMAGE);
        else
            ActivityCompat.requestPermissions(requireActivity(), new String[]{perm}, 1005);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 1 — Load organizations (admins) for user to choose from
    // ══════════════════════════════════════════════════════════════════════════

    private void loadOrganizations() {
        setStatus(STATUS_SEARCHING);
        tvStatusLabel.setText("Loading available organizations…");
        orgList.clear();
        for (Marker m : orgMarkers.values()) m.remove();
        orgMarkers.clear();

        FirebaseDatabase.getInstance().getReference("admin")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) { showNoOrgsFound(); return; }
                        final int[] remaining = {(int) snapshot.getChildrenCount()};
                        if (remaining[0] == 0) { showNoOrgsFound(); return; }

                        for (DataSnapshot adminSnap : snapshot.getChildren()) {
                            String uid     = adminSnap.getKey();
                            String orgName = adminSnap.child("org_name").getValue(String.class);
                            String adminPh = adminSnap.child("phone").getValue(String.class);
                            String photo   = adminSnap.child("photoUrl").getValue(String.class);
                            if (orgName == null || orgName.isEmpty()) orgName = "Unknown Org";

                            OrgItem org    = new OrgItem();
                            org.adminUid   = uid;
                            org.adminPhone = adminPh != null ? adminPh : uid;
                            org.orgName    = orgName;
                            org.photoUrl   = photo;

                            // Count available ambulances under this org
                            final OrgItem finalOrg = org;
                            FirebaseDatabase.getInstance().getReference("ambulances")
                                    .orderByChild("hospitalId").equalTo(adminPh)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot ads) {
                                            int count = 0;
                                            for (DataSnapshot a : ads.getChildren()) {
                                                String s = a.child("status").getValue(String.class);
                                                if ("available".equals(s)) count++;
                                            }
                                            finalOrg.ambulanceCount = count;
                                            orgList.add(finalOrg);
                                            remaining[0]--;
                                            if (remaining[0] <= 0) showOrgPickerDialog();
                                        }
                                        @Override public void onCancelled(@NonNull DatabaseError e) {
                                            orgList.add(finalOrg);
                                            remaining[0]--;
                                            if (remaining[0] <= 0) showOrgPickerDialog();
                                        }
                                    });
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) { showNoOrgsFound(); }
                });
    }

    private void showNoOrgsFound() {
        if (!isAdded()) return;
        tvStatusLabel.setText("No organizations found");
        Toast.makeText(getActivity(), "No ambulance organizations available right now",
                Toast.LENGTH_LONG).show();
        resetState();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 2 — Show org picker dialog
    // ══════════════════════════════════════════════════════════════════════════

    private void showOrgPickerDialog() {
        if (!isAdded() || getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (orgList.isEmpty()) { showNoOrgsFound(); return; }
            // Sort by name
            Collections.sort(orgList, (a, b) -> a.orgName.compareTo(b.orgName));

            View dialogView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_ambulance_list, null);
            RecyclerView rv  = dialogView.findViewById(R.id.rv_ambulance_list);
            TextView   tvCnt = dialogView.findViewById(R.id.tv_ambulance_count);
            tvCnt.setText(orgList.size() + " organization(s) available");
            rv.setLayoutManager(new LinearLayoutManager(getContext()));
            rv.setAdapter(new OrgListAdapter(orgList, this::sendRequestToAdmin));

            orgPickerDialog = new AlertDialog.Builder(requireContext())
                    .setTitle("Choose Ambulance Organization")
                    .setView(dialogView)
                    .setNegativeButton("Cancel", (d, w) -> resetState())
                    .create();
            orgPickerDialog.show();
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ORG LIST ADAPTER
    // ══════════════════════════════════════════════════════════════════════════

    static class OrgListAdapter extends RecyclerView.Adapter<OrgListAdapter.VH> {
        interface OnPick { void pick(OrgItem org); }
        private final List<OrgItem> items;
        private final OnPick        onPick;
        OrgListAdapter(List<OrgItem> items, OnPick onPick) { this.items = items; this.onPick = onPick; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.org_list_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            OrgItem it = items.get(pos);
            h.tvOrgName.setText(it.orgName);
            h.tvAmbCount.setText(it.ambulanceCount + " ambulance(s) available");
            h.tvAmbCount.setTextColor(it.ambulanceCount > 0
                    ? Color.parseColor("#27AE60") : Color.parseColor("#E74C3C"));
            if (it.photoUrl != null && !it.photoUrl.isEmpty())
                Glide.with(h.ivOrgPhoto.getContext()).load(it.photoUrl)
                        .placeholder(R.drawable.ic_person_add).circleCrop().into(h.ivOrgPhoto);
            h.btnSelect.setEnabled(it.ambulanceCount > 0);
            h.btnSelect.setAlpha(it.ambulanceCount > 0 ? 1f : 0.4f);
            h.btnSelect.setOnClickListener(v -> onPick.pick(it));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView      ivOrgPhoto;
            TextView       tvOrgName, tvAmbCount;
            MaterialButton btnSelect;
            VH(@NonNull View v) {
                super(v);
                ivOrgPhoto  = v.findViewById(R.id.iv_org_photo);
                tvOrgName   = v.findViewById(R.id.tv_org_name);
                tvAmbCount  = v.findViewById(R.id.tv_org_amb_count);
                btnSelect   = v.findViewById(R.id.btn_select_org);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 3 — Send request to ADMIN (no driver assigned yet)
    // ══════════════════════════════════════════════════════════════════════════

    private void sendRequestToAdmin(OrgItem org) {
        if (userLatLng == null) {
            Toast.makeText(getActivity(), "Location not ready", Toast.LENGTH_SHORT).show(); return;
        }
        if (orgPickerDialog != null && orgPickerDialog.isShowing()) {
            orgPickerDialog.dismiss(); orgPickerDialog = null;
        }

        String desc = etDescription != null ? etDescription.getText().toString().trim() : "";
        assignedAdminPhone = org.adminPhone;
        hospitalRedirectDone = false; // reset for new trip
        driverAssignedPopupShown = false; // reset assignment popup for new trip
        RouteHelper.clearCache("user");

        DatabaseReference reqRef = FirebaseDatabase.getInstance()
                .getReference("requests").child(phone);

        // Write request — NO driver assigned yet, admin will assign
        reqRef.child("userId").setValue(phone);
        reqRef.child("userName").setValue(userName.isEmpty() ? phone : userName);
        reqRef.child("userPhone").setValue(phone);
        reqRef.child("ambulanceType").setValue(selectedAmbType);
        reqRef.child("severity").setValue(selectedSeverity);
        reqRef.child("description").setValue(desc.isEmpty() ? "No description" : desc);
        reqRef.child("status").setValue("searching");
        reqRef.child("assignedAdmin").setValue(org.adminPhone);     // admin receives it
        reqRef.child("adminUid").setValue(org.adminUid);
        reqRef.child("orgName").setValue(org.orgName);
        reqRef.child("assignedDriver").setValue("");                 // empty until admin assigns
        reqRef.child("timestamp").setValue(System.currentTimeMillis());
        // Insurance
        reqRef.child("hasInsurance").setValue(hasInsurance);
        if (hasInsurance) {
            reqRef.child("insuranceCompany").setValue(insuranceCompany);
            reqRef.child("insuranceNumber").setValue(insuranceNumber);
        }
        if (uploadedPhotoUrl != null && !uploadedPhotoUrl.isEmpty())
            reqRef.child("photoUrl").setValue(uploadedPhotoUrl);

        // Publish location for GeoFire
        double lat = userLatLng.latitude, lng = userLatLng.longitude;
        if (!Double.isNaN(lat) && !Double.isNaN(lng) && lat != 0 && lng != 0) {
            requestGeoFire = new GeoFire(
                    FirebaseDatabase.getInstance().getReference("Requests"));
            requestGeoFire.setLocation(phone, new GeoLocation(lat, lng));
        }

        setStatus(STATUS_ASSIGNED);
        tvStatusLabel.setText("Request sent to " + org.orgName + " — waiting for driver…");
        driverInfoPanel.setVisibility(View.VISIBLE);
        tvDriverName.setText(org.orgName);
        tvDriverHospital.setText("Awaiting assignment");
        tvDriverPhone.setText("Admin: " + org.adminPhone);
        tvAmbTypeBadge.setText("advanced".equalsIgnoreCase(selectedAmbType) ? "ALS" : "BLS");
        tvSeverityDisplay.setText(selectedSeverity);
        tvAmbPlate.setText("—");
        tvAmbCost.setText("—");
        btnCancelRequest.setVisibility(View.VISIBLE);

        // Listen for admin to assign a driver
        listenForAdminAssignment();
        saveTripState();
        Toast.makeText(getActivity(), "Request sent to " + org.orgName + "!", Toast.LENGTH_SHORT).show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LISTEN — admin assigns driver → update UI
    // ══════════════════════════════════════════════════════════════════════════

    private void listenForAdminAssignment() {
        responseRef      = FirebaseDatabase.getInstance().getReference("requests").child(phone);
        responseListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) return;
                String status = snap.child("status").getValue(String.class);
                if (status == null) return;

                switch (status) {
                    case "admin_assigned":
                        // Admin chose a driver — show driver info and start tracking
                        String drName  = snap.child("driverName").getValue(String.class);
                        String drPh    = snap.child("driverPhone").getValue(String.class);
                        String hosp    = snap.child("driverHospital").getValue(String.class);
                        String plate   = snap.child("ambulancePlate").getValue(String.class);
                        String ambType = snap.child("ambulanceType").getValue(String.class);
                        Object costV   = snap.child("costPerTrip").getValue();
                        String costStr = costV != null
                                ? (Double.parseDouble(costV.toString()) <= 0 ? "Free"
                                   : String.format(Locale.getDefault(), "TZS %,.0f",
                                Double.parseDouble(costV.toString()))) : "—";
                        tvDriverName.setText(drName != null ? drName : "—");
                        tvDriverHospital.setText(hosp  != null ? hosp  : "—");
                        tvDriverPhone.setText(drPh    != null ? drPh   : "—");
                        tvAmbTypeBadge.setText("advanced".equalsIgnoreCase(ambType) ? "ALS" : "BLS");
                        tvAmbPlate.setText(plate != null ? plate : "—");
                        tvAmbCost.setText(costStr);
                        // Load driver profile photo
                        if (drPh != null && !drPh.isEmpty() && ivDriverPhoto != null) {
                            FirebaseDatabase.getInstance().getReference("driver")
                                    .child(drPh).child("photoUrl")
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot ds) {
                                            String photoUrl = ds.getValue(String.class);
                                            if (photoUrl != null && !photoUrl.isEmpty()
                                                    && isAdded()) {
                                                Glide.with(requireActivity())
                                                        .load(photoUrl)
                                                        .placeholder(R.drawable.ic_person_add)
                                                        .circleCrop()
                                                        .into(ivDriverPhoto);
                                            }
                                        }
                                        @Override public void onCancelled(@NonNull DatabaseError e) {}
                                    });
                        }
                        // Tap phone number to call driver
                        if (drPh != null && !drPh.isEmpty()) {
                            final String finalDrPh = drPh;
                            tvDriverPhone.setCompoundDrawablesWithIntrinsicBounds(
                                    0, 0, android.R.drawable.sym_action_call, 0);
                            tvDriverPhone.setOnClickListener(v -> {
                                android.content.Intent dial = new android.content.Intent(
                                        android.content.Intent.ACTION_DIAL,
                                        android.net.Uri.parse("tel:" + finalDrPh));
                                startActivity(dial);
                            });
                        }
                        showNotification("Driver assigned: " + drName + " is coming!");
                        if (!driverAssignedPopupShown) {
                            driverAssignedPopupShown = true;
                            showDriverAssignedPopup(drName, drPh, hosp, plate, ambType, costStr);
                        }
                        if (drPh != null) startDriverLiveTracking(drPh);
                        break;

                    case "en_route":
                        setStatus(STATUS_ENROUTE);
                        etaPanel.setVisibility(View.VISIBLE);
                        btnCancelRequest.setVisibility(View.VISIBLE);
                        showNotification("Ambulance is on the way! 🚑");
                        sendPushNotification("Ambulance En Route", "Driver is heading to you.");
                        autoArrivedFired = false;
                        saveTripState();
                        break;

                    case "rejected":
                        showNotification("Driver declined — awaiting reassignment.");
                        break;

                    case "arrived":
                        if (currentStatus != STATUS_ARRIVED) {
                            setStatus(STATUS_ARRIVED);
                            btnCancelRequest.setVisibility(View.GONE);
                            etaPanel.setVisibility(View.GONE);
                            showNotification("Ambulance has arrived! 🚑");
                            sendPushNotification("Arrived", "Help is here!");
                            stopDriverLiveTracking(); clearTripState();
                        }
                        break;

                    case "cancelled":
                        showNotification("Request was cancelled.");
                        clearTripState(); resetState(); break;

                    case "going_to_hospital":
                        // Don't stop tracking — keep showing driver moving toward hospital
                        // Change destination from user location → hospital location
                        tvStatusLabel.setText("Being transported to hospital 🏥");
                        btnCancelRequest.setVisibility(View.GONE);
                        etaPanel.setVisibility(View.VISIBLE);
                        // Only trigger the redirect ONCE — Firebase fires this listener
                        // on every field change, not just status change
                        if (!hospitalRedirectDone) {
                            showNotification("On the way to hospital — hang on!");
                            loadHospitalAndRedirectRoute(snap);
                        }
                        break;

                    case "trip_ended":
                        showNotification("Trip ended. Feel better soon! 🙏");
                        clearTripState(); resetState(); break;
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        responseRef.addValueEventListener(responseListener);
    }


    private void showDriverAssignedPopup(String drName, String drPh, String hosp,
                                         String plate, String ambType, String costStr) {
        if (!isAdded() || getActivity() == null) return;

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_driver_assigned, null);

        ImageView ivPhoto = dialogView.findViewById(R.id.iv_popup_driver_photo);
        TextView tvName   = dialogView.findViewById(R.id.tv_popup_driver_name);
        TextView tvPhone  = dialogView.findViewById(R.id.tv_popup_driver_phone);
        TextView tvHosp   = dialogView.findViewById(R.id.tv_popup_driver_hospital);
        TextView tvPlate  = dialogView.findViewById(R.id.tv_popup_ambulance_plate);
        TextView tvType   = dialogView.findViewById(R.id.tv_popup_ambulance_type);
        TextView tvCost   = dialogView.findViewById(R.id.tv_popup_ambulance_cost);
        MaterialButton btnCall = dialogView.findViewById(R.id.btn_popup_call_driver);

        String safeName  = drName != null && !drName.isEmpty() ? drName : "Driver";
        String safePhone = drPh != null && !drPh.isEmpty() ? drPh : "—";
        String safeHosp  = hosp != null && !hosp.isEmpty() ? hosp : "—";
        String safePlate = plate != null && !plate.isEmpty() ? plate : "—";
        String safeType  = "advanced".equalsIgnoreCase(ambType) ? "ALS" : "BLS";
        String safeCost  = costStr != null && !costStr.isEmpty() ? costStr : "—";

        tvName.setText(safeName);
        tvPhone.setText("Phone: " + safePhone);
        tvHosp.setText("Hospital: " + safeHosp);
        tvPlate.setText("Plate: " + safePlate);
        tvType.setText("Ambulance: " + safeType);
        tvCost.setText("Cost: " + safeCost);

        if (drPh != null && !drPh.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("driver")
                    .child(drPh).child("photoUrl")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot ds) {
                            String photoUrl = ds.getValue(String.class);
                            if (photoUrl != null && !photoUrl.isEmpty() && isAdded()) {
                                Glide.with(requireActivity())
                                        .load(photoUrl)
                                        .placeholder(R.drawable.ic_person_add)
                                        .circleCrop()
                                        .into(ivPhoto);
                            }
                        }
                        @Override public void onCancelled(@NonNull DatabaseError e) {}
                    });
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        btnCall.setEnabled(drPh != null && !drPh.isEmpty());
        btnCall.setOnClickListener(v -> {
            if (drPh == null || drPh.isEmpty()) return;
            Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + drPh));
            startActivity(dial);
        });

        dialogView.findViewById(R.id.btn_popup_ok).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LIVE DRIVER TRACKING (same as before)
    // ══════════════════════════════════════════════════════════════════════════

    private void startDriverLiveTracking(String driverPhoneKey) {
        if (driverPhoneKey == null || driverPhoneKey.isEmpty()) return;

        // Never attach a second GPS listener on top of an old one.
        // Firebase status snapshots can fire repeatedly; duplicate listeners fight over
        // driverMarker/routePolyline and cause flicker or missing routes.
        if (driverLiveRef != null && driverLiveListener != null) {
            driverLiveRef.removeEventListener(driverLiveListener);
            driverLiveListener = null;
        }

        driverLiveRef = FirebaseDatabase.getInstance()
                .getReference("driversavailable").child(driverPhoneKey).child("l");
        driverLiveListener = new ValueEventListener() {
            boolean firstUpdate = true;
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists() || googleMap == null) return;
                List<Object> c = (List<Object>) snap.getValue();
                if (c == null || c.size() < 2) return;
                double lat = Double.parseDouble(c.get(0).toString());
                double lng = Double.parseDouble(c.get(1).toString());
                LatLng driverPos = new LatLng(lat, lng);
                if (userLatLng == null) return;

                if (lastDriverLatLng != null) {
                    float[] n = new float[1];
                    Location.distanceBetween(lastDriverLatLng.latitude, lastDriverLatLng.longitude,
                            driverPos.latitude, driverPos.longitude, n);
                    if (n[0] < 2f) return;
                }
                float bearing = lastDriverLatLng != null ? bearingBetween(lastDriverLatLng, driverPos) : 0f;
                lastDriverLatLng = driverPos;

                if (driverMarker == null) {
                    try {
                        driverMarker = googleMap.addMarker(new MarkerOptions()
                                .position(driverPos).flat(true).rotation(bearing).anchor(0.5f, 0.5f)
                                .icon(RouteNavigator.vectorToBitmapDescriptor(
                                        requireContext(), R.drawable.ic_ambulance_marker)));
                    } catch (Exception e) {
                        driverMarker = googleMap.addMarker(new MarkerOptions()
                                .position(driverPos).flat(true).rotation(bearing));
                    }
                } else { driverMarker.setPosition(driverPos); driverMarker.setRotation(bearing); }

                if (firstUpdate) {
                    firstUpdate = false;
                    if (routePolyline != null) routePolyline.remove();
                    routePolyline = googleMap.addPolyline(
                            new com.google.android.gms.maps.model.PolylineOptions()
                                    .add(driverPos, userLatLng).width(12f)
                                    .color(Color.parseColor("#3B608C")).geodesic(false));
                    try {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                                new LatLngBounds.Builder().include(driverPos).include(userLatLng).build(), 120));
                    } catch (Exception e) {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(driverPos, 14));
                    }
                    RouteHelper.clearCache("user");
                    RouteHelper.updateRoute("user", googleMap, driverPos, userLatLng, null,
                            poly -> { if (routePolyline != null) routePolyline.remove(); routePolyline = poly; },
                            (etaSec, distM) -> { if (!isAdded()) return;
                                tvEta.setText(Math.max(1, etaSec/60) + " min");
                                tvDistance.setText(String.format(Locale.getDefault(), "%.1f km", distM/1000f));
                                if (etaPanel.getVisibility() != View.VISIBLE) etaPanel.setVisibility(View.VISIBLE); });
                } else {
                    RouteHelper.updateRoute("user", googleMap, driverPos, userLatLng, routePolyline,
                            poly -> { if (routePolyline != null) routePolyline.remove(); routePolyline = poly; },
                            (etaSec, distM) -> { if (!isAdded()) return;
                                tvEta.setText(Math.max(1, etaSec/60) + " min");
                                tvDistance.setText(String.format(Locale.getDefault(), "%.1f km", distM/1000f));
                                if (etaPanel.getVisibility() != View.VISIBLE) etaPanel.setVisibility(View.VISIBLE); });
                }

                float[] res = new float[1];
                Location.distanceBetween(lat, lng, userLatLng.latitude, userLatLng.longitude, res);
                if (res[0] <= AUTO_ARRIVE_M && !autoArrivedFired && currentStatus == STATUS_ENROUTE) {
                    autoArrivedFired = true; triggerAutoArrived();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        driverLiveRef.addValueEventListener(driverLiveListener);
    }

    private void triggerAutoArrived() {
        FirebaseDatabase.getInstance().getReference("requests")
                .child(phone).child("status").setValue("arrived");
        setStatus(STATUS_ARRIVED);
        btnCancelRequest.setVisibility(View.GONE);
        etaPanel.setVisibility(View.GONE);
        showNotification("Ambulance has arrived! 🚑");
        sendPushNotification("Arrived", "Help is here!");
        stopDriverLiveTracking(); RouteHelper.clearCache("user");
        if (routePolyline != null) { routePolyline.remove(); routePolyline = null; }
    }

    private void stopDriverLiveTracking() {
        if (driverLiveRef != null && driverLiveListener != null)
            driverLiveRef.removeEventListener(driverLiveListener);
        RouteHelper.clearCache("user");
        if (driverMarker  != null) { driverMarker.remove();  driverMarker  = null; }
        if (routePolyline != null) { routePolyline.remove(); routePolyline = null; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HOSPITAL REDIRECT — when driver says "going to hospital",
    // fetch hospital GPS from admin node and draw driver→hospital route on user map
    // ══════════════════════════════════════════════════════════════════════════

    private void loadHospitalAndRedirectRoute(@NonNull DataSnapshot requestSnap) {
        // CRITICAL FIX:
        // Do NOT use request.adminUid / request.assignedAdmin as the hospital source.
        // AdminMap works because it follows: request -> assignedDriver -> driver/{phone}/adminNo -> admin hospital.
        // User side must follow the same source of truth.
        String resolvedDriverPhone = requestSnap.child("driverPhone").getValue(String.class);
        if (resolvedDriverPhone == null || resolvedDriverPhone.isEmpty()) {
            resolvedDriverPhone = requestSnap.child("assignedDriver").getValue(String.class);
        }

        if (resolvedDriverPhone == null || resolvedDriverPhone.isEmpty()) {
            hospitalRedirectDone = false;
            showNotification("Driver phone missing — cannot draw hospital route.");
            return;
        }

        final String finalDriverPhone = resolvedDriverPhone;

        FirebaseDatabase.getInstance().getReference("driver")
                .child(finalDriverPhone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot driverSnap) {
                        String adminNo = driverSnap.child("adminNo").getValue(String.class);

                        if (adminNo == null || adminNo.isEmpty()) {
                            hospitalRedirectDone = false;
                            showNotification("Driver adminNo missing — cannot find hospital.");
                            return;
                        }

                        loadHospitalByAdminNo(finalDriverPhone, adminNo);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                        hospitalRedirectDone = false;
                        showNotification("Failed to load driver admin: " + e.getMessage());
                    }
                });
    }

    private void loadHospitalByAdminNo(@NonNull String driverPhone, @NonNull String adminNo) {
        FirebaseDatabase.getInstance().getReference("admin")
                .child(adminNo)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot adminSnap) {
                        if (adminSnap.exists()
                                && adminSnap.child("hospitalLat").getValue() != null
                                && adminSnap.child("hospitalLng").getValue() != null) {
                            applyHospitalSnapshot(driverPhone, adminSnap);
                            return;
                        }

                        FirebaseDatabase.getInstance().getReference("admin")
                                .orderByChild("phone").equalTo(adminNo)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snap) {
                                        for (DataSnapshot child : snap.getChildren()) {
                                            if (child.child("hospitalLat").getValue() != null
                                                    && child.child("hospitalLng").getValue() != null) {
                                                applyHospitalSnapshot(driverPhone, child);
                                                return;
                                            }
                                        }

                                        hospitalRedirectDone = false;
                                        showNotification("Hospital GPS not found for driver's admin.");
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError e) {
                                        hospitalRedirectDone = false;
                                        showNotification("Hospital lookup failed: " + e.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                        hospitalRedirectDone = false;
                        showNotification("Hospital lookup failed: " + e.getMessage());
                    }
                });
    }

    private void applyHospitalSnapshot(@NonNull String driverPhone, @NonNull DataSnapshot adminSnap) {
        Object latV = adminSnap.child("hospitalLat").getValue();
        Object lngV = adminSnap.child("hospitalLng").getValue();
        String name = adminSnap.child("hospitalName").getValue(String.class);
        if (latV == null || lngV == null) {
            hospitalRedirectDone = false;
            showNotification("Hospital GPS is incomplete.");
            return;
        }
        LatLng hospital = new LatLng(
                Double.parseDouble(latV.toString()),
                Double.parseDouble(lngV.toString()));
        beginUserHospitalTracking(driverPhone, hospital,
                name != null && !name.isEmpty() ? name : "Hospital");
    }

    private void beginUserHospitalTracking(@NonNull String driverPhone,
                                           @NonNull LatLng hospitalPos,
                                           @NonNull String hospitalName) {
        if (!isAdded() || googleMap == null) return;

        // Mark done ONLY after we have resolved a real driver and real hospital coordinates.
        hospitalRedirectDone = true;

        setStatus(STATUS_ENROUTE);
        tvStatusLabel.setText("Being transported to " + hospitalName + " 🏥");
        btnCancelRequest.setVisibility(View.GONE);
        etaPanel.setVisibility(View.VISIBLE);

        if (userMarker != null) userMarker.setVisible(false);

        googleMap.addMarker(new MarkerOptions()
                .position(hospitalPos)
                .title(hospitalName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        if (driverLiveRef != null && driverLiveListener != null) {
            driverLiveRef.removeEventListener(driverLiveListener);
            driverLiveListener = null;
        }

        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
        }

        lastDriverLatLng = null;
        RouteHelper.clearCache("user");
        RouteHelper.clearCache("user_hospital");

        driverLiveRef = FirebaseDatabase.getInstance()
                .getReference("driversavailable").child(driverPhone).child("l");

        // Draw once immediately from the latest Firebase GPS value, exactly like admin does.
        driverLiveRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                LatLng pos = parseGeoFireLatLng(snap);
                if (pos != null) {
                    renderUserHospitalRoute(pos, hospitalPos, true);
                } else {
                    hospitalRedirectDone = false;
                    showNotification("Waiting for driver GPS to draw hospital route…");
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                hospitalRedirectDone = false;
            }
        });

        driverLiveListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                LatLng pos = parseGeoFireLatLng(snap);
                if (pos == null || googleMap == null) return;

                boolean first = lastDriverLatLng == null;
                if (!first) {
                    float[] moved = new float[1];
                    Location.distanceBetween(lastDriverLatLng.latitude, lastDriverLatLng.longitude,
                            pos.latitude, pos.longitude, moved);
                    if (moved[0] < 2f && routePolyline != null) return;
                }

                renderUserHospitalRoute(pos, hospitalPos, first);
                lastDriverLatLng = pos;
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        driverLiveRef.addValueEventListener(driverLiveListener);
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

    private void renderUserHospitalRoute(@NonNull LatLng driverPos,
                                         @NonNull LatLng hospitalPos,
                                         boolean forceFresh) {
        if (!isAdded() || googleMap == null) return;

        float bearing = lastDriverLatLng != null ? bearingBetween(lastDriverLatLng, driverPos) : 0f;
        if (driverMarker == null) {
            try {
                driverMarker = googleMap.addMarker(new MarkerOptions()
                        .position(driverPos).flat(true).rotation(bearing).anchor(0.5f, 0.5f)
                        .icon(RouteNavigator.vectorToBitmapDescriptor(
                                requireContext(), R.drawable.ic_ambulance_marker)));
            } catch (Exception e) {
                driverMarker = googleMap.addMarker(new MarkerOptions()
                        .position(driverPos).flat(true).rotation(bearing));
            }
        } else {
            driverMarker.setPosition(driverPos);
            driverMarker.setRotation(bearing);
        }

        if (routePolyline == null || forceFresh) {
            if (routePolyline != null) routePolyline.remove();
            routePolyline = googleMap.addPolyline(
                    new com.google.android.gms.maps.model.PolylineOptions()
                            .add(driverPos, hospitalPos)
                            .width(12f)
                            .color(android.graphics.Color.parseColor("#EA6D35"))
                            .geodesic(true));
            try {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                        new LatLngBounds.Builder().include(driverPos).include(hospitalPos).build(), 120));
            } catch (Exception e) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(hospitalPos, 13));
            }
            RouteHelper.drawRoute("user_hospital", googleMap, driverPos, hospitalPos,
                    null, poly -> {
                        if (!isAdded()) return;
                        if (routePolyline != null) routePolyline.remove();
                        routePolyline = poly;
                        routePolyline.setColor(android.graphics.Color.parseColor("#EA6D35"));
                        routePolyline.setWidth(12f);
                    });
        } else {
            RouteHelper.updateRoute("user_hospital", googleMap, driverPos, hospitalPos,
                    routePolyline,
                    poly -> {
                        if (!isAdded()) return;
                        if (routePolyline != null) routePolyline.remove();
                        routePolyline = poly;
                        routePolyline.setColor(android.graphics.Color.parseColor("#EA6D35"));
                        routePolyline.setWidth(12f);
                    },
                    (etaSec, distM) -> {
                        if (!isAdded()) return;
                        tvEta.setText(Math.max(1, etaSec / 60) + " min");
                        tvDistance.setText(String.format(Locale.getDefault(), "%.1f km", distM / 1000f));
                        if (etaPanel.getVisibility() != View.VISIBLE) etaPanel.setVisibility(View.VISIBLE);
                    });
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STATUS BAR
    // ══════════════════════════════════════════════════════════════════════════

    private void setStatus(int status) {
        currentStatus = status;
        statusBarPanel.setVisibility(status == STATUS_IDLE ? View.GONE : View.VISIBLE);
        setDotState(dotSearching, false, false); setDotState(dotAssigned,  false, false);
        setDotState(dotEnroute,   false, false); setDotState(dotArrived,   false, false);
        line1.setBackgroundColor(Color.parseColor("#DDDDDD"));
        line2.setBackgroundColor(Color.parseColor("#DDDDDD"));
        line3.setBackgroundColor(Color.parseColor("#DDDDDD"));
        switch (status) {
            case STATUS_SEARCHING:
                setDotState(dotSearching, true, false);
                tvStatusLabel.setText("Finding organizations…");
                btnFindDriver.setVisibility(View.GONE); break;
            case STATUS_ASSIGNED:
                setDotState(dotSearching, false, true); setDotState(dotAssigned, true, false);
                line1.setBackgroundColor(Color.parseColor("#27AE60"));
                tvStatusLabel.setText("Waiting for driver assignment…"); break;
            case STATUS_ENROUTE:
                setDotState(dotSearching, false, true); setDotState(dotAssigned, false, true);
                setDotState(dotEnroute, true, false);
                line1.setBackgroundColor(Color.parseColor("#27AE60"));
                line2.setBackgroundColor(Color.parseColor("#27AE60"));
                tvStatusLabel.setText("Ambulance en route to you");
                btnCancelRequest.setVisibility(View.VISIBLE); break;
            case STATUS_ARRIVED:
                setDotState(dotSearching, false, true); setDotState(dotAssigned, false, true);
                setDotState(dotEnroute, false, true);   setDotState(dotArrived, false, true);
                line1.setBackgroundColor(Color.parseColor("#27AE60"));
                line2.setBackgroundColor(Color.parseColor("#27AE60"));
                line3.setBackgroundColor(Color.parseColor("#27AE60"));
                tvStatusLabel.setText("Ambulance has arrived! 🚑");
                btnCancelRequest.setVisibility(View.GONE); break;
            case STATUS_IDLE:
                btnFindDriver.setVisibility(View.VISIBLE);
                driverInfoPanel.setVisibility(View.GONE); etaPanel.setVisibility(View.GONE); break;
        }
    }

    private void setDotState(View dot, boolean active, boolean done) {
        dot.setBackgroundResource(done ? R.drawable.dot_done
                : active ? R.drawable.dot_active : R.drawable.dot_inactive);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CANCEL
    // ══════════════════════════════════════════════════════════════════════════

    private void confirmCancel() {
        new AlertDialog.Builder(requireContext()).setTitle("Cancel request?")
                .setMessage("Are you sure?")
                .setPositiveButton("Yes", (d, w) -> cancelRequest())
                .setNegativeButton("No", null).show();
    }

    private void cancelRequest() {
        FirebaseDatabase.getInstance().getReference("requests")
                .child(phone).child("status").setValue("cancelled");
        if (requestGeoFire != null) requestGeoFire.removeLocation(phone);
        stopDriverLiveTracking();
        if (responseRef != null && responseListener != null)
            responseRef.removeEventListener(responseListener);
        for (Marker m : orgMarkers.values()) m.remove(); orgMarkers.clear();
        clearTripState(); resetState();
        Toast.makeText(getActivity(), "Request cancelled", Toast.LENGTH_SHORT).show();
    }

    private void resetState() {
        assignedAdminPhone = ""; lastDriverLatLng = null; autoArrivedFired = false;
        hospitalRedirectDone = false;
        driverAssignedPopupShown = false;
        setStatus(STATUS_IDLE);
        notificationPanel.setVisibility(View.GONE); driverInfoPanel.setVisibility(View.GONE);
        etaPanel.setVisibility(View.GONE); statusBarPanel.setVisibility(View.GONE);
        btnFindDriver.setVisibility(View.VISIBLE); RouteHelper.clearCache();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STATE PERSISTENCE
    // ══════════════════════════════════════════════════════════════════════════

    private void saveTripState() {
        if (getActivity() == null) return;
        getActivity().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
                .putInt(KEY_STATUS,       currentStatus)
                .putString(KEY_ADMIN_PH,  assignedAdminPhone)
                .putString(KEY_DRIVER_PH, "")
                .putString(KEY_AMB_TYPE,  selectedAmbType)
                .putString(KEY_SEVERITY,  selectedSeverity)
                .apply();
    }

    private void restoreTripState() {
        if (getActivity() == null) return;
        android.content.SharedPreferences p = getActivity()
                .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        int s = p.getInt(KEY_STATUS, STATUS_IDLE);
        String dr = p.getString(KEY_DRIVER_PH, "");
        if (s != STATUS_IDLE) {
            assignedAdminPhone = p.getString(KEY_ADMIN_PH, "");
            selectedAmbType    = p.getString(KEY_AMB_TYPE, "basic");
            selectedSeverity   = p.getString(KEY_SEVERITY, "High");
            autoArrivedFired   = false;
            pendingRestoreStatus = s;
            pendingRestoreDriver = dr != null ? dr : "";
        }
    }

    private void clearTripState() {
        if (getActivity() == null) return;
        getActivity().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .edit().clear().apply();
    }

    private void resumeActiveTrip() {
        if (pendingRestoreStatus == STATUS_IDLE) return;
        FirebaseDatabase.getInstance().getReference("requests").child(phone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        if (!snap.exists()) { clearTripState(); return; }
                        String status = snap.child("status").getValue(String.class);
                        if (status == null || "arrived".equals(status) || "cancelled".equals(status)
                                || "rejected".equals(status)
                                || "trip_ended".equals(status)) { clearTripState(); return; }

                        // Special case: going_to_hospital — restore hospital route
                        if ("going_to_hospital".equals(status)) {
                            driverAssignedPopupShown = true;
                            String drvPh = snap.child("driverPhone").getValue(String.class);
                            hospitalRedirectDone = true; // already redirecting, listener will skip duplicate triggers
                            listenForAdminAssignment();
                            loadHospitalAndRedirectRoute(snap);
                            pendingRestoreStatus = STATUS_IDLE; pendingRestoreDriver = "";
                            return;
                        }
                        driverAssignedPopupShown = true;
                        String driverPh = snap.child("driverPhone").getValue(String.class);
                        String drName   = snap.child("driverName").getValue(String.class);
                        String hosp     = snap.child("driverHospital").getValue(String.class);
                        String plate    = snap.child("ambulancePlate").getValue(String.class);
                        String ambType  = snap.child("ambulanceType").getValue(String.class);
                        Object costVal  = snap.child("costPerTrip").getValue();
                        String costStr  = costVal != null
                                ? (Double.parseDouble(costVal.toString()) <= 0 ? "Free"
                                   : String.format(Locale.getDefault(), "TZS %,.0f",
                                Double.parseDouble(costVal.toString()))) : "—";
                        setStatus("en_route".equals(status) ? STATUS_ENROUTE : STATUS_ASSIGNED);
                        driverInfoPanel.setVisibility(View.VISIBLE);
                        tvDriverName.setText(drName != null ? drName : "Awaiting assignment");
                        tvDriverHospital.setText(hosp  != null ? hosp  : "—");
                        tvDriverPhone.setText(driverPh != null ? driverPh : "—");
                        tvAmbTypeBadge.setText("advanced".equalsIgnoreCase(ambType) ? "ALS" : "BLS");
                        tvSeverityDisplay.setText(selectedSeverity);
                        tvAmbPlate.setText(plate != null ? plate : "—");
                        tvAmbCost.setText(costStr);
                        etaPanel.setVisibility(View.VISIBLE);
                        if (driverPh != null && !driverPh.isEmpty())
                            startDriverLiveTracking(driverPh);
                        listenForAdminAssignment();
                        pendingRestoreStatus = STATUS_IDLE; pendingRestoreDriver = "";
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) { clearTripState(); }
                });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NOTIFICATIONS
    // ══════════════════════════════════════════════════════════════════════════

    private void showNotification(String msg) {
        tvNotificationMsg.setText(msg); notificationPanel.setVisibility(View.VISIBLE);
    }

    private void sendPushNotification(String title, String body) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(getActivity(), Home.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        NotificationManagerCompat.from(requireActivity()).notify(NOTIF_ID,
                new NotificationCompat.Builder(requireActivity(), CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title).setContentText(body)
                        .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
                        .setContentIntent(PendingIntent.getActivity(getActivity(), 0, intent, flags))
                        .build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Ambulance Alerts", NotificationManager.IMPORTANCE_HIGH);
            requireActivity().getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private float bearingBetween(LatLng from, LatLng to) {
        double lat1 = Math.toRadians(from.latitude), lat2 = Math.toRadians(to.latitude);
        double dLng = Math.toRadians(to.longitude - from.longitude);
        float b = (float) Math.toDegrees(Math.atan2(Math.sin(dLng) * Math.cos(lat2),
                Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng)));
        return (b + 360f) % 360f;
    }

    private void selectAmbType(String type) {
        selectedAmbType = type;
        btnTypeBasic.setBackgroundResource("basic".equals(type)
                ? R.drawable.type_card_selected : R.drawable.type_card_unselected);
        btnTypeAdvanced.setBackgroundResource("advanced".equals(type)
                ? R.drawable.type_card_selected : R.drawable.type_card_unselected);
    }

    private void selectSeverity(String label, MaterialButton clicked) {
        selectedSeverity = label;
        int orange = Color.parseColor("#EA6D35");
        for (MaterialButton b : new MaterialButton[]{sevLow, sevMedium, sevHigh, sevCritical}) {
            boolean sel = b == clicked;
            b.setTextColor(sel ? Color.WHITE : orange);
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    sel ? orange : Color.parseColor("#FFF6F2")));
        }
    }
}
package com.example.mysignupapp;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Adminrequests extends Fragment {

    static class RequestItem {
        String userPhone, userName, driverName, driverPhone, plate, severity,
                ambType, description, status, photoUrl,
                insuranceCompany, insuranceNumber;
        boolean hasInsurance, routeActive;
        long timestamp;
    }

    private String adminPhone, orgName;
    private final List<RequestItem> items = new ArrayList<>();
    private RecyclerView             rv;
    private LinearLayout             emptyState;
    private RequestsAdminAdapter     adapter;
    private ValueEventListener       requestsListener;

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            adminPhone = getArguments().getString("phone", "");
            orgName    = getArguments().getString("org_name", "");
        }
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_adminrequests, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rv         = view.findViewById(R.id.rv_admin_requests);
        emptyState = view.findViewById(R.id.empty_state_requests);

        MaterialButton btnSetHospital = view.findViewById(R.id.btn_set_hospital);
        if (btnSetHospital != null)
            btnSetHospital.setOnClickListener(v -> showHospitalCoordsDialog());

        adapter = new RequestsAdminAdapter(items, this::onTrackRequest, this::onAssignRequest);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);
        listenRequests();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (requestsListener != null)
            FirebaseDatabase.getInstance().getReference("requests")
                    .removeEventListener(requestsListener);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LISTEN — requests where assignedAdmin = this admin's phone
    // ══════════════════════════════════════════════════════════════════════════

    private void listenRequests() {
        requestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                items.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    // Match by assignedAdmin (new flow) OR old flow where driver belongs to admin
                    String assignedAdmin = ds.child("assignedAdmin").getValue(String.class);
                    String assigned      = ds.child("assignedDriver").getValue(String.class);

                    boolean isMyRequest = adminPhone.equals(assignedAdmin);
                    if (!isMyRequest) continue;

                    RequestItem item = new RequestItem();
                    item.userPhone   = ds.getKey();
                    item.userName    = ds.child("userName").getValue(String.class);
                    item.driverPhone = assigned;
                    item.driverName  = ds.child("driverName").getValue(String.class);
                    item.plate       = ds.child("ambulancePlate").getValue(String.class);
                    item.severity    = ds.child("severity").getValue(String.class);
                    item.ambType     = ds.child("ambulanceType").getValue(String.class);
                    item.description = ds.child("description").getValue(String.class);
                    item.status      = ds.child("status").getValue(String.class);
                    item.photoUrl    = ds.child("photoUrl").getValue(String.class);
                    // Insurance
                    Object ins = ds.child("hasInsurance").getValue();
                    item.hasInsurance     = ins != null && Boolean.parseBoolean(ins.toString());
                    item.insuranceCompany = ds.child("insuranceCompany").getValue(String.class);
                    item.insuranceNumber  = ds.child("insuranceNumber").getValue(String.class);
                    Object ts = ds.child("timestamp").getValue();
                    item.timestamp  = ts != null ? Long.parseLong(ts.toString()) : 0;
                    item.routeActive = "en_route".equals(item.status)
                            || "going_to_hospital".equals(item.status);

                    if (item.status    == null) item.status    = "searching";
                    if (item.userName  == null) item.userName  = item.userPhone;
                    if (item.driverName == null) item.driverName = "";
                    if (item.severity   == null) item.severity   = "—";
                    if (item.plate      == null) item.plate      = "—";

                    items.add(item);
                }

                Collections.sort(items, (a, b) -> {
                    int pa = a.routeActive ? 0 : 1, pb = b.routeActive ? 0 : 1;
                    return pa != pb ? pa - pb : Long.compare(b.timestamp, a.timestamp);
                });

                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    emptyState.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    rv.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        FirebaseDatabase.getInstance().getReference("requests")
                .addValueEventListener(requestsListener);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ASSIGN DRIVER
    // ══════════════════════════════════════════════════════════════════════════

    private void onAssignRequest(RequestItem item) {
        FirebaseDatabase.getInstance().getReference("driver")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        List<String> names  = new ArrayList<>();
                        List<String> phones = new ArrayList<>();
                        for (DataSnapshot d : ds.getChildren()) {
                            String aN  = d.child("adminNo").getValue(String.class);
                            String ph  = d.child("phone").getValue(String.class);
                            String nm  = d.child("name").getValue(String.class);
                            if (!adminPhone.equals(aN) && !adminPhone.equals(ph)) continue;
                            if (ph == null) ph = d.getKey();
                            names.add((nm != null ? nm : ph) + "  (" + ph + ")");
                            phones.add(d.getKey());
                        }
                        if (names.isEmpty()) {
                            Toast.makeText(getActivity(), "No drivers available", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Assign driver for " + item.userName)
                                .setItems(names.toArray(new String[0]), (dlg, which) ->
                                        assignDriverToRequest(item, phones.get(which),
                                                names.get(which).split("  ")[0]))
                                .setNegativeButton("Cancel", null).show();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void assignDriverToRequest(RequestItem item, String driverPhone, String driverName) {
        DatabaseReference reqRef = FirebaseDatabase.getInstance()
                .getReference("requests").child(item.userPhone);
        FirebaseDatabase.getInstance().getReference("driver").child(driverPhone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        String ambId    = ds.child("assignedAmbulance").getValue(String.class);
                        String hospital = ds.child("org_name").getValue(String.class);
                        reqRef.child("assignedDriver").setValue(driverPhone);
                        reqRef.child("driverPhone").setValue(driverPhone);
                        reqRef.child("driverName").setValue(driverName);
                        reqRef.child("driverHospital").setValue(hospital != null ? hospital : "");
                        reqRef.child("status").setValue("admin_assigned");
                        reqRef.child("assignedBy").setValue(adminPhone);
                        reqRef.child("assignedAt").setValue(System.currentTimeMillis());
                        if (ambId != null && !ambId.isEmpty()) {
                            reqRef.child("assignedAmbulanceId").setValue(ambId);
                            FirebaseDatabase.getInstance().getReference("ambulances").child(ambId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot ads) {
                                            String plate = ads.child("plateNo").getValue(String.class);
                                            String type  = ads.child("type").getValue(String.class);
                                            Object cost  = ads.child("costPerTrip").getValue();
                                            reqRef.child("ambulancePlate").setValue(plate != null ? plate : "");
                                            reqRef.child("ambulanceType").setValue(type != null ? type : "");
                                            if (cost != null) reqRef.child("costPerTrip").setValue(cost);
                                        }
                                        @Override public void onCancelled(@NonNull DatabaseError e) {}
                                    });
                        }
                        Toast.makeText(getActivity(),
                                "✓ " + driverName + " assigned!", Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TRACK
    // ══════════════════════════════════════════════════════════════════════════

    private void onTrackRequest(RequestItem item) {
        if (!item.routeActive) {
            Toast.makeText(getActivity(), "Route not active", Toast.LENGTH_SHORT).show(); return;
        }
        AdminMap adminMap = new AdminMap();
        Bundle b = new Bundle();
        b.putString("phone", adminPhone);
        b.putString("trackDriver", item.driverPhone);
        b.putString("trackUser",   item.userPhone);
        adminMap.setArguments(b);
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, adminMap).addToBackStack(null).commit();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HOSPITAL COORDS DIALOG
    // ══════════════════════════════════════════════════════════════════════════

    private void showHospitalCoordsDialog() {
        View dv = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_hospital_coords, null);
        EditText etLat  = dv.findViewById(R.id.et_hospital_lat);
        EditText etLng  = dv.findViewById(R.id.et_hospital_lng);
        EditText etName = dv.findViewById(R.id.et_hospital_name);
        MaterialButton btnGetLocation = dv.findViewById(R.id.btn_get_current_location);

        FirebaseDatabase.getInstance().getReference("admin").child(adminPhone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot ds) {
                        Object lat = ds.child("hospitalLat").getValue();
                        Object lng = ds.child("hospitalLng").getValue();
                        String nm  = ds.child("hospitalName").getValue(String.class);
                        if (lat != null) etLat.setText(lat.toString());
                        if (lng != null) etLng.setText(lng.toString());
                        if (nm  != null) etName.setText(nm);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });

        if (btnGetLocation != null) {
            btnGetLocation.setOnClickListener(v -> {
                if (androidx.core.app.ActivityCompat.checkSelfPermission(requireActivity(),
                        android.Manifest.permission.ACCESS_FINE_LOCATION)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    androidx.core.app.ActivityCompat.requestPermissions(requireActivity(),
                            new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION}, 9001);
                    Toast.makeText(getActivity(), "Grant location permission then try again",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                btnGetLocation.setEnabled(false); btnGetLocation.setText("Getting location…");
                com.google.android.gms.location.FusedLocationProviderClient fused =
                        com.google.android.gms.location.LocationServices
                                .getFusedLocationProviderClient(requireActivity());
                fused.getCurrentLocation(
                                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener(loc -> {
                            if (!isAdded()) return;
                            if (loc != null) {
                                etLat.setText(String.valueOf(loc.getLatitude()));
                                etLng.setText(String.valueOf(loc.getLongitude()));
                                btnGetLocation.setText("✓ Location captured!");
                            } else {
                                fused.getLastLocation().addOnSuccessListener(last -> {
                                    if (!isAdded()) return;
                                    if (last != null) {
                                        etLat.setText(String.valueOf(last.getLatitude()));
                                        etLng.setText(String.valueOf(last.getLongitude()));
                                        btnGetLocation.setText("✓ Location captured!");
                                    } else {
                                        btnGetLocation.setEnabled(true);
                                        btnGetLocation.setText("📍 Use My Current Location");
                                        Toast.makeText(getActivity(), "GPS not ready — turn on Location",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }).addOnFailureListener(e -> {
                            btnGetLocation.setEnabled(true);
                            btnGetLocation.setText("📍 Use My Current Location");
                        });
            });
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Set Hospital Location").setView(dv)
                .setPositiveButton("Save", (dlg, w) -> {
                    String latStr = etLat.getText().toString().trim();
                    String lngStr = etLng.getText().toString().trim();
                    String nmStr  = etName.getText().toString().trim();
                    if (latStr.isEmpty() || lngStr.isEmpty()) {
                        Toast.makeText(getActivity(), "Lat and Lng required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        DatabaseReference adminRef = FirebaseDatabase.getInstance()
                                .getReference("admin").child(adminPhone);
                        adminRef.child("hospitalLat").setValue(Double.parseDouble(latStr));
                        adminRef.child("hospitalLng").setValue(Double.parseDouble(lngStr));
                        adminRef.child("hospitalName").setValue(nmStr.isEmpty() ? orgName : nmStr);
                        Toast.makeText(getActivity(), "Hospital location saved ✓", Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(getActivity(), "Invalid coordinates", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADAPTER
    // ══════════════════════════════════════════════════════════════════════════

    static class RequestsAdminAdapter extends RecyclerView.Adapter<RequestsAdminAdapter.VH> {
        interface OnTrack  { void track(RequestItem item);  }
        interface OnAssign { void assign(RequestItem item); }

        private final List<RequestItem> items;
        private final OnTrack  onTrack;
        private final OnAssign onAssign;

        RequestsAdminAdapter(List<RequestItem> items, OnTrack onTrack, OnAssign onAssign) {
            this.items = items; this.onTrack = onTrack; this.onAssign = onAssign;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.admin_request_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            RequestItem it = items.get(pos);

            // Patient name + phone — tap to call
            String patient = (it.userName != null && !it.userName.equals(it.userPhone))
                    ? it.userName + "\n" + it.userPhone : it.userPhone;
            h.tvUserPhone.setText("Patient: " + patient);
            h.tvUserPhone.setCompoundDrawablesWithIntrinsicBounds(
                    0, 0, android.R.drawable.sym_action_call, 0);
            final String callPhone = it.userPhone;
            h.tvUserPhone.setOnClickListener(v -> {
                android.content.Intent dial = new android.content.Intent(
                        android.content.Intent.ACTION_DIAL,
                        android.net.Uri.parse("tel:" + callPhone));
                v.getContext().startActivity(dial);
            });

            // Driver info
            h.tvDriver.setText(it.driverName != null && !it.driverName.isEmpty()
                    ? "Driver: " + it.driverName + (it.plate != null ? " · " + it.plate : "")
                    : "⚠ Driver not assigned yet");

            h.tvSeverity.setText(it.severity);
            h.tvStatus.setText(it.status.toUpperCase().replace("_", " "));
            h.tvTime.setText(it.timestamp > 0
                    ? new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                    .format(new Date(it.timestamp)) : "—");

            // Description
            if (it.description != null && !it.description.isEmpty()
                    && !"No description".equals(it.description)) {
                h.tvDesc.setVisibility(View.VISIBLE); h.tvDesc.setText(it.description);
            } else h.tvDesc.setVisibility(View.GONE);

            // Insurance
            if (it.hasInsurance) {
                h.tvInsurance.setVisibility(View.VISIBLE);
                String insText = "🏥 Insurance: " +
                        (it.insuranceCompany != null ? it.insuranceCompany : "—") +
                        " | " + (it.insuranceNumber != null ? it.insuranceNumber : "—");
                h.tvInsurance.setText(insText);
            } else {
                h.tvInsurance.setVisibility(View.GONE);
            }

            // Patient photo
            if (it.photoUrl != null && !it.photoUrl.isEmpty()) {
                h.ivReqPhoto.setVisibility(View.VISIBLE);
                Glide.with(h.ivReqPhoto.getContext()).load(it.photoUrl)
                        .placeholder(android.R.color.darker_gray)
                        .centerCrop().into(h.ivReqPhoto);
            } else {
                h.ivReqPhoto.setVisibility(View.GONE);
            }

            // Status color
            int sc;
            switch (it.status != null ? it.status : "") {
                case "en_route": case "going_to_hospital": sc = 0xFFEA6D35; break;
                case "arrived":         sc = 0xFF27AE60; break;
                case "cancelled": case "rejected": sc = 0xFF888888; break;
                case "searching": case "admin_assigned": sc = 0xFF3B608C; break;
                default:                sc = 0xFFAAAAAA;
            }
            h.tvStatus.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(sc));

            // Track button
            h.btnTrack.setEnabled(it.routeActive);
            h.btnTrack.setAlpha(it.routeActive ? 1f : 0.4f);
            h.btnTrack.setOnClickListener(v -> onTrack.track(it));

            // Assign button — show when no driver assigned
            boolean needsDriver = "searching".equals(it.status)
                    || it.driverName == null || it.driverName.isEmpty();
            if (h.btnAssign != null) {
                h.btnAssign.setVisibility(needsDriver ? View.VISIBLE : View.GONE);
                h.btnAssign.setOnClickListener(v -> onAssign.assign(it));
            }
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView       tvUserPhone, tvDriver, tvSeverity, tvStatus, tvTime,
                    tvDesc, tvInsurance;
            ImageView      ivReqPhoto;
            MaterialButton btnTrack, btnAssign;
            VH(@NonNull View v) {
                super(v);
                tvUserPhone  = v.findViewById(R.id.tv_req_user);
                tvDriver     = v.findViewById(R.id.tv_req_driver);
                tvSeverity   = v.findViewById(R.id.tv_req_severity_adm);
                tvStatus     = v.findViewById(R.id.tv_req_status_adm);
                tvTime       = v.findViewById(R.id.tv_req_time);
                tvDesc       = v.findViewById(R.id.tv_req_desc_adm);
                tvInsurance  = v.findViewById(R.id.tv_req_insurance);
                ivReqPhoto   = v.findViewById(R.id.iv_req_photo_admin);
                btnTrack     = v.findViewById(R.id.btn_track_request);
                btnAssign    = v.findViewById(R.id.btn_assign_driver);
            }
        }
    }
}
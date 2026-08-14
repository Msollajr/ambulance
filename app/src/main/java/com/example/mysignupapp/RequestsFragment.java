package com.example.mysignupapp;

import com.bumptech.glide.Glide;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RequestsFragment extends Fragment {

    // ── Data model ─────────────────────────────────────────────────────────────
    static class RequestRow {
        String userPhone, severity, ambType, description, status, assignedDriver;
        double lat, lng, distKm;
        int    etaMin;
    }

    // ── Driver state ───────────────────────────────────────────────────────────
    private String phone;
    private String driverName;
    private String driverHospital;
    private String assignedAmbulanceId = "";
    private String ambulancePlate      = "";
    private String ambulanceType       = "BLS";
    private double ambulanceCost       = 0;
    private double myLat = 0, myLng = 0;

    // ── Views ──────────────────────────────────────────────────────────────────
    private RecyclerView          recyclerView;
    private LinearLayout          emptyState;
    private RequestsAdapter       adapter;
    private final List<RequestRow> rows = new ArrayList<>();

    // ── Firebase ───────────────────────────────────────────────────────────────
    private ValueEventListener requestsListener;

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) phone = getArguments().getString("phone");
        loadDriverProfile();
        getMyLocation();
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_requests_driver, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.rv_requests);
        emptyState   = view.findViewById(R.id.empty_state);

        adapter = new RequestsAdapter(rows, this::onAccept, this::onReject);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

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
    // DRIVER + AMBULANCE PROFILE
    // ══════════════════════════════════════════════════════════════════════════

    private void loadDriverProfile() {
        if (phone == null) return;
        FirebaseDatabase.getInstance().getReference("driver").child(phone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        driverName     = ds.child("name").getValue(String.class);
                        driverHospital = ds.child("org_name").getValue(String.class);
                        String ambId   = ds.child("assignedAmbulance").getValue(String.class);
                        if (ambId != null && !ambId.isEmpty()) {
                            assignedAmbulanceId = ambId;
                            FirebaseDatabase.getInstance().getReference("ambulances")
                                    .child(ambId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot a) {
                                            ambulancePlate = a.child("plateNo")
                                                    .getValue(String.class);
                                            ambulanceType = a.child("type")
                                                    .getValue(String.class);
                                            Object c = a.child("costPerTrip").getValue();
                                            if (c != null)
                                                ambulanceCost = Double.parseDouble(c.toString());
                                            if (ambulancePlate == null) ambulancePlate = "";
                                            if (ambulanceType  == null) ambulanceType  = "BLS";
                                        }
                                        @Override public void onCancelled(@NonNull DatabaseError e) {}
                                    });
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    @SuppressWarnings("MissingPermission")
    private void getMyLocation() {
        FusedLocationProviderClient fused =
                LocationServices.getFusedLocationProviderClient(requireActivity());
        try {
            fused.getLastLocation().addOnSuccessListener(loc -> {
                if (loc != null) {
                    myLat = loc.getLatitude();
                    myLng = loc.getLongitude();
                }
            });
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LISTEN TO REQUESTS
    // ══════════════════════════════════════════════════════════════════════════

    private void listenRequests() {
        requestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<RequestRow> pending = new ArrayList<>();
                long total = snapshot.getChildrenCount();
                if (total == 0) { finaliseList(pending); return; }

                for (DataSnapshot ds : snapshot.getChildren()) {
                    RequestRow row   = new RequestRow();
                    row.userPhone    = ds.getKey();
                    row.severity     = ds.child("severity").getValue(String.class);
                    row.ambType      = ds.child("ambulanceType").getValue(String.class);
                    row.description  = ds.child("description").getValue(String.class);
                    row.status       = ds.child("status").getValue(String.class);
                    row.assignedDriver = ds.child("assignedDriver").getValue(String.class);
                    if (row.status == null) {
                        pending.add(row);
                        if (pending.size() >= total) finaliseList(pending);
                        continue;
                    }
                    fetchGeoAndAdd(row, pending, total);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        FirebaseDatabase.getInstance().getReference("requests")
                .addValueEventListener(requestsListener);
    }

    private void fetchGeoAndAdd(RequestRow row, List<RequestRow> pending, long total) {
        FirebaseDatabase.getInstance().getReference("Requests")
                .child(row.userPhone).child("l")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        if (ds.exists()) {
                            List<Object> c = (List<Object>) ds.getValue();
                            if (c != null && c.size() >= 2) {
                                row.lat = Double.parseDouble(c.get(0).toString());
                                row.lng = Double.parseDouble(c.get(1).toString());
                                if (myLat != 0) {
                                    float[] res = new float[1];
                                    Location.distanceBetween(
                                            myLat, myLng, row.lat, row.lng, res);
                                    row.distKm = res[0] / 1000.0;
                                    row.etaMin = Math.max(1, (int)(row.distKm / 0.5));
                                }
                            }
                        }
                        pending.add(row);
                        if (pending.size() >= total) finaliseList(pending);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        pending.add(row);
                        if (pending.size() >= total) finaliseList(pending);
                    }
                });
    }

    private void finaliseList(List<RequestRow> all) {
        List<RequestRow> sorted = new ArrayList<>(all);
        Collections.sort(sorted, (a, b) -> {
            int pa = "searching".equals(a.status) ? 0 : 1;
            int pb = "searching".equals(b.status) ? 0 : 1;
            if (pa != pb) return pa - pb;
            return Double.compare(a.distKm, b.distKm);
        });
        rows.clear();
        rows.addAll(sorted);
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                adapter.notifyDataSetChanged();
                emptyState.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
            });
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ACCEPT / REJECT
    // ══════════════════════════════════════════════════════════════════════════

    private void onAccept(RequestRow row) {
        if (!"searching".equals(row.status)) {
            Toast.makeText(getActivity(), "Request no longer available",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        DatabaseReference reqRef = FirebaseDatabase.getInstance()
                .getReference("requests").child(row.userPhone);
        reqRef.child("status").setValue("en_route");
        reqRef.child("assignedDriver").setValue(phone);
        reqRef.child("driverName").setValue(driverName != null ? driverName : phone);
        reqRef.child("driverHospital").setValue(driverHospital != null ? driverHospital : "—");
        reqRef.child("driverPhone").setValue(phone);
        reqRef.child("ambulancePlate").setValue(ambulancePlate);
        reqRef.child("ambulanceType").setValue(ambulanceType);
        reqRef.child("costPerTrip").setValue(ambulanceCost);
        reqRef.child("assignedAmbulanceId").setValue(assignedAmbulanceId);
        reqRef.child("acceptedAt").setValue(System.currentTimeMillis());
        if (!assignedAmbulanceId.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("ambulances")
                    .child(assignedAmbulanceId).child("status").setValue("on_trip");
        }
        Toast.makeText(getActivity(),
                "Accepted — check the Map tab for navigation",
                Toast.LENGTH_LONG).show();
    }

    private void onReject(RequestRow row) {
        FirebaseDatabase.getInstance().getReference("requests")
                .child(row.userPhone).child("status").setValue("rejected");
        Toast.makeText(getActivity(), "Request rejected", Toast.LENGTH_SHORT).show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADAPTER — with photo support
    // ══════════════════════════════════════════════════════════════════════════

    static class RequestsAdapter extends RecyclerView.Adapter<RequestsAdapter.VH> {

        interface Action { void run(RequestRow row); }

        private final List<RequestRow> items;
        private final Action onAccept, onReject;

        RequestsAdapter(List<RequestRow> items, Action onAccept, Action onReject) {
            this.items    = items;
            this.onAccept = onAccept;
            this.onReject = onReject;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.request_row_driver, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            h.bind(items.get(pos), onAccept, onReject);
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView       tvSeverity, tvType, tvDistance, tvEta,
                    tvDescription, tvStatus, tvPatient;
            ImageView      ivPhoto;
            MaterialButton btnAccept, btnReject;

            VH(@NonNull View v) {
                super(v);
                tvSeverity    = v.findViewById(R.id.tv_row_severity);
                tvType        = v.findViewById(R.id.tv_row_type);
                tvDistance    = v.findViewById(R.id.tv_row_distance);
                tvEta         = v.findViewById(R.id.tv_row_eta);
                tvDescription = v.findViewById(R.id.tv_row_description);
                tvStatus      = v.findViewById(R.id.tv_row_status);
                tvPatient     = v.findViewById(R.id.tv_row_patient);
                ivPhoto       = v.findViewById(R.id.iv_row_photo); // ← PATCH
                btnAccept     = v.findViewById(R.id.btn_row_accept);
                btnReject     = v.findViewById(R.id.btn_row_reject);
            }

            void bind(RequestRow row, Action onAccept, Action onReject) {
                tvPatient.setText("Patient: " + row.userPhone);
                tvSeverity.setText(row.severity != null ? row.severity : "—");
                tvType.setText("advanced".equalsIgnoreCase(row.ambType)
                        ? "Advanced (ALS)" : "Basic (BLS)");
                tvDistance.setText(row.distKm > 0
                        ? String.format(Locale.getDefault(), "%.1f km", row.distKm) : "—");
                tvEta.setText(row.etaMin > 0 ? row.etaMin + " min" : "—");

                boolean hasDesc = row.description != null && !row.description.isEmpty()
                        && !"No description".equals(row.description);
                tvDescription.setText(hasDesc ? row.description : "");
                tvDescription.setVisibility(hasDesc ? View.VISIBLE : View.GONE);

                // Load photo from Ubuntu server via Glide — NOT Base64
                if (ivPhoto != null) ivPhoto.setVisibility(View.GONE);
                FirebaseDatabase.getInstance().getReference("requests")
                        .child(row.userPhone).child("photoUrl")
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot ds) {
                                String url = ds.getValue(String.class);
                                if (url != null && !url.isEmpty() && ivPhoto != null) {
                                    Glide.with(ivPhoto.getContext())
                                            .load(url)
                                            .placeholder(android.R.color.darker_gray)
                                            .centerCrop()
                                            .into(ivPhoto);
                                    ivPhoto.setVisibility(View.VISIBLE);
                                }
                            }
                            @Override public void onCancelled(@NonNull DatabaseError e) {}
                        });

                // Status badge
                String st = row.status != null ? row.status : "searching";
                switch (st) {
                    case "en_route":
                    case "arrived":
                        tvStatus.setText("Already accepted");
                        tvStatus.setBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(
                                        Color.parseColor("#E67E22")));
                        tvStatus.setVisibility(View.VISIBLE);
                        btnAccept.setVisibility(View.GONE);
                        btnReject.setVisibility(View.GONE);
                        break;
                    case "cancelled":
                        tvStatus.setText("Cancelled by patient");
                        tvStatus.setBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(
                                        Color.parseColor("#888888")));
                        tvStatus.setVisibility(View.VISIBLE);
                        btnAccept.setVisibility(View.GONE);
                        btnReject.setVisibility(View.GONE);
                        break;
                    case "rejected":
                        tvStatus.setText("Rejected");
                        tvStatus.setBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(
                                        Color.parseColor("#E74C3C")));
                        tvStatus.setVisibility(View.VISIBLE);
                        btnAccept.setVisibility(View.GONE);
                        btnReject.setVisibility(View.GONE);
                        break;
                    default: // searching
                        tvStatus.setVisibility(View.GONE);
                        btnAccept.setVisibility(View.VISIBLE);
                        btnReject.setVisibility(View.VISIBLE);
                        break;
                }

                tvSeverity.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(severityColor(row.severity)));
                btnAccept.setOnClickListener(v -> onAccept.run(row));
                btnReject.setOnClickListener(v -> onReject.run(row));
            }

            private int severityColor(String sev) {
                if (sev == null) return Color.parseColor("#27AE60");
                switch (sev.toLowerCase()) {
                    case "critical": return Color.parseColor("#E74C3C");
                    case "high":     return Color.parseColor("#E67E22");
                    case "medium":   return Color.parseColor("#F1C40F");
                    default:         return Color.parseColor("#27AE60");
                }
            }
        }
    }
}
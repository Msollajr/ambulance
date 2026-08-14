package com.example.mysignupapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class DriversAvailable extends Fragment {

    private String adminPhone;
    private RecyclerView rv;
    private TextView tvEmpty;
    private final List<DriverItem> drivers = new ArrayList<>();
    private DriversAdapter adapter;

    static class DriverItem {
        String phone, name, email, orgName, plate, ambType, photoUrl, status;
        boolean isOnline;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null)
            adminPhone = getArguments().getString("phone", "");
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_drivers_available, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rv      = view.findViewById(R.id.rv_drivers);
        tvEmpty = view.findViewById(R.id.tv_no_drivers);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DriversAdapter(drivers);
        rv.setAdapter(adapter);
        loadDrivers();
    }

    private void loadDrivers() {
        FirebaseDatabase.getInstance().getReference("driver")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        drivers.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String aN = ds.child("adminNo").getValue(String.class);
                            // Match drivers belonging to this admin (by phone stored in adminNo)
                            if (!adminPhone.equals(aN)) {
                                // Also check org_name match as fallback
                                String org = ds.child("org_name").getValue(String.class);
                                String adminOrg = getAdminOrgName();
                                if (adminOrg == null || !adminOrg.equals(org)) continue;
                            }

                            DriverItem item = new DriverItem();
                            item.phone    = ds.getKey();
                            item.name     = ds.child("name").getValue(String.class);
                            item.email    = ds.child("email").getValue(String.class);
                            item.orgName  = ds.child("org_name").getValue(String.class);
                            item.photoUrl = ds.child("photoUrl").getValue(String.class);

                            if (item.name  == null) item.name  = item.phone;
                            if (item.email == null) item.email = "—";

                            // Check if driver is online (has GeoFire location)
                            checkDriverOnlineStatus(item, ds.getKey());
                            drivers.add(item);
                        }

                        // Load ambulance info for each driver
                        for (DriverItem d : drivers) {
                            loadAmbulanceForDriver(d);
                        }

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                adapter.notifyDataSetChanged();
                                tvEmpty.setVisibility(drivers.isEmpty() ? View.VISIBLE : View.GONE);
                                rv.setVisibility(drivers.isEmpty() ? View.GONE : View.VISIBLE);
                            });
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private String cachedAdminOrgName = null;
    private String getAdminOrgName() { return cachedAdminOrgName; }

    private void checkDriverOnlineStatus(DriverItem item, String driverPhone) {
        FirebaseDatabase.getInstance().getReference("driversavailable")
                .child(driverPhone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        item.isOnline = ds.exists();
                        if (getActivity() != null)
                            getActivity().runOnUiThread(() -> adapter.notifyDataSetChanged());
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void loadAmbulanceForDriver(DriverItem item) {
        FirebaseDatabase.getInstance().getReference("ambulances")
                .orderByChild("assignedDriver").equalTo(item.phone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            item.plate   = ds.child("plateNo").getValue(String.class);
                            item.ambType = ds.child("type").getValue(String.class);
                            item.status  = ds.child("status").getValue(String.class);
                            break;
                        }
                        if (getActivity() != null)
                            getActivity().runOnUiThread(() -> adapter.notifyDataSetChanged());
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADAPTER
    // ══════════════════════════════════════════════════════════════════════════

    static class DriversAdapter extends RecyclerView.Adapter<DriversAdapter.VH> {

        private final List<DriverItem> items;
        DriversAdapter(List<DriverItem> items) { this.items = items; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.driver_row_admin, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            DriverItem it = items.get(pos);

            h.tvName.setText(it.name);
            h.tvPhone.setText(it.phone);
            h.tvEmail.setText(it.email != null ? it.email : "—");
            h.tvPlate.setText(it.plate != null ? it.plate : "No ambulance");
            h.tvType.setText(it.ambType != null ? it.ambType : "—");

            // Online/offline badge
            h.tvOnline.setText(it.isOnline ? "● Online" : "○ Offline");
            h.tvOnline.setTextColor(it.isOnline
                    ? android.graphics.Color.parseColor("#27AE60")
                    : android.graphics.Color.parseColor("#AAAAAA"));

            // Ambulance status badge
            if (it.status != null) {
                h.tvAmbStatus.setVisibility(View.VISIBLE);
                h.tvAmbStatus.setText(it.status.replace("_", " ").toUpperCase());
                int statusColor;
                switch (it.status) {
                    case "on_trip":     statusColor = 0xFFEA6D35; break;
                    case "available":   statusColor = 0xFF27AE60; break;
                    case "maintenance": statusColor = 0xFF888888; break;
                    default:            statusColor = 0xFFAAAAAA; break;
                }
                h.tvAmbStatus.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(statusColor));
            } else {
                h.tvAmbStatus.setVisibility(View.GONE);
            }

            // Load profile photo with Glide
            if (it.photoUrl != null && !it.photoUrl.isEmpty()) {
                Glide.with(h.ivAvatar.getContext())
                        .load(it.photoUrl)
                        .placeholder(R.drawable.ic_person_add)
                        .error(R.drawable.ic_person_add)
                        .circleCrop()
                        .into(h.ivAvatar);
            } else {
                Glide.with(h.ivAvatar.getContext())
                        .load(R.drawable.ic_person_add)
                        .circleCrop()
                        .into(h.ivAvatar);
            }
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView  tvName, tvPhone, tvEmail, tvPlate, tvType, tvOnline, tvAmbStatus;
            VH(@NonNull View v) {
                super(v);
                ivAvatar    = v.findViewById(R.id.iv_driver_avatar);
                tvName      = v.findViewById(R.id.tv_driver_name_row);
                tvPhone     = v.findViewById(R.id.tv_driver_phone_row);
                tvEmail     = v.findViewById(R.id.tv_driver_email_row);
                tvPlate     = v.findViewById(R.id.tv_driver_plate_row);
                tvType      = v.findViewById(R.id.tv_driver_type_row);
                tvOnline    = v.findViewById(R.id.tv_driver_online_row);
                tvAmbStatus = v.findViewById(R.id.tv_amb_status_row);
            }
        }
    }
}
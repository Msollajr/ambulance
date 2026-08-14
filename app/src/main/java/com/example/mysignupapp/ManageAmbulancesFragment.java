package com.example.mysignupapp;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.widget.LinearLayout;

public class ManageAmbulancesFragment extends Fragment
        implements AmbulanceAdapter.OnAmbulanceClick {

    // ── State ─────────────────────────────────────────────────────────────────
    private String adminPhone;
    private String orgName;

    // ── Firebase ──────────────────────────────────────────────────────────────
    private DatabaseReference ambRef;
    private ValueEventListener ambListener;

    // ── Driver data cache (phone → name) for assignment picker ────────────────
    private final Map<String, String> driverMap = new HashMap<>(); // phone → name
    private final List<String> driverPhones = new ArrayList<>();
    private final List<String> driverNames  = new ArrayList<>();

    // ── Views ─────────────────────────────────────────────────────────────────
    private AmbulanceAdapter adapter;
    private RecyclerView recyclerView;
    private LinearLayout tvEmpty;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            adminPhone = getArguments().getString("phone", "");
            orgName    = getArguments().getString("org_name", "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_manage_ambulances, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvEmpty     = view.findViewById(R.id.tv_empty_ambulances);
        recyclerView = view.findViewById(R.id.rv_ambulances);
        MaterialButton btnAdd = view.findViewById(R.id.btn_add_ambulance);

        adapter = new AmbulanceAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> showAddEditDialog(null));

        loadDriversForOrg();
        listenAmbulances();
    }

    // ── Load org's drivers for the assignment picker ───────────────────────────

    private void loadDriversForOrg() {
        FirebaseDatabase.getInstance().getReference("driver")
                .orderByChild("adminNo").equalTo(adminPhone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        driverMap.clear();
                        driverPhones.clear();
                        driverNames.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String ph   = ds.child("phone").getValue(String.class);
                            String name = ds.child("name").getValue(String.class);
                            if (ph != null && name != null) {
                                driverMap.put(ph, name);
                                driverPhones.add(ph);
                                driverNames.add(name + " (" + ph + ")");
                            }
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    // ── Listen to ambulances belonging to this admin ───────────────────────────

    private void listenAmbulances() {
        ambRef = FirebaseDatabase.getInstance().getReference("ambulances");
        ambListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Ambulance_model> list = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Ambulance_model amb = ds.getValue(Ambulance_model.class);
                    if (amb != null && adminPhone.equals(amb.getHospitalId())) {
                        amb.setAmbulanceId(ds.getKey());
                        list.add(amb);
                    }
                }
                adapter.setData(list);
                tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                Toast.makeText(getActivity(),
                        "Error loading ambulances: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };
        ambRef.addValueEventListener(ambListener);
    }

    // ── Add / Edit dialog ──────────────────────────────────────────────────────

    @Override
    public void onEdit(Ambulance_model amb) {
        showAddEditDialog(amb);
    }

    private void showAddEditDialog(@Nullable Ambulance_model existing) {
        boolean isEdit = existing != null;
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_ambulance, null);

        EditText etPlate  = dialogView.findViewById(R.id.et_plate);
        Spinner  spType   = dialogView.findViewById(R.id.sp_amb_type);
        Spinner  spEquip  = dialogView.findViewById(R.id.sp_equip_level);
        EditText etEquipNotes = dialogView.findViewById(R.id.et_equip_notes);
        EditText etCost   = dialogView.findViewById(R.id.et_cost);
        Spinner  spStatus = dialogView.findViewById(R.id.sp_status);

        // Populate spinners
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"BLS", "ALS", "ICU"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(typeAdapter);

        ArrayAdapter<String> equipAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"basic", "full"});
        equipAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spEquip.setAdapter(equipAdapter);

        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"available", "on_trip", "maintenance"});
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spStatus.setAdapter(statusAdapter);

        // Pre-fill if editing
        if (isEdit) {
            etPlate.setText(existing.getPlateNo());
            etEquipNotes.setText(existing.getEquipmentNotes());
            etCost.setText(String.valueOf((int) existing.getCostPerTrip()));
            selectSpinner(spType,   existing.getType());
            selectSpinner(spEquip,  existing.getEquipmentLevel());
            selectSpinner(spStatus, existing.getStatus());
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(isEdit ? "Edit Ambulance" : "Add Ambulance")
                .setView(dialogView)
                .setPositiveButton(isEdit ? "Save" : "Add", (dlg, which) -> {
                    String plate = etPlate.getText().toString().trim();
                    if (plate.isEmpty()) {
                        Toast.makeText(getActivity(),
                                "Plate number is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String type        = spType.getSelectedItem().toString();
                    String equip       = spEquip.getSelectedItem().toString();
                    String notes       = etEquipNotes.getText().toString().trim();
                    String costStr     = etCost.getText().toString().trim();
                    double cost        = costStr.isEmpty() ? 0 : Double.parseDouble(costStr);
                    String status      = spStatus.getSelectedItem().toString();

                    saveAmbulance(existing, plate, type, equip, notes, cost, status);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveAmbulance(@Nullable Ambulance_model existing,
                               String plate, String type, String equip,
                               String notes, double cost, String status) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("ambulances");

        String key = existing != null ? existing.getAmbulanceId() : ref.push().getKey();
        if (key == null) return;

        String assignedDriver = existing != null ? existing.getAssignedDriver() : "";
        String driverName     = existing != null ? existing.getDriverName()     : "";
        if (assignedDriver == null) assignedDriver = "";
        if (driverName     == null) driverName     = "";

        Ambulance_model amb = new Ambulance_model(
                key, plate, type, equip, notes, cost, status,
                assignedDriver, driverName, adminPhone, orgName);

        ref.child(key).setValue(amb)
                .addOnSuccessListener(v -> Toast.makeText(getActivity(),
                        existing != null ? "Ambulance updated" : "Ambulance added",
                        Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(getActivity(),
                        "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ── Assign driver dialog ───────────────────────────────────────────────────

    @Override
    public void onAssignDriver(Ambulance_model amb) {
        if (driverPhones.isEmpty()) {
            Toast.makeText(getActivity(),
                    "No drivers found for your organisation. Add a driver first.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Build list with "Unassign" option first
        List<String> options = new ArrayList<>();
        options.add("— Unassign (remove driver) —");
        options.addAll(driverNames);

        String[] optArray = options.toArray(new String[0]);

        new AlertDialog.Builder(requireContext())
                .setTitle("Assign driver to " + amb.getPlateNo())
                .setItems(optArray, (dlg, which) -> {
                    DatabaseReference ambNode = FirebaseDatabase.getInstance()
                            .getReference("ambulances").child(amb.getAmbulanceId());

                    if (which == 0) {
                        // Unassign
                        ambNode.child("assignedDriver").setValue("");
                        ambNode.child("driverName").setValue("");
                        Toast.makeText(getActivity(),
                                "Driver removed from " + amb.getPlateNo(),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        // Assign selected driver (index offset by 1 for the Unassign option)
                        String selectedPhone = driverPhones.get(which - 1);
                        String selectedName  = driverMap.get(selectedPhone);

                        ambNode.child("assignedDriver").setValue(selectedPhone);
                        ambNode.child("driverName").setValue(selectedName);

                        // Also update driver record with ambulance info
                        FirebaseDatabase.getInstance().getReference("driver")
                                .child(selectedPhone)
                                .child("assignedAmbulance").setValue(amb.getAmbulanceId());

                        Toast.makeText(getActivity(),
                                selectedName + " assigned to " + amb.getPlateNo(),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Delete ambulance (long-press confirmation) ─────────────────────────────

    public void confirmDelete(Ambulance_model amb) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete " + amb.getPlateNo() + "?")
                .setMessage("This will permanently remove this ambulance record.")
                .setPositiveButton("Delete", (d, w) -> {
                    FirebaseDatabase.getInstance().getReference("ambulances")
                            .child(amb.getAmbulanceId()).removeValue()
                            .addOnSuccessListener(v ->
                                    Toast.makeText(getActivity(),
                                            "Ambulance deleted", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Spinner helper ─────────────────────────────────────────────────────────

    private void selectSpinner(Spinner sp, String value) {
        if (value == null) return;
        for (int i = 0; i < sp.getCount(); i++) {
            if (value.equalsIgnoreCase(sp.getItemAtPosition(i).toString())) {
                sp.setSelection(i);
                return;
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ambRef != null && ambListener != null) {
            ambRef.removeEventListener(ambListener);
        }
    }
}
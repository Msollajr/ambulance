package com.example.mysignupapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AmbulanceAdapter extends RecyclerView.Adapter<AmbulanceAdapter.VH> {

    public interface OnAmbulanceClick {
        void onEdit(Ambulance_model amb);
        void onAssignDriver(Ambulance_model amb);
    }

    private final List<Ambulance_model> items = new ArrayList<>();
    private final OnAmbulanceClick listener;

    public AmbulanceAdapter(OnAmbulanceClick listener) {
        this.listener = listener;
    }

    public void setData(List<Ambulance_model> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.ambulance_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Ambulance_model amb = items.get(position);
        h.bind(amb, listener);
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ── ViewHolder ─────────────────────────────────────────────────────────────
    static class VH extends RecyclerView.ViewHolder {

        private final TextView tvPlate, tvType, tvEquip, tvCost,
                tvStatus, tvDriver, tvBtnEdit, tvBtnAssign;

        VH(@NonNull View v) {
            super(v);
            tvPlate    = v.findViewById(R.id.tv_amb_plate);
            tvType     = v.findViewById(R.id.tv_amb_type);
            tvEquip    = v.findViewById(R.id.tv_amb_equip);
            tvCost     = v.findViewById(R.id.tv_amb_cost);
            tvStatus   = v.findViewById(R.id.tv_amb_status);
            tvDriver   = v.findViewById(R.id.tv_amb_driver);
            tvBtnEdit  = v.findViewById(R.id.btn_edit_amb);
            tvBtnAssign = v.findViewById(R.id.btn_assign_driver);
        }

        void bind(Ambulance_model amb, OnAmbulanceClick listener) {
            tvPlate.setText(amb.getPlateNo() != null ? amb.getPlateNo() : "—");
            tvType.setText(amb.getType()  != null ? amb.getType()  : "—");
            tvEquip.setText(amb.getEquipmentLevel() != null
                    ? amb.getEquipmentLevel().toUpperCase() : "—");
            tvCost.setText(amb.getCostDisplay());

            // Status badge colour
            String status = amb.getStatus() != null ? amb.getStatus() : "available";
            tvStatus.setText(status.toUpperCase().replace("_", " "));
            switch (status) {
                case "on_trip":
                    tvStatus.setBackgroundResource(R.drawable.badge_high);
                    break;
                case "maintenance":
                    tvStatus.setBackgroundResource(R.drawable.badge_medium);
                    break;
                default:
                    tvStatus.setBackgroundResource(R.drawable.badge_low);
                    break;
            }

            // Driver assignment
            if (amb.isUnassigned()) {
                tvDriver.setText("No driver assigned");
                tvDriver.setTextColor(0xFFAAAAAA);
                tvBtnAssign.setText("Assign Driver");
            } else {
                tvDriver.setText(amb.getDriverName() != null
                        ? amb.getDriverName() : amb.getAssignedDriver());
                tvDriver.setTextColor(0xFF3B608C);
                tvBtnAssign.setText("Change Driver");
            }

            tvBtnEdit.setOnClickListener(v -> listener.onEdit(amb));
            tvBtnAssign.setOnClickListener(v -> listener.onAssignDriver(amb));
        }
    }
}

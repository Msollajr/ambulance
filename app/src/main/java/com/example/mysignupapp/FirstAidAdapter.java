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

public class FirstAidAdapter extends RecyclerView.Adapter<FirstAidAdapter.TipViewHolder> {

    public interface OnTipClickListener {
        void onTipClick(FirstAid_model tip);
    }

    private List<FirstAid_model> fullList;
    private List<FirstAid_model> filteredList;
    private final OnTipClickListener listener;
    private final Context context;

    public FirstAidAdapter(Context context, List<FirstAid_model> tips, OnTipClickListener listener) {
        this.context = context;
        this.listener = listener;
        this.fullList = new ArrayList<>(tips);
        this.filteredList = new ArrayList<>(tips);
    }

    // ── Search filter ─────────────────────────────────────────────────────────

    public void filterByQuery(String query) {
        filteredList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredList.addAll(fullList);
        } else {
            String lower = query.toLowerCase().trim();
            for (FirstAid_model tip : fullList) {
                if (tip.getTitle().toLowerCase().contains(lower)
                        || tip.getShortDescription().toLowerCase().contains(lower)
                        || tip.getCategory().toLowerCase().contains(lower)) {
                    filteredList.add(tip);
                }
            }
        }
        notifyDataSetChanged();
    }

    // ── Category filter ───────────────────────────────────────────────────────

    public void filterByCategory(String category) {
        filteredList.clear();
        if (category == null || category.equals("All")) {
            filteredList.addAll(fullList);
        } else {
            for (FirstAid_model tip : fullList) {
                if (tip.getCategory().equals(category)) {
                    filteredList.add(tip);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void filterByCategoryAndQuery(String category, String query) {
        filteredList.clear();
        for (FirstAid_model tip : fullList) {
            boolean categoryMatch = (category == null || category.equals("All") || tip.getCategory().equals(category));
            boolean queryMatch = true;
            if (query != null && !query.trim().isEmpty()) {
                String lower = query.toLowerCase().trim();
                queryMatch = tip.getTitle().toLowerCase().contains(lower)
                        || tip.getShortDescription().toLowerCase().contains(lower)
                        || tip.getCategory().toLowerCase().contains(lower);
            }
            if (categoryMatch && queryMatch) {
                filteredList.add(tip);
            }
        }
        notifyDataSetChanged();
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    @NonNull
    @Override
    public TipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.firstaid_row, parent, false);
        return new TipViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TipViewHolder holder, int position) {
        FirstAid_model tip = filteredList.get(position);
        holder.bind(tip, listener);
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class TipViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvEmoji;
        private final TextView tvTitle;
        private final TextView tvCategory;
        private final TextView tvDescription;
        private final TextView tvSeverityBadge;
        private final TextView tvAmbulanceBadge;
        private final View severityStripe;

        TipViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmoji         = itemView.findViewById(R.id.tv_tip_emoji);
            tvTitle         = itemView.findViewById(R.id.tv_tip_title);
            tvCategory      = itemView.findViewById(R.id.tv_tip_category);
            tvDescription   = itemView.findViewById(R.id.tv_tip_description);
            tvSeverityBadge = itemView.findViewById(R.id.tv_severity_badge);
            tvAmbulanceBadge= itemView.findViewById(R.id.tv_ambulance_badge);
            severityStripe  = itemView.findViewById(R.id.severity_stripe);
        }

        void bind(FirstAid_model tip, OnTipClickListener listener) {
            tvEmoji.setText(tip.getIconEmoji());
            tvTitle.setText(tip.getTitle());
            tvCategory.setText(tip.getCategory());
            tvDescription.setText(tip.getShortDescription());

            // Severity badge
            switch (tip.getSeverityLevel()) {
                case "critical":
                    tvSeverityBadge.setText("⚠ CRITICAL");
                    tvSeverityBadge.setBackgroundResource(R.drawable.badge_critical);
                    severityStripe.setBackgroundResource(R.color.severity_critical);
                    break;
                case "high":
                    tvSeverityBadge.setText("▲ HIGH");
                    tvSeverityBadge.setBackgroundResource(R.drawable.badge_high);
                    severityStripe.setBackgroundResource(R.color.severity_high);
                    break;
                case "medium":
                    tvSeverityBadge.setText("◆ MEDIUM");
                    tvSeverityBadge.setBackgroundResource(R.drawable.badge_medium);
                    severityStripe.setBackgroundResource(R.color.severity_medium);
                    break;
                default:
                    tvSeverityBadge.setText("● LOW");
                    tvSeverityBadge.setBackgroundResource(R.drawable.badge_low);
                    severityStripe.setBackgroundResource(R.color.severity_low);
                    break;
            }

            // Ambulance badge
            switch (tip.getCallAmbulance()) {
                case "always":
                    tvAmbulanceBadge.setVisibility(View.VISIBLE);
                    tvAmbulanceBadge.setText("🚑 Call ambulance");
                    tvAmbulanceBadge.setBackgroundResource(R.drawable.badge_ambulance_always);
                    break;
                case "if_worsens":
                    tvAmbulanceBadge.setVisibility(View.VISIBLE);
                    tvAmbulanceBadge.setText("🚑 If worsens");
                    tvAmbulanceBadge.setBackgroundResource(R.drawable.badge_ambulance_maybe);
                    break;
                default:
                    tvAmbulanceBadge.setVisibility(View.GONE);
                    break;
            }

            // Click opens detail
            itemView.setOnClickListener(v -> listener.onTipClick(tip));
        }
    }
}

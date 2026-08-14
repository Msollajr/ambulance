package com.example.mysignupapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.Serializable;
import java.util.List;

public class FirstAidDetailFragment extends Fragment {

    private static final String ARG_TIP = "first_aid_tip";

    private FirstAid_model tip;

    public FirstAidDetailFragment() {}

    /**
     * Use this factory to create an instance — keeps the tip attached as a Bundle argument
     * so the Fragment survives configuration changes.
     */
    public static FirstAidDetailFragment newInstance(FirstAid_model tip) {
        FirstAidDetailFragment fragment = new FirstAidDetailFragment();
        Bundle args = new Bundle();
        // We pass the tip fields individually because FirstAid_model uses a List<String>
        // which is not directly Parcelable, but individual fields are primitives/Strings.
        args.putString("tip_id",          tip.getId());
        args.putString("tip_title",       tip.getTitle());
        args.putString("tip_category",    tip.getCategory());
        args.putString("tip_short_desc",  tip.getShortDescription());
        args.putString("tip_severity",    tip.getSeverityLevel());
        args.putString("tip_emoji",       tip.getIconEmoji());
        args.putString("tip_ambulance",   tip.getCallAmbulance());
        // Convert lists to arrays for Bundle
        if (tip.getSteps() != null) {
            args.putStringArray("tip_steps",  tip.getSteps().toArray(new String[0]));
        }
        if (tip.getDoNots() != null) {
            args.putStringArray("tip_donots", tip.getDoNots().toArray(new String[0]));
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tip = new FirstAid_model();
            tip.setId(getArguments().getString("tip_id"));
            tip.setTitle(getArguments().getString("tip_title"));
            tip.setCategory(getArguments().getString("tip_category"));
            tip.setShortDescription(getArguments().getString("tip_short_desc"));
            tip.setSeverityLevel(getArguments().getString("tip_severity"));
            tip.setIconEmoji(getArguments().getString("tip_emoji"));
            tip.setCallAmbulance(getArguments().getString("tip_ambulance"));

            String[] stepsArray  = getArguments().getStringArray("tip_steps");
            String[] donotsArray = getArguments().getStringArray("tip_donots");
            if (stepsArray  != null) tip.setSteps(java.util.Arrays.asList(stepsArray));
            if (donotsArray != null) tip.setDoNots(java.util.Arrays.asList(donotsArray));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.firstaid_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (tip == null) return;

        // ── Header ────────────────────────────────────────────────────────────
        TextView tvEmoji         = view.findViewById(R.id.tv_detail_emoji);
        TextView tvTitle         = view.findViewById(R.id.tv_detail_title);
        TextView tvCategory      = view.findViewById(R.id.tv_detail_category);
        TextView tvShortDesc     = view.findViewById(R.id.tv_detail_short_desc);
        TextView tvSeverityBadge = view.findViewById(R.id.tv_detail_severity_badge);
        View     headerStripe    = view.findViewById(R.id.detail_header_stripe);

        tvEmoji.setText(tip.getIconEmoji());
        tvTitle.setText(tip.getTitle());
        tvCategory.setText(tip.getCategory());
        tvShortDesc.setText(tip.getShortDescription());

        // Severity colour theming
        applySeverityTheme(tvSeverityBadge, headerStripe, tip.getSeverityLevel());

        // ── Steps ─────────────────────────────────────────────────────────────
        LinearLayout stepsContainer = view.findViewById(R.id.steps_container);
        buildSteps(stepsContainer, tip.getSteps());

        // ── Do NOTs ───────────────────────────────────────────────────────────
        LinearLayout donotsContainer  = view.findViewById(R.id.donots_container);
        View         donotsSection    = view.findViewById(R.id.donots_section);
        if (tip.getDoNots() != null && !tip.getDoNots().isEmpty()) {
            donotsSection.setVisibility(View.VISIBLE);
            buildDoNots(donotsContainer, tip.getDoNots());
        } else {
            donotsSection.setVisibility(View.GONE);
        }

        // ── Ambulance call-to-action ──────────────────────────────────────────
        View   ambulanceSection = view.findViewById(R.id.ambulance_cta_section);
        Button btnCallAmbulance = view.findViewById(R.id.btn_call_ambulance);
        TextView tvAmbulanceNote = view.findViewById(R.id.tv_ambulance_note);

        switch (tip.getCallAmbulance()) {
            case "always":
                ambulanceSection.setVisibility(View.VISIBLE);
                btnCallAmbulance.setText("🚑  Call Ambulance Now");
                btnCallAmbulance.setBackgroundResource(R.drawable.btn_emergency_red);
                tvAmbulanceNote.setText("This condition requires emergency medical services. Call immediately while administering first aid.");
                break;
            case "if_worsens":
                ambulanceSection.setVisibility(View.VISIBLE);
                btnCallAmbulance.setText("🚑  Call Ambulance");
                btnCallAmbulance.setBackgroundResource(R.drawable.btn_emergency_orange);
                tvAmbulanceNote.setText("Call an ambulance if symptoms worsen or do not improve within the expected time.");
                break;
            default:
                ambulanceSection.setVisibility(View.GONE);
                break;
        }

        // Deep-link to the app's MapsFragment so the user can request an ambulance
        btnCallAmbulance.setOnClickListener(v -> {
            // Navigate back to the maps fragment to initiate an ambulance request
            requireActivity().getSupportFragmentManager().popBackStack();
            // Post a small delay so the back-stack animation finishes before launching
            view.postDelayed(() -> {
                if (getActivity() instanceof Home) {
                    Home homeActivity = (Home) getActivity();
                    homeActivity.openMapsFragment();
                }
            }, 300);
        });

        // ── Back button ───────────────────────────────────────────────────────
        view.findViewById(R.id.btn_back_to_list).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper — build numbered step rows
    // ─────────────────────────────────────────────────────────────────────────
    private void buildSteps(LinearLayout container, List<String> steps) {
        if (steps == null || steps.isEmpty()) return;
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (int i = 0; i < steps.size(); i++) {
            View stepRow = inflater.inflate(R.layout.firstaid_step_row, container, false);

            TextView tvNumber = stepRow.findViewById(R.id.tv_step_number);
            TextView tvText   = stepRow.findViewById(R.id.tv_step_text);

            tvNumber.setText(String.valueOf(i + 1));
            tvText.setText(steps.get(i));

            container.addView(stepRow);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper — build do-not rows
    // ─────────────────────────────────────────────────────────────────────────
    private void buildDoNots(LinearLayout container, List<String> doNots) {
        if (doNots == null || doNots.isEmpty()) return;
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (String donot : doNots) {
            View row = inflater.inflate(R.layout.firstaid_donot_row, container, false);
            TextView tvText = row.findViewById(R.id.tv_donot_text);
            tvText.setText(donot);
            container.addView(row);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper — severity colours
    // ─────────────────────────────────────────────────────────────────────────
    private void applySeverityTheme(TextView badge, View stripe, String severity) {
        switch (severity) {
            case "critical":
                badge.setText("⚠ CRITICAL");
                badge.setBackgroundResource(R.drawable.badge_critical);
                stripe.setBackgroundResource(R.color.severity_critical);
                break;
            case "high":
                badge.setText("▲ HIGH");
                badge.setBackgroundResource(R.drawable.badge_high);
                stripe.setBackgroundResource(R.color.severity_high);
                break;
            case "medium":
                badge.setText("◆ MEDIUM");
                badge.setBackgroundResource(R.drawable.badge_medium);
                stripe.setBackgroundResource(R.color.severity_medium);
                break;
            default:
                badge.setText("● LOW");
                badge.setBackgroundResource(R.drawable.badge_low);
                stripe.setBackgroundResource(R.color.severity_low);
                break;
        }
    }
}

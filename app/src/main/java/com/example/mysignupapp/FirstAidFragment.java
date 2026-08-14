package com.example.mysignupapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

public class FirstAidFragment extends Fragment implements FirstAidAdapter.OnTipClickListener {

    private FirstAidAdapter adapter;
    private EditText etSearch;
    private ChipGroup chipGroup;
    private View tvNoResults;          // ← was TextView, but the XML element is a LinearLayout

    private String activeCategory = "All";
    private String activeQuery    = "";

    public FirstAidFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_first_aid, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etSearch    = view.findViewById(R.id.et_search_tips);
        chipGroup   = view.findViewById(R.id.chip_group_categories);
        tvNoResults = view.findViewById(R.id.tv_no_results);
        RecyclerView recyclerView = view.findViewById(R.id.rv_first_aid_tips);

        // ── RecyclerView setup ────────────────────────────────────────────────
        List<FirstAid_model> allTips = FirstAidData.getAllTips();
        adapter = new FirstAidAdapter(requireContext(), allTips, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(false);

        // ── Category chips ────────────────────────────────────────────────────
        List<String> categories = FirstAidData.getCategories();
        for (String cat : categories) {
            Chip chip = new Chip(requireContext());
            chip.setText(cat);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setTextSize(13f);
            chip.setChipStrokeWidth(1.5f);

            // Style checked vs unchecked using direct color values
            // (avoids dependency on res/color/ selector files)
            chip.setChipBackgroundColorResource(android.R.color.white);
            chip.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
            chip.setChipStrokeColor(
                    android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#CCCCCC")));

            if (cat.equals("All")) {
                chip.setChecked(true);
                chip.setChipBackgroundColor(
                        android.content.res.ColorStateList.valueOf(
                                android.graphics.Color.parseColor("#3B608C")));
                chip.setTextColor(android.graphics.Color.WHITE);
            }

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    chip.setChipBackgroundColor(
                            android.content.res.ColorStateList.valueOf(
                                    android.graphics.Color.parseColor("#3B608C")));
                    chip.setTextColor(android.graphics.Color.WHITE);
                    chip.setChipStrokeColor(
                            android.content.res.ColorStateList.valueOf(
                                    android.graphics.Color.parseColor("#3B608C")));
                    activeCategory = chip.getText().toString();
                    applyFilters();
                } else {
                    chip.setChipBackgroundColor(
                            android.content.res.ColorStateList.valueOf(
                                    android.graphics.Color.WHITE));
                    chip.setTextColor(android.graphics.Color.parseColor("#756C6C"));
                    chip.setChipStrokeColor(
                            android.content.res.ColorStateList.valueOf(
                                    android.graphics.Color.parseColor("#CCCCCC")));
                }
            });

            chipGroup.addView(chip);
        }

        chipGroup.setSingleSelection(true);

        // ── Search ────────────────────────────────────────────────────────────
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                activeQuery = s.toString();
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void applyFilters() {
        adapter.filterByCategoryAndQuery(activeCategory, activeQuery);
        tvNoResults.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    // ── Navigation to detail ──────────────────────────────────────────────────
    @Override
    public void onTipClick(FirstAid_model tip) {
        FirstAidDetailFragment detailFragment = FirstAidDetailFragment.newInstance(tip);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        android.R.anim.slide_in_left,
                        android.R.anim.fade_out,
                        android.R.anim.fade_in,
                        android.R.anim.slide_out_right
                )
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack("first_aid_detail")
                .commit();
    }
}
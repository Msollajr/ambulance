package com.example.mysignupapp;

import java.util.List;

public class FirstAid_model {

    private String id;
    private String title;
    private String category;
    private String shortDescription;
    private String severityLevel;   // "critical", "high", "medium", "low"
    private String iconEmoji;
    private List<String> steps;
    private List<String> doNots;
    private String callAmbulance;   // "always", "if_worsens", "rarely"

    // Required empty constructor for Firebase
    public FirstAid_model() {}

    public FirstAid_model(String id, String title, String category,
                          String shortDescription, String severityLevel,
                          String iconEmoji, List<String> steps,
                          List<String> doNots, String callAmbulance) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.shortDescription = shortDescription;
        this.severityLevel = severityLevel;
        this.iconEmoji = iconEmoji;
        this.steps = steps;
        this.doNots = doNots;
        this.callAmbulance = callAmbulance;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }

    public String getSeverityLevel() { return severityLevel; }
    public void setSeverityLevel(String severityLevel) { this.severityLevel = severityLevel; }

    public String getIconEmoji() { return iconEmoji; }
    public void setIconEmoji(String iconEmoji) { this.iconEmoji = iconEmoji; }

    public List<String> getSteps() { return steps; }
    public void setSteps(List<String> steps) { this.steps = steps; }

    public List<String> getDoNots() { return doNots; }
    public void setDoNots(List<String> doNots) { this.doNots = doNots; }

    public String getCallAmbulance() { return callAmbulance; }
    public void setCallAmbulance(String callAmbulance) { this.callAmbulance = callAmbulance; }
}

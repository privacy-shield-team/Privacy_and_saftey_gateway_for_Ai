package com.aiprivacy.shield.model;

public class SensitiveFinding {
    private SensitivityCategory category;
    private int start;
    private int end;
    private String matchedText;
    private String sanitizedText;
    private RiskLevel severity;
    private boolean blocking;

    public SensitiveFinding() {}

    public SensitiveFinding(SensitivityCategory category, int start, int end, String matchedText, 
                            String sanitizedText, RiskLevel severity, boolean blocking) {
        this.category = category;
        this.start = start;
        this.end = end;
        this.matchedText = matchedText;
        this.sanitizedText = sanitizedText;
        this.severity = severity;
        this.blocking = blocking;
    }

    public SensitivityCategory getCategory() { return category; }
    public void setCategory(SensitivityCategory category) { this.category = category; }

    public int getStart() { return start; }
    public void setStart(int start) { this.start = start; }

    public int getEnd() { return end; }
    public void setEnd(int end) { this.end = end; }

    public String getMatchedText() { return matchedText; }
    public void setMatchedText(String matchedText) { this.matchedText = matchedText; }

    public String getSanitizedText() { return sanitizedText; }
    public void setSanitizedText(String sanitizedText) { this.sanitizedText = sanitizedText; }

    public RiskLevel getSeverity() { return severity; }
    public void setSeverity(RiskLevel severity) { this.severity = severity; }

    public boolean isBlocking() { return blocking; }
    public void setBlocking(boolean blocking) { this.blocking = blocking; }
}

package com.aiprivacy.shield.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private String policyName;

    @Column(nullable = false)
    private String action; // BLOCKED, SANITIZED, PASSED, OVERRIDDEN

    @Column(nullable = false)
    private String riskLevel;

    private int detectionCount;

    @Column(length = 1000)
    private String detectedCategories;

    @Column(length = 1000)
    private String injectionFlags;

    private String strategyApplied;

    private String modelUsed;

    private long latencyMs;

    public AuditEvent() {}

    public AuditEvent(String policyName, String action, String riskLevel, int detectionCount,
                      String detectedCategories, String injectionFlags, String strategyApplied,
                      String modelUsed, long latencyMs) {
        this.timestamp = LocalDateTime.now();
        this.policyName = policyName;
        this.action = action;
        this.riskLevel = riskLevel;
        this.detectionCount = detectionCount;
        this.detectedCategories = detectedCategories;
        this.injectionFlags = injectionFlags;
        this.strategyApplied = strategyApplied;
        this.modelUsed = modelUsed;
        this.latencyMs = latencyMs;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public int getDetectionCount() { return detectionCount; }
    public void setDetectionCount(int detectionCount) { this.detectionCount = detectionCount; }

    public String getDetectedCategories() { return detectedCategories; }
    public void setDetectedCategories(String detectedCategories) { this.detectedCategories = detectedCategories; }

    public String getInjectionFlags() { return injectionFlags; }
    public void setInjectionFlags(String injectionFlags) { this.injectionFlags = injectionFlags; }

    public String getStrategyApplied() { return strategyApplied; }
    public void setStrategyApplied(String strategyApplied) { this.strategyApplied = strategyApplied; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
}

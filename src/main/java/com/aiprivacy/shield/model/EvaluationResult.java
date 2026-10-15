package com.aiprivacy.shield.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvaluationResult {
    private String originalPrompt;
    private String protectedPrompt;
    private List<SensitiveFinding> findings = new ArrayList<>();
    private List<String> injectionFlags = new ArrayList<>();
    private RiskLevel riskLevel = RiskLevel.LOW;
    private boolean blocked;
    private String blockReason;
    private Map<String, String> tokenMap = new HashMap<>(); // token -> original value

    public EvaluationResult() {}

    public String getOriginalPrompt() { return originalPrompt; }
    public void setOriginalPrompt(String originalPrompt) { this.originalPrompt = originalPrompt; }

    public String getProtectedPrompt() { return protectedPrompt; }
    public void setProtectedPrompt(String protectedPrompt) { this.protectedPrompt = protectedPrompt; }

    public List<SensitiveFinding> getFindings() { return findings; }
    public void setFindings(List<SensitiveFinding> findings) { this.findings = findings; }

    public List<String> getInjectionFlags() { return injectionFlags; }
    public void setInjectionFlags(List<String> injectionFlags) { this.injectionFlags = injectionFlags; }

    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }

    public Map<String, String> getTokenMap() { return tokenMap; }
    public void setTokenMap(Map<String, String> tokenMap) { this.tokenMap = tokenMap; }
}

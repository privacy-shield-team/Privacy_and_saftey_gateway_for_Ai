package com.aiprivacy.shield.model;

import java.util.List;
import java.util.Map;

public class GatewayResponse {
    private boolean success;
    private boolean blocked;
    private String blockReason;
    private RiskLevel riskLevel;
    private String actionTaken; // BLOCKED, SANITIZED, PASSED_UNMODIFIED
    private List<SensitiveFinding> detections;
    private List<String> injectionFlags;
    private String originalPrompt;
    private String protectedPrompt;
    private String modelAnswer;
    private String providerUsed;
    private Map<String, String> tokenMapping;
    private String userMessage;

    public GatewayResponse() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }

    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }

    public List<SensitiveFinding> getDetections() { return detections; }
    public void setDetections(List<SensitiveFinding> detections) { this.detections = detections; }

    public List<String> getInjectionFlags() { return injectionFlags; }
    public void setInjectionFlags(List<String> injectionFlags) { this.injectionFlags = injectionFlags; }

    public String getOriginalPrompt() { return originalPrompt; }
    public void setOriginalPrompt(String originalPrompt) { this.originalPrompt = originalPrompt; }

    public String getProtectedPrompt() { return protectedPrompt; }
    public void setProtectedPrompt(String protectedPrompt) { this.protectedPrompt = protectedPrompt; }

    public String getModelAnswer() { return modelAnswer; }
    public void setModelAnswer(String modelAnswer) { this.modelAnswer = modelAnswer; }

    public String getProviderUsed() { return providerUsed; }
    public void setProviderUsed(String providerUsed) { this.providerUsed = providerUsed; }

    public Map<String, String> getTokenMapping() { return tokenMapping; }
    public void setTokenMapping(Map<String, String> tokenMapping) { this.tokenMapping = tokenMapping; }

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }
}

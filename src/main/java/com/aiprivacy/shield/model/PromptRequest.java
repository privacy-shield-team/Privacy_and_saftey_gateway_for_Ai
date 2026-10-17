package com.aiprivacy.shield.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PromptRequest {
    @NotBlank(message = "Prompt cannot be blank")
    @Size(max = 15000, message = "Prompt exceeds maximum allowed length of 15000 characters")
    private String prompt;

    private String policy = "DEFAULT"; // DEFAULT, STRICT_COMPLIANCE, FINANCIAL_PII, SMART_TOKENIZE, DEVELOPER_PERMISSIVE
    private ProtectionStrategy overrideStrategy;
    private boolean userConfirmedOverride; // Review & Override flag

    private String provider; // auto, mock, openai, gemini

    public PromptRequest() {}

    public PromptRequest(String prompt) {
        this.prompt = prompt;
    }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getPolicy() { return policy; }
    public void setPolicy(String policy) { this.policy = policy; }

    public ProtectionStrategy getOverrideStrategy() { return overrideStrategy; }
    public void setOverrideStrategy(ProtectionStrategy overrideStrategy) { this.overrideStrategy = overrideStrategy; }

    public boolean isUserConfirmedOverride() { return userConfirmedOverride; }
    public void setUserConfirmedOverride(boolean userConfirmedOverride) { this.userConfirmedOverride = userConfirmedOverride; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
}

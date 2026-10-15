package com.aiprivacy.shield.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PrivacyPolicy {
    private String policyName;
    private ProtectionStrategy strategy;
    private Set<SensitivityCategory> enabledCategories;
    private boolean blockOnCredentials;
    private boolean blockOnInjection;
    private boolean strictMode; // Blocks if any PII is detected

    public PrivacyPolicy() {
        this.policyName = "DEFAULT";
        this.strategy = ProtectionStrategy.REDACT;
        this.enabledCategories = new HashSet<>();
        Collections.addAll(this.enabledCategories, SensitivityCategory.values());
        this.blockOnCredentials = true;
        this.blockOnInjection = true;
        this.strictMode = false;
    }

    public static PrivacyPolicy strictPolicy() {
        PrivacyPolicy p = new PrivacyPolicy();
        p.setPolicyName("STRICT_COMPLIANCE");
        p.setStrategy(ProtectionStrategy.REDACT);
        p.setStrictMode(true);
        p.setBlockOnCredentials(true);
        p.setBlockOnInjection(true);
        return p;
    }

    public static PrivacyPolicy financialPolicy() {
        PrivacyPolicy p = new PrivacyPolicy();
        p.setPolicyName("FINANCIAL_PII");
        p.setStrategy(ProtectionStrategy.MASK);
        p.setStrictMode(false);
        p.setBlockOnCredentials(true);
        p.setBlockOnInjection(true);
        return p;
    }

    public static PrivacyPolicy tokenizedPolicy() {
        PrivacyPolicy p = new PrivacyPolicy();
        p.setPolicyName("SMART_TOKENIZE");
        p.setStrategy(ProtectionStrategy.TOKENIZE);
        p.setStrictMode(false);
        p.setBlockOnCredentials(true);
        p.setBlockOnInjection(true);
        return p;
    }

    public static PrivacyPolicy developerPolicy() {
        PrivacyPolicy p = new PrivacyPolicy();
        p.setPolicyName("DEVELOPER_PERMISSIVE");
        p.setStrategy(ProtectionStrategy.MASK);
        p.setStrictMode(false);
        p.setBlockOnCredentials(false);
        p.setBlockOnInjection(true);
        return p;
    }

    // Getters and setters
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }

    public ProtectionStrategy getStrategy() { return strategy; }
    public void setStrategy(ProtectionStrategy strategy) { this.strategy = strategy; }

    public Set<SensitivityCategory> getEnabledCategories() { return enabledCategories; }
    public void setEnabledCategories(Set<SensitivityCategory> enabledCategories) { this.enabledCategories = enabledCategories; }

    public boolean isBlockOnCredentials() { return blockOnCredentials; }
    public void setBlockOnCredentials(boolean blockOnCredentials) { this.blockOnCredentials = blockOnCredentials; }

    public boolean isBlockOnInjection() { return blockOnInjection; }
    public void setBlockOnInjection(boolean blockOnInjection) { this.blockOnInjection = blockOnInjection; }

    public boolean isStrictMode() { return strictMode; }
    public void setStrictMode(boolean strictMode) { this.strictMode = strictMode; }
}

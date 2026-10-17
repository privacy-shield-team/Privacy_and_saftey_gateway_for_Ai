package com.aiprivacy.shield.service;

import com.aiprivacy.shield.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PolicyEngineService {

    private final PiiDetectorService piiDetector;
    private final PromptInjectionDetectorService injectionDetector;
    private final SanitizerService sanitizer;

    private final Map<String, PrivacyPolicy> policies = new HashMap<>();
    private volatile String activePolicyName = "DEFAULT";

    public PolicyEngineService(PiiDetectorService piiDetector,
                               PromptInjectionDetectorService injectionDetector,
                               SanitizerService sanitizer) {
        this.piiDetector = piiDetector;
        this.injectionDetector = injectionDetector;
        this.sanitizer = sanitizer;

        // Register default policies
        registerPolicy(new PrivacyPolicy()); // DEFAULT
        registerPolicy(PrivacyPolicy.strictPolicy());
        registerPolicy(PrivacyPolicy.financialPolicy());
        registerPolicy(PrivacyPolicy.tokenizedPolicy());
        registerPolicy(PrivacyPolicy.developerPolicy());
    }

    public void registerPolicy(PrivacyPolicy policy) {
        policies.put(policy.getPolicyName().toUpperCase(), policy);
    }

    public PrivacyPolicy getPolicy(String policyName) {
        if (policyName == null || policyName.isBlank()) return policies.getOrDefault(activePolicyName, policies.get("DEFAULT"));
        return policies.getOrDefault(policyName.toUpperCase(), policies.getOrDefault(activePolicyName, policies.get("DEFAULT")));
    }

    public String getActivePolicyName() {
        return activePolicyName;
    }

    public void setActivePolicyName(String policyName) {
        if (policyName != null && policies.containsKey(policyName.toUpperCase())) {
            this.activePolicyName = policyName.toUpperCase();
        }
    }

    public PrivacyPolicy updatePolicy(String name, PrivacyPolicy updated) {
        if (name == null || !policies.containsKey(name.toUpperCase())) {
            return null;
        }
        PrivacyPolicy existing = policies.get(name.toUpperCase());
        if (updated.getStrategy() != null) existing.setStrategy(updated.getStrategy());
        if (updated.getEnabledCategories() != null) existing.setEnabledCategories(updated.getEnabledCategories());
        existing.setBlockOnCredentials(updated.isBlockOnCredentials());
        existing.setBlockOnInjection(updated.isBlockOnInjection());
        existing.setStrictMode(updated.isStrictMode());
        return existing;
    }

    public Map<String, PrivacyPolicy> getAllPolicies() {
        return Collections.unmodifiableMap(policies);
    }

    public EvaluationResult evaluatePrompt(PromptRequest request) {
        PrivacyPolicy policy = getPolicy(request.getPolicy());
        ProtectionStrategy strategy = (request.getOverrideStrategy() != null)
                ? request.getOverrideStrategy()
                : policy.getStrategy();

        String rawPrompt = request.getPrompt();

        // 1. Detect PII / Sensitive data
        List<SensitiveFinding> findings = piiDetector.detect(rawPrompt, policy.getEnabledCategories());

        // 2. Detect Prompt Injections
        List<String> injectionFlags = injectionDetector.detectInjections(rawPrompt);

        // 3. Compute Risk Level
        RiskLevel risk = RiskLevel.LOW;
        if (!injectionFlags.isEmpty()) {
            risk = RiskLevel.CRITICAL;
        } else if (findings.stream().anyMatch(f -> f.getSeverity() == RiskLevel.HIGH)) {
            risk = RiskLevel.HIGH;
        } else if (!findings.isEmpty()) {
            risk = RiskLevel.MEDIUM;
        }

        // 4. Determine Blocking Conditions
        boolean shouldBlock = false;
        StringBuilder blockReason = new StringBuilder();

        if (policy.isBlockOnInjection() && !injectionFlags.isEmpty()) {
            shouldBlock = true;
            blockReason.append("Suspicious prompt injection attack pattern detected. ");
        }

        if (policy.isBlockOnCredentials() && findings.stream().anyMatch(SensitiveFinding::isBlocking)) {
            shouldBlock = true;
            blockReason.append("Contains raw API keys, credentials, or high-risk authentication tokens. ");
        }

        if (policy.isStrictMode() && !findings.isEmpty()) {
            shouldBlock = true;
            blockReason.append("Strict Compliance mode enabled: prompts containing any sensitive data are blocked. ");
        }

        // 5. Apply Sanitization / Protection
        SanitizerService.SanitizationResult sanitization = sanitizer.applyProtection(rawPrompt, findings, strategy);

        EvaluationResult result = new EvaluationResult();
        result.setOriginalPrompt(rawPrompt);
        result.setProtectedPrompt(sanitization.getSanitizedText());
        result.setFindings(findings);
        result.setInjectionFlags(injectionFlags);
        result.setRiskLevel(risk);
        result.setBlocked(shouldBlock);
        result.setBlockReason(blockReason.toString().trim());
        result.setTokenMap(sanitization.getTokenMap());

        return result;
    }
}

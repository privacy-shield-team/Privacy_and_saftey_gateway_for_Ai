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
        if (policyName == null) return policies.get("DEFAULT");
        return policies.getOrDefault(policyName.toUpperCase(), policies.get("DEFAULT"));
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

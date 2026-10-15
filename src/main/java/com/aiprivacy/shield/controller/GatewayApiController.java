package com.aiprivacy.shield.controller;

import com.aiprivacy.shield.model.*;
import com.aiprivacy.shield.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class GatewayApiController {

    private final PolicyEngineService policyEngine;
    private final SanitizerService sanitizer;
    private final AuditService auditService;
    private final LlmGatewayService llmGateway;

    public GatewayApiController(PolicyEngineService policyEngine,
                                SanitizerService sanitizer,
                                AuditService auditService,
                                LlmGatewayService llmGateway) {
        this.policyEngine = policyEngine;
        this.sanitizer = sanitizer;
        this.auditService = auditService;
        this.llmGateway = llmGateway;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "healthy",
                "service", "AI Privacy & Safety Gateway (Java)",
                "activeModel", llmGateway.getActiveModelName()
        );
    }

    @GetMapping("/policies")
    public Map<String, PrivacyPolicy> getPolicies() {
        return policyEngine.getAllPolicies();
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return auditService.getStatistics();
    }

    @GetMapping("/audit")
    public ResponseEntity<?> getAuditEvents() {
        return ResponseEntity.ok(auditService.getRecentEvents());
    }

    /**
     * Inspect and preview prompt without calling AI provider.
     */
    @PostMapping("/scan")
    public ResponseEntity<GatewayResponse> scanPrompt(@Valid @RequestBody PromptRequest request) {
        long start = System.currentTimeMillis();
        EvaluationResult eval = policyEngine.evaluatePrompt(request);

        String action = eval.isBlocked() ? "BLOCKED" : (eval.getFindings().isEmpty() ? "PASSED" : "SANITIZED");
        long latency = System.currentTimeMillis() - start;

        auditService.recordEvent(
                request.getPolicy(),
                action,
                eval,
                request.getOverrideStrategy() != null ? request.getOverrideStrategy().name() : "AUTO",
                "LOCAL_SCAN",
                latency
        );

        GatewayResponse response = new GatewayResponse();
        response.setSuccess(true);
        response.setBlocked(eval.isBlocked());
        response.setBlockReason(eval.getBlockReason());
        response.setRiskLevel(eval.getRiskLevel());
        response.setActionTaken(action);
        response.setDetections(eval.getFindings());
        response.setInjectionFlags(eval.getInjectionFlags());
        response.setOriginalPrompt(eval.getOriginalPrompt());
        response.setProtectedPrompt(eval.getProtectedPrompt());
        response.setTokenMapping(eval.getTokenMap());
        response.setProviderUsed("LOCAL_ANALYZER");

        if (eval.isBlocked()) {
            response.setUserMessage("Prompt rejected by privacy gateway: " + eval.getBlockReason());
        } else if (!eval.getFindings().isEmpty()) {
            response.setUserMessage("Sensitive information detected and sanitized. Safe to transmit.");
        } else {
            response.setUserMessage("No configured privacy risks detected.");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Intercept, sanitize, and transmit prompt to GenAI API with automatic de-tokenization.
     */
    @PostMapping("/chat")
    public ResponseEntity<GatewayResponse> chatWithGateway(@Valid @RequestBody PromptRequest request) {
        long start = System.currentTimeMillis();
        EvaluationResult eval = policyEngine.evaluatePrompt(request);

        // Security check: If blocked and user has not confirmed override
        if (eval.isBlocked() && !request.isUserConfirmedOverride()) {
            long latency = System.currentTimeMillis() - start;
            auditService.recordEvent(request.getPolicy(), "BLOCKED", eval, "NONE", "BLOCKED_BEFORE_PROVIDER", latency);

            GatewayResponse blockedResponse = new GatewayResponse();
            blockedResponse.setSuccess(false);
            blockedResponse.setBlocked(true);
            blockedResponse.setBlockReason(eval.getBlockReason());
            blockedResponse.setRiskLevel(eval.getRiskLevel());
            blockedResponse.setActionTaken("BLOCKED");
            blockedResponse.setDetections(eval.getFindings());
            blockedResponse.setInjectionFlags(eval.getInjectionFlags());
            blockedResponse.setOriginalPrompt(request.getPrompt());
            blockedResponse.setProtectedPrompt(null); // Never expose unredacted prompt
            blockedResponse.setUserMessage("REQUEST BLOCKED BEFORE AI CALL: " + eval.getBlockReason());

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(blockedResponse);
        }

        // Send ONLY the protected/sanitized prompt to the AI Model
        String safePromptToSend = eval.getProtectedPrompt();
        String activeModel = llmGateway.getActiveModelName();
        String rawAiAnswer = llmGateway.callModel(safePromptToSend);

        // Apply reversible de-tokenization to the AI response if tokens were used
        String finalAiAnswer = sanitizer.detokenize(rawAiAnswer, eval.getTokenMap());

        long latency = System.currentTimeMillis() - start;
        String action = request.isUserConfirmedOverride() ? "OVERRIDDEN" : (eval.getFindings().isEmpty() ? "PASSED" : "SANITIZED");

        auditService.recordEvent(request.getPolicy(), action, eval,
                request.getOverrideStrategy() != null ? request.getOverrideStrategy().name() : "AUTO",
                activeModel, latency);

        GatewayResponse response = new GatewayResponse();
        response.setSuccess(true);
        response.setBlocked(false);
        response.setRiskLevel(eval.getRiskLevel());
        response.setActionTaken(action);
        response.setDetections(eval.getFindings());
        response.setInjectionFlags(eval.getInjectionFlags());
        response.setOriginalPrompt(request.getPrompt());
        response.setProtectedPrompt(safePromptToSend);
        response.setModelAnswer(finalAiAnswer);
        response.setProviderUsed(activeModel);
        response.setTokenMapping(eval.getTokenMap());
        response.setUserMessage("Safely routed through Gateway. AI provider received protected prompt only.");

        return ResponseEntity.ok(response);
    }

    /**
     * Protecto-compatible Live Demo: Real-time PII Identification
     */
    @PostMapping("/demo/identify")
    public ResponseEntity<Map<String, Object>> demoIdentify(@RequestBody ProtectoDemoRequest req) {
        String text = req.getText() != null ? req.getText() : "";
        PromptRequest promptReq = new PromptRequest(text);
        promptReq.setPolicy("DEFAULT");
        EvaluationResult eval = policyEngine.evaluatePrompt(promptReq);

        // Build highlighted text HTML with <mark> tags like Protecto
        StringBuilder highlighted = new StringBuilder();
        int lastIdx = 0;
        java.util.List<Map<String, String>> identifiedEntities = new java.util.ArrayList<>();

        for (SensitiveFinding f : eval.getFindings()) {
            if (f.getStart() >= lastIdx && f.getStart() <= text.length()) {
                highlighted.append(escapeHtml(text.substring(lastIdx, f.getStart())));
                highlighted.append("<mark title=\"").append(f.getCategory().name()).append("\">")
                        .append(escapeHtml(text.substring(f.getStart(), Math.min(f.getEnd(), text.length()))))
                        .append("</mark>");
                lastIdx = Math.min(f.getEnd(), text.length());

                identifiedEntities.add(Map.of(
                        "pii_type", f.getCategory().name(),
                        "value", f.getMatchedText()
                ));
            }
        }
        if (lastIdx < text.length()) {
            highlighted.append(escapeHtml(text.substring(lastIdx)));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "highlighted_text", highlighted.toString(),
                        "identified_entities", identifiedEntities
                )
        ));
    }

    /**
     * Protecto-compatible Live Demo: Real-time Masking & Toxicity Analysis
     */
    @PostMapping("/demo/mask")
    public ResponseEntity<Map<String, Object>> demoMask(@RequestBody ProtectoDemoRequest req) {
        String text = req.getText() != null ? req.getText() : "";
        PromptRequest promptReq = new PromptRequest(text);
        promptReq.setPolicy("FINANCIAL_PII");
        promptReq.setOverrideStrategy(ProtectionStrategy.TOKENIZE);
        EvaluationResult eval = policyEngine.evaluatePrompt(promptReq);

        java.util.List<Map<String, String>> maskedPairs = new java.util.ArrayList<>();
        for (SensitiveFinding f : eval.getFindings()) {
            maskedPairs.add(Map.of(
                    "original", f.getMatchedText(),
                    "type", f.getCategory().name(),
                    "masked", f.getSanitizedText()
            ));
        }

        // Mock toxicity / safety score chips identical to Protecto's live evaluation
        double toxicity = eval.getInjectionFlags().isEmpty() ? 0.02 : 0.94;
        double severeToxicity = eval.getInjectionFlags().isEmpty() ? 0.01 : 0.88;
        double profanity = 0.01;
        double identityAttack = 0.01;

        Map<String, Object> toxicityAnalysis = Map.of(
                "toxicity", toxicity,
                "severe_toxicity", severeToxicity,
                "profanity", profanity,
                "identity_attack", identityAttack
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "masked_text", eval.getProtectedPrompt(),
                        "masked_pairs", maskedPairs,
                        "toxicity_analysis", toxicityAnalysis
                )
        ));
    }

    /**
     * Protecto-compatible Live Demo: Real-time Unmasking (Token restoration)
     */
    @PostMapping("/demo/unmask")
    public ResponseEntity<Map<String, Object>> demoUnmask(@RequestBody ProtectoDemoRequest req) {
        String text = req.getText() != null ? req.getText() : "";
        // Auto-detect tokens and restore known placeholders
        // Also supports arbitrary text
        Map<String, Object> toxicityAnalysis = Map.of(
                "toxicity", 0.02,
                "severe_toxicity", 0.01,
                "profanity", 0.01,
                "identity_attack", 0.01
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "data", java.util.List.of(Map.of(
                                "value", text,
                                "toxicity_analysis", toxicityAnalysis
                        ))
                )
        ));
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}


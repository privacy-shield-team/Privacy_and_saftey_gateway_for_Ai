package com.aiprivacy.shield.service;

import com.aiprivacy.shield.model.ProtectionStrategy;
import com.aiprivacy.shield.model.SensitivityCategory;
import com.aiprivacy.shield.model.SensitiveFinding;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SanitizerService {

    public static class SanitizationResult {
        private final String sanitizedText;
        private final Map<String, String> tokenMap; // token -> original value

        public SanitizationResult(String sanitizedText, Map<String, String> tokenMap) {
            this.sanitizedText = sanitizedText;
            this.tokenMap = tokenMap;
        }

        public String getSanitizedText() { return sanitizedText; }
        public Map<String, String> getTokenMap() { return tokenMap; }
    }

    public SanitizationResult applyProtection(String originalText, List<SensitiveFinding> findings, ProtectionStrategy strategy) {
        if (findings == null || findings.isEmpty()) {
            return new SanitizationResult(originalText, Collections.emptyMap());
        }

        // Work in reverse order of index to preserve character offsets
        List<SensitiveFinding> sortedFindings = new ArrayList<>(findings);
        sortedFindings.sort((a, b) -> Integer.compare(b.getStart(), a.getStart()));

        StringBuilder sb = new StringBuilder(originalText);
        Map<String, String> tokenMap = new HashMap<>();
        int tokenCounter = 1;

        for (SensitiveFinding finding : sortedFindings) {
            String replacement;
            String originalVal = finding.getMatchedText();

            switch (strategy) {
                case MASK -> replacement = generateMask(finding.getCategory(), originalVal);
                case TOKENIZE -> {
                    String token = "__TOKEN_" + finding.getCategory().name() + "_" + (tokenCounter++) + "__";
                    replacement = token;
                    tokenMap.put(token, originalVal);
                }
                case REDACT -> replacement = "[" + finding.getCategory().name() + "_REDACTED]";
                default -> replacement = "[REDACTED]";
            }

            finding.setSanitizedText(replacement);
            sb.replace(finding.getStart(), finding.getEnd(), replacement);
        }

        return new SanitizationResult(sb.toString(), tokenMap);
    }

    // Reversible de-tokenization: replaces tokens in AI response with original values
    public String detokenize(String aiResponse, Map<String, String> tokenMap) {
        if (aiResponse == null || tokenMap == null || tokenMap.isEmpty()) {
            return aiResponse;
        }

        String result = aiResponse;
        for (Map.Entry<String, String> entry : tokenMap.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String generateMask(SensitivityCategory category, String value) {
        if (value == null || value.length() <= 2) {
            return "***";
        }

        return switch (category) {
            case EMAIL -> {
                int atIdx = value.indexOf('@');
                if (atIdx > 2) {
                    yield value.substring(0, 2) + "****" + value.substring(atIdx - 1);
                }
                yield "e****@domain.com";
            }
            case PHONE -> {
                int len = value.length();
                yield "****-***-" + value.substring(Math.max(0, len - 4));
            }
            case CREDIT_CARD -> {
                String clean = value.replaceAll("[-\\s]", "");
                int len = clean.length();
                yield "****-****-****-" + clean.substring(Math.max(0, len - 4));
            }
            case GOVT_ID -> {
                int len = value.length();
                yield "****-****-" + value.substring(Math.max(0, len - 4));
            }
            default -> {
                int keep = Math.min(2, value.length() / 2);
                yield value.substring(0, keep) + "****" + value.substring(value.length() - keep);
            }
        };
    }
}

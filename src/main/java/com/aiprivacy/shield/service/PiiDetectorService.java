package com.aiprivacy.shield.service;

import com.aiprivacy.shield.model.RiskLevel;
import com.aiprivacy.shield.model.SensitivityCategory;
import com.aiprivacy.shield.model.SensitiveFinding;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PiiDetectorService {

    // Regex Patterns for PII / Sensitive Data
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b", Pattern.CASE_INSENSITIVE);

    // International & Indian Phone numbers
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\w)(?:(?:\\+?[0-9]{1,3}[\\s-]?)?(?:\\([0-9]{2,4}\\)|[0-9]{2,4})[\\s-]?[0-9]{3,4}[\\s-]?[0-9]{3,4}|(?:\\+?91[\\s-]?)?[6-9][0-9]{9})(?!\\w)");

    // Credit Card (Visa, Mastercard, Amex, Discover)
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12}|(?:[0-9]{4}[-\\s]){3}[0-9]{4})\\b");

    // US SSN
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b(?!000|666|9\\d{2})\\d{3}-(?!00)\\d{2}-(?!0000)\\d{4}\\b");

    // Indian Aadhaar (12 digits, often formatted as 4-4-4)
    private static final Pattern AADHAAR_PATTERN = Pattern.compile(
            "\\b[2-9][0-9]{3}\\s?[0-9]{4}\\s?[0-9]{4}\\b");

    // Indian PAN (5 letters, 4 digits, 1 letter)
    private static final Pattern PAN_PATTERN = Pattern.compile(
            "\\b[A-Z]{5}[0-9]{4}[A-Z]{1}\\b");

    // API Keys & Tokens (OpenAI sk-, Google AIza, AWS AKIA, Bearer Tokens, JWT)
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "\\b(?:sk-[A-Za-z0-9_-]{20,}|AIza[A-Za-z0-9_-]{20,}|AKIA[0-9A-Z]{16}|ey[A-Za-z0-9-_]{10,}\\.[A-Za-z0-9-_]{10,}\\.[A-Za-z0-9-_]{10,}|ghp_[A-Za-z0-9]{36})\\b");

    // Credentials / Passwords in key-value context (e.g., "password is: abc", "secret = abc", "password: abc")
    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|secret|access[_-]?token|auth[_-]?token)\\s*(?:is\\s*[:=]?|[:=])\\s*([\\S]{4,})");

    private static final List<String> CONFIDENTIAL_KEYWORDS = List.of(
            "internal-only", "do-not-share", "confidential-project", "proprietary", "trade secret",
            "nda protected", "restricted-access", "strictly confidential"
    );

    public List<SensitiveFinding> detect(String text, Set<SensitivityCategory> enabledCategories) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<SensitiveFinding> findings = new ArrayList<>();
        List<int[]> occupiedSpans = new ArrayList<>();

        // 1. Check Passwords / Credentials first (highest specificity)
        if (enabledCategories.contains(SensitivityCategory.CREDENTIAL)) {
            Matcher m = CREDENTIAL_PATTERN.matcher(text);
            while (m.find()) {
                int start = m.start(2);
                int end = m.end(2);
                String matchedVal = m.group(2);
                findings.add(new SensitiveFinding(
                        SensitivityCategory.CREDENTIAL, start, end, matchedVal,
                        "[CREDENTIAL_REDACTED]", RiskLevel.HIGH, true
                ));
                occupiedSpans.add(new int[]{start, end});
            }
        }

        // 2. Check API Keys
        if (enabledCategories.contains(SensitivityCategory.API_KEY)) {
            findWithPattern(text, API_KEY_PATTERN, SensitivityCategory.API_KEY, RiskLevel.HIGH, true, "[API_KEY_REDACTED]", findings, occupiedSpans);
        }

        // 3. Check Credit Cards (with Luhn validation)
        if (enabledCategories.contains(SensitivityCategory.CREDIT_CARD)) {
            Matcher m = CREDIT_CARD_PATTERN.matcher(text);
            while (m.find()) {
                int s = m.start();
                int e = m.end();
                String raw = m.group();
                String digitsOnly = raw.replaceAll("[-\\s]", "");
                if (digitsOnly.length() >= 13 && digitsOnly.length() <= 19 && isValidLuhn(digitsOnly)) {
                    if (!isOverlapping(s, e, occupiedSpans)) {
                        findings.add(new SensitiveFinding(
                                SensitivityCategory.CREDIT_CARD, s, e, raw,
                                "[CREDIT_CARD_REDACTED]", RiskLevel.HIGH, true
                        ));
                        occupiedSpans.add(new int[]{s, e});
                    }
                }
            }
        }

        // 4. Govt Identifiers (Aadhaar, SSN, PAN)
        if (enabledCategories.contains(SensitivityCategory.GOVT_ID)) {
            findWithPattern(text, SSN_PATTERN, SensitivityCategory.GOVT_ID, RiskLevel.HIGH, false, "[SSN_REDACTED]", findings, occupiedSpans);
            findWithPattern(text, PAN_PATTERN, SensitivityCategory.GOVT_ID, RiskLevel.HIGH, false, "[PAN_REDACTED]", findings, occupiedSpans);
            findWithPattern(text, AADHAAR_PATTERN, SensitivityCategory.GOVT_ID, RiskLevel.HIGH, false, "[AADHAAR_REDACTED]", findings, occupiedSpans);
        }

        // 5. Emails
        if (enabledCategories.contains(SensitivityCategory.EMAIL)) {
            findWithPattern(text, EMAIL_PATTERN, SensitivityCategory.EMAIL, RiskLevel.MEDIUM, false, "[EMAIL_REDACTED]", findings, occupiedSpans);
        }

        // 6. Phone Numbers
        if (enabledCategories.contains(SensitivityCategory.PHONE)) {
            findWithPattern(text, PHONE_PATTERN, SensitivityCategory.PHONE, RiskLevel.MEDIUM, false, "[PHONE_REDACTED]", findings, occupiedSpans);
        }

        // 7. Confidential Keywords
        if (enabledCategories.contains(SensitivityCategory.CONFIDENTIAL_KEYWORD)) {
            String lower = text.toLowerCase();
            for (String kw : CONFIDENTIAL_KEYWORDS) {
                int idx = 0;
                while ((idx = lower.indexOf(kw, idx)) != -1) {
                    int end = idx + kw.length();
                    if (!isOverlapping(idx, end, occupiedSpans)) {
                        findings.add(new SensitiveFinding(
                                SensitivityCategory.CONFIDENTIAL_KEYWORD, idx, end, text.substring(idx, end),
                                "[CONFIDENTIAL_REDACTED]", RiskLevel.MEDIUM, false
                        ));
                        occupiedSpans.add(new int[]{idx, end});
                    }
                    idx = end;
                }
            }
        }

        // Sort findings ascending by start position
        findings.sort(Comparator.comparingInt(SensitiveFinding::getStart));
        return findings;
    }

    private void findWithPattern(String text, Pattern pattern, SensitivityCategory category,
                                 RiskLevel severity, boolean blocking, String defaultSanitized,
                                 List<SensitiveFinding> findings, List<int[]> occupiedSpans) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            int s = m.start();
            int e = m.end();
            if (!isOverlapping(s, e, occupiedSpans)) {
                findings.add(new SensitiveFinding(category, s, e, m.group(), defaultSanitized, severity, blocking));
                occupiedSpans.add(new int[]{s, e});
            }
        }
    }

    private boolean isOverlapping(int start, int end, List<int[]> occupied) {
        for (int[] span : occupied) {
            if (start < span[1] && end > span[0]) {
                return true;
            }
        }
        return false;
    }

    // Luhn checksum algorithm for Credit Cards
    private boolean isValidLuhn(String cardNumber) {
        int sum = 0;
        boolean alternate = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(cardNumber.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }
}

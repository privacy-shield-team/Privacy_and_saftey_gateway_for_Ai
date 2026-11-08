package com.aiprivacy.shield.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cloudflare Turnstile CAPTCHA verification service.
 * Secret key is NEVER exposed to the frontend.
 * When TURNSTILE_SECRET_KEY is not set, verification is treated as a "not configured" state.
 */
@Service
public class CaptchaService {

    @Value("${captcha.turnstile.secret-key:}")
    private String secretKey;

    @Value("${captcha.turnstile.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}")
    private String verifyUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isCaptchaConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    /**
     * Verify a Turnstile CAPTCHA token against the Cloudflare API.
     * Returns true if CAPTCHA is not configured (bypass for unconfigured environments).
     * Returns true if Cloudflare confirms the token is valid.
     */
    public boolean verify(String token) {
        if (!isCaptchaConfigured()) {
            // CAPTCHA not configured — allow login (show warning in UI, not silently succeed)
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Map<String, String> body = new HashMap<>();
            body.put("secret", secretKey);
            body.put("response", token);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(verifyUrl, body, Map.class);
            if (response == null) return false;

            Object success = response.get("success");
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            // Network failure or Cloudflare unreachable — deny for safety
            return false;
        }
    }
}

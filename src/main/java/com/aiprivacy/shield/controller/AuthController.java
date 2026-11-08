package com.aiprivacy.shield.controller;

import com.aiprivacy.shield.entity.User;
import com.aiprivacy.shield.model.LoginRequest;
import com.aiprivacy.shield.model.RegisterRequest;
import com.aiprivacy.shield.service.CaptchaService;
import com.aiprivacy.shield.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Authentication controller for login, register, logout, and OAuth.
 * No passwords or secrets are ever returned in responses.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final CaptchaService captchaService;

    @Value("${captcha.turnstile.site-key:}")
    private String turnstileSiteKey;

    @Value("${google.oauth.client-id:}")
    private String googleClientId;

    @Value("${google.oauth.client-secret:}")
    private String googleClientSecret;

    @Value("${google.oauth.redirect-uri:http://localhost:8080/auth/oauth2/callback}")
    private String googleRedirectUri;

    public AuthController(UserService userService, CaptchaService captchaService) {
        this.userService = userService;
        this.captchaService = captchaService;
    }

    // ─── Auth Config Endpoint ──────────────────────────────────────────────────

    /**
     * Returns public configuration for the login page (site key, feature flags).
     * Never returns secret keys.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getAuthConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("captchaConfigured", captchaService.isCaptchaConfigured());
        config.put("captchaSiteKey", captchaService.isCaptchaConfigured() ? turnstileSiteKey : null);
        config.put("googleOAuthConfigured", googleClientId != null && !googleClientId.isBlank());
        return ResponseEntity.ok(config);
    }

    // ─── Session Check ─────────────────────────────────────────────────────────

    /**
     * Returns the currently authenticated user's public info.
     * Returns 401 if not authenticated.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpSession session) {
        User user = (User) session.getAttribute("authenticatedUser");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }
        return ResponseEntity.ok(buildUserResponse(user));
    }

    // ─── Register ──────────────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest req,
            HttpServletRequest httpReq) {

        // Validate password match
        if (!req.getPassword().equals(req.getConfirmPassword())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Passwords do not match"));
        }

        // CAPTCHA verification (if configured)
        if (captchaService.isCaptchaConfigured()) {
            if (!captchaService.verify(req.getCaptchaToken())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "CAPTCHA verification failed. Please try again."));
            }
        }

        // Check duplicate email
        if (userService.emailExists(req.getEmail().toLowerCase().trim())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "An account with this email already exists."));
        }

        User created = userService.register(
                req.getName().trim(),
                req.getEmail().toLowerCase().trim(),
                req.getPassword()
        );

        if (created == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Registration failed. Email may already be in use."));
        }

        // Auto-login after registration
        HttpSession session = httpReq.getSession(true);
        session.setAttribute("authenticatedUser", created);
        session.setMaxInactiveInterval(3600); // 1 hour

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildUserResponse(created));
    }

    // ─── Login ─────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest httpReq) {

        // CAPTCHA verification (if configured)
        if (captchaService.isCaptchaConfigured()) {
            if (!captchaService.verify(req.getCaptchaToken())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "CAPTCHA verification failed. Please complete the security check."));
            }
        }

        Optional<User> maybeUser = userService.authenticate(
                req.getEmail().toLowerCase().trim(),
                req.getPassword()
        );

        if (maybeUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password."));
        }

        User user = maybeUser.get();

        // Create session
        HttpSession session = httpReq.getSession(true);
        session.setAttribute("authenticatedUser", user);
        int maxAge = req.isRememberMe() ? 7 * 24 * 3600 : 3600; // 7 days or 1 hour
        session.setMaxInactiveInterval(maxAge);

        return ResponseEntity.ok(buildUserResponse(user));
    }

    // ─── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute("authenticatedUser");
            session.invalidate();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully."));
    }

    // ─── Google OAuth ──────────────────────────────────────────────────────────

    /**
     * Initiates Google OAuth flow — redirects to Google's consent screen.
     * Returns 503 if credentials are not configured.
     */
    @GetMapping("/oauth2/google")
    public ResponseEntity<Map<String, Object>> initiateGoogleOAuth(HttpServletRequest request) {
        if (googleClientId == null || googleClientId.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    Map.of("error", "Google OAuth is not configured on this server.",
                            "help", "Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET environment variables.")
            );
        }

        String state = UUID.randomUUID().toString();
        HttpSession session = request.getSession(true);
        session.setAttribute("oauth2State", state);

        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=" + URLEncoder.encode(googleClientId, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(googleRedirectUri, StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode("openid email profile", StandardCharsets.UTF_8) +
                "&state=" + state +
                "&access_type=offline";

        return ResponseEntity.ok(Map.of("redirectUrl", authUrl));
    }

    /**
     * Google OAuth callback — exchanges code for token, creates/logs in user.
     */
    @GetMapping("/oauth2/callback")
    public ResponseEntity<?> handleGoogleOAuthCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        if (error != null) {
            response.sendRedirect("/login.html?error=google_denied");
            return null;
        }

        if (googleClientId == null || googleClientId.isBlank() ||
                googleClientSecret == null || googleClientSecret.isBlank()) {
            response.sendRedirect("/login.html?error=oauth_not_configured");
            return null;
        }

        // Validate CSRF state
        HttpSession session = request.getSession(false);
        String savedState = session != null ? (String) session.getAttribute("oauth2State") : null;
        if (savedState == null || !savedState.equals(state)) {
            response.sendRedirect("/login.html?error=invalid_state");
            return null;
        }
        session.removeAttribute("oauth2State");

        try {
            // Exchange code for access token
            Map<String, Object> tokenResponse = exchangeGoogleCode(code);
            String idToken = (String) tokenResponse.get("id_token");

            // Decode id_token (JWT) to extract user info (without full JOSE library)
            Map<String, Object> claims = decodeJwtPayload(idToken);
            String googleId = (String) claims.get("sub");
            String email = (String) claims.get("email");
            String name = (String) claims.get("name");

            if (googleId == null || email == null) {
                response.sendRedirect("/login.html?error=google_profile");
                return null;
            }

            User user = userService.findOrCreateGoogleUser(googleId, email, name != null ? name : email);

            HttpSession newSession = request.getSession(true);
            newSession.setAttribute("authenticatedUser", user);
            newSession.setMaxInactiveInterval(7 * 24 * 3600);

            response.sendRedirect("/?auth=google");
            return null;

        } catch (Exception e) {
            response.sendRedirect("/login.html?error=google_failed");
            return null;
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> buildUserResponse(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getName());
        map.put("email", user.getEmail());
        map.put("provider", user.getProvider());
        map.put("createdAt", user.getCreatedAt());
        map.put("lastLoginAt", user.getLastLoginAt());
        // Never include passwordHash in any response
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeGoogleCode(String code) {
        org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
        Map<String, String> body = new HashMap<>();
        body.put("code", code);
        body.put("client_id", googleClientId);
        body.put("client_secret", googleClientSecret);
        body.put("redirect_uri", googleRedirectUri);
        body.put("grant_type", "authorization_code");

        return rt.postForObject("https://oauth2.googleapis.com/token", body, Map.class);
    }

    private Map<String, Object> decodeJwtPayload(String jwt) {
        // JWT: header.payload.signature — decode the payload (base64url)
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) return Map.of();
        String payload = parts[1];
        // Base64url decode
        byte[] decoded = Base64.getUrlDecoder().decode(addPadding(payload));
        String json = new String(decoded, StandardCharsets.UTF_8);
        // Simple JSON parse using Jackson (available via Spring Boot)
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String addPadding(String base64url) {
        int pad = base64url.length() % 4;
        if (pad == 2) return base64url + "==";
        if (pad == 3) return base64url + "=";
        return base64url;
    }
}

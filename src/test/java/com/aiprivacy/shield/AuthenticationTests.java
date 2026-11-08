package com.aiprivacy.shield;

import com.aiprivacy.shield.entity.User;
import com.aiprivacy.shield.service.UserService;
import com.aiprivacy.shield.service.CaptchaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Authentication integration tests.
 * Covers: register, login, logout, session protection, bad credentials, protected endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthenticationTests {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired CaptchaService captchaService;
    @Autowired ObjectMapper objectMapper;

    static final String TEST_EMAIL = "auth-test@example.com";
    static final String TEST_NAME  = "Auth Tester";
    static final String TEST_PASS  = "SecurePass123!";

    // ─── Auth Config ───────────────────────────────────────────────────────────

    @Test @Order(1)
    void authConfig_returnsPublicFlags() throws Exception {
        mvc.perform(get("/auth/config"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.captchaConfigured").exists())
           .andExpect(jsonPath("$.googleOAuthConfigured").exists());
    }

    // ─── Registration ──────────────────────────────────────────────────────────

    @Test @Order(2)
    void register_withValidCredentials_creates201() throws Exception {
        // Clean up if exists
        userService.findByEmail(TEST_EMAIL).ifPresent(u -> {
            // Repository not injected here; use userService. No delete exposed, so test is idempotent.
        });

        Map<String, String> body = Map.of(
            "name", TEST_NAME,
            "email", TEST_EMAIL,
            "password", TEST_PASS,
            "confirmPassword", TEST_PASS,
            "captchaToken", ""
        );

        MvcResult result = mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.name").value(TEST_NAME))
                .andReturn();

        // Verify password hash NOT in response
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("passwordHash");
        assertThat(responseBody).doesNotContain(TEST_PASS);
    }

    @Test @Order(3)
    void register_duplicateEmail_returns409() throws Exception {
        Map<String, String> body = Map.of(
            "name", TEST_NAME,
            "email", TEST_EMAIL,
            "password", TEST_PASS,
            "confirmPassword", TEST_PASS,
            "captchaToken", ""
        );

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test @Order(4)
    void register_passwordMismatch_returns400() throws Exception {
        Map<String, String> body = Map.of(
            "name", "Test",
            "email", "mismatch@example.com",
            "password", "Password1!",
            "confirmPassword", "Different1!",
            "captchaToken", ""
        );

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Passwords do not match"));
    }

    // ─── Login ─────────────────────────────────────────────────────────────────

    @Test @Order(5)
    void login_withValidCredentials_returns200() throws Exception {
        Map<String, Object> body = Map.of(
            "email", TEST_EMAIL,
            "password", TEST_PASS,
            "rememberMe", false,
            "captchaToken", ""
        );

        MvcResult result = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                .andReturn();

        // Password must NOT appear in response
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("passwordHash");
        assertThat(responseBody).doesNotContain(TEST_PASS);

        // Session must be active
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute("authenticatedUser")).isNotNull();
    }

    @Test @Order(6)
    void login_withWrongPassword_returns401() throws Exception {
        Map<String, Object> body = Map.of(
            "email", TEST_EMAIL,
            "password", "WrongPassword!",
            "rememberMe", false,
            "captchaToken", ""
        );

        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test @Order(7)
    void login_withNonExistentEmail_returns401() throws Exception {
        Map<String, Object> body = Map.of(
            "email", "nobody@example.com",
            "password", "AnyPass123!",
            "rememberMe", false,
            "captchaToken", ""
        );

        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // ─── Session & Protected Routes ────────────────────────────────────────────

    @Test @Order(8)
    void authMe_withoutSession_returns401() throws Exception {
        mvc.perform(get("/auth/me"))
           .andExpect(status().isUnauthorized());
    }

    @Test @Order(9)
    void authMe_withValidSession_returnsUser() throws Exception {
        Map<String, Object> loginBody = Map.of(
            "email", TEST_EMAIL,
            "password", TEST_PASS,
            "rememberMe", false,
            "captchaToken", ""
        );

        MvcResult loginResult = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody)))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // Use the session to access /auth/me
        mvc.perform(get("/auth/me").session(session))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.email").value(TEST_EMAIL));
    }

    // ─── Logout ────────────────────────────────────────────────────────────────

    @Test @Order(10)
    void logout_invalidatesSession() throws Exception {
        Map<String, Object> loginBody = Map.of(
            "email", TEST_EMAIL,
            "password", TEST_PASS,
            "rememberMe", false,
            "captchaToken", ""
        );

        MvcResult loginResult = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody)))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // Logout
        mvc.perform(post("/auth/logout").session(session))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.success").value(true));

        // Session should now be invalid / invalidated
        mvc.perform(get("/auth/me").session(session))
           .andExpect(status().isUnauthorized());
    }

    // ─── Password Hashing ──────────────────────────────────────────────────────

    @Test @Order(11)
    void passwordIsHashedInDatabase() {
        Optional<User> userOpt = userService.findByEmail(TEST_EMAIL);
        assertThat(userOpt).isPresent();

        User user = userOpt.get();
        // Hash must NOT be the raw password
        assertThat(user.getPasswordHash()).isNotEqualTo(TEST_PASS);
        // Hash must start with BCrypt prefix
        assertThat(user.getPasswordHash()).startsWith("$2");
    }

    // ─── Existing Gateway APIs ─────────────────────────────────────────────────

    @Test @Order(12)
    void gatewayHealth_withSession_returns200() throws Exception {
        Map<String, Object> loginBody = Map.of(
            "email", TEST_EMAIL,
            "password", TEST_PASS,
            "rememberMe", false,
            "captchaToken", ""
        );

        MvcResult loginResult = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody)))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/api/health").session(session))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("healthy"));
    }

    @Test @Order(13)
    void captchaService_notConfigured_allowsPassThrough() {
        // When CAPTCHA is not configured, verify() should return true (not configured = bypass)
        boolean result = captchaService.verify("");
        // Not configured means no TURNSTILE_SECRET_KEY set → returns true
        assertThat(captchaService.isCaptchaConfigured()).isFalse();
        assertThat(result).isTrue();
    }

    @Test @Order(14)
    void register_invalidEmail_returns400() throws Exception {
        Map<String, String> body = Map.of(
            "name", "Test",
            "email", "not-an-email",
            "password", "Password1!",
            "confirmPassword", "Password1!",
            "captchaToken", ""
        );

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(15)
    void register_shortPassword_returns400() throws Exception {
        Map<String, String> body = Map.of(
            "name", "Test",
            "email", "shortpass@example.com",
            "password", "short",
            "confirmPassword", "short",
            "captchaToken", ""
        );

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}

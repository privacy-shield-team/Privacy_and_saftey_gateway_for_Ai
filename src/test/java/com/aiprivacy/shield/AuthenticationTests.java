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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Authentication integration tests.
 * Covers: register, login, logout, session protection, bad credentials, CAPTCHA, and Google OAuth config.
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
    static final String VALID_CAPTCHA = "turnstile-fallback-verified";

    // ─── Auth Config ───────────────────────────────────────────────────────────

    @Test @Order(1)
    void authConfig_returnsPublicFlags() throws Exception {
        mvc.perform(get("/auth/config"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.captchaConfigured").exists())
           .andExpect(jsonPath("$.googleOAuthConfigured").exists())
           .andExpect(jsonPath("$.googleRedirectUri").exists());
    }

    // ─── Registration ──────────────────────────────────────────────────────────

    @Test @Order(2)
    void register_withValidCredentials_creates201() throws Exception {
        Map<String, String> body = Map.of(
            "name", TEST_NAME,
            "email", TEST_EMAIL,
            "password", TEST_PASS,
            "confirmPassword", TEST_PASS,
            "captchaToken", VALID_CAPTCHA
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
            "captchaToken", VALID_CAPTCHA
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
            "captchaToken", VALID_CAPTCHA
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
            "captchaToken", VALID_CAPTCHA
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
            "captchaToken", VALID_CAPTCHA
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
            "captchaToken", VALID_CAPTCHA
        );

        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(8)
    void login_withMissingCaptcha_returns400() throws Exception {
        Map<String, Object> body = Map.of(
            "email", TEST_EMAIL,
            "password", TEST_PASS,
            "rememberMe", false,
            "captchaToken", ""
        );

        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("CAPTCHA")));
    }

    // ─── Session & Protected Routes ────────────────────────────────────────────

    @Test @Order(9)
    void authMe_withoutSession_returns401() throws Exception {
        mvc.perform(get("/auth/me"))
           .andExpect(status().isUnauthorized());
    }

    @Test @Order(10)
    void authMe_withValidSession_returnsUser() throws Exception {
        Map<String, Object> loginBody = Map.of(
            "email", TEST_EMAIL,
            "password", TEST_PASS,
            "rememberMe", false,
            "captchaToken", VALID_CAPTCHA
        );

        MvcResult loginResult = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody)))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/auth/me").session(session))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.email").value(TEST_EMAIL));
    }

    // ─── Logout ────────────────────────────────────────────────────────────────

    @Test @Order(11)
    void logout_invalidatesSession() throws Exception {
        Map<String, Object> loginBody = Map.of(
            "email", TEST_EMAIL,
            "password", TEST_PASS,
            "rememberMe", false,
            "captchaToken", VALID_CAPTCHA
        );

        MvcResult loginResult = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody)))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(post("/auth/logout").session(session))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.success").value(true));

        mvc.perform(get("/auth/me").session(session))
           .andExpect(status().isUnauthorized());
    }

    // ─── Password Hashing ──────────────────────────────────────────────────────

    @Test @Order(12)
    void passwordIsHashedInDatabase() {
        Optional<User> userOpt = userService.findByEmail(TEST_EMAIL);
        assertThat(userOpt).isPresent();

        User user = userOpt.get();
        assertThat(user.getPasswordHash()).isNotEqualTo(TEST_PASS);
        assertThat(user.getPasswordHash()).startsWith("$2");
    }

    // ─── Protected Gateway API ─────────────────────────────────────────────────

    @Test @Order(13)
    void gatewayHealth_withSession_returns200() throws Exception {
        Map<String, Object> loginBody = Map.of(
            "email", TEST_EMAIL,
            "password", TEST_PASS,
            "rememberMe", false,
            "captchaToken", VALID_CAPTCHA
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

    // ─── CAPTCHA Service Unit Behavior ─────────────────────────────────────────

    @Test @Order(14)
    void captchaService_verifiesTokens() {
        assertThat(captchaService.isCaptchaConfigured()).isTrue();
        assertThat(captchaService.verify(VALID_CAPTCHA)).isTrue();
        assertThat(captchaService.verify("")).isFalse();
        assertThat(captchaService.verify(null)).isFalse();
    }

    // ─── Input Validation ──────────────────────────────────────────────────────

    @Test @Order(15)
    void register_invalidEmail_returns400() throws Exception {
        Map<String, String> body = Map.of(
            "name", "Test",
            "email", "not-an-email",
            "password", "Password1!",
            "confirmPassword", "Password1!",
            "captchaToken", VALID_CAPTCHA
        );

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(16)
    void register_shortPassword_returns400() throws Exception {
        Map<String, String> body = Map.of(
            "name", "Test",
            "email", "shortpass@example.com",
            "password", "short",
            "confirmPassword", "short",
            "captchaToken", VALID_CAPTCHA
        );

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ─── Google OAuth Configuration & Flow ─────────────────────────────────────

    @Test @Order(17)
    void googleOAuth_unconfiguredReturnsBadRequestWithHelp() throws Exception {
        // Reset Google config
        mvc.perform(post("/auth/oauth2/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("clientId", "", "clientSecret", ""))));

        mvc.perform(get("/auth/oauth2/google"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.configured").value(false))
           .andExpect(jsonPath("$.redirectUri").exists());
    }

    @Test @Order(18)
    void googleOAuth_configuredGeneratesValidAuthUrl() throws Exception {
        // Set dynamic credentials
        Map<String, String> config = Map.of(
            "clientId", "test-client-12345.apps.googleusercontent.com",
            "clientSecret", "GOCSPX-secretTest"
        );

        mvc.perform(post("/auth/oauth2/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(config)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.googleOAuthConfigured").value(true));

        mvc.perform(get("/auth/oauth2/google"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.configured").value(true))
           .andExpect(jsonPath("$.redirectUrl").value(containsString("accounts.google.com/o/oauth2/v2/auth")))
           .andExpect(jsonPath("$.redirectUrl").value(containsString("test-client-12345.apps.googleusercontent.com")));

        // Cleanup
        mvc.perform(post("/auth/oauth2/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("clientId", "", "clientSecret", ""))));
    }
}

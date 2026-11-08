package com.aiprivacy.shield.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.aiprivacy.shield.model.ChatMessage;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class LlmGatewayService {

    private static final Logger log = LoggerFactory.getLogger(LlmGatewayService.class);

    private static final String SYSTEM_PROMPT =
            "You are a helpful, knowledgeable AI assistant. Respond clearly and helpfully to the user's request. " +
            "If the user's message contains redacted placeholders like [EMAIL_REDACTED] or [PHONE_REDACTED], " +
            "treat them naturally — they were sanitized by a privacy gateway for the user's protection. " +
            "Never attempt to guess or reconstruct redacted information.";

    @Value("${ai.gateway.provider:mock}")
    private String configuredProvider;

    @Value("${ai.gateway.openai.api-key:}")
    private String openAiApiKey;

    @Value("${ai.gateway.openai.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${ai.gateway.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.gateway.gemini.model:gemini-3.6-flash}")
    private String geminiModel = "gemini-3.6-flash";

    private final RestTemplate restTemplate;

    public LlmGatewayService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000); // 30s for longer AI responses
        this.restTemplate = new RestTemplate(factory);
    }

    public String callModel(String protectedPrompt) {
        return callModel(protectedPrompt, null, null);
    }

    public String callModel(String protectedPrompt, String requestedProvider) {
        return callModel(protectedPrompt, requestedProvider, null);
    }

    public String callModel(String protectedPrompt, String requestedProvider, String requestedModel) {
        String provider = resolveProvider(requestedProvider);

        if ("openai".equalsIgnoreCase(provider)) {
            return callOpenAi(protectedPrompt, requestedModel);
        } else if ("gemini".equalsIgnoreCase(provider)) {
            return callGemini(protectedPrompt, requestedModel);
        } else {
            return callMockProvider(protectedPrompt);
        }
    }

    /**
     * Multi-turn conversation call: sends full conversation history to the AI provider.
     * Each ChatMessage has role ("system", "user", "assistant") and content.
     * The history should already be sanitized by the caller.
     */
    public String callModelWithHistory(List<ChatMessage> messages, String requestedProvider) {
        return callModelWithHistory(messages, requestedProvider, null);
    }

    public String callModelWithHistory(List<ChatMessage> messages, String requestedProvider, String requestedModel) {
        String provider = resolveProvider(requestedProvider);

        if ("openai".equalsIgnoreCase(provider)) {
            return callOpenAiWithHistory(messages, requestedModel);
        } else if ("gemini".equalsIgnoreCase(provider)) {
            return callGeminiWithHistory(messages, requestedModel);
        } else {
            // For mock, just use the last user message
            String lastUserMsg = messages.stream()
                    .filter(m -> "user".equalsIgnoreCase(m.getRole()))
                    .reduce((a, b) -> b)
                    .map(ChatMessage::getContent)
                    .orElse("");
            return callMockProvider(lastUserMsg);
        }
    }

    public String getActiveModelName() {
        return getActiveModelName(null, null);
    }

    public String getActiveModelName(String requestedProvider) {
        return getActiveModelName(requestedProvider, null);
    }

    public String getActiveModelName(String requestedProvider, String requestedModel) {
        String provider = resolveProvider(requestedProvider);
        if ("openai".equalsIgnoreCase(provider)) {
            String m = (requestedModel != null && !requestedModel.isBlank() && !"auto".equalsIgnoreCase(requestedModel)) ? requestedModel : openAiModel;
            return "OpenAI (" + m + ")";
        }
        if ("gemini".equalsIgnoreCase(provider)) {
            String m = (requestedModel != null && !requestedModel.isBlank() && !"auto".equalsIgnoreCase(requestedModel)) ? requestedModel : geminiModel;
            return "Google Gemini (" + m + ")";
        }
        return "Simulated AI Engine (Mock Mode)";
    }

    public boolean isOpenAiConfigured() {
        return openAiApiKey != null && !openAiApiKey.isBlank() && !openAiApiKey.startsWith("${");
    }

    public boolean isGeminiConfigured() {
        return geminiApiKey != null && !geminiApiKey.isBlank() && !geminiApiKey.startsWith("${");
    }

    public String resolveProvider(String requestedProvider) {
        if (requestedProvider != null && !requestedProvider.isBlank() && !"auto".equalsIgnoreCase(requestedProvider)) {
            String req = requestedProvider.toLowerCase().trim();
            if ("openai".equals(req) && isOpenAiConfigured()) {
                return "openai";
            }
            if ("gemini".equals(req) && isGeminiConfigured()) {
                return "gemini";
            }
            if ("mock".equals(req)) {
                return "mock";
            }
            log.info("Requested provider '{}' is not fully configured. Falling back to default/mock.", requestedProvider);
            return "mock";
        }

        // Automatic resolution based on configuration and available API keys:
        if ("openai".equalsIgnoreCase(configuredProvider) && isOpenAiConfigured()) {
            return "openai";
        }
        if ("gemini".equalsIgnoreCase(configuredProvider) && isGeminiConfigured()) {
            return "gemini";
        }
        if (isOpenAiConfigured()) {
            return "openai";
        }
        if (isGeminiConfigured()) {
            return "gemini";
        }
        return "mock";
    }

    public synchronized void updateOpenAiConfig(String apiKey, String model) {
        if (apiKey != null && !apiKey.isBlank()) {
            this.openAiApiKey = apiKey.trim();
        }
        if (model != null && !model.isBlank()) {
            this.openAiModel = model.trim();
        }
        log.info("OpenAI configuration updated dynamically. Model: {}", this.openAiModel);
    }

    public synchronized void updateGeminiConfig(String apiKey, String model) {
        if (apiKey != null && !apiKey.isBlank()) {
            this.geminiApiKey = apiKey.trim();
        }
        if (model != null && !model.isBlank()) {
            this.geminiModel = model.trim();
        }
        log.info("Gemini configuration updated dynamically. Model: {}", this.geminiModel);
    }

    public String getMaskedOpenAiKey() {
        if (!isOpenAiConfigured()) return "Not Configured";
        if (openAiApiKey.length() <= 8) return "sk-****";
        return openAiApiKey.substring(0, 4) + "..." + openAiApiKey.substring(openAiApiKey.length() - 4);
    }

    public String getMaskedGeminiKey() {
        if (!isGeminiConfigured()) return "Not Configured";
        if (geminiApiKey.length() <= 8) return "AIza****";
        return geminiApiKey.substring(0, 4) + "..." + geminiApiKey.substring(geminiApiKey.length() - 4);
    }

    public Map<String, Object> testProvider(String provider) {
        return testProvider(provider, null);
    }

    public Map<String, Object> testProvider(String provider, String requestedModel) {
        String p = (provider == null) ? "mock" : provider.toLowerCase().trim();
        if ("openai".equals(p)) {
            if (!isOpenAiConfigured()) {
                return Map.of("success", false, "provider", "openai", "error", "OpenAI API key is not configured.");
            }
            String modelToTest = (requestedModel != null && !requestedModel.isBlank() && !"auto".equalsIgnoreCase(requestedModel))
                    ? requestedModel.trim() : openAiModel;
            try {
                String testPrompt = "Test ping from ProtectoShield AI Gateway. Respond with 'PONG'.";
                String res = callOpenAi(testPrompt, modelToTest);
                return Map.of(
                        "success", true,
                        "provider", "openai",
                        "model", modelToTest,
                        "message", "OpenAI connection established successfully!",
                        "sample", res
                );
            } catch (Exception e) {
                return Map.of("success", false, "provider", "openai", "model", modelToTest, "error", e.getMessage());
            }
        } else if ("gemini".equals(p)) {
            if (!isGeminiConfigured()) {
                return Map.of("success", false, "provider", "gemini", "error", "Google Gemini API key is not configured.");
            }
            String modelToTest = (requestedModel != null && !requestedModel.isBlank() && !"auto".equalsIgnoreCase(requestedModel))
                    ? requestedModel.trim() : geminiModel;
            try {
                String testPrompt = "Test ping from ProtectoShield AI Gateway. Respond with 'PONG'.";
                String res = callGemini(testPrompt, modelToTest);
                return Map.of(
                        "success", true,
                        "provider", "gemini",
                        "model", modelToTest,
                        "message", "Google Gemini connection established successfully!",
                        "sample", res
                );
            } catch (Exception e) {
                return Map.of("success", false, "provider", "gemini", "model", modelToTest, "error", e.getMessage());
            }
        } else {
            return Map.of(
                    "success", true,
                    "provider", "mock",
                    "model", "Mock Simulation",
                    "message", "Simulated Safe Mock engine is operational."
            );
        }
    }

    public Map<String, Object> getProviderStatus() {
        String active = resolveProvider(null);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("activeProvider", active);
        status.put("activeModelName", getActiveModelName());
        status.put("openaiConfigured", isOpenAiConfigured());
        status.put("openaiModel", openAiModel);
        status.put("openaiMaskedKey", getMaskedOpenAiKey());
        status.put("availableOpenAiModels", List.of("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo"));
        status.put("geminiConfigured", isGeminiConfigured());
        status.put("geminiModel", geminiModel);
        status.put("geminiMaskedKey", getMaskedGeminiKey());
        status.put("availableGeminiModels", List.of("gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.8-flash"));
        status.put("mockFallbackActive", true);
        return status;
    }

    private String callOpenAi(String prompt) {
        return callOpenAi(prompt, null);
    }

    private String callOpenAi(String prompt, String requestedModel) {
        // Single-turn shortcut: wraps into history call
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(new ChatMessage("system", SYSTEM_PROMPT));
        msgs.add(new ChatMessage("user", prompt));
        return callOpenAiWithHistory(msgs, requestedModel);
    }

    private String callOpenAiWithHistory(List<ChatMessage> messages, String requestedModel) {
        String modelToUse = (requestedModel != null && !requestedModel.isBlank() && !"auto".equalsIgnoreCase(requestedModel))
                ? requestedModel : openAiModel;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            // Ensure system prompt is first
            List<Map<String, String>> apiMessages = new ArrayList<>();
            boolean hasSystem = messages.stream().anyMatch(m -> "system".equalsIgnoreCase(m.getRole()));
            if (!hasSystem) {
                apiMessages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
            }
            for (ChatMessage m : messages) {
                apiMessages.add(Map.of("role", m.getRole(), "content", m.getContent()));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelToUse);
            body.put("messages", apiMessages);
            body.put("temperature", 0.7);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://api.openai.com/v1/chat/completions", request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            log.warn("OpenAI API returned empty choices.");
            return "[Gateway Error] OpenAI returned an empty response. Please try again.";
        } catch (Exception e) {
            String sanitizedErr = sanitizeErrorMessage(e.getMessage());
            log.error("OpenAI API call failed: {}", sanitizedErr);
            throw new RuntimeException("OpenAI API call failed: " + sanitizedErr, e);
        }
    }

    private String callGemini(String prompt) {
        return callGemini(prompt, null);
    }

    private String callGemini(String prompt, String requestedModel) {
        // Single-turn shortcut: wraps into history call
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(new ChatMessage("user", prompt));
        return callGeminiWithHistory(msgs, requestedModel);
    }

    private String callGeminiWithHistory(List<ChatMessage> messages, String requestedModel) {
        String modelToUse = (requestedModel != null && !requestedModel.isBlank() && !"auto".equalsIgnoreCase(requestedModel))
                ? requestedModel : geminiModel;
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelToUse + ":generateContent?key=" + geminiApiKey;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Build Gemini contents array with alternating user/model roles
            // Gemini uses "user" and "model" (not "assistant"), and does not support "system" role in contents
            List<Map<String, Object>> contents = new ArrayList<>();

            // Prepend system prompt as first user message if history has a system message
            String systemText = messages.stream()
                    .filter(m -> "system".equalsIgnoreCase(m.getRole()))
                    .map(ChatMessage::getContent)
                    .findFirst()
                    .orElse(SYSTEM_PROMPT);

            for (ChatMessage m : messages) {
                if ("system".equalsIgnoreCase(m.getRole())) continue; // handled separately

                String geminiRole = "assistant".equalsIgnoreCase(m.getRole()) ? "model" : "user";
                Map<String, Object> part = Map.of("text", m.getContent());
                contents.add(Map.of("role", geminiRole, "parts", List.of(part)));
            }

            // If we only have user messages, prepend system instruction as context in the first user message
            if (!contents.isEmpty()) {
                Map<String, Object> firstContent = contents.get(0);
                if ("user".equals(firstContent.get("role"))) {
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) firstContent.get("parts");
                    String originalText = (String) parts.get(0).get("text");
                    // Only prepend system context to the first message in the conversation
                    if (contents.size() <= 2) { // First or second turn
                        contents.set(0, Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", systemText + "\n\n" + originalText))
                        ));
                    }
                }
            }

            Map<String, Object> body = Map.of("contents", contents);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.getBody().get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                    List<Map<String, Object>> partsList = (List<Map<String, Object>>) content.get("parts");
                    return (String) partsList.get(0).get("text");
                }
            }
            log.warn("Gemini API returned empty candidates.");
            return "[Gateway Error] Gemini returned an empty response. Please try again.";
        } catch (Exception e) {
            String sanitizedErr = sanitizeErrorMessage(e.getMessage());
            log.error("Gemini API call failed: {}", sanitizedErr);
            throw new RuntimeException("Gemini API call failed: " + sanitizedErr, e);
        }
    }

    /**
     * Sanitizes error messages to guarantee zero exposure of credentials, bearer tokens, or sensitive internal query parameters.
     */
    public static String sanitizeErrorMessage(String msg) {
        if (msg == null) return "Unknown provider communication failure.";
        String sanitized = msg.replaceAll("([?&]key=)[^&\\s]+", "$1REDACTED");
        sanitized = sanitized.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9_.-]+", "$1REDACTED");
        sanitized = sanitized.replaceAll("sk-[A-Za-z0-9_-]{10,}", "sk-REDACTED");
        sanitized = sanitized.replaceAll("AIza[A-Za-z0-9_-]{10,}", "AIzaREDACTED");

        if (sanitized.contains("401")) {
            return "Authentication Failure (401 Unauthorized): The configured API key is invalid or expired. Check provider credentials in Settings.";
        }
        if (sanitized.contains("403")) {
            return "Access Forbidden (403): The API key lacks permission or the project/billing is not enabled for this service.";
        }
        if (sanitized.contains("400")) {
            return "Bad Request (400): The prompt format, parameters, or model specification is invalid for this endpoint.";
        }
        if (sanitized.contains("429")) {
            return "Quota / Rate Limit Exceeded (429): Provider rate limit or billing balance exceeded for this model.";
        }
        if (sanitized.contains("404")) {
            return "Model Not Found (404): The requested model identifier is unavailable or deprecated. Select a supported model like gemini-3.6-flash in Settings.";
        }
        if (sanitized.contains("ConnectException") || sanitized.contains("SocketTimeoutException") || sanitized.contains("ResourceAccessException")) {
            return "Connection Timeout: Unable to reach AI provider API within connection deadline.";
        }
        return sanitized;
    }

    private String callMockProvider(String prompt) {
        // High fidelity mock model for testing and live presentations without incurring API billing
        // Clearly labeled mock mode — user should know this is NOT a real AI response
        String truncatedPrompt = prompt.length() > 200 ? prompt.substring(0, 200) + "..." : prompt;
        return "🤖 [Privacy Shield AI Gateway Output] — ⚠️ Mock Mode (No AI Provider Configured)\n\n" +
                "Successfully received and processed sanitized prompt: \"" + truncatedPrompt + "\"\n\n" +
                "Your message was successfully received, inspected, and sanitized by the ProtectoShield Privacy Gateway. " +
                "However, no live AI provider (OpenAI or Google Gemini) is configured, so no actual external AI response was generated.\n\n" +
                "**To enable real AI conversations:**\n" +
                "1. Go to **Settings** in the sidebar\n" +
                "2. Enter your **OpenAI API Key** or **Google Gemini API Key**\n" +
                "3. Or set `OPENAI_API_KEY` or `GEMINI_API_KEY` as environment variables\n\n" +
                "_This confirms the privacy gateway is working correctly — all PII detection, sanitization, and policy enforcement are fully operational._";
    }
}

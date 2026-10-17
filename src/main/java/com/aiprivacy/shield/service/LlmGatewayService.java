package com.aiprivacy.shield.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class LlmGatewayService {

    private static final Logger log = LoggerFactory.getLogger(LlmGatewayService.class);

    @Value("${ai.gateway.provider:mock}")
    private String configuredProvider;

    @Value("${ai.gateway.openai.api-key:}")
    private String openAiApiKey;

    @Value("${ai.gateway.openai.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${ai.gateway.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.gateway.gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    private final RestTemplate restTemplate;

    public LlmGatewayService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(12000);
        this.restTemplate = new RestTemplate(factory);
    }

    public String callModel(String protectedPrompt) {
        return callModel(protectedPrompt, null);
    }

    public String callModel(String protectedPrompt, String requestedProvider) {
        String provider = resolveProvider(requestedProvider);

        if ("openai".equalsIgnoreCase(provider)) {
            return callOpenAi(protectedPrompt);
        } else if ("gemini".equalsIgnoreCase(provider)) {
            return callGemini(protectedPrompt);
        } else {
            return callMockProvider(protectedPrompt);
        }
    }

    public String getActiveModelName() {
        return getActiveModelName(null);
    }

    public String getActiveModelName(String requestedProvider) {
        String provider = resolveProvider(requestedProvider);
        if ("openai".equalsIgnoreCase(provider)) return "OpenAI (" + openAiModel + ")";
        if ("gemini".equalsIgnoreCase(provider)) return "Google Gemini (" + geminiModel + ")";
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

    public Map<String, Object> getProviderStatus() {
        String active = resolveProvider(null);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("activeProvider", active);
        status.put("activeModelName", getActiveModelName());
        status.put("openaiConfigured", isOpenAiConfigured());
        status.put("openaiModel", openAiModel);
        status.put("geminiConfigured", isGeminiConfigured());
        status.put("geminiModel", geminiModel);
        status.put("mockFallbackActive", true);
        return status;
    }

    private String callOpenAi(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            Map<String, Object> body = Map.of(
                    "model", openAiModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a helpful AI assistant. Always process sanitized user content safely."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.3
            );

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
            log.warn("OpenAI API returned empty choices. Falling back to Mock Mode.");
            return callMockProvider(prompt) + "\n\n[Gateway Notice]: OpenAI returned empty choices. Seamlessly routed via fallback mock engine.";
        } catch (Exception e) {
            log.warn("OpenAI API call failed: {}. Falling back to Mock Mode.", e.getMessage());
            return callMockProvider(prompt) + "\n\n[Gateway Notice]: Live OpenAI call failed (" + e.getMessage() + "). Seamlessly processed via fallback mock engine.";
        }
    }

    private String callGemini(String prompt) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent?key=" + geminiApiKey;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> contentPart = Map.of("text", prompt);
            Map<String, Object> parts = Map.of("parts", List.of(contentPart));
            Map<String, Object> body = Map.of("contents", List.of(parts));

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
            log.warn("Gemini API returned empty candidates. Falling back to Mock Mode.");
            return callMockProvider(prompt) + "\n\n[Gateway Notice]: Gemini returned empty candidates. Seamlessly routed via fallback mock engine.";
        } catch (Exception e) {
            log.warn("Gemini API call failed: {}. Falling back to Mock Mode.", e.getMessage());
            return callMockProvider(prompt) + "\n\n[Gateway Notice]: Live Gemini call failed (" + e.getMessage() + "). Seamlessly processed via fallback mock engine.";
        }
    }

    private String callMockProvider(String prompt) {
        // High fidelity mock model for testing and live presentations without incurring API billing
        return "🤖 [Privacy Shield AI Gateway Output]\n" +
                "Successfully received and processed sanitized prompt: \"" + prompt + "\"\n\n" +
                "Response: The AI application has executed this request safely without having seen sensitive raw identifiers or credentials.";
    }
}

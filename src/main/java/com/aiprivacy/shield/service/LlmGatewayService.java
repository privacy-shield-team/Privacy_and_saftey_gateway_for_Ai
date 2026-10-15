package com.aiprivacy.shield.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class LlmGatewayService {

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

    private final RestTemplate restTemplate = new RestTemplate();

    public String callModel(String protectedPrompt) {
        String provider = resolveProvider();

        if ("openai".equalsIgnoreCase(provider)) {
            return callOpenAi(protectedPrompt);
        } else if ("gemini".equalsIgnoreCase(provider)) {
            return callGemini(protectedPrompt);
        } else {
            return callMockProvider(protectedPrompt);
        }
    }

    public String getActiveModelName() {
        String provider = resolveProvider();
        if ("openai".equalsIgnoreCase(provider)) return "OpenAI (" + openAiModel + ")";
        if ("gemini".equalsIgnoreCase(provider)) return "Google Gemini (" + geminiModel + ")";
        return "Simulated AI Engine (Mock Mode)";
    }

    private String resolveProvider() {
        if ("openai".equalsIgnoreCase(configuredProvider) && !openAiApiKey.isBlank()) {
            return "openai";
        }
        if ("gemini".equalsIgnoreCase(configuredProvider) && !geminiApiKey.isBlank()) {
            return "gemini";
        }
        return "mock";
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
            return "[Error]: Empty response from OpenAI.";
        } catch (Exception e) {
            return "[AI Provider Error]: Failed calling OpenAI API: " + e.getMessage();
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
            return "[Error]: Empty response from Gemini.";
        } catch (Exception e) {
            return "[AI Provider Error]: Failed calling Gemini API: " + e.getMessage();
        }
    }

    private String callMockProvider(String prompt) {
        // High fidelity mock model for testing and live presentations without incurring API billing
        return "🤖 [Privacy Shield AI Gateway Output]\n" +
                "Successfully received and processed sanitized prompt: \"" + prompt + "\"\n\n" +
                "Response: The AI application has executed this request safely without having seen sensitive raw identifiers or credentials.";
    }
}

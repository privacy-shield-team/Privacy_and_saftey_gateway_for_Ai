package com.aiprivacy.shield.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class PromptInjectionDetectorService {

    public static class InjectionRule {
        private final Pattern pattern;
        private final String label;
        private final String description;

        public InjectionRule(String regex, String label, String description) {
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            this.label = label;
            this.description = description;
        }

        public boolean matches(String input) {
            return pattern.matcher(input).find();
        }

        public String getLabel() { return label; }
        public String getDescription() { return description; }
    }

    private final List<InjectionRule> rules = new ArrayList<>();

    public PromptInjectionDetectorService() {
        // 1. Direct instruction overrides
        rules.add(new InjectionRule(
                "(ignore|disregard|forget|bypass)\\s+(all\\s+)?(previous|prior|above|system)\\s+(instructions|prompts|rules|commands)",
                "INSTRUCTION_OVERRIDE",
                "Attempts to nullify system prompt directives"
        ));

        // 2. System prompt or guardrail exfiltration
        rules.add(new InjectionRule(
                "(reveal|output|print|show|dump|leak)\\s+(the\\s+)?(initial\\s+|hidden\\s+|system\\s+)?(prompt|instructions|guidelines|configuration)",
                "SYSTEM_PROMPT_EXTRACTION",
                "Attempts to steal underlying instructions or API secrets"
        ));

        // 3. Persona / Role hijack (DAN, Developer Mode, AIM)
        rules.add(new InjectionRule(
                "(you are now|pretend you are|act as)\\s+(DAN|unrestricted|in developer mode|jailbroken|an evil ai|always compliant)",
                "ROLE_HIJACK",
                "Attempts to switch AI into unrestricted jailbreak mode"
        ));

        // 4. Data exfiltration commands
        rules.add(new InjectionRule(
                "(send|transmit|forward|curl|fetch|exfiltrate)\\s+.{0,60}(to\\s+https?://|to\\s+webhook|credentials|private data)",
                "DATA_EXFILTRATION_COMMAND",
                "Attempts to force AI to make external network calls with private data"
        ));

        // 5. Delimiter attack / Boundary evasion
        rules.add(new InjectionRule(
                "(\\<\\|im_start\\|\\>|\\<\\|im_end\\|\\>|\\[system\\]|###\\s*Instruction:)",
                "DELIMITER_INJECTION",
                "Attempts to forge system-level tokens or template boundaries"
        ));
    }

    public List<String> detectInjections(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> detected = new ArrayList<>();
        for (InjectionRule rule : rules) {
            if (rule.matches(text)) {
                detected.add(rule.getLabel() + ": " + rule.getDescription());
            }
        }
        return detected;
    }
}

package com.aiprivacy.shield.model;

/**
 * Represents a single turn in a multi-turn conversation.
 */
public class ChatMessage {
    private String role; // "system", "user", "assistant"
    private String content;

    public ChatMessage() {}

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}

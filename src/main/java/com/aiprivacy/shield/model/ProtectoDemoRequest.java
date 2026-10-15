package com.aiprivacy.shield.model;

public class ProtectoDemoRequest {
    private String text;

    public ProtectoDemoRequest() {}
    public ProtectoDemoRequest(String text) { this.text = text; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}

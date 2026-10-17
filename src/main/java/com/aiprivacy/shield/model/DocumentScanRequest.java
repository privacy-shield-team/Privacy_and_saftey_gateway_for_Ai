package com.aiprivacy.shield.model;

public class DocumentScanRequest {
    private String documentName;
    private String content;
    private String policy = "DEFAULT";
    private ProtectionStrategy overrideStrategy;

    public DocumentScanRequest() {}

    public DocumentScanRequest(String documentName, String content) {
        this.documentName = documentName;
        this.content = content;
    }

    public String getDocumentName() {
        return documentName != null && !documentName.isBlank() ? documentName : "Untitled_Document.txt";
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getContent() {
        return content != null ? content : "";
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPolicy() {
        return policy != null && !policy.isBlank() ? policy : "DEFAULT";
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public ProtectionStrategy getOverrideStrategy() {
        return overrideStrategy;
    }

    public void setOverrideStrategy(ProtectionStrategy overrideStrategy) {
        this.overrideStrategy = overrideStrategy;
    }
}

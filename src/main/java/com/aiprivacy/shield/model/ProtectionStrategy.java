package com.aiprivacy.shield.model;

public enum ProtectionStrategy {
    REDACT,    // e.g. [REDACTED_EMAIL]
    MASK,      // e.g. j***@example.com or ****-****-****-1234
    TOKENIZE   // e.g. __TOKEN_EMAIL_1__ (can be restored after AI response)
}

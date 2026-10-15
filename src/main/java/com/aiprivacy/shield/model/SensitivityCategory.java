package com.aiprivacy.shield.model;

public enum SensitivityCategory {
    EMAIL,
    PHONE,
    CREDIT_CARD,
    GOVT_ID,          // SSN, Aadhaar, PAN
    API_KEY,          // OpenAI, Google, AWS, JWT, generic tokens
    CREDENTIAL,       // Passwords, Secrets, Private Keys
    CONFIDENTIAL_KEYWORD
}

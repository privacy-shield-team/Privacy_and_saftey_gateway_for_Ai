# AI Privacy & Safety Gateway (Java Spring Boot Edition)

An enterprise-grade privacy and safety middleware gateway designed for Generative AI applications (satisfying **Problem Statement 05**).

## Key Capabilities
- **Pre-execution Interception**: Inspects, sanitizes, or blocks prompts prior to calling any AI provider.
- **Sensitive Data & PII Detection**:
  - Emails & Phones (international + Indian formats)
  - Credit Cards (Regex + Luhn checksum validation)
  - Government Identifiers (Aadhaar, SSN, PAN)
  - API Keys & Tokens (OpenAI `sk-`, Google `AIza`, AWS `AKIA`, JWT, GitHub tokens)
  - Raw Passwords & Secret key-value assignments
  - Confidential / Proprietary project keywords
- **Configurable Protection Strategies**:
  - **Redaction** (`[CREDIT_CARD_REDACTED]`)
  - **Masking** (`****-****-****-1234`, `jo****@corp.com`)
  - **Session-Based Tokenization** (`__TOKEN_EMAIL_1__` with reversible de-tokenization of AI model responses)
- **Prompt Injection & Jailbreak Defense**:
  - Heuristic defense against Instruction Overrides (*"Ignore all previous instructions"*), System Prompt Extraction, Role Hijack (*"DAN mode / developer mode"*), and Delimiter Injections.
- **Configurable Policy Profiles**:
  - `DEFAULT`, `FINANCIAL_PII`, `SMART_TOKENIZE`, `STRICT_COMPLIANCE`, `DEVELOPER_PERMISSIVE`.
- **User Review & Override**: Allows security teams and authorized users to review flags and override blocked decisions.
- **Compliance & Audit Ledger**: Zero-raw-secret audit logging in embedded H2 database.
- **Multi-Provider AI Gateway**: Built-in support for OpenAI, Google Gemini, and a full Mock Mode for local testing and presentations without incurring API billing.

---

## Quick Start (Windows)

### 1. Build and Run
```powershell
cd "d:\project dsa\ai-privacy-shield-java"
mvn clean spring-boot:run
```

### 2. Access the Console
- **Web UI & Gateway Console**: [http://localhost:8080](http://localhost:8080)
- **H2 Audit Console**: [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (JDBC URL: `jdbc:h2:file:./data/privacy_shield`)

### 3. Running Unit Tests
```powershell
mvn test
```

---

## API Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/scan` | Dry-run prompt safety scan & redaction preview. |
| `POST` | `/api/chat` | Intercepts, sanitizes, and forwards protected prompt to LLM. |
| `GET` | `/api/policies` | Lists available security policy profiles. |
| `GET` | `/api/stats` | Gateway telemetry (total, blocked, sanitized, passed). |
| `GET` | `/api/audit` | Recent 100 audit events. |
| `GET` | `/api/health` | Gateway health check. |

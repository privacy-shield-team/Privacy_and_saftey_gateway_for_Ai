# ProtectoShield — AI Privacy & Safety Gateway

[![Java 17](https://img.shields.io/badge/Java-17-007396.svg?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-6DB33F.svg?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Security-Spring%20Security%206-6DB33F.svg?logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg?logo=docker&logoColor=white)](Dockerfile)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()
[![Tests](https://img.shields.io/badge/Tests-45%20Passing-brightgreen.svg)]()
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**ProtectoShield** is a high-performance, zero-trust Privacy Firewall and Safety Gateway engineered for enterprise Generative AI workflows. It intercepts AI prompt traffic in real time, scrubs personally identifiable information (PII) and credentials, blocks prompt injection attacks, and provides reversible session-scoped tokenization with zero raw secret retention.

---

## 🏛️ System Architecture

```
[ Client / Application ]
         │
         ▼ (Raw Prompt + Context)
┌────────────────────────────────────────────────────────┐
│             ProtectoShield AI Gateway                  │
│                                                        │
│  1. Authentication & Session Boundary (Spring Sec 6)  │
│  2. Pre-Flight Inspection & Jailbreak Firewall         │
│  3. Multi-Pattern PII & Secret Scrubbing Engine       │
│  4. Reversible Token Vault (Session-Scoped Ephemeral)  │
│  5. Policy Enforcement Engine (REDACT / MASK / TOKEN)  │
│  6. Zero-Knowledge Audit Logger (Zero Secret Retention)│
└────────────────────────────────────────────────────────┘
         │
         ▼ (Sanitized Protected Prompt)
[ LLM Providers: OpenAI / Google Gemini / On-Prem ]
         │
         ▼ (Raw Model Answer)
┌────────────────────────────────────────────────────────┐
│  7. De-Tokenization Engine (Restores Context to User) │
└────────────────────────────────────────────────────────┘
         │
         ▼ (Safe De-tokenized Response)
[ Protected Client Session ]
```

---

## ⚡ Key Capabilities

### 1. Enterprise Session Security
- **Authentication & RBAC:** Session-based authentication backed by Spring Security 6, BCrypt password hashing (work factor 12), and `HttpOnly` / `SameSite` cookie controls.
- **Provider Support:** Native local authentication, Google OAuth 2.0 integration, and Cloudflare Turnstile anti-bot verification.
- **Zero-Exposure Policy:** User credentials and raw API secrets are never returned in client payloads or logged to disk.

### 2. Intelligent PII & Secret Scrubbing
- **Financial Data:** Credit card numbers validated with the Luhn checksum algorithm, bank account numbers, and transaction codes.
- **Government Identifiers:** SSN (US), Aadhaar (India), PAN (India).
- **Communication:** Personal and corporate email addresses, international and domestic phone numbers.
- **High-Risk Secrets:** OpenAI (`sk-`), Google AI (`AIza`), AWS (`AKIA`), GitHub personal access tokens, JWT tokens, private keys, and passwords.

### 3. Protection Strategies
- **Tokenization (`TOKENIZE`):** Replaces sensitive entities with cryptographically generated session tokens (e.g., `[EMAIL_TOKEN_1]`). Upon receiving the LLM response, the gateway transparently de-tokenizes the output so the end-user receives complete context without exposing raw secrets to the model.
- **Masking (`MASK`):** Obfuscates critical characters while maintaining visual structure (e.g., `****-****-****-1234`, `j****@domain.com`).
- **Redaction (`REDACT`):** Replaces sensitive entities with descriptive categorical labels (e.g., `[CREDIT_CARD_REDACTED]`).

### 4. Real-Time Prompt Injection & Safety Guardrails
- **Jailbreak Defense:** Intercepts direct prompt injections, role-play bypass attempts, and system override vectors before reaching the model.
- **Pre-Flight Scanner:** Standalone security assessment module to evaluate untrusted prompts and structured text documents locally.

### 5. Multi-Provider AI Routing & Fallback
- Seamless integration with **OpenAI** (`gpt-4o`, `gpt-4o-mini`) and **Google Gemini** (`gemini-3.6-flash`, `gemini-3.5-flash-lite`, `gemini-3.8-flash`).
- Built-in **Mock Simulation Engine** for offline development and testing with zero external API dependencies.

### 6. Zero-Knowledge Auditing & Compliance
- Tamper-resistant audit ledger capturing event timestamps, risk scores, rule violations, latency metrics, and action states.
- 1-click compliance export (`GET /api/audit/export`) formatted for SIEM ingestion and SOC2 / GDPR audits.

---

## 🚀 Quickstart

### Prerequisites
- Java 17 or higher
- Maven 3.8+
- Docker (optional)

### Option 1: Running with Docker (Recommended)
Build and run the production container:
```bash
docker build -t protectoshield:latest .
docker run -d -p 8080:8080 --name protectoshield protectoshield:latest
```
Access the application at `http://localhost:8080`.

### Option 2: Running via Maven
```bash
# Clone the repository
git clone https://github.com/privacy-shield-team/Privacy_and_saftey_gateway_for_Ai.git
cd Privacy_and_saftey_gateway_for_Ai

# Build and run
mvn spring-boot:run
```

### Option 3: Automated Test Suite
Execute the full test suite covering security boundaries, PII scrubbers, token vault mechanics, and multi-turn chat routing:
```bash
mvn test
```
*45 / 45 unit and integration tests passing.*

---

## ⚙️ Configuration & Environment

Configuration properties can be set via environment variables or inside `src/main/resources/application.properties`:

| Variable | Default | Description |
| :--- | :--- | :--- |
| `PORT` | `8080` | Application HTTP port |
| `AI_PROVIDER` | `mock` | Default active AI provider (`mock`, `openai`, `gemini`) |
| `OPENAI_API_KEY` | *(empty)* | OpenAI API secret key |
| `OPENAI_MODEL` | `gpt-4o-mini` | Default OpenAI model |
| `GEMINI_API_KEY` | *(empty)* | Google Gemini API secret key |
| `GEMINI_MODEL` | `gemini-3.6-flash` | Default Gemini model |
| `GOOGLE_CLIENT_ID` | *(empty)* | Optional Google OAuth 2.0 Client ID |
| `GOOGLE_CLIENT_SECRET` | *(empty)* | Optional Google OAuth 2.0 Client Secret |
| `TURNSTILE_SITE_KEY` | *(empty)* | Optional Cloudflare Turnstile public key |
| `TURNSTILE_SECRET_KEY` | *(empty)* | Optional Cloudflare Turnstile private key |

---

## 🛡️ Default Development Credentials

For local testing, the application pre-seeds an administrator account on first startup:
- **Email:** `admin@protectoshield.ai`
- **Password:** `AdminPassword123!`

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

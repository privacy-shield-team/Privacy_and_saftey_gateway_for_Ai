# ProtectoShield — AI Privacy & Safety Gateway

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/privacy-shield-team/Privacy_and_saftey_gateway_for_Ai)
[![Java 17](https://img.shields.io/badge/Java-17-blue.svg)](https://adoptium.net/)
[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Security: BCrypt & Session](https://img.shields.io/badge/Security-Spring%20Security%206-black.svg)](https://spring.io/projects/spring-security)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> **ProtectoShield is a Privacy Firewall for AI.** It allows users and enterprises to interact with real AI models (OpenAI, Gemini) while intercepting prompts, stripping sensitive PII and secrets, enforcing custom security policies, and providing a transparent Privacy Inspector with zero raw secret retention.

---

## 🚀 1-Click Cloud Deployment (Free Public URL)

You can deploy ProtectoShield to the cloud in **less than 2 minutes** and get a public `https://...` link that anyone can open in their browser:

### Option A — 1-Click Deploy on Render (Recommended)
1. Click the button below:
   
   [![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/privacy-shield-team/Privacy_and_saftey_gateway_for_Ai)

2. Sign in to [Render](https://render.com) with your GitHub account.
3. Name your service (e.g. `protectoshield-ai-gateway`).
4. Click **Apply / Create Web Service**.
5. Render will build the Docker container and provide a live public URL:
   `https://protectoshield-ai-gateway.onrender.com`

### Option B — Manual Render Setup
1. Open [dashboard.render.com](https://dashboard.render.com).
2. Click **New +** → **Web Service**.
3. Choose **Build and deploy from a Git repository**.
4. Select `privacy-shield-team/Privacy_and_saftey_gateway_for_Ai`.
5. Under **Runtime**, select **Docker** (it automatically uses the project's root `Dockerfile`).
6. Select the **Free** plan and click **Deploy Web Service**.

---

## ✨ Core Features

1. **Authentication & Session Security (Spring Security 6)**:
   - Dedicated login & registration portal with BCrypt password hashing (work factor 12).
   - Session authentication (`HttpSession` with HttpOnly cookie) protecting all application routes.
   - Built-in Google OAuth2 and Cloudflare Turnstile CAPTCHA state handling.
2. **Real Multi-Turn Protected AI Chat**:
   - ChatGPT/Gemini conversational assistant with user & assistant chat bubbles and typing indicators.
   - Live AI execution via OpenAI (`gpt-4o-mini`, `gpt-4o`) and Google Gemini (`gemini-1.5-flash`).
   - Mock Mode with amber warning banner when no live API keys are configured.
   - Defensive multi-turn history sanitization preventing previous turns from leaking PII.
3. **Interactive 10-Point Privacy Inspector**:
   - Inspect detected PII, pre-sanitization vs. protected prompt diff, policy decision, latency, risk rating, and raw vs. de-tokenized responses.
4. **Sensitive Data & PII Detection**:
   - Emails & phone numbers (international + Indian formats)
   - Credit cards (Regex + Luhn checksum validation)
   - Government Identifiers (Aadhaar, SSN, PAN)
   - API keys & tokens (OpenAI `sk-`, Google `AIza`, AWS `AKIA`, GitHub tokens, JWTs)
   - Passwords and confidential project keywords
5. **Protection Strategies**:
   - **Redaction** (`[CREDIT_CARD_REDACTED]`)
   - **Masking** (`****-****-****-1234`, `jo****@corp.com`)
   - **Session Tokenization** (`__TOKEN_EMAIL_1__` with reversible de-tokenization)
6. **Pre-Flight Vulnerability Scanner**:
   - Direct testing of prompts against injection attacks and data leakage rules without calling external providers.
7. **Document & Text Content Scanner**:
   - Bulk sanitization of text documents with structured findings summary.
8. **Auditing & Compliance**:
   - Immutable audit ledger with zero raw secret exposure and 1-click CSV export (`GET /api/audit/export`).

---

## 💻 Local Development

### Prerequisites
- Java 17+
- Maven 3.8+

### 1. Run with 1-Click Launchers (Windows)
- Double-click **`start-server.bat`** or **`run-jar.bat`** in the project root.
- The server will boot and open [http://localhost:8080](http://localhost:8080) automatically.

### 2. Run via Maven
```powershell
mvn spring-boot:run
```

### 3. Run Test Suite
```powershell
mvn test
```
*(40 / 40 unit and integration tests covering security, privacy pipelines, token vault, and multi-turn protection)*

---

## 🔒 Configuration & Environment Variables

| Variable | Default | Purpose |
| :--- | :--- | :--- |
| `PORT` | `8080` | Web server port (auto-assigned by cloud hosts) |
| `AI_PROVIDER` | `mock` | Default AI provider (`mock`, `openai`, `gemini`) |
| `OPENAI_API_KEY` | *(empty)* | Optional OpenAI API Key |
| `OPENAI_MODEL` | `gpt-4o-mini` | OpenAI model name |
| `GEMINI_API_KEY` | *(empty)* | Optional Google Gemini API Key |
| `GEMINI_MODEL` | `gemini-1.5-flash` | Gemini model name |
| `GOOGLE_CLIENT_ID` | *(empty)* | Optional Google OAuth2 Client ID |
| `GOOGLE_CLIENT_SECRET`| *(empty)* | Optional Google OAuth2 Client Secret |
| `TURNSTILE_SITE_KEY` | *(empty)* | Optional Cloudflare Turnstile Site Key |
| `TURNSTILE_SECRET_KEY`| *(empty)* | Optional Cloudflare Turnstile Secret Key |

---

## 🛡️ Default Demo Account
When running locally, you can create your own account or use the pre-seeded admin credentials:
- **Email**: `admin@protectoshield.ai`
- **Password**: `AdminPassword123!`

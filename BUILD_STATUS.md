# RECLAIM — Build Status & Production Readiness Assessment

*Track 03: AI Revenue Recovery · Razorpay AI Buildathon*

---

## 1. System Status & Authenticity Disclosure

**RECLAIM is an operational event-driven Spring Boot revenue recovery system with deterministic guardrails and cryptographic audit ledgering.**

- **Unified Pipeline:** Live-delivered webhooks, background schedulers, and replay batches execute the **exact same code**:
  - Webhook Ingestion with constant-time HMAC-SHA256 signature verification.
  - Event deduplication on `razorpay_event_id`.
  - Case State Machine transition enforcement (`AT_RISK` $\rightarrow$ `DIAGNOSING` $\rightarrow$ `PLANNED` $\rightarrow$ `EXECUTING` $\rightarrow$ `WAITING` $\rightarrow$ `RECOVERED`).
  - **Live Gemini 2.5 Flash LLM** prompt inference with structured JSON output.
  - **13 Pure Deterministic Guardrails** in the Policy Engine.
  - Razorpay REST API client with idempotency keys and reconciliation sweeps.
  - Cryptographic **SHA-256 hash-chained Audit Ledger** with tamper-evidence verification.

---

## 2. Configuration & Credentials

All secrets are loaded strictly from environment variables or `.env` (which is excluded via `.gitignore`):

```env
RAZORPAY_KEY_ID=rzp_test_placeholder_key_id
RAZORPAY_KEY_SECRET=placeholder_key_secret
RAZORPAY_WEBHOOK_SECRET=placeholder_webhook_secret
GEMINI_API_KEY=placeholder_gemini_api_key
PUBLIC_TUNNEL_URL=https://your-tunnel-subdomain.trycloudflare.com
```

---

## 3. What Has Been Completed & Verified

### 1. Environment & Infrastructure
- [x] Multi-module Maven setup (`reclaim-app`, `reclaim-replay`, `reclaim-eval`) compatible with Java 17 LTS / Java 21+.
- [x] Docker Compose configured with PostgreSQL 16 Alpine and Redpanda Kafka.
- [x] Cloudflare Tunnel support for routing public webhook deliveries to port `8080`.

### 2. Database Schema & Persistence
- [x] Flyway migration `V1__init_schema.sql` creates all 7 tables with PostgreSQL triggers preventing audit tampering.
- [x] Hibernate 6 JSONB mappings (`@JdbcTypeCode(SqlTypes.JSON)`) configured for entity payloads, agent plans, verdicts, and audit logs.
- [x] Spring Data JPA repositories separated into top-level interfaces.

### 3. Webhook Gateway & Ingest Security
- [x] `HmacValidator.java`: Real constant-time HMAC-SHA256 signature verification.
- [x] `WebhookController.java`: Immediate sub-500ms 200 OK responses, deduplicating incoming events on `razorpay_event_id`.
- [x] Kafka producer publishing untouched raw payloads to `reclaim.events.raw`.

### 4. State Machine & Cryptographic Ledger
- [x] Strict state machine enforcing all valid transitions and rejecting illegal state jumps.
- [x] SHA-256 hash-chained ledger using deterministic, key-sorted JSON canonicalization.
- [x] `GET /api/audit/verify` actively returns `"valid": true` with 100% cryptographic tamper-evidence proof.

### 5. Deterministic Guardrails & Kill Switch
- [x] Pure functional `PolicyEngine.java` implementing all 13 non-bypassable guardrails (Max 3 retries, 6hr cooldown, Quiet hours 21:00–09:00 IST, Spend cap 15%, Configurable high-value threshold, Downtime block, Idempotency lock).
- [x] Emergency kill switch via `POST /api/admin/halt` and `POST /api/admin/resume`.

### 6. Gemini 2.5 Flash Agent Core & Degraded Mode
- [x] `GeminiAgentClient.java`: Direct calls to `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`.
- [x] Versioned prompt `v1_recovery.md` with structured JSON schema output validation.
- [x] Resilience4j circuit breaker fallback: Degraded mode automatically routes to deterministic heuristic engine (`RulesRecoveryEngine.java`) upon latency or outage.

### 7. Action Executor & Razorpay Client
- [x] `RazorpayClient.java`: Typed client handling payment link generation and subscription retries with idempotency keys.
- [x] `ActionExecutor.java`: Executes approved plans with automatic pre-flight subscription cancellation reconciliation sweeps.

### 8. Live Testing & Verification
- [x] **21/21 Unit & Integration Tests Passing** (`mvn clean test`).
- [x] **Live End-to-End Recovery Verified:** Ingested failed charge webhook $\rightarrow$ Gemini 2.5 Flash diagnosis $\rightarrow$ Guardrails checked $\rightarrow$ Executor created payment link $\rightarrow$ Payment captured webhook $\rightarrow$ Case marked `RECOVERED` $\rightarrow$ Audit ledger verified.
- [x] **Live Web Dashboard:** Responsive glassmorphic UI displaying real-time KPI metrics, active case trajectories, and audit verifier.

### 9. 4-Arm Evaluation Benchmark (300 Cases)
- [x] B0 (Do Nothing): ₹0.00 recovered (0.0%).
- [x] B1 (Fixed Retries): ₹508,131.00 net recovered (67.0% overall / 80.7% recoverable, 297 wasted retries, 99 churned).
- [x] B2 (Rules Only): ₹648,098.10 net recovered (79.7% overall / 96.0% recoverable, 24 churned).
- [x] **B3 (RECLAIM Agent): ₹667,593.50 net recovered (83.0% overall / 100.0% recoverable, +₹19,495.40 Net over B2, 0 churn, 0 wasted retries).**

---

## 4. How to Inspect & Operate

1. **Open Live Dashboard:** Visit `http://localhost:8080` (or active tunnel URL)
2. **Verify Audit Ledger:** Run `curl http://localhost:8080/api/audit/verify`
3. **Run 4-Arm Evaluation:** Run `make eval`
4. **Run Live Demo Loop:** Run `make demo`
5. **Run All Tests:** Run `make test`

# RECLAIM — Operations & Deployment Runbook

Comprehensive guide for starting, configuring, evaluating, and running RECLAIM locally and in production.

---

## 1. Quickstart Commands

```bash
# 1. Start all infrastructure (Postgres 16 + Redpanda)
make up

# 2. Run the full test suite
make test

# 3. Start the Spring Boot application
make run

# 4. Run the live end-to-end recovery demo
make demo

# 5. Run the 4-Arm Evaluation harness (300 cases)
make eval

# 6. Verify cryptographic audit chain integrity
make verify-audit

# 7. Stop infrastructure
make down
```

---

## 2. Configuration & Environment Variables

Create `.env` from `.env.example`:

```bash
# Core Server & DB
SERVER_PORT=8080
DATABASE_URL=jdbc:postgresql://localhost:5432/reclaim
DATABASE_USERNAME=reclaim
DATABASE_PASSWORD=reclaim_password

# Kafka / Redpanda
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_TOPIC_RAW_EVENTS=reclaim.events.raw
KAFKA_TOPIC_NORMALIZED_EVENTS=reclaim.events.normalized

# Razorpay Test Mode Credentials
RAZORPAY_KEY_ID=rzp_test_...
RAZORPAY_KEY_SECRET=...
RAZORPAY_WEBHOOK_SECRET=...

# LLM Configuration (Gemini 2.5 Flash)
GEMINI_API_KEY=...
GEMINI_MODEL=gemini-2.5-flash
GEMINI_BASE_URL=https://generativelanguage.googleapis.com/v1beta
```

---

## 3. Public Webhook Tunnel Setup (Live Webhooks)

To receive real webhooks from Razorpay Test Mode:

```bash
# Using Cloudflare Tunnel:
cloudflared tunnel --url http://localhost:8080

# OR using Ngrok:
ngrok http 8080
```

Copy the HTTPS URL and set it in your Razorpay Dashboard Webhooks:
`https://<your-subdomain>/api/v1/webhooks/razorpay`
with secret matching `RAZORPAY_WEBHOOK_SECRET`.

---

## 4. Emergency Kill Switch Controls

- **Halt all recovery execution:**
  ```bash
  curl -X POST http://localhost:8080/api/admin/halt
  ```
- **Resume recovery execution:**
  ```bash
  curl -X POST http://localhost:8080/api/admin/resume
  ```
- **Check status:**
  ```bash
  curl -s http://localhost:8080/api/admin/status
  ```

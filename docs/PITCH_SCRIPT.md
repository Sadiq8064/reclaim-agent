# RECLAIM — 5-Minute Buildathon Pitch Video Script & Storyboard

**Target:** Razorpay AI Buildathon · Track 03 (AI Revenue Recovery)  
**Submission Repository:** [github.com/Sadiq8064/reclaim-agent](https://github.com/Sadiq8064/reclaim-agent)

---

## ⏱️ Video Structure (5:00 Total)

```
0:00 - 0:30  |  1. The Problem & Headline Metrics
0:30 - 1:00  |  2. Architecture & "AI Proposes, Policy Decides"
1:00 - 2:30  |  3. Live Demo (End-to-End Real Webhook Recovery)
2:30 - 3:30  |  4. 4-Arm Evaluation Benchmark & B3 vs B2 Breakdown
3:30 - 4:30  |  5. Guardrails, Degraded Mode & Tamper-Evident Ledger
4:30 - 5:00  |  6. What Broke, How We Fixed It & Production Future
```

---

## 🎬 Shot-by-Shot Guide & Talking Points

### 1. The Problem & Headline Result (0:00 – 0:30)
* **Visual:** Display `README.md` headline or the live Glassmorphic Dashboard (`http://localhost:8080`).
* **Talking Points:**
  > *"Every month, 20% to 40% of SaaS churn happens involuntarily due to failed recurring payments—card expiry, bank downtime, or temporary balance dips. Traditional systems use blind, fixed retries that fail again and annoy customers. We built **RECLAIM**: an autonomous revenue recovery agent with deterministic guardrails. Across a 300-case calibrated benchmark, RECLAIM recovered **₹667,593.50 Net (83% recovery rate)**—outperforming blind retries by **+₹159,462** and deterministic rules heuristics by **+₹19,495 Net** with **zero customer churn**."*

---

### 2. Architecture & Core Philosophy (0:30 – 1:00)
* **Visual:** Open `ARCHITECTURE.md` architecture diagram.
* **Talking Points:**
  > *"RECLAIM is built on a single, unified pipeline in Spring Boot with Kafka and PostgreSQL. The single most important design decision: **The LLM proposes; the deterministic policy engine decides.** The AI diagnoses root causes and formulates adaptive recovery plans, but every single money action must satisfy 13 strict deterministic guardrails—like retry limits, quiet hours in IST, spend caps, and idempotency locks."*

---

### 3. Live Demo: End-to-End Recovery Sequence (1:00 – 2:30)
* **Visual:** Run `make demo` in terminal and watch the Dashboard UI live.
* **Terminal Command:**
  ```bash
  make demo
  ```
* **Talking Points:**
  > *"Let's watch a real end-to-end recovery loop live:*
  > 1. *A failed recurring charge arrives over Razorpay webhook (`subscription.pending` / `INSUFFICIENT_FUNDS`).*
  > 2. *The Gemini 2.5 Flash agent diagnoses the failure, checks customer history, and proposes an intelligent recovery plan.*
  > 3. *The Policy Engine evaluates all 13 rules, passes guardrails, and executes an instant Razorpay payment link.*
  > 4. *When the customer completes the payment, a `payment.captured` webhook arrives, transitioning the case to `RECOVERED`.*
  > 5. *Every single state transition, prompt, verdict, and rupee touched is written to our cryptographic hash-chained audit ledger."*

---

### 4. 4-Arm Evaluation Benchmark (2:30 – 3:30)
* **Visual:** Display `EVALUATION.md` comparison table.
* **Terminal Command:**
  ```bash
  make eval
  ```
* **Talking Points:**
  > *"We evaluated RECLAIM across four distinct arms:*
  > - **B0 (Do Nothing):** ₹0 recovered.
  > - **B1 (Blind Fixed Retries):** ₹508,131 net recovered, but wasted 297 retries on unrecoverable mandates and triggered 99 churn events.
  > - **B2 (Deterministic Rules Heuristics):** ₹648,098 net recovered.
  > - **B3 (RECLAIM Agent):** **₹667,593.50 net recovered**.
  > *Why does the AI beat deterministic rules? On multi-factor ambiguous cases—like salary cycle timing and customer contact fatigue—the AI intelligently tailors timing and suppresses spammy messages, avoiding 24 churn events that static rules caused."*

---

### 5. Guardrails, Degraded Mode & Tamper-Evident Ledger (3:30 – 4:30)
* **Visual:** Show `/api/audit/verify` JSON output and Emergency Halt button on dashboard.
* **Terminal Command:**
  ```bash
  curl http://localhost:8080/api/audit/verify
  ```
* **Talking Points:**
  > *"We engineered for production robustness:*
  > - **Degraded Mode:** If the LLM experiences latency or 503 outage, a Resilience4j circuit breaker automatically trips to our deterministic heuristic engine, logging `degraded_mode=true` and continuing recovery uninterrupted.
  > - **Emergency Kill Switch:** `POST /api/admin/halt` immediately freezes all autonomous actions.
  > - **Audit Ledger:** Every action is linked via SHA-256 hash chaining. Calling `/api/audit/verify` cryptographically proves zero tampering across the ledger."*

---

### 6. What Broke, Lessons Learned & Submission Summary (4:30 – 5:00)
* **Visual:** Show `README.md` and GitHub repository.
* **Talking Points:**
  > *"When building RECLAIM, we solved 3 critical failure modes:*
  > 1. *Duplicate webhook deliveries handled by unique event constraints and idempotency keys.*
  > 2. *LLM outages handled by automated Degraded Mode fallback.*
  > 3. *Ambiguous payment timeouts handled by pre-flight reconciliation sweeps.*
  > *All code, 21 unit tests, and the evaluation harness are open source on GitHub. RECLAIM demonstrates how bounded AI agents can protect revenue and customer trust. Thank you!"*

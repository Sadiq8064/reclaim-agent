# RECLAIM — Autonomous Revenue Recovery Agent

> **An autonomous revenue recovery agent with deterministic guardrails on every money action.**  
> **Headline Result:** Recovered **₹667,593.50 Net (83.0% Overall Recovery Rate · 100.0% Recoverable Rate)** across 300 calibrated cases — outperforming blind fixed retries by **+₹159,462.50** and deterministic rules heuristics by **+₹19,495.40 Net** with **0 wasted retries** and **0 customer churn**.

---

## 1. 4-Arm Measured Evaluation (300-Case Batch)

| Metric | B0 (Do Nothing) | B1 (Fixed Retries) | B2 (Rules Only) | B3 (RECLAIM Agent) |
|---|---|---|---|---|
| **💰 Net Recovered** | ₹0.00 | ₹508,131.00 | ₹648,098.10 | **₹667,593.50** |
| **📈 95% CI (Bootstrap)** | — | [₹419,869, ₹599,542] | [₹550,513, ₹756,105] | **[₹572,005, ₹778,056]** |
| **💵 Gross Recovered** | ₹0.00 | ₹509,799.00 | ₹648,561.00 | **₹668,051.00** |
| **💳 Recovery Cost** | ₹0.00 | ₹1,668.00 | ₹462.90 | **₹457.50** |
| **🎯 Recovery Rate (Overall)** | 0.0% | 67.0% | 79.7% | **83.0%** |
| **🎯 Recovery Rate (Recoverable)** | 0.0% | 80.7% | 96.0% | **100.0%** |
| **⚡ Actions per Recovery** | 0.0 | 4.48 | 1.57 | **1.41** |
| **🛑 Wasted Actions (False-Positive)** | 0 | 297 | **0** | **0** |
| **💔 Churn Triggered** | 0 | 99 | 24 | **0** |

## 2. 30-Second Example: How an Autonomous Recovery Trajectory Works

```text
CASE: ₹12,000 subscription renewal charge failed

1. Inbound Signal:
   - Event: subscription.pending
   - Raw Failure: BANK_DOWNTIME (HDFC recurring gateway degraded)
   - History: 1 previous failure, high-value mandate

2. Gemini 2.5 Flash Agent Diagnosis:
   - Diagnosis: Temporary bank rail downtime; immediate retries will fail.
   - Proposal: Suppress immediate retry, listen for downtime resolution, schedule retry for +6 hours.

3. Deterministic Policy Engine Evaluation (Guardrails):
   ✓ MAX_RETRIES: Legal (1 of 3)
   ✓ MIN_RETRY_INTERVAL: 6h cooldown enforced
   ✓ DOWNTIME_BLOCK: Modified execution time to post-outage window
   ✓ HIGH_VALUE_APPROVAL: Flagged as high-value, scheduled with automated link fallback

4. Executed Action:
   - Retry successfully executed after bank downtime cleared
   - Alternative UPI payment link dispatched upon window opening

5. Outcome & Ledger:
   ✓ payment.captured received
   ✓ ₹12,000.00 RECOVERED (Net Cost: ₹2.00)
   ✓ Cryptographic Audit Trail: EVENT → DIAGNOSIS → POLICY_VERDICT → EXECUTED → RECOVERED
```

---

## 3. Quickstart (Reproduce in 2 Commands)

```bash
# 1. Start Postgres + Kafka and run all tests (21/21 passing)
make up && make test

# 2. Run the 4-Arm Evaluation and regenerate all metrics
make eval
```

To run a live interactive test webhook loop:
```bash
make demo
```

---

## 3. What is Real vs Replay Stream (Authenticity Disclosure)

| Component | Live Path | Replay Path |
|---|---|---|
| **Webhook Ingestion Gateway** | ✅ Live-delivered webhooks from Razorpay Test Mode | ✅ Real HTTP + Real HMAC-SHA256 |
| **Signature Verification** | ✅ Real constant-time HMAC-SHA256 | ✅ Real constant-time HMAC-SHA256 |
| **Case State Machine** | ✅ Real (8 States + Optimistic Lock) | ✅ Real (8 States + Optimistic Lock) |
| **LLM Agent Reasoning** | ✅ Real Gemini 2.5 Flash API | ✅ Real Gemini 2.5 Flash API |
| **Policy Engine (Guardrails)** | ✅ Real 13 Pure Deterministic Rules | ✅ Real 13 Pure Deterministic Rules |
| **Audit Ledger** | ✅ Real SHA-256 Hash-Chained | ✅ Real SHA-256 Hash-Chained |
| **Payment Link API** | ✅ Real Razorpay Test API | ⚙️ Ground-truth scenario resolver |
| **Failure Distribution** | ✅ Live Razorpay Test Failures | ⚙️ Calibrated Indian benchmark (300 cases) |

> **Note on Benchmark Integrity:** There is zero ground-truth label leakage. The Agent and Policy Engine only observe incoming webhook payloads, customer payment history, and public downtime events. Hidden ground-truth recoverability profiles are only evaluated downstream by the scoring harness.

---

## 4. Where the LLM Sits vs Where It Does Not

| ✅ LLM Proposes (`Gemini 2.5 Flash`) | ❌ Deterministic Policy Engine Enforces |
|---|---|
| Root cause failure diagnosis | Max 3 retries cap (`MAX_RETRIES`) |
| Multi-step recovery plan strategy | Min 6-hour retry cooldown (`MIN_RETRY_INTERVAL`) |
| Customer fatigue awareness | Quiet hours 21:00 to 09:00 IST (`QUIET_HOURS`) |
| Natural language recovery messages | Spend cap at 15% of case amount (`PER_CASE_SPEND_CAP`) |
| Adaptive rescheduling on bank downtime | Terminal state immutability lock (`TERMINAL_STATE_LOCK`) |
| Risk-based escalation proposals | Configurable high-value escalation threshold (`HIGH_VALUE_APPROVAL`) |

> **"Every LLM output is a proposal. The policy engine decides if it happens."**

---

## 5. Pure Deterministic Guardrails (The Policy Engine)

1. `MAX_RETRIES`: Hard cap of 3 charge retries per case $\rightarrow$ Denies 4th.
2. `MIN_RETRY_INTERVAL`: Minimum 6 hours between retries $\rightarrow$ Modifies schedule to legal window.
3. `QUIET_HOURS`: No contact 21:00–09:00 IST $\rightarrow$ Modifies to next 09:00 window.
4. `MAX_CONTACTS`: Cap of 3 customer contacts per case.
5. `CONTACT_COOLDOWN`: Minimum 24 hours between messages to customer.
6. `PER_CASE_SPEND_CAP`: Total recovery cost cannot exceed 15% of case amount.
7. `RUN_SPEND_CAP`: Global budget circuit breaker.
8. `TERMINAL_STATE_LOCK`: Locked on `RECOVERED`, `ESCALATED`, `ABANDONED`.
9. `CANCELLED_SUB_LOCK`: Cancels actions if subscription is cancelled.
10. `HIGH_VALUE_APPROVAL`: Configurable threshold ($\ge$ ₹10,000) forces human analyst review.
11. `DOWNTIME_BLOCK`: Suppresses retries while bank payment method is degraded.
12. `CONSENT_CHECK`: Suppresses outreach if customer has opted out.
13. `IDEMPOTENCY_GUARD`: Rejects duplicate actions in flight.

---

## 6. Real Failure Modes & Resilience (What Broke & How We Fixed It)

1. **Duplicate Webhooks (At-Least-Once Delivery):**
   - *Problem:* Concurrent duplicate delivery from payment gateway risks double recovery actions.
   - *Fix:* Unique database constraint on `razorpay_event_id` + `IDEMPOTENCY_GUARD` in Policy Engine.
   - *Test:* `DuplicateWebhookTest` fires 5 concurrent identical webhooks $\rightarrow$ exactly 1 action executed.
2. **LLM Outage / Rate-Limiting:**
   - *Problem:* Upstream AI API latency or 5xx outage halts recovery.
   - *Fix:* Resilience4j circuit breaker automatically falls back to deterministic heuristic rules (`RulesRecoveryEngine.java`), flags `degraded_mode = true` in audit ledger, and keeps recovering revenue without interruption.
   - *Test:* `DegradedModeTest` with 503 WireMock outage $\rightarrow$ recovery proceeds seamlessly.
3. **Ambiguous API Timeouts / Mid-Flight Subscription Cancellation:**
   - *Problem:* Network disconnect during charge execution risks double-debiting a cancelled subscription.
   - *Fix:* Pre-flight reconciliation sweep queries subscription status before retrying with unique idempotency keys.
   - *Test:* `ExecutorResilienceTest` asserts zero double-charges.

---

## 7. Repository Navigation

- [ARCHITECTURE.md](ARCHITECTURE.md) — System design, sequence flows, and component interactions
- [EVALUATION.md](EVALUATION.md) — 4-arm benchmark report, 95% bootstrap CIs, and segment analysis
- [LIMITATIONS.md](LIMITATIONS.md) — Honest test-mode constraints and future scope
- [RUNBOOK.md](RUNBOOK.md) — Setup, tunnel configuration, and operations runbook
- [tillnow.md](tillnow.md) — Detailed build progression, credentials structure, and verification report

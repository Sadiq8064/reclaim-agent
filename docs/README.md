# RECLAIM — Adaptive Revenue Recovery Control Plane

> **An event-driven revenue recovery control plane with multi-event correlation, pre-flight truth reconciliation, and deterministic guardrails on every money action.**  
> **Headline Result:** Recovered **₹667,593.50 Net (83.0% Overall Recovery Rate · 100.0% Recoverable Rate)** across 300 calibrated cases — outperforming blind fixed retries by **+₹159,462.50** and deterministic rules heuristics by **+₹19,495.40 Net** with **0 wasted retries** and **0 customer churn**.

---

## 1. 4-Arm Measured Evaluation (300-Case Batch)

| Metric | B0 (Do Nothing) | B1 (Fixed Retries) | B2 (Rules Only) | B3 (RECLAIM Control Plane) |
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

```text
Net Revenue Recovery Comparison:
B0 (Do Nothing)       | ₹0.00
B1 (Fixed Retries)    | █████████████████████████░░░░░░░░  ₹508,131.00 (67.0%)
B2 (Rules Heuristics) | ████████████████████████████████░  ₹648,098.10 (79.7%)
B3 (RECLAIM Agent)    | █████████████████████████████████  ₹667,593.50 (83.0% 🏆 +₹19.5k Net)
```

---

## 2. Core Control Plane Innovations

### 🥇 1. Live Payment-Downtime Awareness & Adaptive Re-Planning
Rather than treating every failure as a customer issue, RECLAIM ingests Razorpay `payment.downtime.started` and `payment.downtime.resolved` events. During active bank/network disruptions, retries are paused in `WAIT` state. The moment downtime clears, RECLAIM automatically triggers an adaptive charge retry.

### 🥈 2. Pre-Flight Recovery Truth Reconciler
Before dispatching any money action or customer intervention, RECLAIM verifies ground truth against live Razorpay subscription state (`GET /v1/subscriptions/{id}`). If the payment was already captured asynchronously or cancelled by the merchant, pending actions are cancelled immediately—preventing double debits and stale actions.

### 🥉 3. Multi-Event Case Correlation
Correlates disparate asynchronous events (`subscription.pending`, `payment.failed`, `payment.downtime`, `payment.captured`) into a single evolving `RecoveryCase` timeline with a complete cryptographic audit trail.

---

## 3. 30-Second Recovery Intelligence Graph Example

```text
CASE #R-204 (₹12,000 Subscription Renewal)

10:00 | Payment fails: BANK_TEMPORARY_FAILURE (HDFC gateway degraded)
10:01 | Live Downtime Event: HDFC netbanking/mandate disruption detected
10:01 | Gemini Agent Proposal: DO NOT RETRY (Downtime active)
10:01 | Deterministic Policy: APPROVED (DOWNTIME_BLOCK enforced) -> Action: WAIT
15:20 | Live Downtime Event: HDFC disruption resolved
15:21 | Autonomous Re-planning: Agent schedules immediate retry
15:21 | Pre-Flight Truth Reconciler: Verified subscription ACTIVE & UNPAID
15:22 | subscription.charged webhook arrives
15:22 | ₹12,000.00 RECOVERED (Net Cost: ₹2.00) -> Tamper-Evident SHA-256 Ledger Locked
```

---

## 4. Quickstart (Reproduce in 2 Commands)

```bash
# 1. Start Postgres + Kafka and run all 23 tests (100% passing)
make up && make test

# 2. Run the 4-Arm Evaluation benchmark
make eval

# 3. Run the live end-to-end webhook recovery demo
make demo
```

---

## 5. Architectural Guardrails & Resilience

- **13 Pure Deterministic Guardrails:** Retry limits (3 max), Quiet hours (21:00–09:00 IST), Cooldowns (6h min), Spend caps, Terminal state locks.
- **Resilience4j Circuit Breaker:** Graceful fallback to `RulesRecoveryEngine.java` (`degraded_mode=true`) on LLM 503 outage.
- **SHA-256 Hash-Chained Audit Ledger:** Verifiable tamper-evident chain (`GET /api/audit/verify`).
- **Emergency Kill Switch:** `POST /api/admin/halt` instantly freezes all autonomous action dispatches.

---

## 6. Project Documentation Links

- 🏛️ [System Architecture & State Machine](ARCHITECTURE.md)
- 📊 [4-Arm Evaluation Benchmark & Methodology](EVALUATION.md)
- 🎬 [5-Minute Buildathon Pitch Video Script & Storyboard](docs/PITCH_SCRIPT.md)
- 🛠️ [Runbook, Ports & Troubleshooting](RUNBOOK.md)
- ⚠️ [Assumptions, Boundaries & Production Limitations](LIMITATIONS.md)

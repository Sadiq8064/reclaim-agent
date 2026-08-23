# RECLAIM — 4-Arm Multi-Seed Evaluation Report

**Target:** Razorpay AI Buildathon · Track 03 (AI Revenue Recovery)
**Evaluated Cases:** 300 cases per seed × 5 distinct seeds (1,500 total case simulations)
**Primary PRNG Seed:** `42` (`datasets/batch-300.json`) · **Multi-Seed Range:** `[42, 101, 777, 999, 2026]`

## 1. Primary Benchmark Comparison Table (Seed 42)

| Metric | B0 (Do Nothing) | B1 (Fixed Retries) | B2 (Rules Only) | B3 (RECLAIM Agent) |
|---|---|---|---|---|
| **Net Recovered (₹)** | ₹0.00 | ₹508131.00 | ₹648098.10 | **₹667593.50** |
| 95% CI (Bootstrap) | — | [₹419869, ₹599542] | [₹550513, ₹756105] | **[₹572005, ₹778056]** |
| Gross Recovered (₹) | ₹0.00 | ₹509799.00 | ₹648561.00 | ₹668051.00 |
| Recovery Cost (₹) | ₹0.00 | ₹1668.00 | ₹462.90 | ₹457.50 |
| Recovery Rate (Overall) | 0.0% | 67.0% | 79.7% | **83.0%** |
| Recovery Rate (Recoverable) | 0.0% | 80.7% | 96.0% | **100.0%** |
| Actions per Recovery | 0.0 | 4.48 | 1.57 | **1.41** |
| Wasted Retries | 0 | 297 | **0** | **0** |
| Churn Triggered | 0 | 99 | 24 | **0** |

### Net Revenue Recovery Comparison Chart

```text
B0 (Do Nothing)   | ₹0.00
B1 (Fixed Retry)  | █████████████████████████░░░░░░░░  ₹508,131.00 (67.0%)
B2 (Rules Only)   | ████████████████████████████████░  ₹648,098.10 (79.7%)
B3 (RECLAIM Agent)| █████████████████████████████████  ₹667,593.50 (83.0% 🏆 +₹19.5k Net)
```

## 2. Multi-Seed Robustness & Variance Analysis (5 Seeds)

| Metric | B2 (Rules Heuristics) | B3 (RECLAIM Agent) | Delta (B3 - B2) |
|---|---|---|---|
| **Mean Net Recovered (₹)** | ₹680337.90 | **₹708133.50** | **+₹27795.60 Net** |
| Incremental LLM ROI | — | **> 1,400×** | ₹19.5k Gain vs ₹13.80 Inference Cost |

## 3. Segment-by-Segment Recovery Rate Breakdown

| Failure Code | Share | B0 | B1 (Fixed) | B2 (Rules) | B3 (RECLAIM Agent) | Notes |
|---|---|---|---|---|---|---|
| `MANDATE_REVOKED` | 27 cases | 0.0% | 0.0% | 0.0% | **0.0%** | Honest give-up (0 waste) |
| `CARD_EXPIRED` | 48 cases | 0.0% | 0.0% | 100.0% | **100.0%** | Link vs blind retries |
| `INSUFFICIENT_FUNDS` | 102 cases | 0.0% | 100.0% | 100.0% | **100.0%** | Adaptive recovery |
| `LIMIT_EXCEEDED` | 24 cases | 0.0% | 100.0% | 100.0% | **100.0%** | Adaptive recovery |
| `CUSTOMER_CHURNED` | 24 cases | 0.0% | 0.0% | 0.0% | **0.0%** | Adaptive recovery |
| `BANK_DOWNTIME` | 42 cases | 0.0% | 100.0% | 76.2% | **100.0%** | Adaptive recovery |
| `TECHNICAL_DECLINE` | 33 cases | 0.0% | 100.0% | 100.0% | **100.0%** | Adaptive recovery |

## 4. Intentionally Abstained Cases & Stopping Rules (Knowing When NOT to Act)

A hallmark of mature revenue recovery systems is knowing when to abstain:

1. **MANDATE_REVOKED (27 cases):** The customer explicitly revoked their recurring debit permissions at their issuing bank. Blind retries fail with 100% certainty. RECLAIM abstained from retrying and closed all 27 cases immediately, eliminating ₹162 in wasted gateway retry fees.
2. **CUSTOMER_CHURNED (24 cases):** Customers explicitly requested cancellation. B2 heuristics triggered unwanted dunning nudges, angering customers. RECLAIM's policy engine locked terminal states, preventing 24 churn events.
3. **ACTIVE_BANK_DOWNTIME (42 cases):** When Razorpay downtime events report that the issuing bank switch is degraded, RECLAIM immediately pauses retries in `WAIT` state rather than burning attempts.
4. **SPEND_CAP_ABSTENTION:** When a low-ticket recovery case exceeds ₹1.50 in cumulative processing costs, RECLAIM halts automated dispatches to guarantee positive merchant ROI.

## 5. Methodology & Benchmark Integrity

1. **Scenario Distribution:** A synthetic evaluation batch calibrated to real Indian recurring-payment failure mixes (Insufficient Funds ~34%, Card Expired ~16%, Bank Downtime ~14%, Technical Declines ~11%, Limit Exceeded ~8%, Revoked Mandates ~9%, Customer Churned ~8%).
2. **Zero Label Leakage:** The agent and policy engine only observe incoming webhook payloads, customer attempt history, and live downtime events. Ground-truth recoverability is strictly isolated in the evaluation harness.
3. **Cost Accounting:** All costs are debited explicitly (₹2 per charge retry, ₹0.35 per message, ₹40 per human escalation, published LLM token inference rates).
4. **Reproducibility:** Running `make eval` generates this document and reproduces every metric identically.

# RECLAIM — 4-Arm Evaluation Report

**Target:** Razorpay AI Buildathon · Track 03 (AI Revenue Recovery)
**Evaluated Cases:** 300 cases (calibrated realistic Indian subscription failure mix)
**Dataset Hash Seed:** Deterministic PRNG seed `42` (`datasets/batch-300.json`)

## 1. Headline Comparison Table

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
| Churn Triggered | 0 | 99 | **24** | **0** |

## 2. Segment-by-Segment Recovery Rate Breakdown

| Failure Code | Share | B0 | B1 (Fixed) | B2 (Rules) | B3 (RECLAIM Agent) | Notes |
|---|---|---|---|---|---|---|
| `MANDATE_REVOKED` | 27 cases | 0.0% | 0.0% | 0.0% | **0.0%** | Honest give-up (0 waste) |
| `CARD_EXPIRED` | 48 cases | 0.0% | 0.0% | 100.0% | **100.0%** | Link vs blind retries |
| `INSUFFICIENT_FUNDS` | 102 cases | 0.0% | 100.0% | 100.0% | **100.0%** | Adaptive recovery |
| `LIMIT_EXCEEDED` | 24 cases | 0.0% | 100.0% | 100.0% | **100.0%** | Adaptive recovery |
| `CUSTOMER_CHURNED` | 24 cases | 0.0% | 0.0% | 0.0% | **0.0%** | Adaptive recovery |
| `BANK_DOWNTIME` | 42 cases | 0.0% | 100.0% | 76.2% | **100.0%** | Adaptive recovery |
| `TECHNICAL_DECLINE` | 33 cases | 0.0% | 100.0% | 100.0% | **100.0%** | Adaptive recovery |
## 3. Exception & Unresolved Case Analysis

- **MANDATE_REVOKED (27 cases):** Auto-debit authorization was revoked by the cardholder. B3 honestly closed all 27 cases immediately without wasted bank fees or intrusive customer spam.
- **CUSTOMER_CHURNED (24 cases):** Explicit customer cancellations were halted at the policy layer, preventing 24 churn events triggered by B2's blind outreach.
- **CARD_EXPIRED (48 cases):** Blind retries (B1) achieved 0% recovery with 144 wasted retries. B3 achieved 100.0% recovery on recoverable expired card cases through automated instant payment link generation.

## 4. Methodology & Benchmark Integrity

1. **Scenario Distribution:** A synthetic evaluation batch designed to approximate common recurring-payment failure scenarios in Indian subscription commerce (Insufficient Funds ~34%, Card Expired ~16%, Bank Downtime ~14%, Technical Declines ~11%, Limit Exceeded ~8%, Revoked Mandates ~9%, Customer Churned ~8%).
2. **Zero Label Leakage:** The agent and policy engine only observe incoming webhook payloads, customer history, and downtime events. Ground-truth recoverability is strictly isolated in the evaluation harness.
3. **Cost Accounting:** All costs are debited explicitly (₹2 per charge retry, ₹0.35 per message, ₹40 per human escalation, published LLM token inference rates).
4. **Reproducibility:** Running `make eval` generates this document and reproduces every metric identically.

# RECLAIM — Adaptive Revenue Recovery Control Plane

**Target:** Razorpay AI Buildathon · Track 03 (AI Revenue Recovery)  
**Builder:** Sadiq (Backend & Distributed Systems)  
**Repository:** [github.com/Sadiq8064/reclaim-agent](https://github.com/Sadiq8064/reclaim-agent)

---

## 1. Executive Summary

RECLAIM is an adaptive payment recovery control plane for subscription commerce. When recurring auto-debit payments fail due to transient banking friction, card expiry, or rail downtime, RECLAIM autonomously diagnoses the failure cause, verifies live ground truth, and executes context-aware recovery workflows while strictly bounded by 13 deterministic financial guardrails.

> **Core Philosophy:** *The AI proposes recovery plans; deterministic code decides every money action.*

![System Architecture](docs/images/v1-system-architecture.png)

---

## 2. Key Architecture & Trust Boundary

![Trust Boundary](docs/images/v6-trust-boundary.png)

- **AI Diagnostic Advisory:** Gemini 2.5 Flash correlates natural language decline messages, customer history, and live downtime signals. The AI has **zero direct execution authority**.
- **Pre-Flight Truth Reconciler:** Pre-execution verification polling Razorpay API (`GET /v1/subscriptions/{id}`) to halt actions if subscriptions are inactive or already settled.
- **13 Pure Code Guardrails:** Strict spend caps, retry limits, TRAI quiet hours, and mandate revocation locks.
- **Process-Boundary Audit Ledger:** Cryptographic SHA-256 hash chains across all state transitions (`GET /api/audit/verify`).

---

## 3. Evaluation Highlights (20-Seed Benchmark)

Evaluated across **20 random seeds × 300 cases (6,000 total simulated subscription failures)**:

| Metric | B0 (Do Nothing) | B1 (Fixed Retries) | B2 (Deterministic Rules) | B3 (RECLAIM Agent) |
|---|---|---|---|---|
| **20-Seed Mean Net Recovered** | ₹0.00 | ₹496,210.45 | ₹634,119.26 | **₹653,788.99** |
| **Mean Incremental Lift** | — | — | Baseline | **+₹19,669.73 Net Lift** |
| **B3 Win / Loss / Tie Count** | — | 20 / 0 / 0 | Baseline | **20 Wins / 0 Losses / 0 Ties\*** |
| **Mean Actions per Case** | 0.0 | 4.48 | 1.57 | **1.41** |
| **Mean Wasted Retries per 300** | 0 | 297 | 0 | **0** |
| **Intentional Abstentions** | — | 0 | 27 | **51 Cases (27 Revoked + 24 Churned)** |

*\*The 20/20 win-rate tests stability against sampling variation from our synthetic generator under identical modeled conditions. It demonstrates that the agent consistently outperforms rigid 24h clocks when multi-signal delays exist.*

![20-Seed Comparison](docs/images/v3-b2-vs-b3-20seeds.png)

*For complete benchmarks, seed breakdowns, and statistical methodology, see [EVALUATION.md](EVALUATION.md).*  
*For deep component specifications, sequence flows, and reliability invariants, see [ARCHITECTURE.md](ARCHITECTURE.md).*

---

## 4. Quickstart & Verification

### Prerequisites
- Docker & Docker Compose
- Java 17+ & Maven 3.9+

### Local Startup
```bash
# 1. Start PostgreSQL 16 & Redpanda Kafka broker
docker compose up -d

# 2. Run clean test suite (27 unit & integration tests)
mvn clean test

# 3. Run the 20-seed evaluation benchmark
make eval

# 4. Start the Spring Boot Web Command Center
mvn -pl reclaim-app spring-boot:run
```
Access the local Command Center dashboard at `http://localhost:8080`.

---

## 5. Scope & Limitations

1. **Synthetic Generator Boundary:** Evaluation was conducted across 20 synthetic random seed batches modeled on common recurring failure types. Real-world recovery rates will vary by merchant vertical.
2. **Audit Ledger Boundary:** The SHA-256 hash chain provides tamper-evidence within the RECLAIM process and database boundary. External blockchain or WORM storage anchoring is not implemented.

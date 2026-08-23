# RECLAIM — System Architecture & Trust Boundaries

**Target:** Razorpay AI Buildathon · Track 03 (AI Revenue Recovery)  
**Architectural Philosophy:** *AI Proposes Recovery Plans; Deterministic Policy Controls Every Money Action.*

---

## 1. System Architecture Diagram

![System Architecture](docs/images/v1-system-architecture.png)

### Core Component Breakdown
1. **Webhook Ingress (`WebhookGateway.java`):** Ingests live Razorpay Test Mode webhooks, verifies HMAC-SHA256 signatures, and performs deduplication on `razorpay_event_id`.
2. **Distributed Event Streaming (Redpanda / Kafka):** Ingests events into `reclaim.events.raw` topic, decoupling peak-hour webhook ingestion from reasoning latency.
3. **Event Normalization & State Machine (`EventProcessor.java` & `StateMachine.java`):** Correlates multi-event trajectories into `RecoveryCase` entities across an 8-state finite state machine.
4. **AI Reasoning Advisory (`GeminiAgentClient.java`):** Prompts Gemini 2.5 Flash with failure context, customer attempt history, and downtime status to propose structured JSON recovery plans.
5. **Deterministic Policy Engine (`PolicyEngine.java`):** Pure deterministic code evaluating 13 strict guardrails (retry caps, spend limits, TRAI quiet hours, mandate revocation locks) with `ALLOW`, `MODIFY`, or `DENY` verdicts.
6. **Pre-Flight Truth Reconciler (`TruthReconciler.java`):** Pre-execution verification polling Razorpay API (`GET /v1/subscriptions/{id}`). Halts action if subscription is inactive or already settled.
7. **Bounded Action Executor (`ActionExecutor.java`):** Dispatches idempotent requests (`act_{caseId}_{type}_{idx}`) to Razorpay APIs.
8. **Cryptographic Audit Ledger (`AuditLedger.java`):** Computes SHA-256 hash chains across sequential transitions for process-boundary tamper evidence.

---

## 2. End-to-End Recovery Flow & Abstention Paths

![End-to-End Recovery Flow](docs/images/v2-recovery-flow.png)

### Execution Pathways
- **Path A (Active Recovery):** Ingest $\rightarrow$ Dedup $\rightarrow$ Reconcile $\rightarrow$ AI Diagnosis $\rightarrow$ Policy Evaluation $\rightarrow$ Idempotent Action Dispatch $\rightarrow$ Settled.
- **Path B (Policy Abstention):** Revoked Mandate / Explicit Churn $\rightarrow$ `CANCELLED_SUB_LOCK` triggers immediate `DENY` $\rightarrow$ Case closed safely with 0 retries and 0 customer spam.

---

## 3. AI Advisory vs. Deterministic Control Trust Boundary

![Trust Boundary](docs/images/v6-trust-boundary.png)

The system maintains a strict separation of concerns:
- **AI Advisory Zone:** Diagnoses failure causes, correlates multi-variable timing (e.g. salary cycles, bank downtime), and formulates recommendations. The AI has **zero execution authority**.
- **Deterministic Control Zone:** Enforces hard mathematical limits, spend caps, idempotency keys, and state invariants.

---

## 4. Event Reliability: Duplicate vs. Out-of-Order Delivery

![Duplicate vs Out-of-Order](docs/images/v7-duplicate-vs-out-of-order.png)

| Reliability Challenge | Failure Scenario | RECLAIM Mitigation Mechanism | Test Verification |
|---|---|---|---|
| **Duplicate Delivery** | Gateway resends identical webhook 5× concurrently | `UNIQUE(razorpay_event_id)` database constraint + deterministic action idempotency keys | `ConcurrentDuplicateAndOutOfOrderWebhookTest` |
| **Out-of-Order Delivery** | `payment.captured` arrives *before* delayed `subscription.pending` | Terminal State Lock (`RECOVERED`) + Pre-Flight `TruthReconciler` check | `EventOrderingAndShuffleTest` |

---

## 5. Audit Ledger Trust Boundary & Honest Limitations

![Audit Trust Boundary](docs/images/v8-audit-trust-boundary.png)

- **Internal Boundary Guarantee:** Every audit log row stores `SHA-256(prev_hash || canonical_json(entry))`. Any direct row modification in PostgreSQL breaks the cryptographic link and is detected via `GET /api/audit/verify`.
- **Honest Limitation:** External anchoring (to an immutable public ledger or WORM storage) is **not implemented**. Direct PostgreSQL superuser access could theoretically recompute the chain.

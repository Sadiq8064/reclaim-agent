# RECLAIM — System Architecture Specification

## 1. High-Level Architecture Map

```mermaid
flowchart TD
    subgraph Sources [Event Ingestion Sources]
        A[Razorpay Live Test-Mode Webhooks] --> G[Webhook Gateway / Ingestion API]
        B[Replay Event Stream] --> G
    end

    subgraph Core [Reclaim Service Engine]
        G -->|HMAC Verified & Deduped| RAW[(Raw Event Store)]
        G -->|Publish| K[Kafka / Redpanda: reclaim.events.raw]
        K --> EP[Event Processor & Normalizer]
        EP --> SM[Recovery Case State Machine]
        SM -->|Needs Decision| AG[Agent Brain - Gemini 2.5 Flash]
        AG -->|Proposed Actions| PE[Deterministic Policy Engine]
        PE -->|ALLOW / MODIFY / DENY| EX[Action Executor]
        EX -->|Idempotent API Calls| RZP[Razorpay APIs / Simulator]
        EX -->|Append Record| AL[(Hash-Chained Audit Ledger)]
        SM -->|State Transitions| AL
    end

    subgraph Guardrails [Deterministic Policy Rules]
        PE --- R1[MAX_RETRIES: <=3]
        PE --- R2[MIN_RETRY_INTERVAL: >=6h]
        PE --- R3[QUIET_HOURS: 21:00-09:00 IST]
        PE --- R4[MAX_CONTACTS: <=3]
        PE --- R5[PER_CASE_SPEND_CAP: <=15%]
        PE --- R6[HIGH_VALUE_APPROVAL: >=₹10,000]
    end
```

---

## 2. Recovery Case State Machine

```mermaid
stateDiagram-v2
    [*] --> AT_RISK: Charge Failed (subscription.pending / payment.failed)
    AT_RISK --> DIAGNOSING: Ingestion & Normalization
    DIAGNOSING --> PLANNED: Agent Diagnosis + Policy Approved
    DIAGNOSING --> ESCALATED: High-Value (>=₹10,000) or Policy Limit
    DIAGNOSING --> ABANDONED: Unrecoverable (Revoked / Churned)
    PLANNED --> EXECUTING: Action Dispatched
    EXECUTING --> WAITING: Awaiting Response / Scheduled Window
    WAITING --> DIAGNOSING: Retry Failed (Re-planning)
    WAITING --> RECOVERED: payment.captured
    WAITING --> ABANDONED: subscription.cancelled / Max Retries Exceeded
    RECOVERED --> [*]
    ESCALATED --> [*]
    ABANDONED --> [*]
```

---

## 3. End-to-End Recovery Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Customer
    participant RZP as Razorpay Gateway
    participant GW as Webhook Gateway
    participant EP as Event Processor
    participant AG as Gemini 2.5 Flash
    participant PE as Policy Engine
    participant EX as Action Executor
    participant AL as Audit Ledger

    RZP->>GW: POST /api/v1/webhooks/razorpay (HMAC signed)
    GW->>GW: Verify HMAC-SHA256 & Deduplicate event_id
    GW->>AL: Record RAW_EVENT
    GW-->>RZP: 200 OK (<500ms)
    GW->>EP: Dispatch raw event
    EP->>EP: Open/Update RecoveryCase (AT_RISK -> DIAGNOSING)
    EP->>AG: Diagnose failure with case context & history
    AG-->>EP: Proposed recovery plan (Structured JSON)
    EP->>PE: Evaluate plan against 13 pure deterministic rules
    PE-->>EP: ALLOW / MODIFY (Guardrails enforced)
    EP->>EX: Execute approved recovery action
    EX->>RZP: Create payment link / schedule mandate retry
    RZP-->>EX: Return payment link ID & short_url
    EX->>AL: Append ACTION_EXECUTED (SHA-256 chained)
    Customer->>RZP: Pay via generated payment link
    RZP->>GW: POST payment.captured webhook
    GW->>EP: Route captured event
    EP->>EP: Transition Case -> RECOVERED (Terminal)
    EP->>AL: Append STATE_TRANSITION (RECOVERED)
```

---

## 4. Architectural Honesty & Key Decisions

1. **One Code Path, Two Event Sources:** Live webhooks and batch replay both enter via the identical `/api/v1/webhooks/razorpay` endpoint using real HMAC signatures.
2. **Pure Policy Layer:** The LLM produces proposals; the policy engine deterministically enforces limits (caps, quiet hours, spend budgets, kill-switches).
3. **Cryptographic Tamper-Evidence:** Every single lifecycle step is appended to a SHA-256 hash-chained ledger (`entry_hash = sha256(prev_hash || canonical_payload)`).
4. **Degraded Mode Resilience:** If the LLM experiences latency, rate limits, or 5xx outages, a Resilience4j circuit breaker trips and automatically falls back to the deterministic rules-only baseline (`RulesRecoveryEngine`) without dropping recoveries.

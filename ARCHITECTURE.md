# RECLAIM — System Architecture & Design

> **An event-driven Spring Boot revenue recovery system with deterministic guardrails and cryptographic audit ledgering.**

---

## 1. High-Level Component Flow

```mermaid
flowchart TD
    subgraph Sources [Event Ingestion Sources]
        A[Live-delivered webhooks from Razorpay Test Mode] --> G[Webhook Gateway / Ingestion API]
        B[Replay Event Stream] --> G
    end

    subgraph Ingestion [Ingestion Layer]
        G --> H[HMAC-SHA256 Validator]
        H --> D[Idempotency & Deduplication Filter]
        D --> K[(Raw Event DB & Kafka: reclaim.events.raw)]
    end

    subgraph Processing [Event Processing & State Machine]
        K --> P[Event Processor & Normalizer]
        P --> SM[Case State Machine]
    end

    subgraph AgenticCore [Autonomous Agent & Guardrails]
        SM --> AG[Gemini 2.5 Flash Agent Core]
        AG -->|Proposes Plan| PE[Deterministic Policy Engine]
        PE -->|ALLOW / MODIFY / DENY| EX[Action Executor]
    end

    subgraph Actions [Razorpay Execution & Audit]
        EX --> RZ[Razorpay REST API Client]
        EX --> AL[(Cryptographic SHA-256 Hash-Chained Audit Ledger)]
        RZ -->|Webhook Update| G
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
    participant SM as State Machine
    participant LLM as Gemini 2.5 Flash Agent
    participant PE as Policy Engine (Guardrails)
    participant EX as Action Executor
    participant AL as Audit Ledger

    RZP->>GW: POST /api/v1/webhooks/razorpay (subscription.pending)
    GW->>GW: Validate HMAC-SHA256 & Deduplicate
    GW->>AL: Append RAW_EVENT
    GW->>SM: Open / Transition Case -> DIAGNOSING
    SM->>AL: Append STATE_TRANSITION (AT_RISK -> DIAGNOSING)
    
    SM->>LLM: Diagnose Failure & Propose Recovery Plan
    LLM-->>PE: Return Proposed Actions (JSON)
    PE->>PE: Evaluate 13 Pure Deterministic Guardrails
    PE->>AL: Append POLICY_VERDICT (ALLOW / MODIFY / DENY)
    
    alt Approved by Policy
        PE->>EX: Dispatch Recovery Action
        EX->>RZP: Execute (Retry / Payment Method Update Link)
        EX->>AL: Append ACTION_EXECUTED
        SM->>AL: Transition -> EXECUTING -> WAITING
    else Escalated
        PE->>EX: Create Human Review Task
        SM->>AL: Transition -> ESCALATED
    end

    Customer->>RZP: Completes Payment / Updates Method
    RZP->>GW: POST /api/v1/webhooks/razorpay (payment.captured)
    GW->>SM: Transition -> RECOVERED
    SM->>AL: Append STATE_TRANSITION (WAITING -> RECOVERED)
```

---

## 4. Supported Recovery Actions (Razorpay-Aligned)

| Action | When Proposed | Razorpay Workflow | Guardrails Checked |
|---|---|---|---|
| `SCHEDULE_RETRY` | Transient failures (e.g. balance, downtime) | `/v1/subscriptions/{id}/charge` | `MAX_RETRIES`, `MIN_RETRY_INTERVAL`, `DOWNTIME_BLOCK` |
| `REQUEST_PAYMENT_METHOD_UPDATE` | Card expired / mandate invalid | `/v1/payment_links` update link | `PER_CASE_SPEND_CAP`, `IDEMPOTENCY_GUARD` |
| `SEND_CUSTOMER_NUDGE` | Customer intervention needed | Context-aware outreach | `QUIET_HOURS`, `MAX_CONTACTS`, `CONTACT_COOLDOWN` |
| `ESCALATE` | High-value ($\ge$ ₹10k) or risk boundary | Human task queue | `HIGH_VALUE_APPROVAL` |
| `CLOSE_CASE` | Revoked mandate / explicit cancellation | Immediate termination | `TERMINAL_STATE_LOCK`, `CANCELLED_SUB_LOCK` |

---

## 5. Architectural Invariants

1. **One Code Path:** Replay test events and live webhooks traverse the identical HTTP endpoint, HMAC verifier, state machine, and policy engine.
2. **Deterministic Precedence:** The LLM cannot override retry caps, quiet hours, spend limits, or terminal state locks.
3. **Cryptographic Tamper-Evidence:** Every entry hash satisfies `H_n = SHA-256(H_{n-1} || canonical_json(payload))`.

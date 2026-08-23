# RECLAIM — Known Limitations & Realism Constraints

Honest engineering disclosure regarding Razorpay test mode constraints and production differences.

---

## 1. Razorpay Test Mode Quirks
- **Mock Banking Simulators:** Razorpay test mode returns synthetic status codes for simulated failures (e.g. UPI cancel resolves synchronously instead of async bank webhooks).
- **Mandate Revocation:** Test mode simulated subscriptions do not reflect NPCI/e-NACH physical mandate rejection latency (typically 48–72h in production).
- **Card Expiry Emulation:** In test mode, expired cards do not trigger real issuer interchange decline codes unless forced via test card parameters.

---

## 2. Replay vs Live Boundary
- **Batch Evaluation:** The 300-case evaluation dataset is replayed via real HTTP requests signed with real HMAC-SHA256 signatures to test the end-to-end ingestion and state machine pipeline identically to live production.
- **Bank Outage Windows:** Downtime events (`payments.downtime.started` and `payments.downtime.resolved`) in replay are generated from calibrated bank availability models rather than live downtime feeds.

---

## 3. What We Would Build Next for Production
1. **NPCI Auto-Debit Switch Integration:** Direct integration with NPCI mandate retry windows for recurring UPI 2.0.
2. **Dynamic Discount Engine:** Autonomous margin-aware discount offerings (e.g. ₹50 off if paid within 2 hours) bounded by merchant gross margin.
3. **Multi-Agent Debate:** Dual-agent consensus for high-value enterprise cases (> ₹100,000) prior to analyst handoff.

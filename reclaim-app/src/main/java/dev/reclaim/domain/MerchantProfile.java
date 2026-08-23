package dev.reclaim.domain;

public enum MerchantProfile {
    MAXIMIZE_RECOVERY,   // Aggressive retry schedule + instant alternative payment links
    MINIMIZE_FRICTION,   // Silent-first recovery, strict contact limits, zero intrusive alerts
    HIGH_VALUE_FOCUSED   // Human-in-the-loop escalation for amounts >= ₹5,000
}

package dev.reclaim.domain;

public enum CaseState {
    AT_RISK,
    DIAGNOSING,
    PLANNED,
    EXECUTING,
    WAITING,
    RECOVERED,
    ESCALATED,
    ABANDONED;

    public boolean isTerminal() {
        return this == RECOVERED || this == ESCALATED || this == ABANDONED;
    }
}

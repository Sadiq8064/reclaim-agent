package dev.reclaim.domain;

public enum ActionType {
    SCHEDULE_RETRY,
    CREATE_PAYMENT_LINK,
    SEND_MESSAGE,
    WAIT,
    ESCALATE,
    CLOSE_CASE
}

package dev.reclaim.domain;

public enum ActionType {
    SCHEDULE_RETRY,
    CREATE_PAYMENT_LINK,
    REQUEST_PAYMENT_METHOD_UPDATE,
    SEND_CUSTOMER_NUDGE,
    SEND_MESSAGE,
    WAIT,
    ESCALATE,
    CLOSE_CASE
}

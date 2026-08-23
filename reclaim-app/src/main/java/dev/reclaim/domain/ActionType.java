package dev.reclaim.domain;

public enum ActionType {
    SCHEDULE_RETRY,
    CREATE_PAYMENT_LINK,
    SEND_MESSAGE,
    SEND_NOTIFICATION,
    CARD_UPDATER_SYNC,
    WAIT,
    ESCALATE,
    CLOSE_CASE
}

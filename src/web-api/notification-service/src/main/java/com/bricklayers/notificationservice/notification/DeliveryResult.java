package com.bricklayers.notificationservice.notification;

import java.util.UUID;

public record DeliveryResult(UUID notificationId, Outcome outcome, int attempts, String error) {

    public enum Outcome {
        SENT,

        RETRY_LATER,

        GAVE_UP,

        SKIPPED
    }

    public boolean failed() {
        return outcome == Outcome.RETRY_LATER || outcome == Outcome.GAVE_UP;
    }
}

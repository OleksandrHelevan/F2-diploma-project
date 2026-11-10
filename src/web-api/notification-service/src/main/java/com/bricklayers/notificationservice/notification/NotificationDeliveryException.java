package com.bricklayers.notificationservice.notification;

public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(DeliveryResult result) {
        super("Notification " + result.notificationId() + " was not delivered (" + result.outcome()
                + ", attempt " + result.attempts() + "): " + result.error());
    }
}

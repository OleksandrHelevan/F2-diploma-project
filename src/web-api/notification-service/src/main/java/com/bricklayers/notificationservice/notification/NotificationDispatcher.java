package com.bricklayers.notificationservice.notification;

import com.bricklayers.notificationservice.async.AsyncConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NotificationDispatcher {

    private final NotificationDelivery delivery;
    private final int maxAttempts;

    public NotificationDispatcher(NotificationDelivery delivery,
                                  @Value("${app.notifications.max-attempts:5}") int maxAttempts) {
        this.delivery = delivery;
        this.maxAttempts = maxAttempts;
    }

    @Async(AsyncConfig.EXECUTOR)
    public void dispatch(UUID notificationId) {
        DeliveryResult result = delivery.deliver(notificationId, maxAttempts);
        if (result.failed()) {
            throw new NotificationDeliveryException(result);
        }
    }
}

package com.bricklayers.notificationservice.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDelivery {

    private final NotificationOutboxRepository repository;
    private final MailGateway mailGateway;

    @Transactional
    public DeliveryResult deliver(UUID notificationId, int maxAttempts) {
        Optional<NotificationOutbox> claimed = repository.lockPending(notificationId);
        if (claimed.isEmpty()) {
            return new DeliveryResult(notificationId, DeliveryResult.Outcome.SKIPPED, 0, null);
        }
        NotificationOutbox notification = claimed.get();
        try {
            mailGateway.send(notification.getRecipient(), notification.getSubject(), notification.getBody());
            notification.markSent();
            log.info("Notification {} ({}) sent to {} on {}", notificationId, notification.getType(),
                    notification.getRecipient(), Thread.currentThread().getName());
            return new DeliveryResult(notificationId, DeliveryResult.Outcome.SENT, notification.getAttempts(), null);
        } catch (RuntimeException exception) {
            notification.recordFailure(exception, maxAttempts);
            DeliveryResult.Outcome outcome = notification.getStatus() == NotificationStatus.FAILED
                    ? DeliveryResult.Outcome.GAVE_UP
                    : DeliveryResult.Outcome.RETRY_LATER;
            log.warn("Notification {} attempt {}/{} failed: {}", notificationId, notification.getAttempts(),
                    maxAttempts, notification.getLastError());
            return new DeliveryResult(notificationId, outcome, notification.getAttempts(), notification.getLastError());
        }
    }
}

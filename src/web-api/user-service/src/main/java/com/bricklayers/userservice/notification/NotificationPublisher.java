package com.bricklayers.userservice.notification;

import com.bricklayers.common.notification.NotificationRequest;
import com.bricklayers.common.notification.NotificationType;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPublisher {

    private final NotificationSender sender;
    private final NotificationClient client;

    public UUID welcome(User user) {
        Map<String, String> params = new HashMap<>();
        params.put("username", user.getUsername());
        params.put("displayName", displayName(user));
        params.put("role", String.valueOf(user.getRole()));
        return publishAfterCommit(NotificationType.WELCOME, user.getEmail(), params);
    }

    public UUID accountChanged(User user, Role previousRole) {
        Map<String, String> params = new HashMap<>();
        params.put("displayName", displayName(user));
        params.put("role", String.valueOf(user.getRole()));
        params.put("previousRole", String.valueOf(previousRole));
        params.put("active", String.valueOf(user.isActive()));
        return publishAfterCommit(NotificationType.ACCOUNT_CHANGED, user.getEmail(), params);
    }

    private UUID publishAfterCommit(NotificationType type, String recipient, Map<String, String> params) {

        NotificationRequest request = new NotificationRequest(UUID.randomUUID(), type, recipient, Map.copyOf(params));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(request);
                }
            });
        } else {
            publish(request);
        }
        return request.id();
    }

    void publish(NotificationRequest request) {
        try {
            sender.send(request);
        } catch (TaskRejectedException rejected) {
            log.warn("Async executor is saturated; sending notification {} on the caller thread", request.id());
            try {
                client.send(request, 1);
            } catch (RuntimeException failure) {
                log.error("Notification {} ({} for {}) was not delivered to notification-service: {}",
                        request.id(), request.type(), request.recipient(), failure.getMessage());
            }
        }
    }

    private static String displayName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? user.getUsername() : name;
    }
}

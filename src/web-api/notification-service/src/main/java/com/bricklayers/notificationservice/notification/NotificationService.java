package com.bricklayers.notificationservice.notification;

import com.bricklayers.common.notification.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationOutboxRepository repository;
    private final NotificationDispatcher dispatcher;
    private final NotificationTemplates templates;

    public record Accepted(UUID id, boolean duplicate) {
    }

    @Transactional
    public Accepted accept(NotificationRequest request) {
        NotificationTemplates.Rendered rendered = templates.render(request);
        int inserted = repository.insertIfAbsent(request.id(), request.type().name(), request.recipient(),
                rendered.subject(), rendered.body(), LocalDateTime.now());
        if (inserted == 0) {
            log.info("Notification {} already accepted; duplicate request ignored", request.id());
            return new Accepted(request.id(), true);
        }
        dispatchAfterCommit(request.id());
        return new Accepted(request.id(), false);
    }

    private void dispatchAfterCommit(UUID id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchOrLeavePending(id);
                }
            });
        } else {
            dispatchOrLeavePending(id);
        }
    }

    void dispatchOrLeavePending(UUID id) {
        try {
            dispatcher.dispatch(id);
        } catch (TaskRejectedException rejected) {
            log.warn("Async executor is saturated; notification {} stays PENDING for the retry job", id);
        }
    }
}

package com.bricklayers.notificationservice.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRetryService {

    private final NotificationOutboxRepository repository;
    private final NotificationDelivery delivery;

    public RetrySummary retryPending(int batchSize, int maxAttempts, Duration minAge) {

        LocalDateTime createdBefore = LocalDateTime.now().minus(minAge);
        List<UUID> ids = repository.findIdsByStatusCreatedBefore(
                NotificationStatus.PENDING, createdBefore, PageRequest.of(0, batchSize));

        Map<DeliveryResult.Outcome, Integer> outcomes = new EnumMap<>(DeliveryResult.Outcome.class);
        for (UUID id : ids) {
            outcomes.merge(delivery.deliver(id, maxAttempts).outcome(), 1, Integer::sum);
        }
        RetrySummary summary = new RetrySummary(ids.size(),
                outcomes.getOrDefault(DeliveryResult.Outcome.SENT, 0),
                outcomes.getOrDefault(DeliveryResult.Outcome.RETRY_LATER, 0),
                outcomes.getOrDefault(DeliveryResult.Outcome.GAVE_UP, 0),
                outcomes.getOrDefault(DeliveryResult.Outcome.SKIPPED, 0));
        if (summary.picked() > 0) {
            log.info("Notification retry: {}", summary);
        }
        return summary;
    }

    public record RetrySummary(int picked, int sent, int retryLater, int gaveUp, int skipped) {
    }
}

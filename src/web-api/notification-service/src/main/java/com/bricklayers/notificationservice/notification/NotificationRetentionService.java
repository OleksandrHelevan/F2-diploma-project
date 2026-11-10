package com.bricklayers.notificationservice.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRetentionService {

    private final NotificationOutboxRepository repository;

    @Transactional
    public int purgeExpired(int retentionDays, Instant asOf) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("retentionDays must be at least 1, got " + retentionDays);
        }
        LocalDateTime cutoff = cutoff(retentionDays, asOf);
        int deleted = repository.deleteByStatusInCreatedBefore(
                EnumSet.of(NotificationStatus.SENT, NotificationStatus.FAILED), cutoff);
        log.info("Notification retention: deleted {} finished notification(s) created before {}", deleted, cutoff);
        return deleted;
    }

    static LocalDateTime cutoff(int retentionDays, Instant asOf) {
        return LocalDateTime.ofInstant(asOf, ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.DAYS)
                .minusDays(retentionDays);
    }
}

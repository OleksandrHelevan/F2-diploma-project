package com.bricklayers.notificationservice.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO notification_outbox (id, type, recipient, subject, body, status, attempts, created_at)
            VALUES (:id, :type, :recipient, :subject, :body, 'PENDING', 0, :createdAt)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("type") String type,
                       @Param("recipient") String recipient,
                       @Param("subject") String subject,
                       @Param("body") String body,
                       @Param("createdAt") LocalDateTime createdAt);

    @Query(value = "SELECT * FROM notification_outbox WHERE id = :id AND status = 'PENDING' FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    Optional<NotificationOutbox> lockPending(@Param("id") UUID id);

    @Query("SELECT n.id FROM NotificationOutbox n WHERE n.status = :status AND n.createdAt < :createdBefore ORDER BY n.createdAt")
    List<UUID> findIdsByStatusCreatedBefore(@Param("status") NotificationStatus status,
                                            @Param("createdBefore") LocalDateTime createdBefore,
                                            Pageable pageable);

    @Modifying
    @Query("DELETE FROM NotificationOutbox n WHERE n.status IN :statuses AND n.createdAt < :cutoff")
    int deleteByStatusInCreatedBefore(@Param("statuses") Collection<NotificationStatus> statuses,
                                      @Param("cutoff") LocalDateTime cutoff);
}

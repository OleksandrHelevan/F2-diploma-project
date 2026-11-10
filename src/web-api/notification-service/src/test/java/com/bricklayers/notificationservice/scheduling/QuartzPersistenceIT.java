package com.bricklayers.notificationservice.scheduling;

import com.bricklayers.common.notification.NotificationRequest;
import com.bricklayers.common.notification.NotificationType;
import com.bricklayers.notificationservice.notification.DeliveryResult;
import com.bricklayers.notificationservice.notification.MailGateway;
import com.bricklayers.notificationservice.notification.NotificationDelivery;
import com.bricklayers.notificationservice.notification.NotificationOutboxRepository;
import com.bricklayers.notificationservice.notification.NotificationRetentionService;
import com.bricklayers.notificationservice.notification.NotificationRetryService;
import com.bricklayers.notificationservice.notification.NotificationRetryService.RetrySummary;
import com.bricklayers.notificationservice.notification.NotificationService;
import com.bricklayers.notificationservice.notification.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.quartz.auto-startup=false")
class QuartzPersistenceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @MockitoBean private MailGateway mailGateway;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private Environment environment;
    @Autowired private Scheduler scheduler;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRetryService retryService;
    @Autowired private NotificationDelivery delivery;
    @Autowired private NotificationOutboxRepository outboxRepository;
    @Autowired private NotificationRetentionService retentionService;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM notification_outbox");
        reset(mailGateway);
    }

    @Test
    void quartzTablesComeFromLiquibase_andBootDoesNotCreateThem() {
        Integer quartzTables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'qrtz\\_%'",
                Integer.class);
        assertThat(quartzTables).isEqualTo(11);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM databasechangelog WHERE id = '002-create-quartz-tables'", Integer.class))
                .isEqualTo(1);
        assertThat(environment.getProperty("spring.quartz.jdbc.initialize-schema")).isEqualTo("never");
        assertThat(environment.getProperty("spring.quartz.job-store-type")).isEqualTo("jdbc");
    }

    @Test
    void jobsAndTriggersArePersistedInTheJdbcJobStore() throws Exception {
        String schedulerName = scheduler.getSchedulerName();
        List<Map<String, Object>> jobs = jdbc.queryForList(
                "SELECT job_name, is_nonconcurrent FROM qrtz_job_details WHERE sched_name = ? ORDER BY job_name",
                schedulerName);
        assertThat(jobs).extracting(row -> row.get("job_name")).containsExactly("notification-retention", "notification-retry");
        assertThat(jobs).allSatisfy(row -> assertThat(row.get("is_nonconcurrent")).isEqualTo(true));

        List<Map<String, Object>> triggers = jdbc.queryForList(
                "SELECT trigger_name FROM qrtz_triggers WHERE sched_name = ? ORDER BY trigger_name", schedulerName);
        assertThat(triggers).extracting(row -> row.get("trigger_name")).containsExactly(
                "notification-retention-daily", "notification-retry-frequent", "notification-retry-nightly-sweep");
        assertThat(jdbc.queryForList("SELECT DISTINCT time_zone_id FROM qrtz_cron_triggers WHERE sched_name = ?",
                String.class, schedulerName)).containsExactly("Europe/Kyiv");
        assertThat(scheduler.getMetaData().isJobStoreClustered()).isTrue();
    }

    @Test
    void accept_sameIdTwice_storesAndSendsOneNotification() {
        NotificationRequest request = new NotificationRequest(UUID.randomUUID(), NotificationType.WELCOME,
                "worker@example.com", Map.of("username", "worker", "displayName", "Ivan Petrenko", "role", "BUILDER"));

        NotificationService.Accepted first = notificationService.accept(request);
        NotificationService.Accepted second = notificationService.accept(request);

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox", Integer.class)).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                verify(mailGateway).send("worker@example.com", "Welcome to Bricklayers",
                        "Hello Ivan Petrenko,\n\nan account 'worker' was created for you with the role BUILDER.\n"));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(outboxRepository.findById(request.id())).get().extracting("status").isEqualTo(NotificationStatus.SENT));
        verify(mailGateway, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void notificationRetry_secondRunRightAfterTheFirst_sendsNothing() {
        UUID first = insertNotification("PENDING", 0, LocalDateTime.now().minusMinutes(5));
        UUID second = insertNotification("PENDING", 0, LocalDateTime.now().minusMinutes(5));

        RetrySummary firstRun = retryService.retryPending(50, 5, Duration.ofSeconds(30));
        RetrySummary secondRun = retryService.retryPending(50, 5, Duration.ofSeconds(30));

        assertThat(firstRun.sent()).isEqualTo(2);
        assertThat(secondRun.picked()).isZero();
        verify(mailGateway, times(2)).send(anyString(), anyString(), anyString());
        assertThat(outboxRepository.findById(first)).get().extracting("status").isEqualTo(NotificationStatus.SENT);
        assertThat(outboxRepository.findById(second)).get().extracting("attempts").isEqualTo(1);

        assertThat(delivery.deliver(first, 5).outcome()).isEqualTo(DeliveryResult.Outcome.SKIPPED);
        verify(mailGateway, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void notificationRetry_failingSmtp_givesUpAfterMaxAttempts() {
        doThrow(new IllegalStateException("SMTP down")).when(mailGateway).send(anyString(), anyString(), anyString());
        UUID id = insertNotification("PENDING", 0, LocalDateTime.now().minusMinutes(5));

        RetrySummary run1 = retryService.retryPending(50, 2, Duration.ZERO);
        RetrySummary run2 = retryService.retryPending(50, 2, Duration.ZERO);
        RetrySummary run3 = retryService.retryPending(50, 2, Duration.ZERO);

        assertThat(run1.retryLater()).isEqualTo(1);
        assertThat(run2.gaveUp()).isEqualTo(1);
        assertThat(run3.picked()).isZero();
        assertThat(jdbc.queryForMap("SELECT status, attempts, last_error FROM notification_outbox WHERE id = ?", id))
                .containsEntry("status", "FAILED")
                .containsEntry("attempts", 2)
                .containsEntry("last_error", "IllegalStateException: SMTP down");
    }

    @Test
    void notificationRetry_leavesFreshRowsToTheAsyncDispatcher() {
        insertNotification("PENDING", 0, LocalDateTime.now());

        assertThat(retryService.retryPending(50, 5, Duration.ofSeconds(30)).picked()).isZero();
        verify(mailGateway, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void retention_secondRunForTheSameFireTime_deletesNothing_andKeepsPendingRows() {
        insertNotification("SENT", 1, LocalDateTime.now().minusDays(60));
        insertNotification("FAILED", 5, LocalDateTime.now().minusDays(45));
        UUID oldPending = insertNotification("PENDING", 1, LocalDateTime.now().minusDays(45));
        insertNotification("SENT", 1, LocalDateTime.now().minusDays(3));
        Instant fireTime = Instant.now();

        int firstRun = retentionService.purgeExpired(30, fireTime);
        int secondRun = retentionService.purgeExpired(30, fireTime);

        assertThat(firstRun).isEqualTo(2);
        assertThat(secondRun).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox", Integer.class)).isEqualTo(2);
        assertThat(outboxRepository.findById(oldPending)).isPresent();
    }

    private UUID insertNotification(String status, int attempts, LocalDateTime createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO notification_outbox (id, type, recipient, subject, body, status, attempts, created_at) "
                        + "VALUES (?, 'WELCOME', 'worker@example.com', 'Welcome', 'Hello', ?, ?, ?)",
                id, status, attempts, Timestamp.valueOf(createdAt));
        return id;
    }
}

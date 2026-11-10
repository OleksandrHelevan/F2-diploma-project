package com.bricklayers.userservice.async;

import com.bricklayers.common.notification.NotificationRequest;
import com.bricklayers.common.notification.NotificationType;
import com.bricklayers.userservice.entity.AdminAuditLog;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient.KeycloakRole;
import com.bricklayers.userservice.mapper.AdminAuditLogMapper;
import com.bricklayers.userservice.mapper.UserMapper;
import com.bricklayers.userservice.notification.NotificationClient;
import com.bricklayers.userservice.notification.NotificationPublisher;
import com.bricklayers.userservice.notification.NotificationSender;
import com.bricklayers.userservice.overview.AccountLookups;
import com.bricklayers.userservice.overview.AccountOverviewDto;
import com.bricklayers.userservice.overview.AccountOverviewService;
import com.bricklayers.userservice.repository.AdminAuditLogRepository;
import com.bricklayers.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(AsyncProcessingTest.Config.class)
@TestPropertySource(properties = {
        "app.async.core-size=4",
        "app.async.max-size=4",
        "app.async.queue-capacity=10",
        "app.async.thread-name-prefix=user-async-",
        "app.async.overview-timeout=700ms"
})
class AsyncProcessingTest {

    private static final String PREFIX = "user-async-";

    @Configuration
    @Import({AsyncConfig.class, LoggingAsyncExceptionHandler.class,
            NotificationPublisher.class, NotificationSender.class,
            AccountLookups.class, AccountOverviewService.class})
    static class Config {

        @Bean
        static ConversionService conversionService() {
            return ApplicationConversionService.getSharedInstance();
        }
    }

    @MockitoBean private NotificationClient notificationClient;
    @MockitoBean private KeycloakAdminClient keycloakAdminClient;
    @MockitoBean private AdminAuditLogRepository auditLogRepository;
    @MockitoBean private AdminAuditLogMapper auditLogMapper;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private UserMapper userMapper;

    @Autowired private NotificationPublisher notificationPublisher;
    @Autowired private AccountOverviewService overviewService;
    @Autowired private LoggingAsyncExceptionHandler asyncExceptionHandler;
    @Autowired private ThreadPoolTaskExecutor userServiceAsyncExecutor;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).username("worker").email("worker@example.com")
                .firstName("Ivan").lastName("Petrenko")
                .role(Role.BUILDER).keycloakId("kc-worker").active(true).build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(keycloakAdminClient.getAccessToken()).thenReturn("service-token");
    }

    @Test
    void executorIsTheExplicitlySizedPool_notASpringDefault() {
        assertThat(userServiceAsyncExecutor).isNotInstanceOf(SimpleAsyncTaskExecutor.class);
        assertThat(userServiceAsyncExecutor.getCorePoolSize()).isEqualTo(4);
        assertThat(userServiceAsyncExecutor.getMaxPoolSize()).isEqualTo(4);
        assertThat(userServiceAsyncExecutor.getQueueCapacity()).isEqualTo(10);
        assertThat(userServiceAsyncExecutor.getThreadNamePrefix()).isEqualTo(PREFIX);
    }

    @Test
    void welcome_returnsBeforeNotificationServiceAnswers_andCallsItOnThePoolThread() {
        CountDownLatch serviceMayAnswer = new CountDownLatch(1);
        AtomicReference<String> callingThread = new AtomicReference<>();
        doAnswer(invocation -> {
            callingThread.set(Thread.currentThread().getName());
            serviceMayAnswer.await(5, TimeUnit.SECONDS);
            return null;
        }).when(notificationClient).send(any(NotificationRequest.class));

        long started = System.nanoTime();
        UUID notificationId = notificationPublisher.welcome(user);
        long callerMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(notificationId).isNotNull();
        assertThat(callerMillis).isLessThan(500);
        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationClient, timeout(2000)).send(sent.capture());
        assertThat(serviceMayAnswer.getCount()).isEqualTo(1);
        assertThat(sent.getValue().id()).isEqualTo(notificationId);
        assertThat(sent.getValue().type()).isEqualTo(NotificationType.WELCOME);
        assertThat(sent.getValue().recipient()).isEqualTo("worker@example.com");
        assertThat(sent.getValue().params()).containsEntry("displayName", "Ivan Petrenko").containsEntry("role", "BUILDER");

        serviceMayAnswer.countDown();
        assertThat(callingThread.get()).startsWith(PREFIX).isNotEqualTo(Thread.currentThread().getName());
    }

    @Test
    void overview_runsKeycloakAndAuditLookupsConcurrently_andCombinesThem() {

        CyclicBarrier bothRunning = new CyclicBarrier(2);
        AtomicReference<String> keycloakThread = new AtomicReference<>();
        AtomicReference<String> auditThread = new AtomicReference<>();
        when(keycloakAdminClient.getRealmRoles("kc-worker", "service-token")).thenAnswer(invocation -> {
            keycloakThread.set(Thread.currentThread().getName());
            bothRunning.await(2, TimeUnit.SECONDS);
            return List.of(new KeycloakRole("r1", "BUILDER"));
        });
        when(keycloakAdminClient.countSessions("kc-worker", "service-token")).thenReturn(2);
        when(auditLogRepository.countByTarget(user.getId().toString())).thenAnswer(invocation -> {
            auditThread.set(Thread.currentThread().getName());
            bothRunning.await(2, TimeUnit.SECONDS);
            return 3L;
        });
        when(auditLogRepository.findTop5ByTargetOrderByCreatedAtDesc(user.getId().toString()))
                .thenReturn(List.of(new AdminAuditLog()));

        AccountOverviewDto overview = overviewService.overview(user.getId());

        assertThat(overview.keycloak().available()).isTrue();
        assertThat(overview.keycloak().realmRoles()).containsExactly("BUILDER");
        assertThat(overview.keycloak().activeSessions()).isEqualTo(2);
        assertThat(overview.audit().available()).isTrue();
        assertThat(overview.audit().totalActions()).isEqualTo(3);
        assertThat(keycloakThread.get()).startsWith(PREFIX);
        assertThat(auditThread.get()).startsWith(PREFIX).isNotEqualTo(keycloakThread.get());
    }

    @Test
    void overview_keycloakFailure_degradesThatPartOnly() {
        when(keycloakAdminClient.getRealmRoles(anyString(), anyString()))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(auditLogRepository.countByTarget(anyString())).thenReturn(1L);
        when(auditLogRepository.findTop5ByTargetOrderByCreatedAtDesc(anyString())).thenReturn(List.of());

        AccountOverviewDto overview = overviewService.overview(user.getId());

        assertThat(overview.keycloak().available()).isFalse();
        assertThat(overview.keycloak().error()).contains("ResourceAccessException", "Connection refused");
        assertThat(overview.audit().available()).isTrue();
        assertThat(overview.audit().totalActions()).isEqualTo(1);
    }

    @Test
    void overview_slowKeycloak_stopsWaitingAfterTheTimeout() {
        when(keycloakAdminClient.getRealmRoles(anyString(), anyString())).thenAnswer(invocation -> {
            Thread.sleep(3000);
            return List.of();
        });
        when(auditLogRepository.countByTarget(anyString())).thenReturn(0L);
        when(auditLogRepository.findTop5ByTargetOrderByCreatedAtDesc(anyString())).thenReturn(List.of());

        long started = System.nanoTime();
        AccountOverviewDto overview = overviewService.overview(user.getId());
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(elapsed).isBetween(600L, 2000L);
        assertThat(overview.keycloak().available()).isFalse();
        assertThat(overview.keycloak().error()).contains("timed out");
        assertThat(overview.audit().available()).isTrue();
    }

    @Test
    void notificationServiceDown_failureIsReportedToTheAsyncExceptionHandler() {
        long failuresBefore = asyncExceptionHandler.failures();
        doThrow(new ResourceAccessException("notification-service: Connection refused"))
                .when(notificationClient).send(any(NotificationRequest.class));

        UUID notificationId = notificationPublisher.accountChanged(user, Role.FOREMAN);

        assertThat(notificationId).isNotNull();
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(asyncExceptionHandler.failures()).isEqualTo(failuresBefore + 1));
    }
}

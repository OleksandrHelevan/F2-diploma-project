package com.bricklayers.userservice.async;

import com.bricklayers.common.notification.NotificationRequest;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient;
import com.bricklayers.userservice.mapper.AdminAuditLogMapper;
import com.bricklayers.userservice.mapper.UserMapper;
import com.bricklayers.userservice.notification.NotificationClient;
import com.bricklayers.userservice.notification.NotificationPublisher;
import com.bricklayers.userservice.notification.NotificationSender;
import com.bricklayers.userservice.overview.AccountLookups;
import com.bricklayers.userservice.overview.AccountOverviewService;
import com.bricklayers.userservice.repository.AdminAuditLogRepository;
import com.bricklayers.userservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(ExecutorSaturationTest.Config.class)
@TestPropertySource(properties = {
        "app.async.core-size=1",
        "app.async.max-size=1",
        "app.async.queue-capacity=1",
        "app.async.thread-name-prefix=tiny-async-",
        "app.async.await-termination=1s",
        "app.async.overview-timeout=1s"
})
class ExecutorSaturationTest {

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
    @Autowired private ThreadPoolTaskExecutor userServiceAsyncExecutor;

    private final CountDownLatch serviceMayAnswer = new CountDownLatch(1);
    private User user;

    @BeforeEach
    void setUp() {

        await().atMost(Duration.ofSeconds(6)).until(() -> userServiceAsyncExecutor.getActiveCount() == 0
                && userServiceAsyncExecutor.getThreadPoolExecutor().getQueue().isEmpty());
        user = User.builder().id(UUID.randomUUID()).username("worker").email("worker@example.com")
                .role(Role.BUILDER).keycloakId("kc-worker").active(true).build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        doAnswer(invocation -> serviceMayAnswer.await(5, TimeUnit.SECONDS))
                .when(notificationClient).send(any(NotificationRequest.class));
    }

    @AfterEach
    void releaseWorker() {
        serviceMayAnswer.countDown();
    }

    @Test
    void tinyPool_rejectsTheThirdTask() {
        userServiceAsyncExecutor.execute(this::blockOnService);
        awaitWorkerBusy();
        userServiceAsyncExecutor.execute(this::blockOnService);

        assertThatThrownBy(() -> userServiceAsyncExecutor.execute(this::blockOnService))
                .isInstanceOf(TaskRejectedException.class);
    }

    @Test
    void notifications_rejectedTaskIsSentOnTheCallerThreadWithOneAttempt() {
        AtomicReference<String> callerRunThread = new AtomicReference<>();
        doAnswer(invocation -> {
            callerRunThread.set(Thread.currentThread().getName());
            return null;
        }).when(notificationClient).send(any(NotificationRequest.class), eq(1));

        notificationPublisher.welcome(user);

        awaitWorkerBusy();
        notificationPublisher.welcome(user);
        UUID third = notificationPublisher.welcome(user);

        verify(notificationClient).send(any(NotificationRequest.class), eq(1));
        assertThat(callerRunThread.get()).isEqualTo(Thread.currentThread().getName());
        assertThat(third).isNotNull();

        serviceMayAnswer.countDown();
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> verify(notificationClient, times(2)).send(any(NotificationRequest.class)));
    }

    @Test
    void notifications_callerRunFailure_isLoggedButDoesNotFailTheCaller() {
        doThrow(new ResourceAccessException("notification-service down"))
                .when(notificationClient).send(any(NotificationRequest.class), eq(1));
        notificationPublisher.welcome(user);
        awaitWorkerBusy();
        notificationPublisher.welcome(user);

        assertThatCode(() -> notificationPublisher.welcome(user)).doesNotThrowAnyException();
    }

    @Test
    void overview_whenPoolIsFull_failsFastWithTaskRejected_whichTheApiMapsTo503() {
        userServiceAsyncExecutor.execute(this::blockOnService);
        awaitWorkerBusy();
        userServiceAsyncExecutor.execute(this::blockOnService);

        assertThatThrownBy(() -> overviewService.overview(user.getId()))
                .isInstanceOf(TaskRejectedException.class);
    }

    private void awaitWorkerBusy() {
        await().atMost(Duration.ofSeconds(3)).until(() -> userServiceAsyncExecutor.getActiveCount() == 1
                && userServiceAsyncExecutor.getThreadPoolExecutor().getQueue().isEmpty());
    }

    private void blockOnService() {
        try {
            serviceMayAnswer.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}

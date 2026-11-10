package com.bricklayers.notificationservice.async;

import com.bricklayers.common.notification.NotificationRequest;
import com.bricklayers.common.notification.NotificationType;
import com.bricklayers.notificationservice.notification.MailGateway;
import com.bricklayers.notificationservice.notification.NotificationDelivery;
import com.bricklayers.notificationservice.notification.NotificationDispatcher;
import com.bricklayers.notificationservice.notification.NotificationOutbox;
import com.bricklayers.notificationservice.notification.NotificationOutboxRepository;
import com.bricklayers.notificationservice.notification.NotificationService;
import com.bricklayers.notificationservice.notification.NotificationTemplates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(AsyncProcessingTest.Config.class)
@TestPropertySource(properties = {
        "app.async.core-size=2",
        "app.async.max-size=4",
        "app.async.queue-capacity=10",
        "app.async.thread-name-prefix=notif-async-",
        "app.notifications.max-attempts=5"
})
class AsyncProcessingTest {

    private static final String PREFIX = "notif-async-";

    @Configuration
    @Import({AsyncConfig.class, LoggingAsyncExceptionHandler.class, NotificationService.class,
            NotificationDispatcher.class, NotificationDelivery.class, NotificationTemplates.class})
    static class Config {
        @Bean
        static ConversionService conversionService() {
            return ApplicationConversionService.getSharedInstance();
        }
    }

    @MockitoBean private NotificationOutboxRepository outboxRepository;
    @MockitoBean private MailGateway mailGateway;

    @Autowired private NotificationService notificationService;
    @Autowired private LoggingAsyncExceptionHandler asyncExceptionHandler;
    @Autowired private ThreadPoolTaskExecutor notificationAsyncExecutor;

    private final NotificationRequest request = new NotificationRequest(UUID.randomUUID(), NotificationType.WELCOME,
            "worker@example.com", Map.of("username", "worker", "displayName", "Ivan Petrenko", "role", "BUILDER"));

    @BeforeEach
    void setUp() {
        when(outboxRepository.insertIfAbsent(any(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(outboxRepository.lockPending(any(UUID.class))).thenAnswer(invocation -> Optional.of(
                NotificationOutbox.builder().id(invocation.getArgument(0)).type(NotificationType.WELCOME)
                        .recipient("worker@example.com").subject("Welcome to Bricklayers").body("Hello").build()));
    }

    @Test
    void executorIsTheExplicitlySizedPool_notASpringDefault() {
        assertThat(notificationAsyncExecutor).isNotInstanceOf(SimpleAsyncTaskExecutor.class);
        assertThat(notificationAsyncExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(notificationAsyncExecutor.getMaxPoolSize()).isEqualTo(4);
        assertThat(notificationAsyncExecutor.getQueueCapacity()).isEqualTo(10);
        assertThat(notificationAsyncExecutor.getThreadNamePrefix()).isEqualTo(PREFIX);
    }

    @Test
    void accept_returnsBeforeTheSlowSmtpSendFinishes_andSendsOnThePoolThread() {
        CountDownLatch smtpMayAnswer = new CountDownLatch(1);
        AtomicReference<String> sendingThread = new AtomicReference<>();
        doAnswer(invocation -> {
            sendingThread.set(Thread.currentThread().getName());
            smtpMayAnswer.await(5, TimeUnit.SECONDS);
            return null;
        }).when(mailGateway).send(anyString(), anyString(), anyString());

        long started = System.nanoTime();
        NotificationService.Accepted accepted = notificationService.accept(request);
        long callerMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(accepted.duplicate()).isFalse();
        assertThat(callerMillis).isLessThan(500);
        verify(mailGateway, timeout(2000)).send(eq("worker@example.com"), eq("Welcome to Bricklayers"), anyString());
        assertThat(smtpMayAnswer.getCount()).isEqualTo(1);

        smtpMayAnswer.countDown();
        assertThat(sendingThread.get()).startsWith(PREFIX).isNotEqualTo(Thread.currentThread().getName());
    }

    @Test
    void duplicateRequest_isAcknowledgedButNotSentAgain() throws InterruptedException {
        when(outboxRepository.insertIfAbsent(any(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(0);

        NotificationService.Accepted accepted = notificationService.accept(request);

        assertThat(accepted.duplicate()).isTrue();
        Thread.sleep(300);
        verify(outboxRepository, never()).lockPending(any(UUID.class));
        verify(mailGateway, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void failedSmtpSend_isReportedToTheAsyncExceptionHandler() {
        long failuresBefore = asyncExceptionHandler.failures();
        doThrow(new IllegalStateException("SMTP 451 temporary failure"))
                .when(mailGateway).send(anyString(), anyString(), anyString());

        notificationService.accept(request);

        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(asyncExceptionHandler.failures()).isEqualTo(failuresBefore + 1));
    }
}

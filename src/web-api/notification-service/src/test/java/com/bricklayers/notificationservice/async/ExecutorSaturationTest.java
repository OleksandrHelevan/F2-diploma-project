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

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(ExecutorSaturationTest.Config.class)
@TestPropertySource(properties = {
        "app.async.core-size=1",
        "app.async.max-size=1",
        "app.async.queue-capacity=1",
        "app.async.thread-name-prefix=tiny-async-",
        "app.async.await-termination=1s"
})
class ExecutorSaturationTest {

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
    @Autowired private ThreadPoolTaskExecutor notificationAsyncExecutor;

    private final CountDownLatch smtpMayAnswer = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        await().atMost(Duration.ofSeconds(6)).until(() -> notificationAsyncExecutor.getActiveCount() == 0
                && notificationAsyncExecutor.getThreadPoolExecutor().getQueue().isEmpty());
        when(outboxRepository.insertIfAbsent(any(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(outboxRepository.lockPending(any(UUID.class))).thenAnswer(invocation -> Optional.of(
                NotificationOutbox.builder().id(invocation.getArgument(0)).type(NotificationType.WELCOME)
                        .recipient("worker@example.com").subject("Welcome").body("Hello").build()));

        doAnswer(invocation -> smtpMayAnswer.await(5, TimeUnit.SECONDS))
                .when(mailGateway).send(anyString(), anyString(), anyString());
    }

    @AfterEach
    void releaseWorker() {
        smtpMayAnswer.countDown();
    }

    @Test
    void tinyPool_rejectsTheThirdTask() {
        notificationAsyncExecutor.execute(this::blockOnSmtp);
        awaitWorkerBusy();
        notificationAsyncExecutor.execute(this::blockOnSmtp);

        assertThatThrownBy(() -> notificationAsyncExecutor.execute(this::blockOnSmtp))
                .isInstanceOf(TaskRejectedException.class);
    }

    @Test
    void rejectedTask_staysPendingForTheRetryJob_andTheRequestIsStillAccepted() {
        notificationService.accept(request());
        awaitWorkerBusy();
        notificationService.accept(request());

        assertThatCode(() -> notificationService.accept(request())).doesNotThrowAnyException();

        smtpMayAnswer.countDown();

        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> verify(mailGateway, times(2)).send(anyString(), anyString(), anyString()));
        verify(outboxRepository, times(3)).insertIfAbsent(any(), anyString(), anyString(), anyString(), anyString(), any());
        verify(outboxRepository, times(2)).lockPending(any(UUID.class));
    }

    private static NotificationRequest request() {
        return new NotificationRequest(UUID.randomUUID(), NotificationType.WELCOME, "worker@example.com",
                Map.of("username", "worker"));
    }

    private void awaitWorkerBusy() {
        await().atMost(Duration.ofSeconds(3)).until(() -> notificationAsyncExecutor.getActiveCount() == 1
                && notificationAsyncExecutor.getThreadPoolExecutor().getQueue().isEmpty());
    }

    private void blockOnSmtp() {
        try {
            smtpMayAnswer.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}

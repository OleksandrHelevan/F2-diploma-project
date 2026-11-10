package com.bricklayers.notificationservice.scheduling;

import com.bricklayers.notificationservice.notification.NotificationRetryService;
import com.bricklayers.notificationservice.notification.NotificationRetryService.RetrySummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.bricklayers.notificationservice.scheduling.QuartzTestSupport.config;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuartzSchedulerBehaviourTest {

    private Scheduler scheduler;

    @BeforeEach
    void startScheduler() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "test-" + UUID.randomUUID());
        properties.setProperty("org.quartz.threadPool.threadCount", "3");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        scheduler = new StdSchedulerFactory(properties).getScheduler();
    }

    @AfterEach
    void stopScheduler() throws Exception {
        scheduler.shutdown(false);
    }

    @Test
    void retryJob_nextFireWaitsForTheRunningOne_evenWithFreeWorkerThreads() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        AtomicInteger runs = new AtomicInteger();
        NotificationRetryService slowService = mock(NotificationRetryService.class);
        when(slowService.retryPending(anyInt(), anyInt(), any())).thenAnswer(invocation -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            Thread.sleep(300);
            running.decrementAndGet();
            runs.incrementAndGet();
            return new RetrySummary(0, 0, 0, 0, 0);
        });
        scheduler.setJobFactory((bundle, ignored) -> new NotificationRetryJob(slowService));

        QuartzJobsConfig config = config("Europe/Kyiv");
        JobDetail job = config.notificationRetryJobDetail();
        Trigger every50ms = TriggerBuilder.newTrigger().forJob(job.getKey())
                .withIdentity("fast", QuartzJobsConfig.NOTIFICATIONS_GROUP)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(50).repeatForever()
                        .withMisfireHandlingInstructionNextWithRemainingCount())
                .startNow().build();
        scheduler.scheduleJob(job, every50ms);
        scheduler.start();

        await().atMost(Duration.ofSeconds(5)).until(() -> runs.get() >= 3);

        assertThat(maxRunning.get()).isEqualTo(1);
    }

    @Test
    void reconciler_keepsUnchangedTriggers_andReschedulesChangedOnes() throws Exception {
        QuartzJobsConfig deployedVersion = config("Europe/Kyiv");
        JobDetail job = deployedVersion.notificationRetentionJobDetail();
        CronTrigger stored = deployedVersion.notificationRetentionDailyTrigger(job);
        scheduler.scheduleJob(job, stored);
        Date nextFireBefore = scheduler.getTrigger(stored.getKey()).getNextFireTime();

        new QuartzScheduleReconciler(scheduler, List.of(job), List.of(deployedVersion.notificationRetentionDailyTrigger(job)))
                .reconcile();
        assertThat(scheduler.getTrigger(stored.getKey()).getNextFireTime()).isEqualTo(nextFireBefore);

        QuartzJobsConfig newVersion = new QuartzJobsConfig("Europe/Kyiv", Duration.ofSeconds(60),
                "0 0 3 * * ?", "0 0 4 * * ?", 30);
        new QuartzScheduleReconciler(scheduler, List.of(job), List.of(newVersion.notificationRetentionDailyTrigger(job)))
                .reconcile();

        CronTrigger after = (CronTrigger) scheduler.getTrigger(stored.getKey());
        assertThat(after.getCronExpression()).isEqualTo("0 0 4 * * ?");
        assertThat(scheduler.getTriggersOfJob(job.getKey())).hasSize(1);
    }

    @Test
    void reconciler_replacesAJobWhoseDefaultsChanged() throws Exception {
        JobDetail job = config("Europe/Kyiv").notificationRetentionJobDetail();
        scheduler.addJob(job, false);
        JobDetail changed = job.getJobBuilder().usingJobData(NotificationRetentionJob.RETENTION_DAYS, 30).build();

        new QuartzScheduleReconciler(scheduler, List.of(changed), List.of()).reconcile();

        assertThat(scheduler.getJobDetail(job.getKey()).getJobDataMap().getInt(NotificationRetentionJob.RETENTION_DAYS))
                .isEqualTo(30);
    }
}

package com.bricklayers.notificationservice.scheduling;

import com.bricklayers.notificationservice.notification.NotificationRetryService;
import com.bricklayers.notificationservice.notification.NotificationRetryService.RetrySummary;
import com.bricklayers.notificationservice.notification.NotificationRetentionService;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.JobExecutionException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.JobExecutionContextImpl;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static com.bricklayers.notificationservice.scheduling.QuartzTestSupport.config;
import static com.bricklayers.notificationservice.scheduling.QuartzTestSupport.contextFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class QuartzJobsTest {

    private final QuartzJobsConfig config = config("Europe/Kyiv");
    private final JobDetail retryJobDetail = config.notificationRetryJobDetail();
    private final JobDetail retentionJobDetail = config.notificationRetentionJobDetail();
    private final NotificationRetryService retryService = mock(NotificationRetryService.class);
    private final NotificationRetentionService retentionService = mock(NotificationRetentionService.class);
    private final NotificationRetryJob retryJob = new NotificationRetryJob(retryService);
    private final NotificationRetentionJob retentionJob = new NotificationRetentionJob(retentionService);

    @Test
    void retryJob_readsMergedParametersAndMakesOneServiceCall() throws Exception {
        RetrySummary summary = new RetrySummary(3, 3, 0, 0, 0);
        when(retryService.retryPending(20, 5, Duration.ofSeconds(30))).thenReturn(summary);
        JobExecutionContextImpl context = contextFor(retryJobDetail,
                config.notificationRetryFrequentTrigger(retryJobDetail), retryJob, new Date());

        retryJob.execute(context);

        verify(retryService).retryPending(20, 5, Duration.ofSeconds(30));
        verifyNoMoreInteractions(retryService);
        assertThat(context.getResult()).isEqualTo(summary);
    }

    @Test
    void retentionJob_passesTheScheduledFireTime_soARefireUsesTheSameCutoff() throws Exception {
        Date scheduled = Date.from(Instant.parse("2026-11-05T00:30:00Z"));
        JobExecutionContextImpl context = contextFor(retentionJobDetail,
                config.notificationRetentionDailyTrigger(retentionJobDetail), retentionJob, scheduled);

        retentionJob.execute(context);

        verify(retentionService).purgeExpired(30, scheduled.toInstant());
    }

    @Test
    void misspelledTriggerKey_isRejectedExplicitly_insteadOfSilentlyUsingTheDefault() {
        Trigger typo = TriggerBuilder.newTrigger().forJob(retryJobDetail.getKey())
                .withIdentity("typo", QuartzJobsConfig.NOTIFICATIONS_GROUP)
                .usingJobData("batchsize", 10)
                .startNow().build();

        assertThatThrownBy(() -> retryJob.execute(contextFor(retryJobDetail, typo, retryJob, new Date())))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("unknown parameter(s) [batchsize]")
                .hasMessageContaining("batchSize")
                .satisfies(e -> assertThat(((JobExecutionException) e).refireImmediately()).isFalse());
        verify(retryService, never()).retryPending(anyInt(), anyInt(), any());
    }

    @Test
    void missingRequiredKey_isRejectedExplicitly() throws Exception {
        JobDetail withoutDefaults = retentionJobDetail.getJobBuilder().setJobData(new org.quartz.JobDataMap()).build();

        assertThatThrownBy(() -> retentionJob.execute(contextFor(withoutDefaults,
                config.notificationRetentionDailyTrigger(withoutDefaults), retentionJob, new Date())))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("missing required parameter 'retentionDays'");
        verify(retentionService, never()).purgeExpired(anyInt(), any());
    }

    @Test
    void invalidValue_isRejectedExplicitly() {
        Trigger negative = TriggerBuilder.newTrigger().forJob(retryJobDetail.getKey())
                .withIdentity("negative", QuartzJobsConfig.NOTIFICATIONS_GROUP)
                .usingJobData(NotificationRetryJob.BATCH_SIZE, "zero")
                .startNow().build();

        assertThatThrownBy(() -> retryJob.execute(contextFor(retryJobDetail, negative, retryJob, new Date())))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("'batchSize' is not an integer");
    }

    @Test
    void serviceFailure_isRefiredImmediatelyAtMostTwice_thenWaitsForTheNextFire() {
        when(retryService.retryPending(anyInt(), anyInt(), any())).thenThrow(new IllegalStateException("database is down"));
        JobExecutionContextImpl context = contextFor(retryJobDetail,
                config.notificationRetryFrequentTrigger(retryJobDetail), retryJob, new Date());

        for (int run = 0; run <= JobFailurePolicy.MAX_IMMEDIATE_REFIRES; run++) {
            boolean lastAllowedRun = run == JobFailurePolicy.MAX_IMMEDIATE_REFIRES;
            assertThatThrownBy(() -> retryJob.execute(context))
                    .isInstanceOf(JobExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .satisfies(e -> assertThat(((JobExecutionException) e).refireImmediately()).isEqualTo(!lastAllowedRun));
            context.incrementRefireCount();
        }
        verify(retryService, times(JobFailurePolicy.MAX_IMMEDIATE_REFIRES + 1)).retryPending(anyInt(), anyInt(), any());
    }
}

package com.bricklayers.notificationservice.scheduling;

import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

import java.util.Date;
import java.util.List;

import static com.bricklayers.notificationservice.scheduling.QuartzTestSupport.config;
import static com.bricklayers.notificationservice.scheduling.QuartzTestSupport.contextFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QuartzJobsConfigTest {

    private final QuartzJobsConfig config = config("Europe/Kyiv");
    private final JobDetail retryJob = config.notificationRetryJobDetail();
    private final JobDetail retentionJob = config.notificationRetentionJobDetail();
    private final SimpleTrigger retryFrequent = config.notificationRetryFrequentTrigger(retryJob);
    private final CronTrigger retryNightly = config.notificationRetryNightlyTrigger(retryJob);
    private final CronTrigger retentionDaily = config.notificationRetentionDailyTrigger(retentionJob);

    @Test
    void everyJobAndTriggerHasAnExplicitNameAndGroup() {
        assertThat(retryJob.getKey().getName()).isEqualTo("notification-retry");
        assertThat(retryJob.getKey().getGroup()).isEqualTo(QuartzJobsConfig.NOTIFICATIONS_GROUP);
        assertThat(retentionJob.getKey().getName()).isEqualTo("notification-retention");
        assertThat(retentionJob.getKey().getGroup()).isEqualTo(QuartzJobsConfig.MAINTENANCE_GROUP);

        for (Trigger trigger : List.of(retryFrequent, retryNightly, retentionDaily)) {
            assertThat(trigger.getKey().getName()).isNotBlank();
            assertThat(trigger.getKey().getGroup()).isNotEqualTo(org.quartz.utils.Key.DEFAULT_GROUP);
        }
    }

    @Test
    void everyTriggerPointsToItsJobByJobKey_andNotificationRetryHasTwoTriggers() {
        assertThat(retryFrequent.getJobKey()).isEqualTo(QuartzJobsConfig.NOTIFICATION_RETRY_JOB).isEqualTo(retryJob.getKey());
        assertThat(retryNightly.getJobKey()).isEqualTo(QuartzJobsConfig.NOTIFICATION_RETRY_JOB);
        assertThat(retentionDaily.getJobKey()).isEqualTo(QuartzJobsConfig.NOTIFICATION_RETENTION_JOB).isEqualTo(retentionJob.getKey());
        assertThat(retryFrequent.getKey()).isNotEqualTo(retryNightly.getKey());
    }

    @Test
    void jobsAreDurableAndRetryJobForbidsConcurrentRuns() {
        assertThat(retryJob.isDurable()).isTrue();
        assertThat(retryJob.requestsRecovery()).isTrue();
        assertThat(retryJob.isConcurrentExecutionDisallowed()).isTrue();
        assertThat(retentionJob.isConcurrentExecutionDisallowed()).isTrue();
    }

    @Test
    void mergedJobDataMap_triggerOverridesOnlyTheKeysItSets() {
        JobDataMap frequent = contextFor(retryJob, retryFrequent, mock(NotificationRetryJob.class), new Date())
                .getMergedJobDataMap();
        assertThat(retryJob.getJobDataMap().getInt(NotificationRetryJob.BATCH_SIZE)).isEqualTo(50);
        assertThat(frequent.getInt(NotificationRetryJob.BATCH_SIZE)).isEqualTo(20);
        assertThat(frequent.getInt(NotificationRetryJob.MAX_ATTEMPTS)).isEqualTo(5);
        assertThat(frequent.getInt(NotificationRetryJob.MIN_AGE_SECONDS)).isEqualTo(30);

        JobDataMap nightly = contextFor(retryJob, retryNightly, mock(NotificationRetryJob.class), new Date())
                .getMergedJobDataMap();
        assertThat(nightly.getInt(NotificationRetryJob.BATCH_SIZE)).isEqualTo(500);
        assertThat(nightly.getInt(NotificationRetryJob.MIN_AGE_SECONDS)).isZero();
        assertThat(nightly.getInt(NotificationRetryJob.MAX_ATTEMPTS)).isEqualTo(5);
    }

    @Test
    void everyTriggerHasAnExplicitMisfireInstruction() {
        assertThat(retryFrequent.getMisfireInstruction())
                .isEqualTo(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT);
        assertThat(retryNightly.getMisfireInstruction())
                .isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
        assertThat(retentionDaily.getMisfireInstruction())
                .isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        for (Trigger trigger : List.of(retryFrequent, retryNightly, retentionDaily)) {
            assertThat(trigger.getMisfireInstruction()).isNotEqualTo(Trigger.MISFIRE_INSTRUCTION_SMART_POLICY);
        }
    }

    @Test
    void cronTriggersUseTheConfiguredTimeZone() {
        assertThat(retryNightly.getTimeZone().getID()).isEqualTo("Europe/Kyiv");
        assertThat(retentionDaily.getTimeZone().getID()).isEqualTo("Europe/Kyiv");
        assertThat(retryNightly.getCronExpression()).isEqualTo("0 0 3 * * ?");
    }

    @Test
    void unknownTimeZoneId_fallsBackToGmt() {
        QuartzJobsConfig misconfigured = config("Mars/Olympus_Mons");

        CronTrigger trigger = misconfigured.notificationRetentionDailyTrigger(misconfigured.notificationRetentionJobDetail());

        assertThat(trigger.getTimeZone().getID()).isEqualTo("GMT");
        assertThat(QuartzTimeZones.resolve("Mars/Olympus_Mons").getID()).isEqualTo("GMT");
        assertThat(QuartzTimeZones.resolve("UTC").getID()).isEqualTo("UTC");
    }
}

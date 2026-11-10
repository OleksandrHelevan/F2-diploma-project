package com.bricklayers.notificationservice.scheduling;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.TimeZone;

@Configuration
public class QuartzJobsConfig {

    public static final String NOTIFICATIONS_GROUP = "notifications";
    public static final String MAINTENANCE_GROUP = "maintenance";

    public static final JobKey NOTIFICATION_RETRY_JOB = JobKey.jobKey("notification-retry", NOTIFICATIONS_GROUP);
    public static final TriggerKey NOTIFICATION_RETRY_FREQUENT =
            TriggerKey.triggerKey("notification-retry-frequent", NOTIFICATIONS_GROUP);
    public static final TriggerKey NOTIFICATION_RETRY_NIGHTLY =
            TriggerKey.triggerKey("notification-retry-nightly-sweep", NOTIFICATIONS_GROUP);

    public static final JobKey NOTIFICATION_RETENTION_JOB = JobKey.jobKey("notification-retention", MAINTENANCE_GROUP);
    public static final TriggerKey NOTIFICATION_RETENTION_DAILY =
            TriggerKey.triggerKey("notification-retention-daily", MAINTENANCE_GROUP);

    private final TimeZone timeZone;
    private final Duration retryInterval;
    private final String nightlySweepCron;
    private final String retentionCron;
    private final int retentionDays;

    public QuartzJobsConfig(@Value("${app.quartz.time-zone:Europe/Kyiv}") String timeZoneId,
                            @Value("${app.quartz.notification-retry-interval:60s}") Duration retryInterval,
                            @Value("${app.quartz.notification-sweep-cron:0 0 3 * * ?}") String nightlySweepCron,
                            @Value("${app.quartz.notification-retention-cron:0 30 2 * * ?}") String retentionCron,
                            @Value("${app.quartz.notification-retention-days:30}") int retentionDays) {
        this.timeZone = QuartzTimeZones.resolve(timeZoneId);
        this.retryInterval = retryInterval;
        this.nightlySweepCron = nightlySweepCron;
        this.retentionCron = retentionCron;
        this.retentionDays = retentionDays;
    }

    @Bean
    public JobDetail notificationRetryJobDetail() {
        return JobBuilder.newJob(NotificationRetryJob.class)
                .withIdentity(NOTIFICATION_RETRY_JOB)
                .withDescription("Delivers PENDING e-mails from notification_outbox (rejected or failed async sends)")
                .usingJobData(NotificationRetryJob.BATCH_SIZE, 50)
                .usingJobData(NotificationRetryJob.MAX_ATTEMPTS, 5)
                .usingJobData(NotificationRetryJob.MIN_AGE_SECONDS, 30)
                .storeDurably()

                .requestRecovery()
                .build();
    }

    @Bean
    public SimpleTrigger notificationRetryFrequentTrigger(@Qualifier("notificationRetryJobDetail") JobDetail job) {
        return TriggerBuilder.newTrigger()
                .forJob(job.getKey())
                .withIdentity(NOTIFICATION_RETRY_FREQUENT)
                .withDescription("Every " + retryInterval.toSeconds() + " s, small batch")
                .usingJobData(NotificationRetryJob.BATCH_SIZE, 20)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(retryInterval.toMillis())
                        .repeatForever()

                        .withMisfireHandlingInstructionNextWithRemainingCount())
                .startNow()
                .build();
    }

    @Bean
    public CronTrigger notificationRetryNightlyTrigger(@Qualifier("notificationRetryJobDetail") JobDetail job) {
        return TriggerBuilder.newTrigger()
                .forJob(job.getKey())
                .withIdentity(NOTIFICATION_RETRY_NIGHTLY)
                .withDescription("Nightly sweep, large batch")
                .usingJobData(NotificationRetryJob.BATCH_SIZE, 500)
                .usingJobData(NotificationRetryJob.MIN_AGE_SECONDS, 0)
                .withSchedule(CronScheduleBuilder.cronSchedule(nightlySweepCron)
                        .inTimeZone(timeZone)

                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }

    @Bean
    public JobDetail notificationRetentionJobDetail() {
        return JobBuilder.newJob(NotificationRetentionJob.class)
                .withIdentity(NOTIFICATION_RETENTION_JOB)
                .withDescription("Deletes SENT/FAILED notification_outbox rows older than retentionDays")
                .usingJobData(NotificationRetentionJob.RETENTION_DAYS, retentionDays)
                .storeDurably()
                .build();
    }

    @Bean
    public CronTrigger notificationRetentionDailyTrigger(@Qualifier("notificationRetentionJobDetail") JobDetail job) {
        return TriggerBuilder.newTrigger()
                .forJob(job.getKey())
                .withIdentity(NOTIFICATION_RETENTION_DAILY)
                .withDescription("Daily cleanup")
                .withSchedule(CronScheduleBuilder.cronSchedule(retentionCron)
                        .inTimeZone(timeZone)

                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }
}

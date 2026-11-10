package com.bricklayers.notificationservice.scheduling;

import com.bricklayers.notificationservice.notification.NotificationRetentionService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.Date;
import java.util.Set;

@DisallowConcurrentExecution
public class NotificationRetentionJob implements Job {

    public static final String RETENTION_DAYS = "retentionDays";
    static final Set<String> PARAMETERS = Set.of(RETENTION_DAYS);

    private final NotificationRetentionService retentionService;

    public NotificationRetentionJob(NotificationRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        int retentionDays = JobParameters.of(context, PARAMETERS).intAtLeast(RETENTION_DAYS, 1);

        Date asOf = context.getScheduledFireTime() != null ? context.getScheduledFireTime() : context.getFireTime();
        try {
            context.setResult(retentionService.purgeExpired(retentionDays, asOf.toInstant()));
        } catch (RuntimeException failure) {
            throw JobFailurePolicy.onFailure(context, failure);
        }
    }
}

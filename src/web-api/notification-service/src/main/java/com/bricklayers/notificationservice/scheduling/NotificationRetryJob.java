package com.bricklayers.notificationservice.scheduling;

import com.bricklayers.notificationservice.notification.NotificationRetryService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.time.Duration;
import java.util.Set;

@DisallowConcurrentExecution
public class NotificationRetryJob implements Job {

    public static final String BATCH_SIZE = "batchSize";
    public static final String MAX_ATTEMPTS = "maxAttempts";
    public static final String MIN_AGE_SECONDS = "minAgeSeconds";
    static final Set<String> PARAMETERS = Set.of(BATCH_SIZE, MAX_ATTEMPTS, MIN_AGE_SECONDS);

    private final NotificationRetryService retryService;

    public NotificationRetryJob(NotificationRetryService retryService) {
        this.retryService = retryService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobParameters parameters = JobParameters.of(context, PARAMETERS);
        int batchSize = parameters.intAtLeast(BATCH_SIZE, 1);
        int maxAttempts = parameters.intAtLeast(MAX_ATTEMPTS, 1);
        int minAgeSeconds = parameters.intAtLeast(MIN_AGE_SECONDS, 0);
        try {
            context.setResult(retryService.retryPending(batchSize, maxAttempts, Duration.ofSeconds(minAgeSeconds)));
        } catch (RuntimeException failure) {
            throw JobFailurePolicy.onFailure(context, failure);
        }
    }
}

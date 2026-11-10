package com.bricklayers.notificationservice.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

@Slf4j
public final class JobFailurePolicy {

    public static final int MAX_IMMEDIATE_REFIRES = 2;

    private JobFailurePolicy() {
    }

    public static JobExecutionException onFailure(JobExecutionContext context, RuntimeException failure) {
        int refireCount = context.getRefireCount();
        String job = context.getJobDetail().getKey().toString();
        if (refireCount < MAX_IMMEDIATE_REFIRES) {
            log.warn("Job {} failed (run {} of {}), refiring immediately: {}",
                    job, refireCount + 1, MAX_IMMEDIATE_REFIRES + 1, failure.toString());
            return new JobExecutionException(failure, true);
        }
        log.error("Job {} failed {} times in a row; giving up until its next scheduled fire",
                job, refireCount + 1, failure);
        return new JobExecutionException(failure, false);
    }
}

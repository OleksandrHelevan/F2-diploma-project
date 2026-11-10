package com.bricklayers.notificationservice.scheduling;

import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.impl.JobExecutionContextImpl;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;

import java.util.Date;

import static org.mockito.Mockito.mock;

final class QuartzTestSupport {

    private QuartzTestSupport() {
    }

    static JobExecutionContextImpl contextFor(JobDetail job, Trigger trigger, Job instance, Date scheduledFireTime) {
        Date now = new Date();
        TriggerFiredBundle bundle = new TriggerFiredBundle(job, (OperableTrigger) trigger, null, false,
                now, scheduledFireTime, null, null);
        return new JobExecutionContextImpl(mock(Scheduler.class), bundle, instance);
    }

    static QuartzJobsConfig config(String timeZoneId) {
        return new QuartzJobsConfig(timeZoneId, java.time.Duration.ofSeconds(60), "0 0 3 * * ?", "0 30 2 * * ?", 30);
    }
}

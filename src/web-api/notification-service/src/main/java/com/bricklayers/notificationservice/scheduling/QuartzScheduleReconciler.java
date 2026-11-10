package com.bricklayers.notificationservice.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class QuartzScheduleReconciler {

    private final Scheduler scheduler;
    private final List<JobDetail> jobs;
    private final List<Trigger> triggers;

    public QuartzScheduleReconciler(Scheduler scheduler, List<JobDetail> jobs, List<Trigger> triggers) {
        this.scheduler = scheduler;
        this.jobs = jobs;
        this.triggers = triggers;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() throws SchedulerException {
        for (JobDetail declared : jobs) {
            JobDetail stored = scheduler.getJobDetail(declared.getKey());
            if (stored != null && !sameJob(stored, declared)) {
                scheduler.addJob(declared, true);
                log.warn("Quartz job {} changed in this version: stored definition replaced", declared.getKey());
            }
        }
        for (Trigger declared : triggers) {
            Trigger stored = scheduler.getTrigger(declared.getKey());
            if (stored == null) {
                scheduler.scheduleJob(declared);
                log.warn("Quartz trigger {} was missing and has been scheduled", declared.getKey());
            } else if (!sameTrigger(stored, declared)) {
                scheduler.rescheduleJob(declared.getKey(), declared);
                log.warn("Quartz trigger {} changed in this version: rescheduled", declared.getKey());
            }
        }
        log.info("Quartz schedule reconciled: {} job(s), {} trigger(s) on scheduler {}",
                jobs.size(), triggers.size(), scheduler.getSchedulerInstanceId());
    }

    static boolean sameJob(JobDetail stored, JobDetail declared) {
        return stored.getJobClass().equals(declared.getJobClass())
                && Objects.equals(stored.getDescription(), declared.getDescription())
                && stored.isDurable() == declared.isDurable()
                && stored.requestsRecovery() == declared.requestsRecovery()
                && sameData(stored.getJobDataMap(), declared.getJobDataMap());
    }

    static boolean sameTrigger(Trigger stored, Trigger declared) {
        if (!stored.getJobKey().equals(declared.getJobKey())
                || stored.getMisfireInstruction() != declared.getMisfireInstruction()
                || !Objects.equals(stored.getDescription(), declared.getDescription())
                || !sameData(stored.getJobDataMap(), declared.getJobDataMap())) {
            return false;
        }
        if (stored instanceof CronTrigger storedCron && declared instanceof CronTrigger declaredCron) {
            return storedCron.getCronExpression().equals(declaredCron.getCronExpression())
                    && storedCron.getTimeZone().getID().equals(declaredCron.getTimeZone().getID());
        }
        if (stored instanceof SimpleTrigger storedSimple && declared instanceof SimpleTrigger declaredSimple) {
            return storedSimple.getRepeatInterval() == declaredSimple.getRepeatInterval()
                    && storedSimple.getRepeatCount() == declaredSimple.getRepeatCount();
        }
        return false;
    }

    private static boolean sameData(JobDataMap stored, JobDataMap declared) {
        return new HashMap<>(stored.getWrappedMap()).equals(new HashMap<>(declared.getWrappedMap()));
    }
}

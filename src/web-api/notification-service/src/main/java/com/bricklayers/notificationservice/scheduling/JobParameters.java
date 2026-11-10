package com.bricklayers.notificationservice.scheduling;

import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.Set;
import java.util.TreeSet;

public final class JobParameters {

    private final JobDataMap values;
    private final String jobName;

    private JobParameters(JobDataMap values, String jobName) {
        this.values = values;
        this.jobName = jobName;
    }

    public static JobParameters of(JobExecutionContext context, Set<String> expectedKeys) throws JobExecutionException {
        return of(context.getMergedJobDataMap(), context.getJobDetail().getKey().toString(), expectedKeys);
    }

    public static JobParameters of(JobDataMap values, String jobName, Set<String> expectedKeys) throws JobExecutionException {
        Set<String> unknown = new TreeSet<>(values.keySet());
        unknown.removeAll(expectedKeys);
        if (!unknown.isEmpty()) {
            throw new JobExecutionException("Job " + jobName + " got unknown parameter(s) " + unknown
                    + "; expected " + new TreeSet<>(expectedKeys), false);
        }
        return new JobParameters(values, jobName);
    }

    public int intAtLeast(String key, int min) throws JobExecutionException {
        if (!values.containsKey(key)) {
            throw new JobExecutionException("Job " + jobName + " is missing required parameter '" + key + "'", false);
        }
        Object raw = values.get(key);
        int value;
        if (raw instanceof Number number) {
            value = number.intValue();
        } else if (raw instanceof String text) {
            try {
                value = Integer.parseInt(text.trim());
            } catch (NumberFormatException exception) {
                throw new JobExecutionException("Job " + jobName + " parameter '" + key + "' is not an integer: " + text, false);
            }
        } else {
            throw new JobExecutionException("Job " + jobName + " parameter '" + key + "' has unsupported type "
                    + (raw == null ? "null" : raw.getClass().getSimpleName()), false);
        }
        if (value < min) {
            throw new JobExecutionException("Job " + jobName + " parameter '" + key + "' must be >= " + min + ", got " + value, false);
        }
        return value;
    }
}

package com.bricklayers.notificationservice.scheduling;

import lombok.extern.slf4j.Slf4j;

import java.util.TimeZone;

@Slf4j
public final class QuartzTimeZones {

    private QuartzTimeZones() {
    }

    public static TimeZone resolve(String zoneId) {
        TimeZone zone = TimeZone.getTimeZone(zoneId);
        if ("GMT".equals(zone.getID()) && !"GMT".equals(zoneId)) {
            log.warn("Unknown time zone id '{}': Quartz cron triggers fall back to GMT", zoneId);
        }
        return zone;
    }
}

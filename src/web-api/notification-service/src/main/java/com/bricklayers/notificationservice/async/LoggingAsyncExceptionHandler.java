package com.bricklayers.notificationservice.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class LoggingAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    private final AtomicLong failures = new AtomicLong();

    @Override
    public void handleUncaughtException(Throwable exception, Method method, Object... params) {
        long total = failures.incrementAndGet();
        log.error("Async task {}.{}{} failed on thread {} (async failures so far: {}): {}",
                method.getDeclaringClass().getSimpleName(), method.getName(), Arrays.toString(params),
                Thread.currentThread().getName(), total, exception.getMessage(), exception);
    }

    public long failures() {
        return failures.get();
    }
}

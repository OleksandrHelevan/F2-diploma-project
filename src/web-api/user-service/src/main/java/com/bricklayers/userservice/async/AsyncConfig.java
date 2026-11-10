package com.bricklayers.userservice.async;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;


@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String EXECUTOR = "userServiceAsyncExecutor";

    private final AsyncExecutorSettings settings;
    private final LoggingAsyncExceptionHandler exceptionHandler;
    private ThreadPoolTaskExecutor executor;

    public AsyncConfig(@Value("${app.async.core-size:4}") int coreSize,
                       @Value("${app.async.max-size:8}") int maxSize,
                       @Value("${app.async.queue-capacity:50}") int queueCapacity,
                       @Value("${app.async.thread-name-prefix:user-async-}") String threadNamePrefix,
                       @Value("${app.async.await-termination:20s}") Duration awaitTermination,
                       LoggingAsyncExceptionHandler exceptionHandler) {
        this.settings = new AsyncExecutorSettings(coreSize, maxSize, queueCapacity, threadNamePrefix, awaitTermination);
        this.exceptionHandler = exceptionHandler;
    }

    @Bean(name = EXECUTOR)
    public ThreadPoolTaskExecutor userServiceAsyncExecutor() {
        if (executor == null) {
            executor = newExecutor(settings);
        }
        return executor;
    }

    public static ThreadPoolTaskExecutor newExecutor(AsyncExecutorSettings settings) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(settings.coreSize());
        executor.setMaxPoolSize(settings.maxSize());
        executor.setQueueCapacity(settings.queueCapacity());
        executor.setThreadNamePrefix(settings.threadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(settings.awaitTermination().toMillis());
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return userServiceAsyncExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return exceptionHandler;
    }

    public record AsyncExecutorSettings(int coreSize, int maxSize, int queueCapacity,
                                        String threadNamePrefix, Duration awaitTermination) {
    }
}

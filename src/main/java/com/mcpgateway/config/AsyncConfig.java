package com.mcpgateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * Async Configuration for Event Listeners and Background Tasks
 *
 * Thread Pool Configuration:
 * - Core pool size: 5 threads (handles typical load)
 * - Max pool size: 20 threads (handles peak load)
 * - Queue capacity: 100 tasks (buffer for temporary spikes)
 * - Thread name prefix: "async-event-" (for easy identification in logs)
 *
 * Use Cases:
 * - Domain event processing (@EventListener + @Async)
 * - Email sending
 * - Webhook notifications
 * - Audit logging
 * - Analytics tracking
 *
 * Error Handling:
 * - Custom exception handler logs errors without failing the main transaction
 * - Exceptions in async methods don't propagate to caller
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Configure thread pool executor for async tasks
     */
    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size: minimum threads kept alive
        executor.setCorePoolSize(5);

        // Max pool size: maximum threads under high load
        executor.setMaxPoolSize(20);

        // Queue capacity: tasks buffered before rejecting
        executor.setQueueCapacity(100);

        // Thread naming for easy debugging
        executor.setThreadNamePrefix("async-event-");

        // Wait for all tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // Reject new tasks if pool is exhausted (default: AbortPolicy)
        // Alternative: CallerRunsPolicy (runs in caller thread)

        executor.initialize();

        log.info("Async executor configured: corePoolSize=5, maxPoolSize=20, queueCapacity=100");
        return executor;
    }

    /**
     * Custom exception handler for uncaught exceptions in async methods
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }

    /**
     * Logs exceptions from async methods without propagating them
     */
    public static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("Uncaught exception in async method: {}.{}",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName(),
                    ex);

            log.error("Method parameters: {}", params);

            // Future enhancement: Send alert to monitoring system
            // alertService.sendAlert("Async task failed", ex);
        }
    }
}

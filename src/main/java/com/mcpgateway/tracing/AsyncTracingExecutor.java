package com.mcpgateway.tracing;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Async Tracing Executor - propagates trace context across threads.
 *
 * Provides:
 * - Context-aware ExecutorService
 * - Context-aware CompletableFuture helpers
 * - Automatic span creation for async operations
 */
@Slf4j
@Component
public class AsyncTracingExecutor {

    private final Tracer tracer;
    private final TracingService tracingService;
    private final ExecutorService virtualThreadExecutor;
    private final ExecutorService contextAwareExecutor;

    public AsyncTracingExecutor(Tracer tracer, TracingService tracingService) {
        this.tracer = tracer;
        this.tracingService = tracingService;

        // Create virtual thread executor
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

        // Wrap with context propagation
        this.contextAwareExecutor = ContextExecutorService.wrap(virtualThreadExecutor);
    }

    /**
     * Run a task with trace context propagated.
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        return runAsync(task, "async-task");
    }

    /**
     * Run a named task with trace context.
     */
    public CompletableFuture<Void> runAsync(Runnable task, String spanName) {
        ContextSnapshot snapshot = ContextSnapshotFactory.builder().build().captureAll();

        return CompletableFuture.runAsync(() -> {
            try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
                try (TracingService.SpanWrapper span = tracingService.startSpan(spanName)) {
                    span.tag("async", "true");
                    task.run();
                }
            }
        }, contextAwareExecutor);
    }

    /**
     * Supply a value with trace context propagated.
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return supplyAsync(supplier, "async-supply");
    }

    /**
     * Supply a named value with trace context.
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, String spanName) {
        ContextSnapshot snapshot = ContextSnapshotFactory.builder().build().captureAll();

        return CompletableFuture.supplyAsync(() -> {
            try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
                try (TracingService.SpanWrapper span = tracingService.startSpan(spanName)) {
                    span.tag("async", "true");
                    return supplier.get();
                }
            }
        }, contextAwareExecutor);
    }

    /**
     * Submit a callable with trace context.
     */
    public <T> Future<T> submit(Callable<T> callable) {
        return submit(callable, "async-callable");
    }

    /**
     * Submit a named callable with trace context.
     */
    public <T> Future<T> submit(Callable<T> callable, String spanName) {
        ContextSnapshot snapshot = ContextSnapshotFactory.builder().build().captureAll();

        return contextAwareExecutor.submit(() -> {
            try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
                try (TracingService.SpanWrapper span = tracingService.startSpan(spanName)) {
                    span.tag("async", "true");
                    return callable.call();
                }
            }
        });
    }

    /**
     * Wrap a runnable with trace context.
     */
    public Runnable wrap(Runnable task) {
        ContextSnapshot snapshot = ContextSnapshotFactory.builder().build().captureAll();

        return () -> {
            try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
                task.run();
            }
        };
    }

    /**
     * Wrap a callable with trace context.
     */
    public <T> Callable<T> wrap(Callable<T> callable) {
        ContextSnapshot snapshot = ContextSnapshotFactory.builder().build().captureAll();

        return () -> {
            try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
                return callable.call();
            }
        };
    }

    /**
     * Wrap a supplier with trace context.
     */
    public <T> Supplier<T> wrap(Supplier<T> supplier) {
        ContextSnapshot snapshot = ContextSnapshotFactory.builder().build().captureAll();

        return () -> {
            try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
                return supplier.get();
            }
        };
    }

    /**
     * Get the context-aware executor.
     */
    public ExecutorService getExecutor() {
        return contextAwareExecutor;
    }

    /**
     * Continue a trace from parent context in a new thread.
     */
    public CompletableFuture<Void> continueTrace(String parentTraceId, String parentSpanId, Runnable task) {
        return CompletableFuture.runAsync(() -> {
            // Create a new span linked to parent (if tracer supports it)
            try (TracingService.SpanWrapper span = tracingService.startSpan("continued-trace")) {
                span.tag("parent.traceId", parentTraceId);
                span.tag("parent.spanId", parentSpanId);
                span.tag("async", "true");
                task.run();
            }
        }, contextAwareExecutor);
    }

    /**
     * Shutdown the executor.
     */
    public void shutdown() {
        contextAwareExecutor.shutdown();
        virtualThreadExecutor.shutdown();
    }
}

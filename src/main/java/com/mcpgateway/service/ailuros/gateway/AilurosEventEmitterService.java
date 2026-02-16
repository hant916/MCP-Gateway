package com.mcpgateway.service.ailuros.gateway;

import com.mcpgateway.config.AilurosObservabilityProperties;
import com.mcpgateway.dto.ailuros.CallEventV1DTO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class AilurosEventEmitterService {

    private final WebClient webClient;
    private final AilurosObservabilityProperties properties;

    private BlockingQueue<CallEventV1DTO> queue;
    private ExecutorService worker;
    private volatile boolean running = true;

    private final AtomicLong submitted = new AtomicLong(0);
    private final AtomicLong sent = new AtomicLong(0);
    private final AtomicLong dropped = new AtomicLong(0);
    private final AtomicLong failed = new AtomicLong(0);
    private final AtomicLong retried = new AtomicLong(0);

    @PostConstruct
    public void init() {
        queue = new ArrayBlockingQueue<>(properties.getMaxQueue());
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ailuros-event-emitter");
            thread.setDaemon(true);
            return thread;
        });
        worker.submit(this::runLoop);

        log.info("Ailuros emitter initialized: enabled={}, ingestUrl={}, sampleRate={}, maxQueue={}, timeoutMs={}",
            properties.isEnabled(),
            properties.getIngestUrl(),
            properties.getSampleRate(),
            properties.getMaxQueue(),
            properties.getEmitTimeoutMs());
    }

    public void emitAsync(CallEventV1DTO event) {
        if (event == null || !properties.isEnabled()) {
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() > properties.getSampleRate()) {
            return;
        }

        submitted.incrementAndGet();

        boolean offered = queue.offer(event);
        if (!offered) {
            // Drop oldest first to keep latest incident signal.
            queue.poll();
            if (!queue.offer(event)) {
                dropped.incrementAndGet();
            } else {
                dropped.incrementAndGet();
            }
        }
    }

    public Map<String, Long> snapshotMetrics() {
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("submitted", submitted.get());
        metrics.put("sent", sent.get());
        metrics.put("dropped", dropped.get());
        metrics.put("failed", failed.get());
        metrics.put("retried", retried.get());
        metrics.put("queue_size", (long) queue.size());
        return metrics;
    }

    private void runLoop() {
        while (running) {
            try {
                CallEventV1DTO event = queue.poll(500, TimeUnit.MILLISECONDS);
                if (event == null) {
                    continue;
                }

                boolean ok = doSend(event);
                if (!ok) {
                    failed.incrementAndGet();
                    // One retry only.
                    if (doSend(event)) {
                        retried.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.warn("Unexpected emitter loop error: {}", ex.getMessage());
            }
        }
    }

    private boolean doSend(CallEventV1DTO event) {
        try {
            webClient.post()
                .uri(properties.getIngestUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(event)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofMillis(properties.getEmitTimeoutMs()))
                .block();

            sent.incrementAndGet();
            return true;
        } catch (Exception ex) {
            log.debug("Failed to emit call event traceId={}, spanId={}, reason={}",
                event.getIdentity() != null ? event.getIdentity().getTraceId() : "unknown",
                event.getIdentity() != null ? event.getIdentity().getSpanId() : "unknown",
                ex.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        if (worker != null) {
            worker.shutdownNow();
            try {
                worker.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

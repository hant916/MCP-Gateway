package com.mcpgateway.stream;

import com.mcpgateway.stream.observability.StreamLogger;
import com.mcpgateway.stream.policy.*;
import com.mcpgateway.stream.transport.*;
import com.mcpgateway.stream.upstream.LlmRequest;
import com.mcpgateway.stream.upstream.LlmStreamAdapter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Main service for StreamSafe streaming.
 *
 * This is the integration point that brings together:
 * - Policy engine (decides HOW to deliver)
 * - Transports (delivers content)
 * - Upstream adapters (fetches content)
 * - Observability (logs everything)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

    private final StreamPolicyEngine policyEngine;
    private final StreamLogger streamLogger;
    private final StreamMetrics streamMetrics;
    private final StreamReplay streamReplay;
    private final LlmStreamAdapter llmStreamAdapter;

    // Active sessions
    private final Map<String, StreamSession> activeSessions = new ConcurrentHashMap<>();

    // Executor for async operations
    private final ExecutorService asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${mcp.stream.sse-timeout-ms:300000}")
    private long sseTimeoutMs;

    @Value("${mcp.stream.first-byte-timeout-ms:1000}")
    private long firstByteTimeoutMs;

    @Value("${mcp.stream.buffer.max-tokens:10000}")
    private int bufferMaxTokens;

    @Value("${mcp.stream.buffer.max-bytes:10485760}")
    private long bufferMaxBytes;

    /**
     * Start a streaming request.
     *
     * @param request The HTTP request (for context detection)
     * @param llmRequest The LLM request
     * @return StreamResult with delivery mode and response object
     */
    public StreamResult startStream(HttpServletRequest request, LlmRequest llmRequest) {
        String requestId = UUID.randomUUID().toString();

        // Build context from request
        StreamContext context = buildContext(requestId, request);

        // Make policy decision
        StreamDecision decision = policyEngine.decide(context);

        // Record metrics
        streamMetrics.recordRequest(decision.getMode(), decision.getReason());

        log.info("Stream started: requestId={}, mode={}, reason={}",
                requestId, decision.getMode(), decision.getReason());

        return switch (decision.getMode()) {
            case SSE_DIRECT -> startSseStream(requestId, context, decision, llmRequest);
            case ASYNC_JOB -> startAsyncJob(requestId, context, decision, llmRequest);
            case SYNC -> startSyncRequest(requestId, context, decision, llmRequest);
            case WS_PUSH -> throw new UnsupportedOperationException(
                    "WebSocket streaming should use WebSocket endpoint");
        };
    }

    /**
     * Start an SSE streaming response.
     */
    private StreamResult startSseStream(String requestId, StreamContext context,
                                        StreamDecision decision, LlmRequest llmRequest) {
        SseEmitter emitter = SseStreamTransport.createEmitter(sseTimeoutMs);
        SseStreamTransport transport = new SseStreamTransport(emitter, requestId);

        StreamSession session = StreamSession.builder()
                .requestId(requestId)
                .context(context)
                .decision(decision)
                .bufferMaxTokens(bufferMaxTokens)
                .bufferMaxBytes(bufferMaxBytes)
                .build();

        session.start(transport);
        activeSessions.put(requestId, session);
        streamMetrics.recordSessionStart(decision.getMode());

        // Start streaming in background
        asyncExecutor.submit(() -> executeStream(session, llmRequest));

        // Schedule first-byte timeout check
        scheduleFirstByteCheck(session);

        return StreamResult.sse(requestId, emitter, decision);
    }

    /**
     * Start an async job.
     */
    private StreamResult startAsyncJob(String requestId, StreamContext context,
                                       StreamDecision decision, LlmRequest llmRequest) {
        StreamSession session = StreamSession.builder()
                .requestId(requestId)
                .context(context)
                .decision(decision)
                .bufferMaxTokens(bufferMaxTokens)
                .bufferMaxBytes(bufferMaxBytes)
                .build();

        AsyncJobTransport transport = new AsyncJobTransport(requestId, session.getBuffer());
        session.start(transport);
        activeSessions.put(requestId, session);
        streamMetrics.recordSessionStart(decision.getMode());

        // Start processing in background
        asyncExecutor.submit(() -> executeStream(session, llmRequest));

        return StreamResult.asyncJob(requestId, decision);
    }

    /**
     * Start a synchronous request.
     */
    private StreamResult startSyncRequest(String requestId, StreamContext context,
                                          StreamDecision decision, LlmRequest llmRequest) {
        StreamSession session = StreamSession.builder()
                .requestId(requestId)
                .context(context)
                .decision(decision)
                .bufferMaxTokens(bufferMaxTokens)
                .bufferMaxBytes(bufferMaxBytes)
                .build();

        SyncTransport transport = new SyncTransport(requestId);
        session.start(transport);
        streamMetrics.recordSessionStart(decision.getMode());

        // Execute synchronously
        executeStream(session, llmRequest);

        String content = transport.getContent();
        streamMetrics.recordSessionEnd(decision.getMode());

        return StreamResult.sync(requestId, content, decision);
    }

    /**
     * Execute the stream - fetch from upstream and deliver via transport.
     */
    private void executeStream(StreamSession session, LlmRequest llmRequest) {
        try {
            llmStreamAdapter.streamTokens(llmRequest)
                    .doOnNext(token -> {
                        if (!session.sendToken(token)) {
                            log.warn("Failed to send token: requestId={}", session.getRequestId());
                        }
                    })
                    .doOnComplete(() -> {
                        session.complete();
                        onStreamComplete(session);
                    })
                    .doOnError(error -> {
                        session.fail("upstream_error", error);
                        onStreamError(session, error);
                    })
                    .subscribe();
        } catch (Exception e) {
            session.fail("execution_error", e);
            onStreamError(session, e);
        }
    }

    /**
     * Handle stream completion.
     */
    private void onStreamComplete(StreamSession session) {
        activeSessions.remove(session.getRequestId());
        streamMetrics.recordSessionEnd(session.getDecision().getMode());

        Long ttfbMs = session.getTtfbMs();
        if (ttfbMs != null) {
            streamMetrics.recordTtfb(session.getDecision().getMode(), ttfbMs);
        }

        streamMetrics.recordCompletion(
                session.getDecision().getMode(),
                session.getDurationMs() != null ? session.getDurationMs() : 0,
                session.getChunksDelivered().get(),
                session.getBytesDelivered().get()
        );

        streamLogger.logCompletion(
                session.getRequestId(),
                session.getDecision(),
                session.getChunksDelivered().get(),
                session.getBytesDelivered().get()
        );

        // Store for replay if needed
        if (session.getDecision().getMode() == DeliveryMode.ASYNC_JOB) {
            streamReplay.store(session);
        }

        log.info("Stream completed: requestId={}, mode={}, chunks={}, bytes={}",
                session.getRequestId(),
                session.getDecision().getMode(),
                session.getChunksDelivered().get(),
                session.getBytesDelivered().get());
    }

    /**
     * Handle stream error.
     */
    private void onStreamError(StreamSession session, Throwable error) {
        activeSessions.remove(session.getRequestId());
        streamMetrics.recordSessionEnd(session.getDecision().getMode());
        streamMetrics.recordFailure(session.getDecision().getMode(), error.getClass().getSimpleName());
        streamLogger.logFailure(session.getRequestId(), session.getDecision(), error);

        log.error("Stream failed: requestId={}, mode={}, error={}",
                session.getRequestId(),
                session.getDecision().getMode(),
                error.getMessage());
    }

    /**
     * Schedule first-byte timeout check.
     * If first byte isn't sent within 1s, trigger fallback.
     */
    private void scheduleFirstByteCheck(StreamSession session) {
        asyncExecutor.submit(() -> {
            try {
                Thread.sleep(firstByteTimeoutMs);

                if (session.isFirstByteTimedOut(Duration.ofMillis(firstByteTimeoutMs))) {
                    log.warn("First byte timeout: requestId={}", session.getRequestId());

                    // Trigger fallback
                    StreamDecision fallbackDecision = policyEngine.fallback(
                            session.getContext(),
                            session.getDecision(),
                            "first_byte_timeout"
                    );

                    streamMetrics.recordFallback(
                            session.getDecision().getMode(),
                            fallbackDecision.getMode(),
                            "first_byte_timeout"
                    );

                    // Cancel current session
                    session.cancel();
                    activeSessions.remove(session.getRequestId());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Build StreamContext from HTTP request.
     */
    private StreamContext buildContext(String requestId, HttpServletRequest request) {
        ClientType clientType = detectClientType(request);
        EntryTopology topology = detectTopology(request);

        return StreamContext.builder()
                .requestId(requestId)
                .clientType(clientType)
                .entryTopology(topology)
                .expectedLatency(Duration.ofSeconds(30))
                .clientIp(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .acceptHeader(request.getHeader("Accept"))
                .sseSupported(acceptsSse(request))
                .webSocketSupported(false)
                .streamingRequested(wantsStreaming(request))
                .build();
    }

    private ClientType detectClientType(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return ClientType.UNKNOWN;
        }

        userAgent = userAgent.toLowerCase();

        if (userAgent.contains("mozilla") || userAgent.contains("chrome") ||
            userAgent.contains("safari") || userAgent.contains("firefox")) {
            return ClientType.BROWSER;
        }

        if (userAgent.contains("curl") || userAgent.contains("httpie") ||
            userAgent.contains("wget")) {
            return ClientType.CLI;
        }

        if (userAgent.contains("sdk") || userAgent.contains("python") ||
            userAgent.contains("java") || userAgent.contains("node")) {
            return ClientType.SDK;
        }

        if (userAgent.contains("mobile") || userAgent.contains("android") ||
            userAgent.contains("iphone")) {
            return ClientType.MOBILE;
        }

        return ClientType.UNKNOWN;
    }

    private EntryTopology detectTopology(HttpServletRequest request) {
        // Check for API Gateway headers
        if (request.getHeader("X-Amzn-Apigateway-Api-Id") != null ||
            request.getHeader("X-Kong-Request-Id") != null) {
            return EntryTopology.API_GATEWAY;
        }

        // Check for CloudFlare
        if (request.getHeader("CF-Ray") != null) {
            return EntryTopology.CDN;
        }

        // Check for ALB
        if (request.getHeader("X-Amzn-Trace-Id") != null) {
            return EntryTopology.ALB;
        }

        // Check for nginx
        if (request.getHeader("X-Nginx-Proxy") != null) {
            return EntryTopology.REVERSE_PROXY;
        }

        // Check for forwarded headers (indicates proxy)
        if (request.getHeader("X-Forwarded-For") != null) {
            return EntryTopology.REVERSE_PROXY;
        }

        return EntryTopology.DIRECT;
    }

    private boolean acceptsSse(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/event-stream");
    }

    private boolean wantsStreaming(HttpServletRequest request) {
        String stream = request.getParameter("stream");
        if (stream != null) {
            return Boolean.parseBoolean(stream);
        }
        // Default to streaming if Accept header indicates SSE
        return acceptsSse(request);
    }

    /**
     * Get async job result.
     */
    public StreamReplay.AsyncJobResult getAsyncJobResult(String requestId) {
        // Check active sessions first
        StreamSession active = activeSessions.get(requestId);
        if (active != null) {
            return StreamReplay.AsyncJobResult.pending(requestId);
        }

        return streamReplay.getAsyncJobResult(requestId);
    }

    /**
     * Get async job result with cursor (for pagination).
     */
    public StreamReplay.ReplayResult getAsyncJobResultWithCursor(String requestId, long cursor, int limit) {
        return streamReplay.getFromCursor(requestId, cursor, limit);
    }

    /**
     * Get active session count.
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Result of starting a stream.
     */
    public record StreamResult(
            String requestId,
            DeliveryMode mode,
            String reason,
            Object response  // SseEmitter for SSE, Map for async, String for sync
    ) {
        public static StreamResult sse(String requestId, SseEmitter emitter, StreamDecision decision) {
            return new StreamResult(requestId, decision.getMode(), decision.getReason(), emitter);
        }

        public static StreamResult asyncJob(String requestId, StreamDecision decision) {
            return new StreamResult(requestId, decision.getMode(), decision.getReason(),
                    Map.of(
                            "status", "accepted",
                            "requestId", requestId,
                            "resultUrl", "/api/v1/stream/result/" + requestId
                    ));
        }

        public static StreamResult sync(String requestId, String content, StreamDecision decision) {
            return new StreamResult(requestId, decision.getMode(), decision.getReason(),
                    Map.of(
                            "requestId", requestId,
                            "content", content
                    ));
        }
    }
}

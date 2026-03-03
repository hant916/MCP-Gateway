package com.mcpgateway.stream;

import com.mcpgateway.stream.transport.StreamTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stream replay service for async job results and reconnection.
 *
 * Supports:
 * 1. Fetching completed async job results
 * 2. Resuming from a cursor/sequence
 * 3. WebSocket reconnection with token replay
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamReplay {

    // Store completed sessions for replay
    private final Map<String, StreamSession> completedSessions = new ConcurrentHashMap<>();

    // TTL for completed sessions (default: 1 hour)
    private static final long SESSION_TTL_MS = 60 * 60 * 1000;

    /**
     * Store a completed session for replay.
     */
    public void store(StreamSession session) {
        if (session.getState() == StreamSession.State.COMPLETED ||
            session.getState() == StreamSession.State.FAILED) {
            completedSessions.put(session.getRequestId(), session);
            log.debug("Stored session for replay: requestId={}", session.getRequestId());

            // Schedule cleanup
            scheduleCleanup(session.getRequestId());
        }
    }

    /**
     * Get a stored session.
     */
    public StreamSession getSession(String requestId) {
        return completedSessions.get(requestId);
    }

    /**
     * Check if a request has a stored session.
     */
    public boolean hasSession(String requestId) {
        return completedSessions.containsKey(requestId);
    }

    /**
     * Get the result of an async job.
     */
    public AsyncJobResult getAsyncJobResult(String requestId) {
        StreamSession session = completedSessions.get(requestId);
        if (session == null) {
            return AsyncJobResult.notFound(requestId);
        }

        return switch (session.getState()) {
            case COMPLETED -> AsyncJobResult.completed(
                    requestId,
                    session.getBuffer().getFullText(),
                    session.getBuffer().getTokenCount(),
                    session.getDurationMs()
            );
            case FAILED -> AsyncJobResult.failed(
                    requestId,
                    session.getFailureReason()
            );
            default -> AsyncJobResult.pending(requestId);
        };
    }

    /**
     * Get tokens from a cursor position (for pagination/resume).
     */
    public ReplayResult getFromCursor(String requestId, long cursor, int limit) {
        StreamSession session = completedSessions.get(requestId);
        if (session == null) {
            return ReplayResult.notFound(requestId);
        }

        StreamBuffer buffer = session.getBuffer();
        List<StreamToken> tokens = buffer.getFromSequence(cursor);

        // Apply limit
        if (limit > 0 && tokens.size() > limit) {
            tokens = tokens.subList(0, limit);
        }

        long nextCursor = tokens.isEmpty() ? cursor :
                tokens.get(tokens.size() - 1).getSequence() + 1;

        return ReplayResult.builder()
                .requestId(requestId)
                .found(true)
                .tokens(tokens)
                .cursor(cursor)
                .nextCursor(nextCursor)
                .hasMore(!buffer.isCompleted() || nextCursor < buffer.getCurrentSequence())
                .completed(buffer.isCompleted())
                .build();
    }

    /**
     * Replay tokens to a transport (for WebSocket reconnection).
     */
    public int replayTo(String requestId, long fromSequence, StreamTransport transport) {
        StreamSession session = completedSessions.get(requestId);
        if (session == null) {
            log.warn("Session not found for replay: requestId={}", requestId);
            return 0;
        }

        List<StreamToken> tokens = session.getBuffer().getFromSequence(fromSequence);
        int replayed = 0;

        for (StreamToken token : tokens) {
            try {
                transport.send(token);
                replayed++;
            } catch (Exception e) {
                log.error("Failed to replay token: requestId={}, sequence={}",
                        requestId, token.getSequence(), e);
                break;
            }
        }

        try {
            transport.flush();
        } catch (Exception e) {
            log.warn("Failed to flush after replay: requestId={}", requestId, e);
        }

        log.info("Replayed {} tokens for request {}", replayed, requestId);
        return replayed;
    }

    /**
     * Remove a session.
     */
    public void remove(String requestId) {
        StreamSession removed = completedSessions.remove(requestId);
        if (removed != null) {
            removed.getBuffer().clear();
            log.debug("Removed session: requestId={}", requestId);
        }
    }

    /**
     * Get number of stored sessions.
     */
    public int getStoredSessionCount() {
        return completedSessions.size();
    }

    private void scheduleCleanup(String requestId) {
        // In production, use a proper scheduler
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(SESSION_TTL_MS);
                remove(requestId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Result of an async job query.
     */
    public record AsyncJobResult(
            String requestId,
            String status,
            String content,
            Integer tokenCount,
            Long durationMs,
            String errorMessage
    ) {
        public static AsyncJobResult notFound(String requestId) {
            return new AsyncJobResult(requestId, "not_found", null, null, null, null);
        }

        public static AsyncJobResult pending(String requestId) {
            return new AsyncJobResult(requestId, "pending", null, null, null, null);
        }

        public static AsyncJobResult completed(String requestId, String content,
                                               int tokenCount, long durationMs) {
            return new AsyncJobResult(requestId, "completed", content, tokenCount, durationMs, null);
        }

        public static AsyncJobResult failed(String requestId, String errorMessage) {
            return new AsyncJobResult(requestId, "failed", null, null, null, errorMessage);
        }
    }

    /**
     * Result of a replay query.
     */
    @lombok.Builder
    public record ReplayResult(
            String requestId,
            boolean found,
            List<StreamToken> tokens,
            long cursor,
            long nextCursor,
            boolean hasMore,
            boolean completed
    ) {
        public static ReplayResult notFound(String requestId) {
            return ReplayResult.builder()
                    .requestId(requestId)
                    .found(false)
                    .tokens(List.of())
                    .build();
        }
    }
}

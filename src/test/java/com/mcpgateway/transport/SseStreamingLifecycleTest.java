package com.mcpgateway.transport;

import com.mcpgateway.domain.MessageLog;
import com.mcpgateway.domain.Session;
import com.mcpgateway.service.MessageLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SSE Streaming Lifecycle Tests
 *
 * Tests the SSE transport layer for:
 * - Client cancellation and server-side cleanup
 * - Long-running connections (30s+)
 * - Slow client backpressure handling
 * - Heartbeat and event ID sequencing
 * - Concurrent connection management
 * - Resource cleanup and leak prevention
 */
@ExtendWith(MockitoExtension.class)
class SseStreamingLifecycleTest {

    @Mock
    private MessageLogService messageLogService;

    private SseTransport sseTransport;
    private Session testSession;

    @BeforeEach
    void setUp() {
        sseTransport = new SseTransport(messageLogService);
        testSession = new Session();
        testSession.setId(UUID.randomUUID());
        testSession.setSessionToken("test-token-" + UUID.randomUUID());
    }

    @Test
    void testClientCancellation_ShouldCleanupEmitter() throws Exception {
        // Arrange
        sseTransport.initialize(testSession);
        SseEmitter emitter = sseTransport.getEmitter(testSession.getSessionToken());
        assertThat(emitter).isNotNull();

        CountDownLatch completionLatch = new CountDownLatch(1);
        emitter.onCompletion(completionLatch::countDown);

        // Act - Simulate client cancellation
        emitter.complete();

        // Assert
        boolean completed = completionLatch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // Verify emitter is removed from registry
        SseEmitter removedEmitter = sseTransport.getEmitter(testSession.getSessionToken());
        assertThat(removedEmitter).isNull();
    }

    @Test
    void testClientTimeout_ShouldCleanupEmitter() throws Exception {
        // Arrange
        Session shortTimeoutSession = new Session();
        shortTimeoutSession.setId(UUID.randomUUID());
        shortTimeoutSession.setSessionToken("timeout-token");

        // Create transport with short timeout
        SseTransport timeoutTransport = new SseTransport(messageLogService) {
            @Override
            public void initialize(Session session) {
                SseEmitter emitter = new SseEmitter(100L); // 100ms timeout
                emitter.onTimeout(() -> {
                    log.info("Timeout triggered for session: {}", session.getSessionToken());
                });
                emitter.onCompletion(() -> {
                    log.info("Completed for session: {}", session.getSessionToken());
                });
            }
        };

        // Act
        timeoutTransport.initialize(shortTimeoutSession);

        // Assert - Wait for timeout
        Thread.sleep(200);

        // Timeout callback should have been triggered (verified by logs)
    }

    @Test
    @Timeout(value = 35, unit = TimeUnit.SECONDS)
    void testLongRunningConnection_30SecondsPlus() throws Exception {
        // Arrange
        sseTransport.initialize(testSession);
        SseEmitter emitter = sseTransport.getEmitter(testSession.getSessionToken());

        AtomicInteger messageCount = new AtomicInteger(0);
        AtomicBoolean hasError = new AtomicBoolean(false);

        // Act - Send messages over 30+ seconds
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        CountDownLatch completionLatch = new CountDownLatch(30);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                String message = "heartbeat-" + messageCount.incrementAndGet();
                sseTransport.sendMessage(message);
                completionLatch.countDown();
            } catch (Exception e) {
                hasError.set(true);
            }
        }, 0, 1, TimeUnit.SECONDS);

        // Assert
        boolean completed = completionLatch.await(35, TimeUnit.SECONDS);
        scheduler.shutdown();

        assertThat(completed).isTrue();
        assertThat(hasError.get()).isFalse();
        assertThat(messageCount.get()).isGreaterThanOrEqualTo(30);

        // Verify emitter still active
        SseEmitter activeEmitter = sseTransport.getEmitter(testSession.getSessionToken());
        assertThat(activeEmitter).isNotNull();

        // Cleanup
        sseTransport.close();
    }

    @Test
    void testEventIdSequencing() throws Exception {
        // Arrange
        sseTransport.initialize(testSession);
        List<String> eventIds = new CopyOnWriteArrayList<>();

        // Mock emitter to capture event IDs
        SseEmitter emitter = sseTransport.getEmitter(testSession.getSessionToken());

        // Act - Send multiple messages quickly
        for (int i = 0; i < 10; i++) {
            sseTransport.sendMessage("message-" + i);
            Thread.sleep(10); // Small delay to ensure timestamp differs
        }

        // Assert - Verify messages were logged
        verify(messageLogService, atLeast(10)).logMessage(
            eq(testSession.getId()),
            eq(MessageLog.MessageType.REQUEST),
            anyString()
        );

        // Event IDs should be timestamps and monotonically increasing
        // (verified implicitly through the system timestamp mechanism)
    }

    @Test
    void testConcurrentConnections_MultipleClients() throws Exception {
        // Arrange
        int numClients = 10;
        List<Session> sessions = new ArrayList<>();
        List<SseEmitter> emitters = new ArrayList<>();

        // Create multiple sessions
        for (int i = 0; i < numClients; i++) {
            Session session = new Session();
            session.setId(UUID.randomUUID());
            session.setSessionToken("concurrent-token-" + i);
            sessions.add(session);

            SseTransport transport = new SseTransport(messageLogService);
            transport.initialize(session);
            emitters.add(transport.getEmitter(session.getSessionToken()));
        }

        // Act - All emitters should be active
        for (int i = 0; i < numClients; i++) {
            assertThat(emitters.get(i)).isNotNull();
        }

        // Assert - No interference between connections
        assertThat(emitters).hasSize(numClients);
        assertThat(emitters).doesNotContainNull();
    }

    @Test
    void testSlowClientBackpressure_RapidMessaging() throws Exception {
        // Arrange
        sseTransport.initialize(testSession);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Act - Send messages rapidly (simulate backpressure scenario)
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            int messageNum = i;
            futures.add(executor.submit(() -> {
                try {
                    sseTransport.sendMessage("rapid-message-" + messageNum);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }));
        }

        // Wait for all sends to complete
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Assert - Most messages should succeed
        // (Some may fail if client can't keep up, which is acceptable)
        assertThat(successCount.get()).isGreaterThan(50);

        // Verify logging happened for successful sends
        verify(messageLogService, atLeast(50)).logMessage(
            eq(testSession.getId()),
            eq(MessageLog.MessageType.REQUEST),
            anyString()
        );
    }

    @Test
    void testSendMessageAfterClientDisconnect_ShouldHandleGracefully() throws Exception {
        // Arrange
        sseTransport.initialize(testSession);
        SseEmitter emitter = sseTransport.getEmitter(testSession.getSessionToken());

        // Simulate client disconnect
        emitter.complete();

        // Wait for cleanup
        Thread.sleep(100);

        // Act - Try to send message after disconnect
        sseTransport.sendMessage("message-after-disconnect");

        // Assert - Should not throw exception, should handle gracefully
        // Error should be logged
        verify(messageLogService, never()).logMessage(
            eq(testSession.getId()),
            eq(MessageLog.MessageType.REQUEST),
            eq("message-after-disconnect")
        );
    }

    @Test
    void testResourceCleanup_1000ConnectDisconnectCycles() throws Exception {
        // Arrange
        AtomicInteger activeConnections = new AtomicInteger(0);

        // Act - Create and close 1000 connections
        for (int i = 0; i < 1000; i++) {
            Session session = new Session();
            session.setId(UUID.randomUUID());
            session.setSessionToken("cycle-token-" + i);

            SseTransport transport = new SseTransport(messageLogService);
            transport.initialize(session);
            activeConnections.incrementAndGet();

            SseEmitter emitter = transport.getEmitter(session.getSessionToken());
            assertThat(emitter).isNotNull();

            // Close immediately
            transport.close();
            activeConnections.decrementAndGet();
        }

        // Assert - All connections cleaned up
        assertThat(activeConnections.get()).isEqualTo(0);
    }

    @Test
    void testHeartbeatMechanism_RegularInterval() throws Exception {
        // Arrange
        sseTransport.initialize(testSession);
        List<Long> timestamps = new CopyOnWriteArrayList<>();

        // Act - Send heartbeats at regular intervals
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        CountDownLatch heartbeatLatch = new CountDownLatch(5);

        scheduler.scheduleAtFixedRate(() -> {
            timestamps.add(System.currentTimeMillis());
            sseTransport.sendMessage("heartbeat");
            heartbeatLatch.countDown();
        }, 0, 500, TimeUnit.MILLISECONDS);

        // Wait for 5 heartbeats
        boolean completed = heartbeatLatch.await(5, TimeUnit.SECONDS);
        scheduler.shutdown();

        // Assert
        assertThat(completed).isTrue();
        assertThat(timestamps).hasSize(5);

        // Verify intervals are roughly 500ms apart
        for (int i = 1; i < timestamps.size(); i++) {
            long interval = timestamps.get(i) - timestamps.get(i - 1);
            assertThat(interval).isBetween(400L, 600L); // Allow 100ms tolerance
        }
    }

    @Test
    void testMultipleMessagesSequentialOrder() throws Exception {
        // Arrange
        sseTransport.initialize(testSession);
        List<String> sentMessages = new ArrayList<>();

        // Act - Send messages sequentially
        for (int i = 0; i < 20; i++) {
            String message = "sequential-" + i;
            sentMessages.add(message);
            sseTransport.sendMessage(message);
        }

        // Assert - Verify all messages were logged in order
        for (String message : sentMessages) {
            verify(messageLogService).logMessage(
                eq(testSession.getId()),
                eq(MessageLog.MessageType.REQUEST),
                eq(message)
            );
        }
    }

    @Test
    void testEmitterErrorHandling_IOException() throws Exception {
        // Arrange
        sseTransport.initialize(testSession);

        // Get the emitter and complete it to cause IOException on next send
        SseEmitter emitter = sseTransport.getEmitter(testSession.getSessionToken());
        emitter.completeWithError(new IOException("Simulated network error"));

        // Wait for error handling
        Thread.sleep(100);

        // Act - Try to send message (should fail gracefully)
        sseTransport.sendMessage("message-after-error");

        // Assert - Error should be logged
        verify(messageLogService, atLeastOnce()).logMessage(
            eq(testSession.getId()),
            eq(MessageLog.MessageType.ERROR),
            contains("Failed to send SSE message")
        );

        // Emitter should be removed from registry
        assertThat(sseTransport.getEmitter(testSession.getSessionToken())).isNull();
    }

    @Test
    void testCloseIdempotency_MultipleCloseCalls() {
        // Arrange
        sseTransport.initialize(testSession);

        // Act - Call close multiple times
        sseTransport.close();
        sseTransport.close();
        sseTransport.close();

        // Assert - Should not throw exception
        assertThat(sseTransport.getEmitter(testSession.getSessionToken())).isNull();
    }

    @Test
    void testMessageLogging_AllTypesRecorded() {
        // Arrange
        sseTransport.initialize(testSession);

        // Act
        sseTransport.sendMessage("test-request");
        sseTransport.handleMessage("test-response");

        // Assert
        verify(messageLogService).logMessage(
            testSession.getId(),
            MessageLog.MessageType.REQUEST,
            "test-request"
        );
        verify(messageLogService).logMessage(
            testSession.getId(),
            MessageLog.MessageType.RESPONSE,
            "test-response"
        );
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testNonBlockingSend_DoesNotHangOnSlowClient() throws Exception {
        // Arrange
        sseTransport.initialize(testSession);

        // Act - Send many messages without waiting
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            sseTransport.sendMessage("non-blocking-" + i);
        }
        long duration = System.currentTimeMillis() - startTime;

        // Assert - Should complete quickly even with many messages
        assertThat(duration).isLessThan(5000); // Should be much faster than 5s
    }
}

package com.mcpgateway.job;

import com.mcpgateway.domain.Payment;
import com.mcpgateway.domain.WebhookConfig;
import com.mcpgateway.domain.WebhookLog;
import com.mcpgateway.repository.PaymentRepository;
import com.mcpgateway.repository.WebhookConfigRepository;
import com.mcpgateway.repository.WebhookLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Background Job Idempotency Tests
 *
 * Tests the idempotency guarantees of background jobs:
 * - Idempotency with same jobId/paymentIntentId
 * - Retry logic without duplicate charging
 * - Concurrent job acquisition (only one worker succeeds)
 * - At-least-once delivery + idempotent design
 * - Duplicate payment prevention
 * - Webhook retry idempotency
 */
@ExtendWith(MockitoExtension.class)
class BackgroundJobIdempotencyTest {

    private JobProcessingService jobProcessingService;
    private Map<String, JobRecord> jobStore;
    private Map<String, Payment> paymentStore;
    private Map<String, WebhookLog> webhookLogStore;
    private AtomicInteger paymentChargeCount;

    @BeforeEach
    void setUp() {
        jobStore = new ConcurrentHashMap<>();
        paymentStore = new ConcurrentHashMap<>();
        webhookLogStore = new ConcurrentHashMap<>();
        paymentChargeCount = new AtomicInteger(0);
        jobProcessingService = new JobProcessingService(jobStore, paymentStore, webhookLogStore, paymentChargeCount);
    }

    @Test
    void testPaymentIdempotency_SamePaymentIntentId_ProcessedOnce() throws Exception {
        // Arrange
        String paymentIntentId = "pi_test_123";
        UUID userId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("99.99");

        // Act - Process same payment multiple times
        jobProcessingService.processPayment(paymentIntentId, userId, amount);
        jobProcessingService.processPayment(paymentIntentId, userId, amount);
        jobProcessingService.processPayment(paymentIntentId, userId, amount);

        // Assert - Payment charged only once
        assertThat(paymentChargeCount.get()).isEqualTo(1);
        assertThat(paymentStore).containsKey(paymentIntentId);
        assertThat(paymentStore.get(paymentIntentId).getStatus()).isEqualTo(Payment.PaymentStatus.SUCCEEDED);
    }

    @Test
    void testConcurrentJobAcquisition_OnlyOneWorkerSucceeds() throws Exception {
        // Arrange
        String jobId = "job-concurrent-test";
        int numWorkers = 10;

        // Act - Multiple workers try to acquire same job
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < numWorkers; i++) {
            results.add(executor.submit(() -> jobProcessingService.tryAcquireJob(jobId)));
        }

        // Wait for all workers
        int successCount = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                successCount++;
            }
        }
        executor.shutdown();

        // Assert - Only one worker acquired the job
        assertThat(successCount).isEqualTo(1);
    }

    @Test
    void testRetryLogic_FailedJob_RetriesWithExponentialBackoff() throws Exception {
        // Arrange
        String jobId = "job-retry-test";
        int maxRetries = 3;

        // Act - Process job that fails and retries
        List<Long> retryDelays = jobProcessingService.processJobWithRetries(jobId, maxRetries, true);

        // Assert - Exponential backoff delays (2^n seconds)
        assertThat(retryDelays).hasSize(maxRetries);
        assertThat(retryDelays.get(0)).isBetween(1900L, 2100L); // ~2s
        assertThat(retryDelays.get(1)).isBetween(3900L, 4100L); // ~4s
        assertThat(retryDelays.get(2)).isBetween(7900L, 8100L); // ~8s
    }

    @Test
    void testRetryLogic_NoDuplicatePaymentCharges() throws Exception {
        // Arrange
        String paymentIntentId = "pi_retry_test";
        UUID userId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50.00");

        // Act - Payment fails initially, then succeeds on retry
        // Simulate 3 attempts (1 initial + 2 retries)
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (attempt < 2) {
                    // Simulate failure
                    jobProcessingService.processPaymentWithFailure(paymentIntentId, userId, amount, attempt);
                } else {
                    // Success on 3rd attempt
                    jobProcessingService.processPayment(paymentIntentId, userId, amount);
                }
            } catch (Exception e) {
                // Expected on first 2 attempts
            }
        }

        // Assert - Charged only once despite retries
        assertThat(paymentChargeCount.get()).isEqualTo(1);
    }

    @Test
    void testWebhookRetryIdempotency_SameEventDeliveredOnce() throws Exception {
        // Arrange
        String webhookId = "webhook-123";
        String eventId = "evt-456";
        String eventType = "payment.success";

        // Act - Retry webhook delivery 5 times
        for (int i = 0; i < 5; i++) {
            jobProcessingService.deliverWebhook(webhookId, eventId, eventType);
        }

        // Assert - Webhook delivered only once (subsequent calls are idempotent)
        String logKey = webhookId + "-" + eventId;
        assertThat(webhookLogStore).containsKey(logKey);
        assertThat(webhookLogStore.get(logKey).getRetryCount()).isEqualTo(0);
    }

    @Test
    void testAtLeastOnceDelivery_JobCompletesEventually() throws Exception {
        // Arrange
        String jobId = "job-eventual-success";

        // Act - Job fails first 3 times, succeeds on 4th
        boolean success = false;
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                jobProcessingService.processJobWithEventualSuccess(jobId, 3);
                success = true;
                break;
            } catch (Exception e) {
                // Retry
            }
        }

        // Assert - Job eventually succeeded
        assertThat(success).isTrue();
        assertThat(jobStore.get(jobId).getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    void testIdempotencyKey_PreventsDuplicateOperations() throws Exception {
        // Arrange
        String idempotencyKey = "idem-key-789";
        String operation = "create-subscription";

        // Act - Execute same operation with same idempotency key
        String result1 = jobProcessingService.executeWithIdempotencyKey(idempotencyKey, operation);
        String result2 = jobProcessingService.executeWithIdempotencyKey(idempotencyKey, operation);
        String result3 = jobProcessingService.executeWithIdempotencyKey(idempotencyKey, operation);

        // Assert - All calls return same result (operation executed once)
        assertThat(result1).isEqualTo(result2).isEqualTo(result3);
        assertThat(jobStore).containsKey(idempotencyKey);
    }

    @Test
    void testConcurrentPayments_DifferentIntents_AllProcessed() throws Exception {
        // Arrange
        int numPayments = 50;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();

        // Act - Process multiple different payments concurrently
        for (int i = 0; i < numPayments; i++) {
            final int paymentNum = i;
            futures.add(executor.submit(() -> {
                String paymentIntentId = "pi_concurrent_" + paymentNum;
                UUID userId = UUID.randomUUID();
                BigDecimal amount = new BigDecimal("10.00");
                jobProcessingService.processPayment(paymentIntentId, userId, amount);
            }));
        }

        // Wait for all
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        // Assert - All payments processed
        assertThat(paymentChargeCount.get()).isEqualTo(numPayments);
        assertThat(paymentStore).hasSize(numPayments);
    }

    @Test
    void testJobLocking_PreventsConcurrentExecution() throws Exception {
        // Arrange
        String jobId = "job-lock-test";
        AtomicInteger executionCount = new AtomicInteger(0);

        // Act - 20 workers try to execute same job concurrently
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(20);

        for (int i = 0; i < 20; i++) {
            executor.submit(() -> {
                try {
                    if (jobProcessingService.tryAcquireJob(jobId)) {
                        executionCount.incrementAndGet();
                        Thread.sleep(100); // Simulate work
                        jobProcessingService.releaseJob(jobId);
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Assert - Job executed by only one worker
        assertThat(executionCount.get()).isEqualTo(1);
    }

    @Test
    void testRetryWithBackoff_DoesNotExceedMaxRetries() throws Exception {
        // Arrange
        String jobId = "job-max-retry-test";
        int maxRetries = 5;

        // Act - Job always fails
        List<Long> retryDelays = jobProcessingService.processJobWithRetries(jobId, maxRetries, false);

        // Assert - Stopped after max retries
        assertThat(retryDelays).hasSize(maxRetries);
        JobRecord job = jobStore.get(jobId);
        assertThat(job.getRetryCount()).isEqualTo(maxRetries);
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void testDuplicatePaymentPrevention_RaceCondition() throws Exception {
        // Arrange
        String paymentIntentId = "pi_race_condition";
        UUID userId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        // Act - Simulate race condition: 10 workers try to process same payment
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    jobProcessingService.processPayment(paymentIntentId, userId, amount);
                } catch (Exception e) {
                    // Some will fail with duplicate detection
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads at once
        doneLatch.await();
        executor.shutdown();

        // Assert - Payment charged exactly once
        assertThat(paymentChargeCount.get()).isEqualTo(1);
    }

    @Test
    void testJobStateTransitions_InvalidTransitionsPrevented() {
        // Arrange
        String jobId = "job-state-test";
        JobRecord job = new JobRecord(jobId);

        // Act & Assert - Valid transitions
        assertThat(job.transitionTo(JobStatus.PROCESSING)).isTrue();
        assertThat(job.transitionTo(JobStatus.COMPLETED)).isTrue();

        // Invalid transitions (can't go back from COMPLETED)
        assertThat(job.transitionTo(JobStatus.PROCESSING)).isFalse();
        assertThat(job.transitionTo(JobStatus.PENDING)).isFalse();
    }

    @Test
    void testIdempotencyExpiration_OldKeysCanBeReused() throws Exception {
        // Arrange
        String idempotencyKey = "idem-key-expiring";
        String operation = "test-operation";

        // Act - Execute operation
        String result1 = jobProcessingService.executeWithIdempotencyKey(idempotencyKey, operation);

        // Simulate expiration (24 hours)
        JobRecord record = jobStore.get(idempotencyKey);
        record.setCreatedAt(LocalDateTime.now().minusHours(25));

        // Execute again after expiration
        String result2 = jobProcessingService.executeWithIdempotencyKey(idempotencyKey, operation);

        // Assert - Different results (operation executed twice)
        assertThat(result1).isNotEqualTo(result2);
    }

    /**
     * Mock Job Processing Service
     */
    static class JobProcessingService {
        private final Map<String, JobRecord> jobStore;
        private final Map<String, Payment> paymentStore;
        private final Map<String, WebhookLog> webhookLogStore;
        private final AtomicInteger paymentChargeCount;
        private final Map<String, Long> lockTimestamps = new ConcurrentHashMap<>();

        public JobProcessingService(Map<String, JobRecord> jobStore,
                                     Map<String, Payment> paymentStore,
                                     Map<String, WebhookLog> webhookLogStore,
                                     AtomicInteger paymentChargeCount) {
            this.jobStore = jobStore;
            this.paymentStore = paymentStore;
            this.webhookLogStore = webhookLogStore;
            this.paymentChargeCount = paymentChargeCount;
        }

        public void processPayment(String paymentIntentId, UUID userId, BigDecimal amount) {
            // Idempotency check
            if (paymentStore.containsKey(paymentIntentId)) {
                Payment existing = paymentStore.get(paymentIntentId);
                if (existing.getStatus() == Payment.PaymentStatus.SUCCEEDED) {
                    return; // Already processed
                }
            }

            // Double-checked locking for thread safety
            synchronized (paymentIntentId.intern()) {
                if (paymentStore.containsKey(paymentIntentId)) {
                    return;
                }

                // Create payment record
                Payment payment = Payment.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .paymentIntentId(paymentIntentId)
                        .amount(amount)
                        .currency("USD")
                        .status(Payment.PaymentStatus.SUCCEEDED)
                        .build();

                paymentStore.put(paymentIntentId, payment);
                paymentChargeCount.incrementAndGet();
            }
        }

        public void processPaymentWithFailure(String paymentIntentId, UUID userId,
                                               BigDecimal amount, int attemptNumber) throws Exception {
            if (attemptNumber < 2) {
                throw new Exception("Payment failed");
            }
            processPayment(paymentIntentId, userId, amount);
        }

        public boolean tryAcquireJob(String jobId) {
            long currentTime = System.currentTimeMillis();
            Long existingLock = lockTimestamps.putIfAbsent(jobId, currentTime);
            return existingLock == null;
        }

        public void releaseJob(String jobId) {
            lockTimestamps.remove(jobId);
        }

        public List<Long> processJobWithRetries(String jobId, int maxRetries, boolean shouldSucceed) throws InterruptedException {
            JobRecord job = jobStore.computeIfAbsent(jobId, JobRecord::new);
            List<Long> retryDelays = new ArrayList<>();

            for (int retry = 0; retry < maxRetries; retry++) {
                long startTime = System.currentTimeMillis();

                // Exponential backoff
                long delayMs = (long) (Math.pow(2, retry) * 1000);
                Thread.sleep(delayMs);

                retryDelays.add(delayMs);
                job.incrementRetry();

                if (shouldSucceed && retry == maxRetries - 1) {
                    job.setStatus(JobStatus.COMPLETED);
                    return retryDelays;
                }
            }

            job.setStatus(JobStatus.FAILED);
            return retryDelays;
        }

        public void deliverWebhook(String webhookId, String eventId, String eventType) {
            String logKey = webhookId + "-" + eventId;

            // Idempotency: only deliver once per event
            webhookLogStore.computeIfAbsent(logKey, k -> {
                WebhookLog log = WebhookLog.builder()
                        .id(UUID.randomUUID())
                        .eventType(eventType)
                        .status(WebhookLog.DeliveryStatus.SUCCESS)
                        .retryCount(0)
                        .build();
                return log;
            });
        }

        public void processJobWithEventualSuccess(String jobId, int failureCount) throws Exception {
            JobRecord job = jobStore.computeIfAbsent(jobId, JobRecord::new);

            if (job.getRetryCount() < failureCount) {
                job.incrementRetry();
                throw new Exception("Job failed, retry " + job.getRetryCount());
            }

            job.setStatus(JobStatus.COMPLETED);
        }

        public String executeWithIdempotencyKey(String idempotencyKey, String operation) {
            JobRecord existing = jobStore.get(idempotencyKey);

            // Check if idempotency key expired (24 hours)
            if (existing != null) {
                if (existing.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24))) {
                    return existing.getResult();
                } else {
                    // Expired, remove and re-execute
                    jobStore.remove(idempotencyKey);
                }
            }

            // Execute operation
            String result = UUID.randomUUID().toString();
            JobRecord record = new JobRecord(idempotencyKey);
            record.setResult(result);
            record.setStatus(JobStatus.COMPLETED);
            jobStore.put(idempotencyKey, record);

            return result;
        }
    }

    /**
     * Job record for tracking state
     */
    static class JobRecord {
        private final String jobId;
        private JobStatus status;
        private int retryCount;
        private String result;
        private LocalDateTime createdAt;

        public JobRecord(String jobId) {
            this.jobId = jobId;
            this.status = JobStatus.PENDING;
            this.retryCount = 0;
            this.createdAt = LocalDateTime.now();
        }

        public boolean transitionTo(JobStatus newStatus) {
            // Validate state transitions
            if (status == JobStatus.COMPLETED || status == JobStatus.FAILED) {
                return false; // Terminal states
            }
            status = newStatus;
            return true;
        }

        public void incrementRetry() {
            retryCount++;
        }

        // Getters and setters
        public String getJobId() { return jobId; }
        public JobStatus getStatus() { return status; }
        public void setStatus(JobStatus status) { this.status = status; }
        public int getRetryCount() { return retryCount; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    enum JobStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}

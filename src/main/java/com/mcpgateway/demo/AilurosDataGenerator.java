package com.mcpgateway.demo;

import com.mcpgateway.domain.ailuros.AcCall;
import com.mcpgateway.domain.ailuros.AcCallFlag;
import com.mcpgateway.repository.ailuros.AcCallRepository;
import com.mcpgateway.repository.ailuros.AcCallFlagRepository;
import com.mcpgateway.service.ailuros.CostEstimator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Ailuros Control Demo Data Generator
 *
 * Generates realistic demo data with three dramatic scenarios:
 * 1. COST SPIKE: Model switch causes budget explosion
 * 2. DRIFT DETECTION: Quality degradation over time
 * 3. ERROR SURGE: Upstream issues trigger alerts
 *
 * Run this to populate the database with demo data that shows
 * the "control" Ailuros provides.
 *
 * Usage:
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--demo.generate=true"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AilurosDataGenerator implements CommandLineRunner {

    private final AcCallRepository callRepository;
    private final AcCallFlagRepository flagRepository;
    private final CostEstimator costEstimator;

    private final Random random = new Random(42); // Deterministic for reproducibility

    // Prompts and responses for realism
    private static final String[] PROMPTS = {
        "Summarize this document about cloud computing...",
        "Translate this text to French: Hello world...",
        "Write a product description for wireless headphones...",
        "Generate test cases for user login functionality...",
        "Explain quantum computing to a 10 year old...",
        "Create SQL query to find top customers by revenue...",
        "Draft an email response to customer complaint...",
        "Generate Python code to parse CSV files..."
    };

    private static final String[] GOOD_RESPONSES = {
        "Cloud computing enables on-demand access to shared computing resources...",
        "Bonjour le monde",
        "Premium wireless headphones featuring active noise cancellation...",
        "Test Case 1: Valid credentials should grant access...",
        "Imagine quantum computing as a magical library where books can be in multiple places...",
        "SELECT customer_id, SUM(revenue) FROM orders GROUP BY customer_id ORDER BY 2 DESC LIMIT 10;",
        "Dear valued customer, We sincerely apologize for the inconvenience...",
        "import pandas as pd\ndf = pd.read_csv('data.csv')"
    };

    private static final String[] BAD_RESPONSES = {
        "I apologize, but I don't have information about that...",
        "As an AI language model, I cannot...",
        "[HALLUCINATION] The moon is made of cheese and orbits Mars...",
        "Error: Invalid input format",
        "I don't understand the question",
        "SELECT * FROM customers WHERE 1=1; DROP TABLE orders;--",
        "Your complaint is noted.",
        "def parse(): pass"
    };

    @Override
    public void run(String... args) throws Exception {
        // Check if demo generation is enabled
        boolean generateDemo = false;
        for (String arg : args) {
            if ("--demo.generate=true".equals(arg)) {
                generateDemo = true;
                break;
            }
        }

        if (!generateDemo) {
            log.info("Demo data generation disabled. Run with --demo.generate=true to enable.");
            return;
        }

        log.info("🎬 Starting Ailuros Control Demo Data Generation...");

        // Clear existing demo data
        log.info("Clearing existing data...");
        callRepository.deleteAll();
        flagRepository.deleteAll();

        // Generate data for the last 30 days
        Instant now = Instant.now();
        Instant thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);

        List<AcCall> allCalls = new ArrayList<>();

        // Scenario 1: NORMAL OPERATIONS (Days 1-20)
        log.info("📊 Generating Scenario 1: Normal Operations (Days 1-20)");
        allCalls.addAll(generateNormalOperations(thirtyDaysAgo, 20));

        // Scenario 2: COST SPIKE (Days 21-23)
        log.info("💰 Generating Scenario 2: Cost Spike (Days 21-23)");
        allCalls.addAll(generateCostSpike(thirtyDaysAgo.plus(20, ChronoUnit.DAYS), 3));

        // Scenario 3: MODEL DRIFT (Days 24-27)
        log.info("📉 Generating Scenario 3: Model Drift (Days 24-27)");
        allCalls.addAll(generateModelDrift(thirtyDaysAgo.plus(23, ChronoUnit.DAYS), 4));

        // Scenario 4: ERROR SURGE (Days 28-30)
        log.info("🔥 Generating Scenario 4: Error Surge (Days 28-30)");
        allCalls.addAll(generateErrorSurge(thirtyDaysAgo.plus(27, ChronoUnit.DAYS), 3));

        // Save all calls
        log.info("💾 Saving {} calls to database...", allCalls.size());
        callRepository.saveAll(allCalls);

        // Generate flags for problematic calls
        log.info("🚩 Generating flags for problematic calls...");
        generateFlags(allCalls);

        // Print summary
        printSummary(allCalls);

        log.info("✅ Demo data generation complete!");
    }

    /**
     * Scenario 1: Normal operations with GPT-3.5 Turbo
     * - Stable costs (~$0.002 per call)
     * - 1-2% error rate
     * - Consistent latency
     */
    private List<AcCall> generateNormalOperations(Instant startTime, int days) {
        List<AcCall> calls = new ArrayList<>();

        for (int day = 0; day < days; day++) {
            Instant dayStart = startTime.plus(day, ChronoUnit.DAYS);

            // 40-60 calls per day
            int callsPerDay = 40 + random.nextInt(21);

            for (int i = 0; i < callsPerDay; i++) {
                Instant callTime = dayStart.plus(random.nextInt(86400), ChronoUnit.SECONDS);

                calls.add(createCall(
                    callTime,
                    "openai",
                    "gpt-3.5-turbo",
                    BigDecimal.valueOf(0.7),
                    randomPrompt(),
                    randomGoodResponse(),
                    500 + random.nextInt(500),  // 500-1000 prompt tokens
                    200 + random.nextInt(300),  // 200-500 completion tokens
                    800 + random.nextInt(400),  // 800-1200ms latency
                    random.nextDouble() < 0.02 ? "error" : "ok"  // 2% error rate
                ));
            }
        }

        return calls;
    }

    /**
     * Scenario 2: Cost spike due to model switch
     * - Switch from GPT-3.5 to GPT-4 (10x cost increase)
     * - Budget breach alert
     */
    private List<AcCall> generateCostSpike(Instant startTime, int days) {
        List<AcCall> calls = new ArrayList<>();

        for (int day = 0; day < days; day++) {
            Instant dayStart = startTime.plus(day, ChronoUnit.DAYS);

            // Same volume, but GPT-4
            int callsPerDay = 45 + random.nextInt(16);

            for (int i = 0; i < callsPerDay; i++) {
                Instant callTime = dayStart.plus(random.nextInt(86400), ChronoUnit.SECONDS);

                calls.add(createCall(
                    callTime,
                    "openai",
                    "gpt-4",  // 💰 Cost spike!
                    BigDecimal.valueOf(0.7),
                    randomPrompt(),
                    randomGoodResponse(),
                    600 + random.nextInt(600),  // Slightly longer prompts
                    300 + random.nextInt(400),  // Slightly longer responses
                    1500 + random.nextInt(1000), // Higher latency
                    "ok"
                ));
            }
        }

        return calls;
    }

    /**
     * Scenario 3: Model drift - quality degradation
     * - Someone switches to GPT-3.5 to save cost
     * - But quality drops, flags increase
     */
    private List<AcCall> generateModelDrift(Instant startTime, int days) {
        List<AcCall> calls = new ArrayList<>();

        for (int day = 0; day < days; day++) {
            Instant dayStart = startTime.plus(day, ChronoUnit.DAYS);
            int callsPerDay = 50 + random.nextInt(21);

            // Increasing probability of bad responses over time
            double badResponseProbability = 0.1 + (day * 0.1); // 10% -> 40%

            for (int i = 0; i < callsPerDay; i++) {
                Instant callTime = dayStart.plus(random.nextInt(86400), ChronoUnit.SECONDS);

                boolean isBadResponse = random.nextDouble() < badResponseProbability;

                calls.add(createCall(
                    callTime,
                    "openai",
                    "gpt-3.5-turbo",
                    BigDecimal.valueOf(0.9), // Higher temperature = more drift
                    randomPrompt(),
                    isBadResponse ? randomBadResponse() : randomGoodResponse(),
                    400 + random.nextInt(400),
                    150 + random.nextInt(250),
                    700 + random.nextInt(400),
                    "ok"
                ));
            }
        }

        return calls;
    }

    /**
     * Scenario 4: Error surge - upstream issues
     * - API errors spike to 30%
     * - Latency increases dramatically
     */
    private List<AcCall> generateErrorSurge(Instant startTime, int days) {
        List<AcCall> calls = new ArrayList<>();

        for (int day = 0; day < days; day++) {
            Instant dayStart = startTime.plus(day, ChronoUnit.DAYS);
            int callsPerDay = 55 + random.nextInt(16);

            for (int i = 0; i < callsPerDay; i++) {
                Instant callTime = dayStart.plus(random.nextInt(86400), ChronoUnit.SECONDS);

                boolean isError = random.nextDouble() < 0.30; // 30% error rate

                calls.add(createCall(
                    callTime,
                    "openai",
                    "gpt-3.5-turbo",
                    BigDecimal.valueOf(0.7),
                    randomPrompt(),
                    isError ? "Error: Rate limit exceeded" : randomGoodResponse(),
                    isError ? 0 : 500 + random.nextInt(500),
                    isError ? 0 : 200 + random.nextInt(300),
                    isError ? 30000 : 1000 + random.nextInt(500), // Timeouts
                    isError ? "error" : "ok"
                ));
            }
        }

        return calls;
    }

    /**
     * Create a call with calculated cost
     */
    private AcCall createCall(Instant createdAt, String provider, String model,
                               BigDecimal temperature, String request, String response,
                               int promptTokens, int completionTokens,
                               int latencyMs, String status) {

        BigDecimal cost = costEstimator.estimateCost(model, promptTokens, completionTokens);

        return AcCall.builder()
            .traceId(UUID.randomUUID().toString().replace("-", ""))
            .projectKey("default")
            .env("prod")
            .status(status)
            .provider(provider)
            .model(model)
            .temperature(temperature)
            .promptRef("adhoc")
            .requestText(request)
            .requestSha256(hashString(request))
            .responseText(response)
            .responseSha256(hashString(response))
            .tokensPrompt(promptTokens)
            .tokensCompletion(completionTokens)
            .tokensTotal(promptTokens + completionTokens)
            .costEstimateUsd(cost)
            .latencyMs(latencyMs)
            .createdAt(createdAt)
            .build();
    }

    /**
     * Generate flags for bad responses and errors
     */
    private void generateFlags(List<AcCall> calls) {
        List<AcCallFlag> flags = new ArrayList<>();

        for (AcCall call : calls) {
            // Flag errors
            if ("error".equals(call.getStatus())) {
                if (random.nextDouble() < 0.2) { // Flag 20% of errors
                    flags.add(createFlag(call, "review", "High error rate detected"));
                }
            }

            // Flag bad responses
            if (call.getResponseText() != null &&
                (call.getResponseText().contains("HALLUCINATION") ||
                 call.getResponseText().contains("don't have information") ||
                 call.getResponseText().contains("DROP TABLE"))) {
                flags.add(createFlag(call, "wrong", "Quality issue detected"));
            }

            // Flag high-cost calls
            if (call.getCostEstimateUsd() != null &&
                call.getCostEstimateUsd().compareTo(BigDecimal.valueOf(0.05)) > 0) {
                if (random.nextDouble() < 0.1) { // Flag 10% of expensive calls
                    flags.add(createFlag(call, "review", "High cost call"));
                }
            }
        }

        flagRepository.saveAll(flags);
        log.info("Created {} flags", flags.size());
    }

    private AcCallFlag createFlag(AcCall call, String flagType, String note) {
        return AcCallFlag.builder()
            .call(call)
            .flagType(flagType)
            .note(note)
            .createdBy("demo-generator")
            .build();
    }

    private void printSummary(List<AcCall> calls) {
        long totalCalls = calls.size();
        long errors = calls.stream().filter(c -> "error".equals(c.getStatus())).count();
        BigDecimal totalCost = calls.stream()
            .map(AcCall::getCostEstimateUsd)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("=====================================");
        log.info("📊 DEMO DATA SUMMARY");
        log.info("=====================================");
        log.info("Total Calls: {}", totalCalls);
        log.info("Errors: {} ({} %)", errors, String.format("%.1f", errors * 100.0 / totalCalls));
        log.info("Total Cost: ${}", totalCost);
        log.info("Avg Cost/Call: ${}", totalCost.divide(BigDecimal.valueOf(totalCalls), 6, java.math.RoundingMode.HALF_UP));
        log.info("=====================================");
        log.info("");
        log.info("🎯 SCENARIOS GENERATED:");
        log.info("  Days 1-20:  Normal ops (GPT-3.5, low cost)");
        log.info("  Days 21-23: 💰 COST SPIKE (GPT-4, 10x cost)");
        log.info("  Days 24-27: 📉 DRIFT (quality degrades)");
        log.info("  Days 28-30: 🔥 ERRORS (30% failure rate)");
        log.info("=====================================");
    }

    // Helper methods
    private String randomPrompt() {
        return PROMPTS[random.nextInt(PROMPTS.length)];
    }

    private String randomGoodResponse() {
        return GOOD_RESPONSES[random.nextInt(GOOD_RESPONSES.length)];
    }

    private String randomBadResponse() {
        return BAD_RESPONSES[random.nextInt(BAD_RESPONSES.length)];
    }

    private String hashString(String input) {
        if (input == null) return null;
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return null;
        }
    }
}

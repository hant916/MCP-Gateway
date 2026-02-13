package com.mcpgateway.service.ailuros;

import com.mcpgateway.dto.ailuros.CallDetailDTO;
import com.mcpgateway.dto.ailuros.CompareDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ailuros Control: Comparison Service
 *
 * Provides diff and comparison functionality for LLM calls
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonService {

    private final AilurosControlService controlService;

    /**
     * Compare two calls
     */
    public CompareDTO compare(UUID callIdA, UUID callIdB) {
        CallDetailDTO callA = controlService.getCallDetail(callIdA);
        CallDetailDTO callB = controlService.getCallDetail(callIdB);

        // Generate text diff
        String diffText = generateDiff(callA.getResponseText(), callB.getResponseText());

        // Generate summary
        Map<String, Object> summary = generateSummary(callA, callB);

        return CompareDTO.builder()
            .callA(callA)
            .callB(callB)
            .diffText(diffText)
            .summary(summary)
            .build();
    }

    /**
     * Generate simple text diff
     * For v0.1, this is a basic implementation
     * v0.2 can use a proper diff library
     */
    private String generateDiff(String textA, String textB) {
        if (textA == null) textA = "";
        if (textB == null) textB = "";

        if (textA.equals(textB)) {
            return "No differences";
        }

        StringBuilder diff = new StringBuilder();
        diff.append("=== CALL A ===\n");
        diff.append(textA);
        diff.append("\n\n=== CALL B ===\n");
        diff.append(textB);
        diff.append("\n\n=== SUMMARY ===\n");
        diff.append(String.format("Length A: %d characters\n", textA.length()));
        diff.append(String.format("Length B: %d characters\n", textB.length()));
        diff.append(String.format("Difference: %d characters\n",
                                 Math.abs(textA.length() - textB.length())));

        // Calculate similarity (simple Jaccard)
        double similarity = calculateSimilarity(textA, textB);
        diff.append(String.format("Similarity: %.2f%%\n", similarity * 100));

        return diff.toString();
    }

    /**
     * Generate comparison summary
     */
    private Map<String, Object> generateSummary(CallDetailDTO callA, CallDetailDTO callB) {
        Map<String, Object> summary = new HashMap<>();

        // Model comparison
        summary.put("sameModel", callA.getModel().equals(callB.getModel()));
        summary.put("modelA", callA.getModel());
        summary.put("modelB", callB.getModel());

        // Cost comparison
        BigDecimal costDiff = callB.getCostEstimateUsd().subtract(callA.getCostEstimateUsd());
        summary.put("costDifference", costDiff);
        summary.put("costA", callA.getCostEstimateUsd());
        summary.put("costB", callB.getCostEstimateUsd());

        // Token comparison
        summary.put("tokenDifference", callB.getTokensTotal() - callA.getTokensTotal());
        summary.put("tokensA", callA.getTokensTotal());
        summary.put("tokensB", callB.getTokensTotal());

        // Latency comparison
        summary.put("latencyDifference", callB.getLatencyMs() - callA.getLatencyMs());
        summary.put("latencyA", callA.getLatencyMs());
        summary.put("latencyB", callB.getLatencyMs());

        // Temperature comparison
        summary.put("sameTemperature",
                   callA.getTemperature() != null && callA.getTemperature().equals(callB.getTemperature()));
        summary.put("temperatureA", callA.getTemperature());
        summary.put("temperatureB", callB.getTemperature());

        // Text similarity
        double similarity = calculateSimilarity(
            callA.getResponseText() != null ? callA.getResponseText() : "",
            callB.getResponseText() != null ? callB.getResponseText() : ""
        );
        summary.put("textSimilarity", similarity);

        return summary;
    }

    /**
     * Calculate text similarity using simple Jaccard index
     */
    private double calculateSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }

        if (text1.equals(text2)) {
            return 1.0;
        }

        // Split into words
        String[] words1 = text1.toLowerCase().split("\\s+");
        String[] words2 = text2.toLowerCase().split("\\s+");

        // Convert to sets
        java.util.Set<String> set1 = new java.util.HashSet<>(java.util.Arrays.asList(words1));
        java.util.Set<String> set2 = new java.util.HashSet<>(java.util.Arrays.asList(words2));

        // Calculate Jaccard index
        java.util.Set<String> intersection = new java.util.HashSet<>(set1);
        intersection.retainAll(set2);

        java.util.Set<String> union = new java.util.HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }
}

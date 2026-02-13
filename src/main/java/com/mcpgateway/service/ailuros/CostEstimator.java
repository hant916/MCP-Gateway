package com.mcpgateway.service.ailuros;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Ailuros Control: Cost Estimation Service
 * Calculates estimated costs based on token usage and model pricing
 *
 * Pricing is hardcoded for v0.1. Future versions may support:
 * - Dynamic pricing from external sources
 * - Custom pricing per project
 * - Volume discounts
 */
@Slf4j
@Service
public class CostEstimator {

    /**
     * Model pricing configuration
     * Format: Map<modelId, ModelPricing>
     */
    private final Map<String, ModelPricing> pricingTable;

    public CostEstimator() {
        this.pricingTable = initializePricingTable();
    }

    /**
     * Estimate cost for an LLM call
     *
     * @param model Model identifier
     * @param promptTokens Number of prompt tokens
     * @param completionTokens Number of completion tokens
     * @return Estimated cost in USD
     */
    public BigDecimal estimateCost(String model, int promptTokens, int completionTokens) {
        if (model == null || model.trim().isEmpty()) {
            log.warn("Model not specified, cannot estimate cost");
            return BigDecimal.ZERO;
        }

        ModelPricing pricing = findPricing(model);
        if (pricing == null) {
            log.warn("No pricing data for model: {}, using zero cost", model);
            return BigDecimal.ZERO;
        }

        // Cost = (prompt_tokens * input_price_per_1M) + (completion_tokens * output_price_per_1M)
        BigDecimal promptCost = pricing.inputPricePer1M
            .multiply(BigDecimal.valueOf(promptTokens))
            .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);

        BigDecimal completionCost = pricing.outputPricePer1M
            .multiply(BigDecimal.valueOf(completionTokens))
            .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);

        BigDecimal totalCost = promptCost.add(completionCost);

        log.debug("Cost estimate for {}: promptTokens={}, completionTokens={}, cost=${}",
                  model, promptTokens, completionTokens, totalCost);

        return totalCost.setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Find pricing for a model, with fuzzy matching
     */
    private ModelPricing findPricing(String model) {
        String normalizedModel = model.toLowerCase().trim();

        // Exact match first
        ModelPricing pricing = pricingTable.get(normalizedModel);
        if (pricing != null) {
            return pricing;
        }

        // Fuzzy match - check if model contains any key
        for (Map.Entry<String, ModelPricing> entry : pricingTable.entrySet()) {
            if (normalizedModel.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Initialize pricing table with current rates (as of Jan 2025)
     */
    private Map<String, ModelPricing> initializePricingTable() {
        Map<String, ModelPricing> table = new HashMap<>();

        // OpenAI Models
        table.put("gpt-4o", new ModelPricing(
            new BigDecimal("2.50"),    // $2.50 per 1M input tokens
            new BigDecimal("10.00")    // $10.00 per 1M output tokens
        ));

        table.put("gpt-4o-mini", new ModelPricing(
            new BigDecimal("0.150"),   // $0.15 per 1M input tokens
            new BigDecimal("0.600")    // $0.60 per 1M output tokens
        ));

        table.put("gpt-4-turbo", new ModelPricing(
            new BigDecimal("10.00"),   // $10.00 per 1M input tokens
            new BigDecimal("30.00")    // $30.00 per 1M output tokens
        ));

        table.put("gpt-4", new ModelPricing(
            new BigDecimal("30.00"),   // $30.00 per 1M input tokens
            new BigDecimal("60.00")    // $60.00 per 1M output tokens
        ));

        table.put("gpt-3.5-turbo", new ModelPricing(
            new BigDecimal("0.50"),    // $0.50 per 1M input tokens
            new BigDecimal("1.50")     // $1.50 per 1M output tokens
        ));

        // Anthropic Claude Models
        table.put("claude-opus-4", new ModelPricing(
            new BigDecimal("15.00"),   // $15.00 per 1M input tokens
            new BigDecimal("75.00")    // $75.00 per 1M output tokens
        ));

        table.put("claude-sonnet-4", new ModelPricing(
            new BigDecimal("3.00"),    // $3.00 per 1M input tokens
            new BigDecimal("15.00")    // $15.00 per 1M output tokens
        ));

        table.put("claude-3-opus", new ModelPricing(
            new BigDecimal("15.00"),   // $15.00 per 1M input tokens
            new BigDecimal("75.00")    // $75.00 per 1M output tokens
        ));

        table.put("claude-3-sonnet", new ModelPricing(
            new BigDecimal("3.00"),    // $3.00 per 1M input tokens
            new BigDecimal("15.00")    // $15.00 per 1M output tokens
        ));

        table.put("claude-3-haiku", new ModelPricing(
            new BigDecimal("0.25"),    // $0.25 per 1M input tokens
            new BigDecimal("1.25")     // $1.25 per 1M output tokens
        ));

        table.put("claude-3.5-sonnet", new ModelPricing(
            new BigDecimal("3.00"),    // $3.00 per 1M input tokens
            new BigDecimal("15.00")    // $15.00 per 1M output tokens
        ));

        table.put("claude-3.5-haiku", new ModelPricing(
            new BigDecimal("0.80"),    // $0.80 per 1M input tokens
            new BigDecimal("4.00")     // $4.00 per 1M output tokens
        ));

        // Azure OpenAI (same as OpenAI for now)
        table.put("azure-gpt-4o", table.get("gpt-4o"));
        table.put("azure-gpt-4", table.get("gpt-4"));
        table.put("azure-gpt-35-turbo", table.get("gpt-3.5-turbo"));

        return table;
    }

    /**
     * Get all available models with pricing
     */
    public Map<String, ModelPricing> getAllPricing() {
        return new HashMap<>(pricingTable);
    }

    /**
     * Model pricing structure
     */
    public static class ModelPricing {
        public final BigDecimal inputPricePer1M;
        public final BigDecimal outputPricePer1M;

        public ModelPricing(BigDecimal inputPricePer1M, BigDecimal outputPricePer1M) {
            this.inputPricePer1M = inputPricePer1M;
            this.outputPricePer1M = outputPricePer1M;
        }

        @Override
        public String toString() {
            return String.format("Input: $%s/1M, Output: $%s/1M",
                               inputPricePer1M, outputPricePer1M);
        }
    }
}

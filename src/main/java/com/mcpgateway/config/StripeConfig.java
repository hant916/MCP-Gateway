package com.mcpgateway.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Stripe payment gateway configuration
 */
@Slf4j
@Configuration
public class StripeConfig {

    @Value("${stripe.api.key:${STRIPE_API_KEY:}}")
    private String stripeApiKey;

    @PostConstruct
    public void init() {
        if (stripeApiKey != null && !stripeApiKey.isEmpty()) {
            Stripe.apiKey = stripeApiKey;
            log.info("Stripe API initialized");
        } else {
            log.warn("Stripe API key not configured. Payment features will be disabled.");
        }
    }
}

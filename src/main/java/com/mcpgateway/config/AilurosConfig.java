package com.mcpgateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Ailuros Control Configuration
 *
 * Enables async processing for audit operations
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties({
    AilurosObservabilityProperties.class,
    AilurosGovernanceProperties.class
})
public class AilurosConfig {
    // Additional configuration can be added here in future versions:
    // - Text truncation limits
    // - PII detection
    // - Custom cost pricing
    // - Retention policies
}

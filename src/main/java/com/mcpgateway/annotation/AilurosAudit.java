package com.mcpgateway.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ailuros Control: Audit Annotation
 *
 * Mark methods that should be audited by Ailuros Control.
 * When applied to a method, the AilurosAuditAspect will automatically
 * capture and log the LLM call details.
 *
 * Example usage:
 * <pre>
 * @AilurosAudit(provider = "openai", model = "gpt-4")
 * public CompletionResponse callLLM(CompletionRequest request) {
 *     // LLM call implementation
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AilurosAudit {

    /**
     * Provider name (e.g., "openai", "anthropic", "azure_openai")
     */
    String provider() default "";

    /**
     * Model name (can be overridden at runtime via SpEL)
     */
    String model() default "";

    /**
     * Project key for grouping calls
     */
    String projectKey() default "default";

    /**
     * Environment (prod, stage, dev)
     */
    String env() default "prod";

    /**
     * Enable/disable auditing for this method
     */
    boolean enabled() default true;
}

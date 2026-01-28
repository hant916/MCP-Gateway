package com.mcpgateway.circuitbreaker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to apply circuit breaker protection to a method.
 * The serverId should be passed as a method parameter or can be specified in the annotation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CircuitBreakerProtected {

    /**
     * The name/ID of the circuit breaker instance.
     * If empty, the method will look for a parameter named "serverId" or annotated with @ServerId
     */
    String name() default "";

    /**
     * Whether to enable retry logic
     */
    boolean enableRetry() default true;

    /**
     * Whether to enable timeout protection
     */
    boolean enableTimeout() default true;

    /**
     * Fallback method name to call when circuit is open
     */
    String fallbackMethod() default "";
}

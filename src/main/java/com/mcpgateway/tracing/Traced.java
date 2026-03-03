package com.mcpgateway.tracing;

import io.micrometer.observation.annotation.Observed;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enable tracing for a method.
 * Uses Micrometer's @Observed under the hood.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Observed
public @interface Traced {

    /**
     * The name of the span. If not specified, the method name will be used.
     */
    String value() default "";

    /**
     * Low cardinality key-value pairs to add to the observation.
     * Format: "key1=value1,key2=value2"
     */
    String[] tags() default {};
}

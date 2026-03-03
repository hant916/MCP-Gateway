package com.mcpgateway.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Audit Log Annotation
 *
 * Usage:
 * @AuditLog(action = "PAYMENT_CREATED", resource = "Payment")
 * public Payment createPayment(...) { ... }
 *
 * The AOP aspect will automatically log:
 * - User performing the action
 * - HTTP request details
 * - Method parameters
 * - Execution time
 * - Success/failure status
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /**
     * Action being performed (e.g., "PAYMENT_CREATED", "USER_DELETED")
     */
    String action();

    /**
     * Resource type being acted upon (e.g., "Payment", "User", "Tool")
     */
    String resource();

    /**
     * Include method parameters in audit metadata
     */
    boolean includeParams() default false;

    /**
     * Include response in audit metadata
     */
    boolean includeResponse() default false;
}

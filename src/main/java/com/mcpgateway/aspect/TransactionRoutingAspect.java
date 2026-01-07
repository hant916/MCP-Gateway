package com.mcpgateway.aspect;

import com.mcpgateway.config.datasource.DataSourceContextHolder;
import com.mcpgateway.config.datasource.DataSourceType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

/**
 * AOP Aspect for routing database transactions to master or replica
 *
 * Intercepts @Transactional methods and routes to:
 * - MASTER: if readOnly = false (default) - for writes
 * - REPLICA: if readOnly = true - for reads
 *
 * Order = 1: Executes BEFORE Spring's transaction management (@Order = 100)
 * This ensures datasource is selected before transaction starts
 */
@Slf4j
@Aspect
@Component
@Order(1)
public class TransactionRoutingAspect {

    /**
     * Intercept all @Transactional methods
     */
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object routeDataSource(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            // Determine if this is a read-only transaction
            boolean isReadOnly = isReadOnlyTransaction(joinPoint);

            // Route to appropriate datasource
            if (isReadOnly) {
                DataSourceContextHolder.setDataSourceType(DataSourceType.REPLICA);
                log.debug("Routing to REPLICA for read-only transaction: {}.{}",
                        joinPoint.getSignature().getDeclaringTypeName(),
                        joinPoint.getSignature().getName());
            } else {
                DataSourceContextHolder.setDataSourceType(DataSourceType.MASTER);
                log.debug("Routing to MASTER for write transaction: {}.{}",
                        joinPoint.getSignature().getDeclaringTypeName(),
                        joinPoint.getSignature().getName());
            }

            // Execute the method
            return joinPoint.proceed();

        } finally {
            // Always clear context to prevent memory leaks
            DataSourceContextHolder.clearDataSourceType();
        }
    }

    /**
     * Check if method has @Transactional(readOnly = true)
     */
    private boolean isReadOnlyTransaction(ProceedingJoinPoint joinPoint) {
        try {
            // Get method
            String methodName = joinPoint.getSignature().getName();
            Class<?> targetClass = joinPoint.getTarget().getClass();
            Class<?>[] parameterTypes = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature())
                    .getMethod()
                    .getParameterTypes();

            Method method = targetClass.getMethod(methodName, parameterTypes);

            // Check method-level annotation
            Transactional transactional = method.getAnnotation(Transactional.class);
            if (transactional != null) {
                return transactional.readOnly();
            }

            // Check class-level annotation
            transactional = targetClass.getAnnotation(Transactional.class);
            if (transactional != null) {
                return transactional.readOnly();
            }

            // Default: not read-only (route to master)
            return false;

        } catch (Exception e) {
            log.warn("Error determining transaction type, defaulting to MASTER", e);
            return false;
        }
    }
}

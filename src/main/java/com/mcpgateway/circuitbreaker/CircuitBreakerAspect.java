package com.mcpgateway.circuitbreaker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class CircuitBreakerAspect {

    private final CircuitBreakerService circuitBreakerService;

    @Around("@annotation(circuitBreakerProtected)")
    public Object handleCircuitBreaker(ProceedingJoinPoint joinPoint, CircuitBreakerProtected circuitBreakerProtected) throws Throwable {
        String circuitBreakerId = resolveCircuitBreakerId(joinPoint, circuitBreakerProtected);

        if (circuitBreakerProtected.enableRetry() && circuitBreakerProtected.enableTimeout()) {
            return circuitBreakerService.executeWithFullProtection(
                    circuitBreakerId,
                    () -> {
                        try {
                            return joinPoint.proceed();
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    },
                    getFallbackResult(joinPoint, circuitBreakerProtected)
            );
        } else if (circuitBreakerProtected.enableRetry()) {
            return circuitBreakerService.executeWithCircuitBreakerAndRetry(
                    circuitBreakerId,
                    () -> {
                        try {
                            return joinPoint.proceed();
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
        } else {
            return circuitBreakerService.executeWithCircuitBreaker(
                    circuitBreakerId,
                    () -> {
                        try {
                            return joinPoint.proceed();
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
        }
    }

    private String resolveCircuitBreakerId(ProceedingJoinPoint joinPoint, CircuitBreakerProtected annotation) {
        // First check if name is specified in annotation
        if (!annotation.name().isEmpty()) {
            return annotation.name();
        }

        // Try to find serverId parameter
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals("serverId") ||
                parameters[i].getName().equals("serverName")) {
                if (args[i] != null) {
                    return args[i].toString();
                }
            }
        }

        // Default to method name
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    @SuppressWarnings("unchecked")
    private <T> T getFallbackResult(ProceedingJoinPoint joinPoint, CircuitBreakerProtected annotation) {
        if (annotation.fallbackMethod().isEmpty()) {
            return null;
        }

        try {
            Object target = joinPoint.getTarget();
            Method fallbackMethod = target.getClass().getMethod(annotation.fallbackMethod(), getParameterTypes(joinPoint));
            return (T) fallbackMethod.invoke(target, joinPoint.getArgs());
        } catch (Exception e) {
            log.warn("Failed to invoke fallback method: {}", annotation.fallbackMethod(), e);
            return null;
        }
    }

    private Class<?>[] getParameterTypes(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getParameterTypes();
    }
}

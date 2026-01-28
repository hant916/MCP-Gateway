package com.mcpgateway.tracing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Aspect for handling @Traced annotation
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class TracingAspect {

    private final TracingService tracingService;

    @Around("@annotation(traced)")
    public Object traceMethod(ProceedingJoinPoint joinPoint, Traced traced) throws Throwable {
        if (!tracingService.isEnabled()) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String spanName = traced.value().isEmpty()
                ? signature.getDeclaringType().getSimpleName() + "." + signature.getName()
                : traced.value();

        Map<String, String> tags = parseTags(traced.tags());
        tags.put("class", signature.getDeclaringType().getSimpleName());
        tags.put("method", signature.getName());

        try (TracingService.SpanWrapper span = tracingService.startSpan(spanName, tags)) {
            try {
                Object result = joinPoint.proceed();
                span.tag("status", "success");
                return result;
            } catch (Throwable e) {
                span.error(e);
                span.tag("status", "error");
                span.tag("error.type", e.getClass().getSimpleName());
                throw e;
            }
        }
    }

    private Map<String, String> parseTags(String[] tagStrings) {
        Map<String, String> tags = new HashMap<>();
        for (String tagString : tagStrings) {
            String[] parts = tagString.split("=", 2);
            if (parts.length == 2) {
                tags.put(parts[0].trim(), parts[1].trim());
            }
        }
        return tags;
    }
}

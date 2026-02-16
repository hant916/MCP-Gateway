package com.mcpgateway.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Ailuros Control: Trace ID Filter
 *
 * Generates a unique trace ID for each request and:
 * - Stores it in request attributes for access by downstream components
 * - Adds it to response headers for client-side tracking
 * - Logs it for correlation with audit records
 *
 * The trace ID is used to link gateway requests with LLM call audit logs.
 */
@Slf4j
@Component
@Order(1) // Execute early in the filter chain
public class AilurosTraceIdFilter implements Filter {

    public static final String TRACE_ID_HEADER = "X-Ailuros-Trace-Id";
    public static final String TRACE_ID_HEADER_ALIAS = "X-Trace-Id";
    public static final String TRACE_ID_ATTRIBUTE = "ailuros.traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Check if trace ID already exists (from upstream)
        String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = httpRequest.getHeader(TRACE_ID_HEADER_ALIAS);
        }

        // Generate new trace ID if not present
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = generateTraceId();
        }

        // Store in request attributes for access by controllers/services
        httpRequest.setAttribute(TRACE_ID_ATTRIBUTE, traceId);

        // Add to response headers
        httpResponse.setHeader(TRACE_ID_HEADER, traceId);
        httpResponse.setHeader(TRACE_ID_HEADER_ALIAS, traceId);

        // Add to MDC for structured logging
        org.slf4j.MDC.put("traceId", traceId);

        try {
            log.debug("Request traced: {} {} [traceId={}]",
                     httpRequest.getMethod(),
                     httpRequest.getRequestURI(),
                     traceId);

            chain.doFilter(request, response);
        } finally {
            // Clean up MDC
            org.slf4j.MDC.remove("traceId");
        }
    }

    /**
     * Generate a unique trace ID
     * Format: UUID without hyphens for compactness
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Extract trace ID from current request
     * This utility method can be called from anywhere in the request processing chain
     */
    public static String getTraceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(TRACE_ID_ATTRIBUTE);
        return traceId != null ? traceId.toString() : null;
    }
}

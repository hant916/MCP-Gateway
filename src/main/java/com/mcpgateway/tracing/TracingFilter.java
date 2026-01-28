package com.mcpgateway.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Filter to add tracing information to HTTP requests and responses
 */
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TracingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String SPAN_ID_HEADER = "X-Span-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final TracingService tracingService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        String requestPath = request.getRequestURI();
        String method = request.getMethod();

        // Extract or generate request ID
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = java.util.UUID.randomUUID().toString();
        }

        // Start a span for this request
        try (TracingService.SpanWrapper span = tracingService.startSpan("http.request", Map.of(
                "http.method", method,
                "http.url", requestPath,
                "http.request_id", requestId
        ))) {
            // Add trace headers to response
            String traceId = span.getTraceId();
            String spanId = span.getSpanId();

            if (traceId != null) {
                response.setHeader(TRACE_ID_HEADER, traceId);
            }
            if (spanId != null) {
                response.setHeader(SPAN_ID_HEADER, spanId);
            }
            response.setHeader(REQUEST_ID_HEADER, requestId);

            // Store in request attributes for downstream use
            request.setAttribute("traceId", traceId);
            request.setAttribute("spanId", spanId);
            request.setAttribute("requestId", requestId);

            try {
                filterChain.doFilter(request, response);

                // Record success
                span.tag("http.status_code", String.valueOf(response.getStatus()));
                span.tag("http.response_time_ms", String.valueOf(System.currentTimeMillis() - startTime));

                if (response.getStatus() >= 400) {
                    span.event("http.error");
                }
            } catch (Exception e) {
                span.error(e);
                span.tag("http.status_code", "500");
                throw e;
            }
        }

        // Log request completion
        long duration = System.currentTimeMillis() - startTime;
        if (log.isDebugEnabled()) {
            log.debug("Request {} {} completed with status {} in {}ms",
                    method, requestPath, response.getStatus(), duration);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip tracing for actuator and health check endpoints
        return path.startsWith("/actuator") ||
               path.equals("/health") ||
               path.equals("/ready") ||
               path.equals("/live");
    }
}

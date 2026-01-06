package com.mcpgateway.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Global exception handler for rate limit exceptions
 */
@Slf4j
@RestControllerAdvice
public class RateLimitExceptionHandler {

    /**
     * Handle rate limit exceeded exceptions
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<RateLimitErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException ex) {

        log.warn("Rate limit exceeded: {}", ex.getMessage());

        RateLimitErrorResponse errorResponse = new RateLimitErrorResponse(
                "RATE_LIMIT_EXCEEDED",
                ex.getMessage(),
                ex.getLimit(),
                ex.getResetTime(),
                ex.getRetryAfterSeconds(),
                ex.getAppliedRule()
        );

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .header("X-RateLimit-Limit", String.valueOf(ex.getLimit()))
                .header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset", String.valueOf(ex.getResetTime().getEpochSecond()))
                .body(errorResponse);
    }

    /**
     * Error response for rate limit exceeded
     */
    @Data
    @AllArgsConstructor
    public static class RateLimitErrorResponse {
        private String error;
        private String message;
        private long limit;
        private Instant resetTime;
        private Long retryAfterSeconds;
        private String appliedRule;
    }
}

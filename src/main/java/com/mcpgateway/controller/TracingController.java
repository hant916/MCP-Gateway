package com.mcpgateway.controller;

import com.mcpgateway.tracing.TracingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tracing")
@RequiredArgsConstructor
@Tag(name = "Tracing", description = "Distributed tracing information")
public class TracingController {

    private final TracingService tracingService;

    @GetMapping("/current")
    @Operation(summary = "Get current trace context")
    public ResponseEntity<Map<String, Object>> getCurrentTraceContext() {
        return ResponseEntity.ok(Map.of(
                "enabled", tracingService.isEnabled(),
                "traceId", tracingService.getCurrentTraceId() != null ? tracingService.getCurrentTraceId() : "N/A",
                "spanId", tracingService.getCurrentSpanId() != null ? tracingService.getCurrentSpanId() : "N/A"
        ));
    }

    @GetMapping("/status")
    @Operation(summary = "Get tracing status")
    public ResponseEntity<Map<String, Object>> getTracingStatus() {
        return ResponseEntity.ok(Map.of(
                "enabled", tracingService.isEnabled(),
                "provider", "Micrometer Tracing with Brave/Zipkin"
        ));
    }
}

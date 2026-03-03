package com.mcpgateway.controller;

import com.mcpgateway.circuitbreaker.CircuitBreakerService;
import com.mcpgateway.circuitbreaker.CircuitBreakerService.CircuitBreakerState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/circuit-breakers")
@RequiredArgsConstructor
@Tag(name = "Circuit Breaker", description = "Circuit breaker management and monitoring")
public class CircuitBreakerController {

    private final CircuitBreakerService circuitBreakerService;

    @GetMapping
    @Operation(summary = "Get all circuit breaker states")
    public ResponseEntity<List<CircuitBreakerState>> getAllStates() {
        return ResponseEntity.ok(circuitBreakerService.getAllCircuitBreakerStates());
    }

    @GetMapping("/{serverId}")
    @Operation(summary = "Get circuit breaker state for a specific server")
    public ResponseEntity<CircuitBreakerState> getState(@PathVariable String serverId) {
        return ResponseEntity.ok(circuitBreakerService.getCircuitBreakerState(serverId));
    }

    @PostMapping("/{serverId}/reset")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reset circuit breaker for a server")
    public ResponseEntity<Map<String, String>> reset(@PathVariable String serverId) {
        circuitBreakerService.resetCircuitBreaker(serverId);
        return ResponseEntity.ok(Map.of(
                "message", "Circuit breaker reset successfully",
                "serverId", serverId
        ));
    }

    @PostMapping("/{serverId}/force-open")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Force circuit breaker to open state")
    public ResponseEntity<Map<String, String>> forceOpen(@PathVariable String serverId) {
        circuitBreakerService.forceOpen(serverId);
        return ResponseEntity.ok(Map.of(
                "message", "Circuit breaker forced to OPEN state",
                "serverId", serverId
        ));
    }

    @PostMapping("/{serverId}/force-close")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Force circuit breaker to closed state")
    public ResponseEntity<Map<String, String>> forceClosed(@PathVariable String serverId) {
        circuitBreakerService.forceClosed(serverId);
        return ResponseEntity.ok(Map.of(
                "message", "Circuit breaker forced to CLOSED state",
                "serverId", serverId
        ));
    }
}

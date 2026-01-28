package com.mcpgateway.controller;

import com.mcpgateway.loadbalancer.LoadBalancerService;
import com.mcpgateway.loadbalancer.LoadBalancerService.PoolStatistics;
import com.mcpgateway.loadbalancer.ServerInstance;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/load-balancer")
@RequiredArgsConstructor
@Tag(name = "Load Balancer", description = "Load balancer management and monitoring")
public class LoadBalancerController {

    private final LoadBalancerService loadBalancerService;

    @GetMapping("/strategies")
    @Operation(summary = "Get all available load balancing strategies")
    public ResponseEntity<Set<String>> getAvailableStrategies() {
        return ResponseEntity.ok(loadBalancerService.getAvailableStrategies());
    }

    @GetMapping("/pools")
    @Operation(summary = "Get statistics for all server pools")
    public ResponseEntity<Map<String, PoolStatistics>> getAllPoolStatistics() {
        return ResponseEntity.ok(loadBalancerService.getPoolStatistics());
    }

    @GetMapping("/pools/{poolName}")
    @Operation(summary = "Get statistics for a specific server pool")
    public ResponseEntity<PoolStatistics> getPoolStatistics(@PathVariable String poolName) {
        PoolStatistics stats = loadBalancerService.getPoolStatistics(poolName);
        if (stats == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/pools/{poolName}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Register a new server pool")
    public ResponseEntity<Map<String, String>> registerPool(
            @PathVariable String poolName,
            @RequestBody @Valid RegisterPoolRequest request) {

        List<ServerInstance> instances = request.getInstances().stream()
                .map(this::toServerInstance)
                .toList();

        loadBalancerService.registerPool(poolName, instances);

        if (request.getStrategy() != null && !request.getStrategy().isEmpty()) {
            loadBalancerService.setStrategy(poolName, request.getStrategy());
        }

        return ResponseEntity.ok(Map.of(
                "message", "Pool registered successfully",
                "poolName", poolName,
                "instanceCount", String.valueOf(instances.size())
        ));
    }

    @PostMapping("/pools/{poolName}/instances")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add an instance to a server pool")
    public ResponseEntity<Map<String, String>> addInstance(
            @PathVariable String poolName,
            @RequestBody @Valid InstanceRequest request) {

        ServerInstance instance = toServerInstance(request);
        loadBalancerService.addInstance(poolName, instance);

        return ResponseEntity.ok(Map.of(
                "message", "Instance added successfully",
                "poolName", poolName,
                "instanceId", instance.getId()
        ));
    }

    @DeleteMapping("/pools/{poolName}/instances/{instanceId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remove an instance from a server pool")
    public ResponseEntity<Map<String, String>> removeInstance(
            @PathVariable String poolName,
            @PathVariable String instanceId) {

        loadBalancerService.removeInstance(poolName, instanceId);

        return ResponseEntity.ok(Map.of(
                "message", "Instance removed successfully",
                "poolName", poolName,
                "instanceId", instanceId
        ));
    }

    @PutMapping("/pools/{poolName}/strategy")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Set the load balancing strategy for a pool")
    public ResponseEntity<Map<String, String>> setStrategy(
            @PathVariable String poolName,
            @RequestBody @Valid SetStrategyRequest request) {

        loadBalancerService.setStrategy(poolName, request.getStrategy());

        return ResponseEntity.ok(Map.of(
                "message", "Strategy updated successfully",
                "poolName", poolName,
                "strategy", request.getStrategy()
        ));
    }

    private ServerInstance toServerInstance(InstanceRequest request) {
        ServerInstance instance = new ServerInstance(
                request.getId(),
                request.getHost(),
                request.getPort(),
                request.getWeight() != null ? request.getWeight() : 1
        );

        if (request.getProtocol() != null) {
            instance.setProtocol(request.getProtocol());
        }
        if (request.getZone() != null) {
            instance.setZone(request.getZone());
        }
        if (request.getVersion() != null) {
            instance.setVersion(request.getVersion());
        }

        return instance;
    }

    @Data
    public static class RegisterPoolRequest {
        private List<InstanceRequest> instances;
        private String strategy;
    }

    @Data
    public static class InstanceRequest {
        @NotBlank
        private String id;
        @NotBlank
        private String host;
        private int port = 80;
        private String protocol = "http";
        private Integer weight = 1;
        private String zone;
        private String version;
    }

    @Data
    public static class SetStrategyRequest {
        @NotBlank
        private String strategy;
    }
}

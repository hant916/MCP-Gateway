package com.mcpgateway.controller;

import com.mcpgateway.service.McpToolDemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/mcp-demo")
@RequiredArgsConstructor
@Tag(name = "MCP Demo", description = "MCP tool demonstration APIs")
@SecurityRequirement(name = "bearerAuth")
public class McpDemoController {

    private final McpToolDemoService mcpToolDemoService;

    /**
     * 演示完整的 MCP 工具使用流程
     */
    @PostMapping("/full-demo")
    @Operation(summary = "Run a full MCP tool usage demonstration")
    public ResponseEntity<Map<String, Object>> runFullDemo() {
        try {
            log.info("Starting MCP tool demonstration...");
            mcpToolDemoService.demonstrateMcpToolUsage();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "MCP tool demonstration completed successfully. Check logs for details."
            ));
        } catch (Exception e) {
            log.error("Error during MCP demonstration", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "MCP demonstration failed: " + e.getMessage()
            ));
        }
    }

    /**
     * 演示天气工具
     */
    @PostMapping("/weather-demo")
    @Operation(summary = "Run weather tool demonstration")
    public ResponseEntity<Map<String, Object>> runWeatherDemo() {
        try {
            log.info("Starting weather tool demonstration...");
            mcpToolDemoService.demonstrateWeatherTool();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Weather tool demonstration completed successfully. Check logs for details."
            ));
        } catch (Exception e) {
            log.error("Error during weather demonstration", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Weather demonstration failed: " + e.getMessage()
            ));
        }
    }
} 
package com.mcpgateway.controller;

import com.mcpgateway.dto.tool.McpToolDTO;
import com.mcpgateway.service.McpToolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/mcp-tools")
@RequiredArgsConstructor
@Tag(name = "MCP Tools", description = "MCP tool management APIs")
@SecurityRequirement(name = "bearerAuth")
public class McpToolController {

    private final McpToolService mcpToolService;

    @GetMapping("/api-specification/{apiSpecId}")
    @Operation(summary = "Get all tools for an API specification")
    public ResponseEntity<List<McpToolDTO>> getToolsByApiSpecification(@PathVariable UUID apiSpecId) {
        return ResponseEntity.ok(mcpToolService.getToolsByApiSpecification(apiSpecId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a tool by ID")
    public ResponseEntity<McpToolDTO> getTool(@PathVariable UUID id) {
        return ResponseEntity.ok(mcpToolService.getTool(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a tool")
    public ResponseEntity<Void> deleteTool(@PathVariable UUID id) {
        mcpToolService.deleteTool(id);
        return ResponseEntity.ok().build();
    }
} 
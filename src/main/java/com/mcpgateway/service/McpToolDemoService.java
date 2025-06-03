package com.mcpgateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolDemoService {
    
    private final McpServerManager mcpServerManager;
    private final ObjectMapper objectMapper;

    /**
     * 演示完整的 MCP 工具使用流程
     */
    public void demonstrateMcpToolUsage() {
        UUID serverId = null;
        try {
            // Step 1: 启动 MCP Server (以文件系统服务器为例)
            log.info("=== Step 1: 启动 MCP Server ===");
            serverId = startFileSystemMcpServer();
            log.info("MCP Server started with ID: {}", serverId);

            // Step 2: 获取 MCP 配置
            log.info("=== Step 2: 获取 MCP 配置 ===");
            JsonNode config = mcpServerManager.getMcpConfig(serverId);
            log.info("MCP Config: {}", config.toPrettyString());

            // Step 3: 获取工具列表
            log.info("=== Step 3: 获取工具列表 ===");
            JsonNode tools = mcpServerManager.getTools(serverId);
            log.info("Available Tools: {}", tools.toPrettyString());

            // Step 4: 调用工具 (示例: 读取文件)
            log.info("=== Step 4: 调用工具 ===");
            demonstrateToolInvocation(serverId);

        } catch (Exception e) {
            log.error("Error during MCP tool demonstration", e);
        } finally {
            // 清理: 停止服务器
            if (serverId != null) {
                try {
                    mcpServerManager.stopMcpServer(serverId);
                    log.info("MCP Server stopped: {}", serverId);
                } catch (Exception e) {
                    log.error("Error stopping MCP Server", e);
                }
            }
        }
    }

    /**
     * 启动文件系统 MCP Server
     */
    public UUID startFileSystemMcpServer() {
        UUID serverId = UUID.randomUUID();
        
        // 文件系统服务器命令
        List<String> command = Arrays.asList(
            "npx", 
            "@modelcontextprotocol/server-filesystem", 
            "/tmp"  // 允许访问的目录
        );
        
        mcpServerManager.startMcpServer(serverId, command, null);
        return serverId;
    }

    /**
     * 启动天气 MCP Server (示例)
     */
    public UUID startWeatherMcpServer() {
        UUID serverId = UUID.randomUUID();
        
        // 天气服务器命令 (假设存在)
        List<String> command = Arrays.asList(
            "npx", 
            "@modelcontextprotocol/server-weather"
        );
        
        mcpServerManager.startMcpServer(serverId, command, null);
        return serverId;
    }

    /**
     * 演示工具调用
     */
    private void demonstrateToolInvocation(UUID serverId) {
        try {
            // 示例 1: 调用文件读取工具
            ObjectNode readFileArgs = objectMapper.createObjectNode();
            readFileArgs.put("path", "/tmp/test.txt");
            
            JsonNode readResult = mcpServerManager.invokeTool(serverId, "read_file", readFileArgs);
            log.info("Read file result: {}", readResult.toPrettyString());

            // 示例 2: 调用目录列表工具
            ObjectNode listDirArgs = objectMapper.createObjectNode();
            listDirArgs.put("path", "/tmp");
            
            JsonNode listResult = mcpServerManager.invokeTool(serverId, "list_directory", listDirArgs);
            log.info("List directory result: {}", listResult.toPrettyString());

        } catch (Exception e) {
            log.error("Error during tool invocation", e);
        }
    }

    /**
     * 演示天气工具调用
     */
    public void demonstrateWeatherTool() {
        UUID serverId = null;
        try {
            // 启动天气服务器
            serverId = startWeatherMcpServer();
            
            // 获取工具列表
            JsonNode tools = mcpServerManager.getTools(serverId);
            log.info("Weather Tools: {}", tools.toPrettyString());
            
            // 调用天气查询工具
            ObjectNode weatherArgs = objectMapper.createObjectNode();
            weatherArgs.put("location", "Beijing");
            weatherArgs.put("unit", "celsius");
            
            JsonNode weatherResult = mcpServerManager.invokeTool(serverId, "get_weather", weatherArgs);
            log.info("Weather result: {}", weatherResult.toPrettyString());
            
        } catch (Exception e) {
            log.error("Error during weather tool demonstration", e);
        } finally {
            if (serverId != null) {
                mcpServerManager.stopMcpServer(serverId);
            }
        }
    }

    /**
     * 批量工具调用示例
     */
    public void demonstrateBatchToolCalls(UUID serverId, List<ToolCall> toolCalls) {
        for (ToolCall toolCall : toolCalls) {
            try {
                log.info("Calling tool: {} with args: {}", toolCall.getToolName(), toolCall.getArguments());
                JsonNode result = mcpServerManager.invokeTool(serverId, toolCall.getToolName(), toolCall.getArguments());
                log.info("Tool result: {}", result.toPrettyString());
            } catch (Exception e) {
                log.error("Error calling tool: {}", toolCall.getToolName(), e);
            }
        }
    }

    /**
     * 工具调用封装类
     */
    public static class ToolCall {
        private String toolName;
        private JsonNode arguments;

        public ToolCall(String toolName, JsonNode arguments) {
            this.toolName = toolName;
            this.arguments = arguments;
        }

        public String getToolName() { return toolName; }
        public JsonNode getArguments() { return arguments; }
    }
} 
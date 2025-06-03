package com.mcpgateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerManager {
    private final ObjectMapper objectMapper;
    private final Map<UUID, McpServerProcess> runningServers = new ConcurrentHashMap<>();

    /**
     * 启动 MCP Server
     * @param serverId 服务器ID
     * @param command 启动命令 (例如: ["npx", "@modelcontextprotocol/server-filesystem", "/path/to/allowed/files"])
     * @param workingDirectory 工作目录
     * @return 服务器进程信息
     */
    public McpServerProcess startMcpServer(UUID serverId, List<String> command, String workingDirectory) {
        try {
            log.info("Starting MCP Server with ID: {} and command: {}", serverId, command);
            
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            if (workingDirectory != null) {
                processBuilder.directory(new File(workingDirectory));
            }
            
            // 设置环境变量
            processBuilder.environment().put("NODE_ENV", "production");
            
            // 重定向错误流
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            McpServerProcess serverProcess = new McpServerProcess(
                serverId, 
                process, 
                new BufferedWriter(new OutputStreamWriter(process.getOutputStream())),
                new BufferedReader(new InputStreamReader(process.getInputStream()))
            );
            
            runningServers.put(serverId, serverProcess);
            
            // 启动读取线程
            startReaderThread(serverProcess);
            
            // 等待服务器启动
            Thread.sleep(2000);
            
            log.info("MCP Server started successfully with ID: {}", serverId);
            return serverProcess;
            
        } catch (Exception e) {
            log.error("Failed to start MCP Server with ID: {}", serverId, e);
            throw new RuntimeException("Failed to start MCP Server", e);
        }
    }

    /**
     * 停止 MCP Server
     */
    public void stopMcpServer(UUID serverId) {
        McpServerProcess serverProcess = runningServers.remove(serverId);
        if (serverProcess != null) {
            try {
                serverProcess.close();
                log.info("MCP Server stopped: {}", serverId);
            } catch (Exception e) {
                log.error("Error stopping MCP Server: {}", serverId, e);
            }
        }
    }

    /**
     * 获取 MCP 配置
     */
    public JsonNode getMcpConfig(UUID serverId) {
        McpServerProcess serverProcess = runningServers.get(serverId);
        if (serverProcess == null) {
            throw new IllegalArgumentException("MCP Server not found: " + serverId);
        }

        try {
            // 发送初始化请求
            ObjectNode clientInfo = objectMapper.createObjectNode();
            clientInfo.put("name", "MCP Gateway");
            clientInfo.put("version", "1.0.0");
            
            ObjectNode params = objectMapper.createObjectNode();
            params.put("protocolVersion", "2024-11-05");
            params.set("capabilities", objectMapper.createObjectNode());
            params.set("clientInfo", clientInfo);
            
            ObjectNode initRequest = objectMapper.createObjectNode();
            initRequest.put("jsonrpc", "2.0");
            initRequest.put("id", 1);
            initRequest.put("method", "initialize");
            initRequest.set("params", params);

            String response = sendRequest(serverProcess, initRequest.toString());
            return objectMapper.readTree(response);
            
        } catch (Exception e) {
            log.error("Failed to get MCP config for server: {}", serverId, e);
            throw new RuntimeException("Failed to get MCP config", e);
        }
    }

    /**
     * 获取工具列表
     */
    public JsonNode getTools(UUID serverId) {
        McpServerProcess serverProcess = runningServers.get(serverId);
        if (serverProcess == null) {
            throw new IllegalArgumentException("MCP Server not found: " + serverId);
        }

        try {
            ObjectNode toolsRequest = objectMapper.createObjectNode();
            toolsRequest.put("jsonrpc", "2.0");
            toolsRequest.put("id", 2);
            toolsRequest.put("method", "tools/list");
            toolsRequest.set("params", objectMapper.createObjectNode());

            String response = sendRequest(serverProcess, toolsRequest.toString());
            return objectMapper.readTree(response);
            
        } catch (Exception e) {
            log.error("Failed to get tools for server: {}", serverId, e);
            throw new RuntimeException("Failed to get tools", e);
        }
    }

    /**
     * 调用工具
     */
    public JsonNode invokeTool(UUID serverId, String toolName, JsonNode arguments) {
        McpServerProcess serverProcess = runningServers.get(serverId);
        if (serverProcess == null) {
            throw new IllegalArgumentException("MCP Server not found: " + serverId);
        }

        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            params.set("arguments", arguments);
            
            ObjectNode invokeRequest = objectMapper.createObjectNode();
            invokeRequest.put("jsonrpc", "2.0");
            invokeRequest.put("id", System.currentTimeMillis());
            invokeRequest.put("method", "tools/call");
            invokeRequest.set("params", params);

            String response = sendRequest(serverProcess, invokeRequest.toString());
            return objectMapper.readTree(response);
            
        } catch (Exception e) {
            log.error("Failed to invoke tool {} for server: {}", toolName, serverId, e);
            throw new RuntimeException("Failed to invoke tool", e);
        }
    }

    /**
     * 发送请求并等待响应
     */
    private String sendRequest(McpServerProcess serverProcess, String request) throws IOException {
        synchronized (serverProcess) {
            // 发送请求
            serverProcess.getWriter().write(request);
            serverProcess.getWriter().newLine();
            serverProcess.getWriter().flush();
            
            // 读取响应
            return serverProcess.getReader().readLine();
        }
    }

    /**
     * 启动读取线程
     */
    private void startReaderThread(McpServerProcess serverProcess) {
        Thread readerThread = new Thread(() -> {
            try {
                String line;
                while (serverProcess.isRunning() && (line = serverProcess.getReader().readLine()) != null) {
                    log.debug("MCP Server output: {}", line);
                    // 这里可以处理服务器的异步消息
                }
            } catch (IOException e) {
                if (serverProcess.isRunning()) {
                    log.error("Error reading from MCP Server: {}", serverProcess.getServerId(), e);
                }
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * 获取运行中的服务器
     */
    public McpServerProcess getRunningServer(UUID serverId) {
        return runningServers.get(serverId);
    }

    /**
     * 检查服务器是否运行
     */
    public boolean isServerRunning(UUID serverId) {
        McpServerProcess serverProcess = runningServers.get(serverId);
        return serverProcess != null && serverProcess.isRunning();
    }

    /**
     * MCP Server 进程封装类
     */
    public static class McpServerProcess {
        private final UUID serverId;
        private final Process process;
        private final BufferedWriter writer;
        private final BufferedReader reader;
        private volatile boolean running = true;

        public McpServerProcess(UUID serverId, Process process, BufferedWriter writer, BufferedReader reader) {
            this.serverId = serverId;
            this.process = process;
            this.writer = writer;
            this.reader = reader;
        }

        public void close() throws Exception {
            running = false;
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (process != null) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        }

        // Getters
        public UUID getServerId() { return serverId; }
        public Process getProcess() { return process; }
        public BufferedWriter getWriter() { return writer; }
        public BufferedReader getReader() { return reader; }
        public boolean isRunning() { return running && process.isAlive(); }
    }
} 
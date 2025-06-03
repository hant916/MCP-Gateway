package com.mcpgateway.transport;

import com.mcpgateway.domain.Session;

public interface McpTransport {
    void initialize(Session session);
    void sendMessage(String message);
    void handleMessage(String message);
    void close();
} 
package com.mcpgateway.service;

import com.mcpgateway.domain.McpServer;
import com.mcpgateway.dto.session.MessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerConnectionService {
    
    private final WebClient webClient;
    private final Map<UUID, Disposable> upstreamConnections = new ConcurrentHashMap<>();
    
    public void establishUpstreamSseConnection(UUID sessionId, McpServer mcpServer, SseEmitter clientEmitter) {
        String upstreamUrl = buildSseUrl(mcpServer, sessionId);
        log.info("=== Establishing upstream SSE connection ===");
        log.info("Session ID: {}", sessionId);
        log.info("MCP Server: {} (ID: {})", mcpServer.getServiceName(), mcpServer.getId());
        log.info("Upstream URL: {}", upstreamUrl);
        log.info("Transport Type: {}", mcpServer.getTransportType());
        log.info("Session ID Location: {}", mcpServer.getSessionIdLocation());
        log.info("Session ID Param Name: {}", mcpServer.getSessionIdParamName());
        log.info("Auth Type: {}", mcpServer.getAuthType());
        
        try {
            // Create upstream SSE connection with detailed logging
            Flux<ServerSentEvent<String>> eventStream = webClient
                .get()
                .uri(upstreamUrl)
                .headers(headers -> {
                    log.info("=== Request Headers ===");
                    addAuthenticationHeaders(headers, mcpServer, sessionId);
                    // Log all headers (but mask sensitive data)
                    headers.forEach((name, values) -> {
                        if (name.toLowerCase().contains("authorization") || 
                            name.toLowerCase().contains("key") ||
                            name.toLowerCase().contains("token")) {
                            log.info("Header: {} = [MASKED]", name);
                        } else {
                            log.info("Header: {} = {}", name, values);
                        }
                    });
                    log.info("Accept: {}", MediaType.TEXT_EVENT_STREAM);
                    log.info("=== End Headers ===");
                })
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});
            
            // Subscribe to upstream events and forward to client
            Disposable subscription = eventStream
                .doOnNext(event -> forwardEventToClient(clientEmitter, event))
                .doOnError(error -> handleUpstreamError(sessionId, clientEmitter, error))
                .doOnComplete(() -> handleUpstreamComplete(sessionId, clientEmitter))
                .subscribe();
            
            // Store subscription for cleanup
            upstreamConnections.put(sessionId, subscription);
            
        } catch (Exception e) {
            log.error("Failed to establish upstream SSE connection for session: {}", sessionId, e);
            try {
                clientEmitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\":\"Failed to connect to upstream MCP server\"}"));
                clientEmitter.completeWithError(e);
            } catch (Exception sendError) {
                log.error("Failed to send error to client", sendError);
            }
        }
    }
    
    public void sendMessageToUpstream(UUID sessionId, McpServer mcpServer, MessageRequest message) {
        String messageUrl = buildMessageUrl(mcpServer, sessionId);
        log.info("=== Sending message to upstream ===");
        log.info("Session ID: {}", sessionId);
        log.info("MCP Server: {} (ID: {})", mcpServer.getServiceName(), mcpServer.getId());
        log.info("Message URL: {}", messageUrl);
        
        // Log original message format
        if (message.isJsonRpcFormat()) {
            log.info("Message Format: JSON-RPC");
            log.info("JSON-RPC Version: {}", message.getJsonRpc());
            log.info("Method: {}", message.getMethod());
            log.info("Params: {}", message.getParams());
            log.info("ID: {}", message.getId());
        } else {
            log.info("Message Format: Standard");
            log.info("Type: {}", message.getType());
            log.info("Tool: {}", message.getTool());
            log.info("Arguments: {}", message.getArguments());
            log.info("Data: {}", message.getData());
        }
        
        // Prepare message for upstream server
        Object upstreamMessage;
        if (message.isJsonRpcFormat()) {
            // For JSON-RPC format, send only the JSON-RPC fields
            Map<String, Object> jsonRpcMessage = new HashMap<>();
            jsonRpcMessage.put("jsonrpc", message.getJsonRpc());
            jsonRpcMessage.put("method", message.getMethod());
            if (message.getParams() != null) {
                jsonRpcMessage.put("params", message.getParams());
            }
            if (message.getId() != null) {
                jsonRpcMessage.put("id", message.getId());
            }
            upstreamMessage = jsonRpcMessage;
            log.info("Prepared JSON-RPC message for upstream: {}", jsonRpcMessage);
        } else {
            // For standard format, convert to a clean structure
            Map<String, Object> standardMessage = new HashMap<>();
            if (message.getType() != null) {
                standardMessage.put("type", message.getType());
            }
            if (message.getTool() != null) {
                standardMessage.put("tool", message.getTool());
            }
            if (message.getArguments() != null) {
                standardMessage.put("arguments", message.getArguments());
            }
            if (message.getData() != null) {
                standardMessage.put("data", message.getData());
            }
            upstreamMessage = standardMessage;
            log.info("Prepared standard message for upstream: {}", standardMessage);
        }
        
        webClient
            .post()
            .uri(messageUrl)
            .headers(headers -> {
                log.info("=== Message Request Headers ===");
                addAuthenticationHeaders(headers, mcpServer, sessionId);
                headers.add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                // Log all headers (but mask sensitive data)
                headers.forEach((name, values) -> {
                    if (name.toLowerCase().contains("authorization") || 
                        name.toLowerCase().contains("key") ||
                        name.toLowerCase().contains("token")) {
                        log.info("Header: {} = [MASKED]", name);
                    } else {
                        log.info("Header: {} = {}", name, values);
                    }
                });
                log.info("=== End Message Headers ===");
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(upstreamMessage)
            .retrieve()
            .bodyToMono(String.class)
            .doOnNext(response -> log.info("Upstream response for session {}: {}", sessionId, response))
            .doOnError(error -> log.error("Upstream message error for session {}: {}", sessionId, error.getMessage()))
            .subscribe(); // Fire and forget - response comes through SSE
    }
    
    public void closeUpstreamConnection(UUID sessionId) {
        Disposable connection = upstreamConnections.remove(sessionId);
        if (connection != null && !connection.isDisposed()) {
            connection.dispose();
            log.info("Closed upstream connection for session: {}", sessionId);
        }
    }
    
    private String buildSseUrl(McpServer server, UUID sessionId) {
        String baseUrl = server.getServiceEndpoint();
        return addSessionIdToUrl(baseUrl, server, sessionId);
    }
    
    private String buildMessageUrl(McpServer server, UUID sessionId) {
        log.info("=== Building Message URL ===");
        log.info("Service Endpoint: {}", server.getServiceEndpoint());
        log.info("Message Endpoint: {}", server.getMessageEndpoint());
        
        String baseUrl = server.getMessageEndpoint();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            // Fallback to service endpoint with /message path
            String serviceEndpoint = server.getServiceEndpoint();
            if (serviceEndpoint != null) {
                if (serviceEndpoint.endsWith("/sse")) {
                    baseUrl = serviceEndpoint.replace("/sse", "/message");
                } else if (serviceEndpoint.contains("/sse")) {
                    baseUrl = serviceEndpoint.replace("/sse", "/message");
                } else {
                    // If no /sse in the URL, append /message
                    baseUrl = serviceEndpoint.endsWith("/") ? 
                        serviceEndpoint + "message" : 
                        serviceEndpoint + "/message";
                }
            } else {
                log.error("Both messageEndpoint and serviceEndpoint are null!");
                return null;
            }
            log.info("Using fallback message URL: {}", baseUrl);
        }
        
        String finalUrl = addSessionIdToUrl(baseUrl, server, sessionId);
        log.info("Final Message URL: {}", finalUrl);
        log.info("=== End Building Message URL ===");
        return finalUrl;
    }
    
    private String addSessionIdToUrl(String baseUrl, McpServer server, UUID sessionId) {
        log.info("=== Building URL ===");
        log.info("Base URL: {}", baseUrl);
        log.info("Session ID Location: {}", server.getSessionIdLocation());
        log.info("Session ID Param Name: {}", server.getSessionIdParamName());
        log.info("Session ID: {}", sessionId);
        
        if (server.getSessionIdLocation() == null) {
            log.warn("No session ID location configured, defaulting to QUERY_PARAM");
            // Default to query parameter if not specified
            String paramName = "sessionId";
            String separator = baseUrl.contains("?") ? "&" : "?";
            String finalUrl = baseUrl + separator + paramName + "=" + sessionId;
            log.info("Added default query parameter: {}={}", paramName, sessionId);
            log.info("Final URL: {}", finalUrl);
            log.info("=== End URL Building ===");
            return finalUrl;
        }
        
        String finalUrl;
        switch (server.getSessionIdLocation()) {
            case QUERY_PARAM:
                String paramName = server.getSessionIdParamName() != null ? 
                    server.getSessionIdParamName() : "sessionId";
                String separator = baseUrl.contains("?") ? "&" : "?";
                finalUrl = baseUrl + separator + paramName + "=" + sessionId;
                log.info("Added query parameter: {}={}", paramName, sessionId);
                break;
                
            case PATH_PARAM:
                // Replace {sessionId} or {id} placeholders
                finalUrl = baseUrl
                    .replace("{sessionId}", sessionId.toString())
                    .replace("{id}", sessionId.toString());
                log.info("Replaced path parameters with session ID");
                break;
                    
            case HEADER:
                // Headers are handled in addAuthenticationHeaders
                finalUrl = baseUrl;
                log.info("Session ID will be added as header");
                break;
                
            default:
                // Fallback to query parameter
                paramName = "sessionId";
                separator = baseUrl.contains("?") ? "&" : "?";
                finalUrl = baseUrl + separator + paramName + "=" + sessionId;
                log.warn("Unknown session ID location, defaulting to query parameter");
                break;
        }
        
        log.info("Final URL: {}", finalUrl);
        log.info("=== End URL Building ===");
        return finalUrl;
    }
    
    private void addAuthenticationHeaders(HttpHeaders headers, McpServer server, UUID sessionId) {
        log.info("=== Adding Authentication Headers ===");
        log.info("Server Auth Type: {}", server.getAuthType());
        log.info("Session ID Location: {}", server.getSessionIdLocation());
        
        // Add session ID header if configured
        if (server.getSessionIdLocation() == McpServer.SessionIdLocationType.HEADER) {
            String headerName = server.getSessionIdParamName() != null ? 
                server.getSessionIdParamName() : "X-Session-ID";
            headers.add(headerName, sessionId.toString());
            log.info("Added session ID header: {} = {}", headerName, sessionId);
        }
        
        // Add authentication based on server auth type
        if (server.getAuthType() != null) {
            switch (server.getAuthType()) {
                case API_KEY:
                    if (server.getClientId() != null) {
                        headers.add("X-API-Key", server.getClientId());
                        log.info("Added API Key authentication header");
                    } else {
                        log.warn("API_KEY auth type configured but no clientId provided");
                    }
                    break;
                    
                case BASIC_AUTH:
                    if (server.getClientId() != null && server.getClientSecret() != null) {
                        String credentials = server.getClientId() + ":" + server.getClientSecret();
                        String encodedCredentials = java.util.Base64.getEncoder()
                            .encodeToString(credentials.getBytes());
                        headers.add("Authorization", "Basic " + encodedCredentials);
                        log.info("Added Basic authentication header for user: {}", server.getClientId());
                    } else {
                        log.warn("BASIC_AUTH auth type configured but clientId/clientSecret missing");
                    }
                    break;
                    
                case OAUTH2:
                    // OAuth2 tokens would be managed separately and added here
                    // This would require a token management service
                    log.info("OAuth2 authentication not yet implemented");
                    break;
                    
                case NONE:
                    log.info("No authentication configured");
                    break;
                    
                default:
                    log.info("Unknown auth type: {}", server.getAuthType());
                    break;
            }
        } else {
            log.info("No auth type specified");
        }
        
        log.info("=== End Authentication Headers ===");
    }
    
    private void forwardEventToClient(SseEmitter clientEmitter, ServerSentEvent<String> event) {
        try {
            SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event();
            
            if (event.id() != null) {
                eventBuilder.id(event.id());
            }
            if (event.event() != null) {
                eventBuilder.name(event.event());
            }
            if (event.data() != null) {
                eventBuilder.data(event.data());
            }
            if (event.retry() != null) {
                eventBuilder.reconnectTime(event.retry().toMillis());
            }
            
            clientEmitter.send(eventBuilder);
            log.debug("Forwarded event to client: {}", event.event());
            
        } catch (Exception e) {
            log.error("Failed to forward event to client", e);
            clientEmitter.completeWithError(e);
        }
    }
    
    private void handleUpstreamError(UUID sessionId, SseEmitter clientEmitter, Throwable error) {
        log.error("Upstream SSE error for session: {}", sessionId, error);
        try {
            clientEmitter.send(SseEmitter.event()
                .name("error")
                .data("{\"error\":\"Upstream connection error: " + error.getMessage() + "\"}"));
        } catch (Exception e) {
            log.error("Failed to send error event to client", e);
        }
        closeUpstreamConnection(sessionId);
    }
    
    private void handleUpstreamComplete(UUID sessionId, SseEmitter clientEmitter) {
        log.info("Upstream SSE connection completed for session: {}", sessionId);
        try {
            clientEmitter.send(SseEmitter.event()
                .name("complete")
                .data("{\"status\":\"upstream_connection_closed\"}"));
            clientEmitter.complete();
        } catch (Exception e) {
            log.error("Failed to complete client emitter", e);
        }
        closeUpstreamConnection(sessionId);
    }
} 
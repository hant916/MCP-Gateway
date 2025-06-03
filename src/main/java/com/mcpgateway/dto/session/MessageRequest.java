package com.mcpgateway.dto.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class MessageRequest {
    // Current format fields
    private String type;
    private String tool;
    private JsonNode arguments;
    private JsonNode data;
    
    // JSON-RPC format fields
    @JsonProperty("jsonrpc")
    private String jsonRpc;
    private String method;
    private JsonNode params;
    private String id;
    
    // Helper methods to determine format and extract relevant data
    public boolean isJsonRpcFormat() {
        return jsonRpc != null && method != null;
    }
    
    public String getEffectiveType() {
        if (isJsonRpcFormat()) {
            return "jsonrpc_call";
        }
        return type;
    }
    
    public String getEffectiveTool() {
        if (isJsonRpcFormat()) {
            return method;
        }
        return tool;
    }
    
    public JsonNode getEffectiveArguments() {
        if (isJsonRpcFormat()) {
            return params;
        }
        return arguments;
    }
} 
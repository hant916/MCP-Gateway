package com.mcpgateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenApiToMcpConverter {
    private final ObjectMapper objectMapper;

    public List<String> convertToMcpTools(String openApiSpec) {
        try {
            JsonNode root = objectMapper.readTree(openApiSpec);
            List<String> mcpTools = new ArrayList<>();

            JsonNode paths = root.get("paths");
            if (paths != null && paths.isObject()) {
                paths.fields().forEachRemaining(entry -> {
                    String path = entry.getKey();
                    JsonNode pathItem = entry.getValue();

                    pathItem.fields().forEachRemaining(methodEntry -> {
                        String method = methodEntry.getKey();
                        JsonNode operation = methodEntry.getValue();

                        if (isValidHttpMethod(method)) {
                            String mcpTool = convertOperationToMcpTool(path, method, operation);
                            mcpTools.add(mcpTool);
                        }
                    });
                });
            }

            return mcpTools;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse OpenAPI specification", e);
        }
    }

    private String convertOperationToMcpTool(String path, String method, JsonNode operation) {
        ObjectNode mcpTool = objectMapper.createObjectNode();

        // Basic tool information
        mcpTool.put("name", generateToolName(path, method, operation));
        mcpTool.put("description", getDescription(operation));

        // Parameters
        ObjectNode parameters = objectMapper.createObjectNode();
        addPathParameters(parameters, path);
        addOperationParameters(parameters, operation);
        addRequestBody(parameters, operation);

        mcpTool.set("parameters", parameters);

        return mcpTool.toString();
    }

    private String generateToolName(String path, String method, JsonNode operation) {
        JsonNode operationId = operation.get("operationId");
        if (operationId != null && !operationId.isNull()) {
            return operationId.asText();
        }

        // Generate name from path and method
        String pathName = path.replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return method.toLowerCase() + "_" + pathName;
    }

    private String getDescription(JsonNode operation) {
        JsonNode summary = operation.get("summary");
        JsonNode description = operation.get("description");

        if (summary != null && !summary.isNull()) {
            return summary.asText();
        } else if (description != null && !description.isNull()) {
            return description.asText();
        }
        return "";
    }

    private void addPathParameters(ObjectNode parameters, String path) {
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment.startsWith("{") && segment.endsWith("}")) {
                String paramName = segment.substring(1, segment.length() - 1);
                ObjectNode param = objectMapper.createObjectNode();
                param.put("type", "string");
                param.put("description", "Path parameter: " + paramName);
                parameters.set(paramName, param);
            }
        }
    }

    private void addOperationParameters(ObjectNode parameters, JsonNode operation) {
        JsonNode operationParams = operation.get("parameters");
        if (operationParams != null && operationParams.isArray()) {
            for (JsonNode param : operationParams) {
                String name = param.get("name").asText();
                ObjectNode paramDef = objectMapper.createObjectNode();
                paramDef.put("type", getParameterType(param));
                
                JsonNode description = param.get("description");
                if (description != null && !description.isNull()) {
                    paramDef.put("description", description.asText());
                }

                JsonNode required = param.get("required");
                if (required != null && !required.isNull()) {
                    paramDef.put("required", required.asBoolean());
                }

                parameters.set(name, paramDef);
            }
        }
    }

    private void addRequestBody(ObjectNode parameters, JsonNode operation) {
        JsonNode requestBody = operation.get("requestBody");
        if (requestBody != null && !requestBody.isNull()) {
            JsonNode content = requestBody.get("content");
            if (content != null && content.has("application/json")) {
                JsonNode schema = content.get("application/json").get("schema");
                if (schema != null) {
                    parameters.set("body", convertSchema(schema));
                }
            }
        }
    }

    private ObjectNode convertSchema(JsonNode schema) {
        ObjectNode result = objectMapper.createObjectNode();
        
        String type = schema.get("type").asText();
        result.put("type", type);

        if ("object".equals(type)) {
            JsonNode properties = schema.get("properties");
            if (properties != null) {
                ObjectNode propertiesNode = objectMapper.createObjectNode();
                properties.fields().forEachRemaining(entry -> 
                    propertiesNode.set(entry.getKey(), convertSchema(entry.getValue()))
                );
                result.set("properties", propertiesNode);
            }
        } else if ("array".equals(type)) {
            JsonNode items = schema.get("items");
            if (items != null) {
                result.set("items", convertSchema(items));
            }
        }

        return result;
    }

    private String getParameterType(JsonNode param) {
        JsonNode schema = param.get("schema");
        if (schema != null && schema.has("type")) {
            return schema.get("type").asText();
        }
        return "string";
    }

    private boolean isValidHttpMethod(String method) {
        return method.equalsIgnoreCase("get") ||
               method.equalsIgnoreCase("post") ||
               method.equalsIgnoreCase("put") ||
               method.equalsIgnoreCase("delete") ||
               method.equalsIgnoreCase("patch");
    }
} 
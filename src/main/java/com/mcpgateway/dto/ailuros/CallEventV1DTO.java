package com.mcpgateway.dto.ailuros;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gateway -> AILUROS protocol payload for call event capture.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallEventV1DTO {

    @JsonProperty("event_version")
    private String eventVersion;

    private Identity identity;
    private Dimensions dims;
    private Timing timing;
    private Usage usage;
    private Outcome outcome;
    private Privacy privacy;

    @Builder.Default
    private List<String> flags = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Identity {
        @JsonProperty("trace_id")
        private String traceId;

        @JsonProperty("span_id")
        private String spanId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dimensions {
        @JsonProperty("app_id")
        private String appId;

        private String env;
        private String route;
        private String provider;
        private String model;

        @JsonProperty("prompt_version")
        private String promptVersion;

        private Boolean streaming;

        @JsonProperty("user_tier")
        private String userTier;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Timing {
        @JsonProperty("request_ts")
        private Instant requestTs;

        @JsonProperty("response_ts")
        private Instant responseTs;

        @JsonProperty("latency_ms")
        private Integer latencyMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        @JsonProperty("input_tokens")
        private Integer inputTokens;

        @JsonProperty("output_tokens")
        private Integer outputTokens;

        @JsonProperty("cost_usd")
        private BigDecimal costUsd;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Outcome {
        private String status;

        @JsonProperty("error_type")
        private String errorType;

        @JsonProperty("http_status")
        private Integer httpStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Privacy {
        @JsonProperty("prompt_hash")
        private String promptHash;
    }
}

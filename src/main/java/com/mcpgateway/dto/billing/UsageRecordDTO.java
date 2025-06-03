package com.mcpgateway.dto.billing;

import com.mcpgateway.domain.UsageRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageRecordDTO {
    private UUID id;
    private UUID sessionId;
    private UUID userId;
    private ZonedDateTime timestamp;
    private String apiEndpoint;
    private String httpMethod;
    private Integer statusCode;
    private Long requestSize;
    private Long responseSize;
    private Integer processingMs;
    private BigDecimal costAmount;
    private String messageType;
    private String errorMessage;
    private String clientIp;
    private String userAgent;
    private UsageRecord.BillingStatus billingStatus;

    // 静态工厂方法 - 从域对象转换为DTO
    public static UsageRecordDTO fromEntity(UsageRecord usageRecord) {
        return UsageRecordDTO.builder()
                .id(usageRecord.getId())
                .sessionId(usageRecord.getSessionId())
                .userId(usageRecord.getUserId())
                .timestamp(usageRecord.getTimestamp() != null ? 
                    usageRecord.getTimestamp().toInstant().atZone(java.time.ZoneId.systemDefault()) : null)
                .apiEndpoint(usageRecord.getApiEndpoint())
                .httpMethod(usageRecord.getHttpMethod())
                .statusCode(usageRecord.getStatusCode())
                .requestSize(usageRecord.getRequestSize())
                .responseSize(usageRecord.getResponseSize())
                .processingMs(usageRecord.getProcessingMs())
                .costAmount(usageRecord.getCostAmount())
                .messageType(usageRecord.getMessageType())
                .errorMessage(usageRecord.getErrorMessage())
                .clientIp(usageRecord.getClientIp())
                .userAgent(usageRecord.getUserAgent())
                .billingStatus(usageRecord.getBillingStatus())
                .build();
    }
} 
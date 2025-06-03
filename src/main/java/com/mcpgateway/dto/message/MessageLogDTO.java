package com.mcpgateway.dto.message;

import com.mcpgateway.domain.MessageLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageLogDTO {
    private UUID id;
    private UUID sessionId;
    private MessageLog.MessageType messageType;
    private String content;
    private ZonedDateTime createdAt;
} 
package com.mcpgateway.service;

import com.mcpgateway.domain.ToolCallRecord;
import com.mcpgateway.domain.McpTool;
import com.mcpgateway.domain.User;
import com.mcpgateway.repository.ToolCallRecordRepository;
import com.mcpgateway.repository.McpToolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ToolCallService {
    private final ToolCallRecordRepository callRecordRepository;
    private final McpToolRepository toolRepository;
    private final BillingService billingService;

    public ToolCallRecord startToolCall(UUID toolId, User user, String requestPayload) {
        McpTool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found"));

        ToolCallRecord record = new ToolCallRecord();
        record.setRequestId(generateRequestId());
        record.setTool(tool);
        record.setUser(user);
        record.setRequestPayload(requestPayload);
        record.setStatus(ToolCallRecord.CallStatus.STARTED);

        // 记录使用并计费
        billingService.recordUsage(tool.getId(), user.getId(), 1L);

        return callRecordRepository.save(record);
    }

    public ToolCallRecord completeToolCall(String requestId, String responsePayload, Long tokensUsed) {
        ToolCallRecord record = callRecordRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Record not found"));

        record.setEndTime(LocalDateTime.now());
        record.setDurationMs(ChronoUnit.MILLIS.between(record.getStartTime(), record.getEndTime()));
        record.setResponsePayload(responsePayload);
        record.setTokensUsed(tokensUsed);
        record.setStatus(ToolCallRecord.CallStatus.COMPLETED);

        return callRecordRepository.save(record);
    }

    public ToolCallRecord recordError(String requestId, String errorMessage) {
        ToolCallRecord record = callRecordRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Record not found"));

        record.setEndTime(LocalDateTime.now());
        record.setDurationMs(ChronoUnit.MILLIS.between(record.getStartTime(), record.getEndTime()));
        record.setErrorMessage(errorMessage);
        record.setStatus(ToolCallRecord.CallStatus.FAILED);

        return callRecordRepository.save(record);
    }

    private String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }
} 
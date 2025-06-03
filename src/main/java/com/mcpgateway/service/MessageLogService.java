package com.mcpgateway.service;

import com.mcpgateway.domain.MessageLog;
import com.mcpgateway.domain.Session;
import com.mcpgateway.dto.message.MessageLogDTO;
import com.mcpgateway.repository.MessageLogRepository;
import com.mcpgateway.repository.SessionRepository;
import com.mcpgateway.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageLogService {
    private final MessageLogRepository messageLogRepository;
    private final SessionRepository sessionRepository;

    @Transactional
    public MessageLogDTO logMessage(UUID sessionId, MessageLog.MessageType messageType, String content) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        MessageLog messageLog = new MessageLog();
        messageLog.setSession(session);
        messageLog.setMessageType(messageType);
        messageLog.setContent(content);

        MessageLog savedLog = messageLogRepository.save(messageLog);
        return mapToDTO(savedLog);
    }

    @Transactional(readOnly = true)
    public List<MessageLogDTO> getSessionMessages(UUID sessionId) {
        return messageLogRepository.findBySessionId(sessionId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MessageLogDTO> getSessionMessagesByTimeRange(UUID sessionId, ZonedDateTime start, ZonedDateTime end) {
        return messageLogRepository.findBySessionIdAndCreatedAtBetween(
                sessionId, 
                TimeUtil.toTimestamp(start), 
                TimeUtil.toTimestamp(end)
            )
            .stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MessageLogDTO> getSessionMessagesByType(UUID sessionId, MessageLog.MessageType messageType) {
        return messageLogRepository.findBySessionIdAndMessageType(sessionId, messageType)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private MessageLogDTO mapToDTO(MessageLog messageLog) {
        return MessageLogDTO.builder()
                .id(messageLog.getId())
                .sessionId(messageLog.getSession().getId())
                .messageType(messageLog.getMessageType())
                .content(messageLog.getContent())
                .createdAt(TimeUtil.toZonedDateTime(messageLog.getCreatedAt()))
                .build();
    }
} 
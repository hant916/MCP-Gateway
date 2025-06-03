package com.mcpgateway.repository;

import com.mcpgateway.domain.MessageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public interface MessageLogRepository extends JpaRepository<MessageLog, UUID> {
    List<MessageLog> findBySessionId(UUID sessionId);
    List<MessageLog> findBySessionIdAndCreatedAtBetween(UUID sessionId, Timestamp start, Timestamp end);
    List<MessageLog> findBySessionIdAndMessageType(UUID sessionId, MessageLog.MessageType messageType);
} 
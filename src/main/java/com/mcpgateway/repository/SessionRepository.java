package com.mcpgateway.repository;

import com.mcpgateway.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findBySessionToken(String sessionToken);
    List<Session> findByUserId(UUID userId);
    
    @Query("SELECT s FROM Session s WHERE s.expiresAt < :now")
    List<Session> findExpiredSessions(Timestamp now);
    
    void deleteBySessionToken(String sessionToken);
} 
package com.mcpgateway.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@Entity
@Table(name = "sessions")
public class Session {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mcp_server_id", nullable = false)
    private McpServer mcpServer;

    @Column(name = "session_token", nullable = false, unique = true)
    private String sessionToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", nullable = false)
    private TransportType transportType;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "last_active_at")
    private Timestamp lastActiveAt;

    @Column(name = "expires_at", nullable = false)
    private Timestamp expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    public enum TransportType {
        STDIO,
        SSE,
        WEBSOCKET,
        STREAMABLE_HTTP
    }

    public enum SessionStatus {
        CREATED,
        CONNECTED,
        ACTIVE,
        EXPIRED,
        CLOSED
    }

    @PrePersist
    protected void onCreate() {
        createdAt = new Timestamp(System.currentTimeMillis());
        lastActiveAt = new Timestamp(System.currentTimeMillis());
        status = SessionStatus.CREATED;
    }

    public void updateLastActiveAt() {
        this.lastActiveAt = new Timestamp(System.currentTimeMillis());
    }

    public boolean isExpired() {
        return new Timestamp(System.currentTimeMillis()).after(expiresAt);
    }
} 
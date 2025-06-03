package com.mcpgateway.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@Entity
@Table(name = "mcp_servers")
public class McpServer {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(nullable = false)
    private String serviceName;

    @Column(length = 1000)
    private String description;

    private String iconUrl;

    private String repositoryUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransportType transportType;

    @Column(nullable = false)
    private String serviceEndpoint;

    private String messageEndpoint;

    @Enumerated(EnumType.STRING)
    private SessionIdLocationType sessionIdLocation;

    private String sessionIdParamName;

    @Enumerated(EnumType.STRING)
    private AuthType authType;

    private String clientId;

    @Column(length = 1000)
    private String clientSecret;

    private String authorizationUrl;

    private String tokenUrl;

    @ElementCollection
    @CollectionTable(name = "mcp_server_scopes")
    private Set<String> scopes = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServerStatus status;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public enum TransportType {
        SSE,
        WEBSOCKET,
        STREAMABLE_HTTP,
        STDIO
    }

    public enum SessionIdLocationType {
        QUERY_PARAM,
        HEADER,
        PATH_PARAM
    }

    public enum AuthType {
        OAUTH2,
        API_KEY,
        BASIC_AUTH,
        NONE
    }

    public enum ServerStatus {
        REGISTERED,
        ACTIVE,
        INACTIVE,
        ERROR
    }

    @PrePersist
    protected void onCreate() {
        createdAt = new Timestamp(System.currentTimeMillis());
        updatedAt = new Timestamp(System.currentTimeMillis());
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Timestamp(System.currentTimeMillis());
    }
} 
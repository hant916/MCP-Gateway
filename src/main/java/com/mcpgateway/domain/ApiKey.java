package com.mcpgateway.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "api_keys")
@EqualsAndHashCode(of = "id")
public class ApiKey {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "api_key", unique = true, nullable = false)
    private String keyValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (keyValue == null) {
            // 生成简单的 API KEY，格式: uk_{uuid}
            keyValue = "uk_" + UUID.randomUUID().toString().replace("-", "");
        }
    }
} 
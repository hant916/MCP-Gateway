package com.mcpgateway.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "mcp_tools")
@EqualsAndHashCode(of = "id")
public class McpTool {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "builder_id")
    private User builder;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_specification_id")
    private ApiSpecification apiSpecification;
    
    @OneToMany(mappedBy = "tool", cascade = CascadeType.ALL)
    private List<ToolSubscription> subscriptions;

    @OneToMany(mappedBy = "tool", cascade = CascadeType.ALL)
    private List<ToolReview> reviews;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ToolCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ToolStatus status;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal price;
    
    @Column(name = "pricing_model")
    @Enumerated(EnumType.STRING)
    private PricingModel pricingModel;
    
    @Column(name = "usage_quota")
    private Integer usageQuota;
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "description")
    private String description;

    @Column(name = "long_description", length = 5000)
    private String longDescription;

    @Column(name = "icon_url")
    private String iconUrl;

    @Column(name = "tags")
    private String tags; // Comma-separated tags

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "review_count")
    private Integer reviewCount = 0;

    @Column(name = "subscriber_count")
    private Integer subscriberCount = 0;

    @Column(name = "version")
    private String version;
    
    @Column(name = "parameters")
    private String parameters;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    public enum ToolStatus {
        DRAFT, PUBLISHED, DEPRECATED
    }
    
    public enum PricingModel {
        MONTHLY,
        PAY_AS_YOU_GO,
        FREE_TIER
    }
    
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
} 
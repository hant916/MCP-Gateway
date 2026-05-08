package com.mcpgateway.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User implements UserDetails {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role = UserRole.USER;

    @Column(name = "subscription_tier")
    private String subscriptionTierName;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @Column(name = "last_login_at")
    private Timestamp lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @PrePersist
    protected void onCreate() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Timestamp(System.currentTimeMillis());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(isActive);
    }

    public enum UserRole {
        USER, ADMIN, BUILDER, MODERATOR
    }

    /**
     * Backward-compatible alias used by older tests/code.
     */
    public enum Role {
        USER, ADMIN, BUILDER, MODERATOR
    }

    public enum SubscriptionTier {
        FREE,
        BASIC,
        PRO,
        ENTERPRISE
    }

    public SubscriptionTier getSubscriptionTier() {
        if (subscriptionTierName == null || subscriptionTierName.isBlank()) {
            return null;
        }
        try {
            return SubscriptionTier.valueOf(subscriptionTierName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void setSubscriptionTier(SubscriptionTier subscriptionTier) {
        this.subscriptionTierName = subscriptionTier != null ? subscriptionTier.name() : null;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public void setRole(Role role) {
        this.role = role != null ? UserRole.valueOf(role.name()) : null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }
} 

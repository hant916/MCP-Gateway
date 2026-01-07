package com.mcpgateway.event;

import com.mcpgateway.domain.User;
import lombok.Getter;

import java.util.UUID;

/**
 * Event published when a new user registers
 *
 * Listeners can:
 * - Send welcome email
 * - Create default subscriptions
 * - Initialize user quota
 * - Trigger onboarding workflow
 */
@Getter
public class UserRegisteredEvent extends DomainEvent {

    private final UUID userId;
    private final String username;
    private final String email;
    private final User.UserRole role;

    public UserRegisteredEvent(User user) {
        super();
        this.userId = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.role = user.getRole();
    }

    @Override
    public UUID getAggregateId() {
        return userId;
    }

    @Override
    public String getEventData() {
        return String.format("User %s (%s) registered with email %s, role: %s",
                userId, username, email, role);
    }
}

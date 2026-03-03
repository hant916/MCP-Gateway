package com.mcpgateway.event;

import com.mcpgateway.domain.ToolSubscription;
import lombok.Getter;

import java.util.UUID;

/**
 * Event published when a user subscribes to a tool
 *
 * Listeners can:
 * - Send confirmation email
 * - Update subscription analytics
 * - Grant tool access
 * - Trigger webhook notifications
 */
@Getter
public class ToolSubscribedEvent extends DomainEvent {

    private final UUID subscriptionId;
    private final UUID userId;
    private final UUID toolId;
    private final String toolName;
    private final ToolSubscription.SubscriptionStatus status;

    public ToolSubscribedEvent(ToolSubscription subscription) {
        super();
        this.subscriptionId = subscription.getId();
        this.userId = subscription.getUser().getId();
        this.toolId = subscription.getTool().getId();
        this.toolName = subscription.getTool().getName();
        this.status = subscription.getStatus();
    }

    @Override
    public UUID getAggregateId() {
        return subscriptionId;
    }

    @Override
    public String getEventData() {
        return String.format("User %s subscribed to tool %s (%s), status: %s",
                userId, toolName, toolId, status);
    }
}

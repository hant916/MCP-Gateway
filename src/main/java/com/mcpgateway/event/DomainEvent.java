package com.mcpgateway.event;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for all domain events
 *
 * Domain events represent significant occurrences in the business domain.
 * They enable loose coupling between components and facilitate:
 * - Asynchronous processing
 * - Event sourcing
 * - Integration with external systems
 * - Audit logging
 */
@Getter
public abstract class DomainEvent {

    private final UUID eventId;
    private final LocalDateTime occurredAt;
    private final String eventType;

    protected DomainEvent() {
        this.eventId = UUID.randomUUID();
        this.occurredAt = LocalDateTime.now();
        this.eventType = this.getClass().getSimpleName();
    }

    /**
     * Get the aggregate ID that this event relates to
     */
    public abstract UUID getAggregateId();

    /**
     * Get the event data as a string (for logging/storage)
     */
    public abstract String getEventData();
}

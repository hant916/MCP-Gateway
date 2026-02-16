package com.mcpgateway.dto.session;

import com.mcpgateway.domain.Session;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateSessionRequest {
    private UUID serverId;

    @NotNull(message = "Transport type is required")
    private Session.TransportType transportType;

    public void setTransportType(Session.TransportType transportType) {
        this.transportType = transportType;
    }

    public void setTransportType(String transportType) {
        if (transportType == null || transportType.isBlank()) {
            this.transportType = null;
            return;
        }

        String normalized = transportType.trim().toUpperCase().replace('-', '_');
        this.transportType = Session.TransportType.valueOf(normalized);
    }
} 

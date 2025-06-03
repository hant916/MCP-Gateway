package com.mcpgateway.dto.session;

import com.mcpgateway.domain.Session;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateSessionRequest {
    @NotNull(message = "Transport type is required")
    private Session.TransportType transportType;
} 
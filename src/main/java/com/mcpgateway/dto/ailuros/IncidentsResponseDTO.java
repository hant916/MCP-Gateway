package com.mcpgateway.dto.ailuros;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentsResponseDTO {
    private String appId;
    private String env;
    private String range;
    private DashboardWindowDTO window;

    @Builder.Default
    private List<IncidentDTO> incidents = new ArrayList<>();
}

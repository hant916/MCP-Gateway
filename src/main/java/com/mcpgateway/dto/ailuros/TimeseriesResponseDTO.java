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
public class TimeseriesResponseDTO {
    private String appId;
    private String env;
    private String range;
    private String metric;
    private DashboardWindowDTO window;

    @Builder.Default
    private List<TimeseriesPointDTO> points = new ArrayList<>();
}

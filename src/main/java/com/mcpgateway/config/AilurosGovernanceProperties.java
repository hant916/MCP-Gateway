package com.mcpgateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "ailuros")
public class AilurosGovernanceProperties {

    private final Budget budget = new Budget();
    private final Webhook webhook = new Webhook();
    private final Regression regression = new Regression();

    @Data
    public static class Budget {
        private boolean enabled = true;
        private String evalCron = "0 0 * * * *";
        private int forecastDays = 7;
    }

    @Data
    public static class Webhook {
        private boolean enabled = false;
        private String url;
    }

    @Data
    public static class Regression {
        private boolean enabled = true;
        private int maxCases = 30;
        private boolean judgeEnabled = false;
        private int timeoutMs = 30000;
        private int detectorWindowHours = 24;
    }
}

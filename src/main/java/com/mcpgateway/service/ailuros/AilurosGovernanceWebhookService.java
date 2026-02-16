package com.mcpgateway.service.ailuros;

import com.mcpgateway.config.AilurosGovernanceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AilurosGovernanceWebhookService {

    private final WebClient webClient;
    private final AilurosGovernanceProperties properties;

    public void notifyEvent(String eventType, Map<String, Object> payload) {
        if (!properties.getWebhook().isEnabled()) {
            return;
        }

        String webhookUrl = properties.getWebhook().getUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event", eventType);
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("data", payload != null ? payload : Map.of());

        try {
            webClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(envelope)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(3))
                .block();
        } catch (Exception ex) {
            log.warn("Governance webhook delivery failed event={} reason={}", eventType, ex.getMessage());
        }
    }
}

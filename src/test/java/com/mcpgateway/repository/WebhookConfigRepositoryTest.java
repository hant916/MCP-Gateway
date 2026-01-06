package com.mcpgateway.repository;

import com.mcpgateway.domain.WebhookConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WebhookConfigRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private WebhookConfigRepository webhookConfigRepository;

    private UUID userId;
    private WebhookConfig activeWebhook;
    private WebhookConfig inactiveWebhook;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        activeWebhook = WebhookConfig.builder()
                .userId(userId)
                .url("https://example.com/webhook1")
                .secret("secret1")
                .events("payment.success,subscription.created")
                .status(WebhookConfig.WebhookStatus.ACTIVE)
                .isActive(true)
                .retryCount(3)
                .timeoutSeconds(30)
                .successCount(0)
                .failureCount(0)
                .build();

        inactiveWebhook = WebhookConfig.builder()
                .userId(userId)
                .url("https://example.com/webhook2")
                .secret("secret2")
                .events("payment.failure")
                .status(WebhookConfig.WebhookStatus.INACTIVE)
                .isActive(false)
                .retryCount(3)
                .timeoutSeconds(30)
                .successCount(0)
                .failureCount(0)
                .build();

        entityManager.persist(activeWebhook);
        entityManager.persist(inactiveWebhook);
        entityManager.flush();
    }

    @Test
    void findByUserIdAndIsActiveTrue_ShouldReturnOnlyActiveWebhooks() {
        // Act
        List<WebhookConfig> result = webhookConfigRepository.findByUserIdAndIsActiveTrue(userId);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsActive()).isTrue();
        assertThat(result.get(0).getUrl()).isEqualTo("https://example.com/webhook1");
    }

    @Test
    void findActiveWebhooksByEvent_ShouldReturnMatchingWebhooks() {
        // Act
        List<WebhookConfig> result = webhookConfigRepository.findActiveWebhooksByEvent("payment.success");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEvents()).contains("payment.success");
    }

    @Test
    void findActiveWebhooksByEvent_WithNonMatchingEvent_ShouldReturnEmpty() {
        // Act
        List<WebhookConfig> result = webhookConfigRepository.findActiveWebhooksByEvent("nonexistent.event");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void findByUserId_ShouldReturnAllUserWebhooks() {
        // Act
        List<WebhookConfig> result = webhookConfigRepository.findByUserId(userId);

        // Assert
        assertThat(result).hasSize(2);
    }

    @Test
    void countByUserIdAndIsActiveTrue_ShouldReturnCorrectCount() {
        // Act
        Integer count = webhookConfigRepository.countByUserIdAndIsActiveTrue(userId);

        // Assert
        assertThat(count).isEqualTo(1);
    }
}

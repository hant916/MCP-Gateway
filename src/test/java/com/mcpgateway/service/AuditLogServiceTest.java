package com.mcpgateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.domain.AuditLog;
import com.mcpgateway.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditLogService
 *
 * Tests audit logging functionality for compliance and security
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogService auditLogService;

    private ObjectMapper objectMapper;

    private UUID testUserId;
    private String testUsername;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditLogService = new AuditLogService(auditLogRepository, objectMapper);

        testUserId = UUID.randomUUID();
        testUsername = "test-user";
    }

    @Test
    void logAsync_WithBasicInfo_SavesAuditLog() {
        // Given
        String action = "CREATE_PAYMENT";
        String resourceType = "Payment";
        String resourceId = UUID.randomUUID().toString();
        AuditLog.Status status = AuditLog.Status.SUCCESS;
        Map<String, Object> metadata = Map.of("amount", "100.00", "currency", "USD");

        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(new AuditLog());

        // When
        auditLogService.logAsync(testUserId, testUsername, action, resourceType, resourceId, status, metadata);

        // Then
        verify(auditLogRepository, timeout(1000)).save(auditLogCaptor.capture());

        AuditLog captured = auditLogCaptor.getValue();
        assertThat(captured).isNotNull();
        assertThat(captured.getUserId()).isEqualTo(testUserId);
        assertThat(captured.getUsername()).isEqualTo(testUsername);
        assertThat(captured.getAction()).isEqualTo(action);
        assertThat(captured.getResourceType()).isEqualTo(resourceType);
        assertThat(captured.getResourceId()).isEqualTo(resourceId);
        assertThat(captured.getStatus()).isEqualTo(status);
        assertThat(captured.getMetadata()).contains("amount");
        assertThat(captured.getMetadata()).contains("USD");
    }

    @Test
    void logAsync_WithNullMetadata_SavesAuditLogWithoutMetadata() {
        // Given
        String action = "DELETE_USER";
        String resourceType = "User";
        String resourceId = UUID.randomUUID().toString();
        AuditLog.Status status = AuditLog.Status.SUCCESS;

        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(new AuditLog());

        // When
        auditLogService.logAsync(testUserId, testUsername, action, resourceType, resourceId, status, null);

        // Then
        verify(auditLogRepository, timeout(1000)).save(auditLogCaptor.capture());

        AuditLog captured = auditLogCaptor.getValue();
        assertThat(captured).isNotNull();
        assertThat(captured.getMetadata()).isNull();
    }

    @Test
    void logAsync_WithEmptyMetadata_SavesAuditLogWithoutMetadata() {
        // Given
        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(new AuditLog());

        // When
        auditLogService.logAsync(
                testUserId, testUsername, "ACTION", "Resource", "id123",
                AuditLog.Status.SUCCESS, new HashMap<>()
        );

        // Then
        verify(auditLogRepository, timeout(1000)).save(auditLogCaptor.capture());

        AuditLog captured = auditLogCaptor.getValue();
        assertThat(captured.getMetadata()).isNull();
    }

    @Test
    void logWithHttpContext_SavesCompleteAuditLog() {
        // Given
        String action = "UPDATE_USER_PROFILE";
        String resourceType = "User";
        String resourceId = UUID.randomUUID().toString();
        String httpMethod = "PUT";
        String endpoint = "/api/users/" + resourceId;
        String ipAddress = "192.168.1.100";
        String userAgent = "Mozilla/5.0";
        AuditLog.Status status = AuditLog.Status.SUCCESS;
        Long executionTime = 150L;
        Map<String, Object> metadata = Map.of("field", "email", "oldValue", "old@test.com", "newValue", "new@test.com");

        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(new AuditLog());

        // When
        auditLogService.logWithHttpContext(
                testUserId, testUsername, action, resourceType, resourceId,
                httpMethod, endpoint, ipAddress, userAgent, status, null,
                executionTime, metadata
        );

        // Then
        verify(auditLogRepository, timeout(1000)).save(auditLogCaptor.capture());

        AuditLog captured = auditLogCaptor.getValue();
        assertThat(captured).isNotNull();
        assertThat(captured.getUserId()).isEqualTo(testUserId);
        assertThat(captured.getHttpMethod()).isEqualTo(httpMethod);
        assertThat(captured.getEndpoint()).isEqualTo(endpoint);
        assertThat(captured.getIpAddress()).isEqualTo(ipAddress);
        assertThat(captured.getUserAgent()).isEqualTo(userAgent);
        assertThat(captured.getExecutionTimeMs()).isEqualTo(executionTime);
        assertThat(captured.getMetadata()).contains("email");
    }

    @Test
    void logWithHttpContext_WithError_SavesErrorMessage() {
        // Given
        String errorMessage = "Validation failed: Email already exists";
        AuditLog.Status status = AuditLog.Status.FAILURE;

        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(new AuditLog());

        // When
        auditLogService.logWithHttpContext(
                testUserId, testUsername, "CREATE_USER", "User", "new-user",
                "POST", "/api/users", "192.168.1.100", "curl/7.0", status,
                errorMessage, 50L, null
        );

        // Then
        verify(auditLogRepository, timeout(1000)).save(auditLogCaptor.capture());

        AuditLog captured = auditLogCaptor.getValue();
        assertThat(captured.getStatus()).isEqualTo(AuditLog.Status.FAILURE);
        assertThat(captured.getErrorMessage()).isEqualTo(errorMessage);
    }

    @Test
    void logPaymentOperation_SavesPaymentAuditLog() {
        // Given
        String action = "PAYMENT_CREATED";
        String paymentId = UUID.randomUUID().toString();
        String amount = "250.00";
        String currency = "EUR";

        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(new AuditLog());

        // When
        auditLogService.logPaymentOperation(
                testUserId, testUsername, action, paymentId, amount, currency, AuditLog.Status.SUCCESS
        );

        // Then
        verify(auditLogRepository, timeout(1000)).save(auditLogCaptor.capture());

        AuditLog captured = auditLogCaptor.getValue();
        assertThat(captured.getAction()).isEqualTo(action);
        assertThat(captured.getResourceType()).isEqualTo("Payment");
        assertThat(captured.getResourceId()).isEqualTo(paymentId);
        assertThat(captured.getMetadata()).contains(amount);
        assertThat(captured.getMetadata()).contains(currency);
    }

    @Test
    void logUserManagement_SavesUserManagementAuditLog() {
        // Given
        UUID adminId = UUID.randomUUID();
        String adminUsername = "admin-user";
        String action = "USER_ROLE_CHANGED";
        UUID targetUserId = UUID.randomUUID();
        Map<String, Object> changes = Map.of("oldRole", "USER", "newRole", "ADMIN");

        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(new AuditLog());

        // When
        auditLogService.logUserManagement(
                adminId, adminUsername, action, targetUserId, AuditLog.Status.SUCCESS, changes
        );

        // Then
        verify(auditLogRepository, timeout(1000)).save(auditLogCaptor.capture());

        AuditLog captured = auditLogCaptor.getValue();
        assertThat(captured.getUserId()).isEqualTo(adminId);
        assertThat(captured.getUsername()).isEqualTo(adminUsername);
        assertThat(captured.getAction()).isEqualTo(action);
        assertThat(captured.getResourceType()).isEqualTo("User");
        assertThat(captured.getResourceId()).isEqualTo(targetUserId.toString());
        assertThat(captured.getMetadata()).contains("oldRole");
        assertThat(captured.getMetadata()).contains("ADMIN");
    }

    @Test
    void logApiKeyOperation_SavesApiKeyAuditLog() {
        // Given
        String action = "API_KEY_CREATED";
        String apiKeyId = UUID.randomUUID().toString();

        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(new AuditLog());

        // When
        auditLogService.logApiKeyOperation(testUserId, testUsername, action, apiKeyId, AuditLog.Status.SUCCESS);

        // Then
        verify(auditLogRepository, timeout(1000)).save(auditLogCaptor.capture());

        AuditLog captured = auditLogCaptor.getValue();
        assertThat(captured.getAction()).isEqualTo(action);
        assertThat(captured.getResourceType()).isEqualTo("ApiKey");
        assertThat(captured.getResourceId()).isEqualTo(apiKeyId);
    }

    @Test
    void getAuditLogs_ReturnsPagedResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        List<AuditLog> logs = Arrays.asList(
                createTestAuditLog("ACTION1"),
                createTestAuditLog("ACTION2")
        );
        Page<AuditLog> expectedPage = new PageImpl<>(logs, pageable, logs.size());

        when(auditLogRepository.findAll(pageable)).thenReturn(expectedPage);

        // When
        Page<AuditLog> result = auditLogService.getAuditLogs(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);

        verify(auditLogRepository).findAll(pageable);
    }

    @Test
    void getAuditLogsByUser_ReturnsUserSpecificLogs() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        List<AuditLog> logs = Arrays.asList(createTestAuditLog("USER_LOGIN"));
        Page<AuditLog> expectedPage = new PageImpl<>(logs, pageable, logs.size());

        when(auditLogRepository.findByUserIdOrderByTimestampDesc(testUserId, pageable))
                .thenReturn(expectedPage);

        // When
        Page<AuditLog> result = auditLogService.getAuditLogsByUser(testUserId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);

        verify(auditLogRepository).findByUserIdOrderByTimestampDesc(testUserId, pageable);
    }

    @Test
    void getAuditLogsByAction_ReturnsActionSpecificLogs() {
        // Given
        String action = "PAYMENT_CREATED";
        Pageable pageable = PageRequest.of(0, 10);
        List<AuditLog> logs = Arrays.asList(createTestAuditLog(action));
        Page<AuditLog> expectedPage = new PageImpl<>(logs, pageable, logs.size());

        when(auditLogRepository.findByActionOrderByTimestampDesc(action, pageable))
                .thenReturn(expectedPage);

        // When
        Page<AuditLog> result = auditLogService.getAuditLogsByAction(action, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);

        verify(auditLogRepository).findByActionOrderByTimestampDesc(action, pageable);
    }

    @Test
    void getAuditLogsByResource_ReturnsResourceSpecificLogs() {
        // Given
        String resourceType = "Payment";
        String resourceId = UUID.randomUUID().toString();
        Pageable pageable = PageRequest.of(0, 10);
        List<AuditLog> logs = Arrays.asList(createTestAuditLog("PAYMENT_UPDATED"));
        Page<AuditLog> expectedPage = new PageImpl<>(logs, pageable, logs.size());

        when(auditLogRepository.findByResourceTypeAndResourceIdOrderByTimestampDesc(
                resourceType, resourceId, pageable
        )).thenReturn(expectedPage);

        // When
        Page<AuditLog> result = auditLogService.getAuditLogsByResource(resourceType, resourceId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);

        verify(auditLogRepository).findByResourceTypeAndResourceIdOrderByTimestampDesc(
                resourceType, resourceId, pageable
        );
    }

    @Test
    void getSecurityEvents_ReturnsSecurityRelatedLogs() {
        // Given
        int hoursBack = 24;
        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack);
        List<AuditLog> securityLogs = Arrays.asList(
                createTestAuditLog("LOGIN_FAILED"),
                createTestAuditLog("UNAUTHORIZED_ACCESS")
        );

        when(auditLogRepository.findSecurityEvents(any(LocalDateTime.class))).thenReturn(securityLogs);

        // When
        List<AuditLog> result = auditLogService.getSecurityEvents(hoursBack);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);

        verify(auditLogRepository).findSecurityEvents(any(LocalDateTime.class));
    }

    @Test
    void getUserActivityCount_ReturnsActivityCount() {
        // Given
        int minutesBack = 60;
        long expectedCount = 25L;

        when(auditLogRepository.countByUserIdSince(eq(testUserId), any(LocalDateTime.class)))
                .thenReturn(expectedCount);

        // When
        long result = auditLogService.getUserActivityCount(testUserId, minutesBack);

        // Then
        assertThat(result).isEqualTo(expectedCount);

        verify(auditLogRepository).countByUserIdSince(eq(testUserId), any(LocalDateTime.class));
    }

    // Helper method
    private AuditLog createTestAuditLog(String action) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setUserId(testUserId);
        log.setUsername(testUsername);
        log.setAction(action);
        log.setResourceType("TestResource");
        log.setResourceId(UUID.randomUUID().toString());
        log.setStatus(AuditLog.Status.SUCCESS);
        log.setTimestamp(LocalDateTime.now());
        return log;
    }
}

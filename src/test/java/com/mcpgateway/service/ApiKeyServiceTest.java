package com.mcpgateway.service;

import com.mcpgateway.domain.ApiKey;
import com.mcpgateway.domain.User;
import com.mcpgateway.repository.ApiKeyRepository;
import com.mcpgateway.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private User testUser;
    private ApiKey testApiKey;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        testUser = new User();
        testUser.setId(userId);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        testApiKey = new ApiKey();
        testApiKey.setId(UUID.randomUUID());
        testApiKey.setUser(testUser);
        testApiKey.setKeyValue("test-api-key-12345");
    }

    @Test
    void generateApiKey_WithValidUserId_ShouldCreateNewApiKey() {
        // Arrange
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(apiKeyRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

        // Act
        ApiKey result = apiKeyService.generateApiKey(userId);

        // Assert
        assertNotNull(result);
        assertEquals(testUser, result.getUser());
        verify(userRepository).findById(userId);
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    void generateApiKey_WithExistingApiKey_ShouldDeleteOldAndCreateNew() {
        // Arrange
        ApiKey existingKey = new ApiKey();
        existingKey.setId(UUID.randomUUID());
        existingKey.setUser(testUser);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(apiKeyRepository.findByUserId(userId)).thenReturn(Optional.of(existingKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

        // Act
        ApiKey result = apiKeyService.generateApiKey(userId);

        // Assert
        assertNotNull(result);
        verify(apiKeyRepository).delete(existingKey);
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    void generateApiKey_WithNonExistentUser_ShouldThrowException() {
        // Arrange
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> apiKeyService.generateApiKey(userId)
        );
        assertEquals("User not found", exception.getMessage());
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void generateApiKeyByUsername_WithValidUsername_ShouldCreateNewApiKey() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(apiKeyRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

        // Act
        ApiKey result = apiKeyService.generateApiKeyByUsername("testuser");

        // Assert
        assertNotNull(result);
        assertEquals(testUser, result.getUser());
        verify(userRepository).findByUsername("testuser");
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    void generateApiKeyByUsername_WithNonExistentUser_ShouldThrowException() {
        // Arrange
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> apiKeyService.generateApiKeyByUsername("nonexistent")
        );
        assertEquals("User not found", exception.getMessage());
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void validateApiKey_WithValidKey_ShouldReturnUser() {
        // Arrange
        String keyValue = "valid-api-key";
        when(apiKeyRepository.findByKeyValue(keyValue)).thenReturn(Optional.of(testApiKey));

        // Act
        User result = apiKeyService.validateApiKey(keyValue);

        // Assert
        assertNotNull(result);
        assertEquals(testUser, result);
        assertEquals("testuser", result.getUsername());
        verify(apiKeyRepository).findByKeyValue(keyValue);
    }

    @Test
    void validateApiKey_WithInvalidKey_ShouldThrowException() {
        // Arrange
        String invalidKey = "invalid-api-key";
        when(apiKeyRepository.findByKeyValue(invalidKey)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> apiKeyService.validateApiKey(invalidKey)
        );
        assertEquals("Invalid API key", exception.getMessage());
    }

    @Test
    void validateApiKey_WithNullKey_ShouldThrowException() {
        // Arrange
        when(apiKeyRepository.findByKeyValue(null)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> apiKeyService.validateApiKey(null)
        );
    }
}

package com.mcpgateway.service;

import com.mcpgateway.domain.ApiKey;
import com.mcpgateway.domain.User;
import com.mcpgateway.repository.ApiKeyRepository;
import com.mcpgateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ApiKeyService {
    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;

    public ApiKey generateApiKey(UUID userId) {
        // 检查用户是否已有API KEY
        apiKeyRepository.findByUserId(userId).ifPresent(apiKeyRepository::delete);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ApiKey apiKey = new ApiKey();
        apiKey.setUser(user);
        return apiKeyRepository.save(apiKey);
    }

    public ApiKey generateApiKeyByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // 检查用户是否已有API KEY
        apiKeyRepository.findByUserId(user.getId()).ifPresent(apiKeyRepository::delete);

        ApiKey apiKey = new ApiKey();
        apiKey.setUser(user);
        return apiKeyRepository.save(apiKey);
    }

    public User validateApiKey(String keyValue) {
        return apiKeyRepository.findByKeyValue(keyValue)
                .map(ApiKey::getUser)
                .orElseThrow(() -> new IllegalArgumentException("Invalid API key"));
    }
} 
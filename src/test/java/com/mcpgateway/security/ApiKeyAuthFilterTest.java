package com.mcpgateway.security;

import com.mcpgateway.domain.User;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private UserRepository userRepository;

    private ApiKeyAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter(apiKeyService, userRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledApiKeyUser_ShouldNotAuthenticate() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        User lookupUser = new User();
        lookupUser.setId(userId);

        User disabledUser = new User();
        disabledUser.setId(userId);
        disabledUser.setUsername("disabled");
        disabledUser.setPassword("encoded");
        disabledUser.setEmail("disabled@example.com");
        disabledUser.setRole(User.UserRole.USER);
        disabledUser.setIsActive(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payments/history");
        request.addHeader("X-API-KEY", "key-disabled");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        when(apiKeyService.validateApiKey("key-disabled")).thenReturn(lookupUser);
        when(userRepository.findById(userId)).thenReturn(Optional.of(disabledUser));

        filter.doFilter(request, response, chain);

        assertThat(chainCalled.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void enabledApiKeyUser_ShouldAuthenticate() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        User lookupUser = new User();
        lookupUser.setId(userId);

        User enabledUser = new User();
        enabledUser.setId(userId);
        enabledUser.setUsername("enabled");
        enabledUser.setPassword("encoded");
        enabledUser.setEmail("enabled@example.com");
        enabledUser.setRole(User.UserRole.USER);
        enabledUser.setIsActive(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payments/history");
        request.addHeader("X-API-KEY", "key-enabled");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        when(apiKeyService.validateApiKey("key-enabled")).thenReturn(lookupUser);
        when(userRepository.findById(userId)).thenReturn(Optional.of(enabledUser));

        filter.doFilter(request, response, chain);

        assertThat(chainCalled.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
}

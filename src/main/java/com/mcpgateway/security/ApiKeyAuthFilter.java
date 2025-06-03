package com.mcpgateway.security;

import com.mcpgateway.domain.User;
import com.mcpgateway.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private final ApiKeyService apiKeyService;
    private final UserDetailsService userDetailsService;
    private static final String API_KEY_HEADER = "X-API-KEY";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        log.debug("Processing request in API Key filter: {} {}", request.getMethod(), request.getRequestURI());
        
        // Skip authentication for public endpoints
        if (isPublicEndpoint(request)) {
            log.debug("Skipping API Key authentication for public endpoint");
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);
        log.debug("API Key header: {}", apiKey != null ? "present" : "absent");
        
        if (apiKey != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                User user = apiKeyService.validateApiKey(apiKey);
                log.debug("API Key validation successful for user ID: {}", user.getId());
                
                UserDetails userDetails = userDetailsService.loadUserByUsername(user.getId().toString());
                log.debug("Loaded user details for API Key user");
                
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Successfully authenticated user with API Key");
            } catch (Exception e) {
                log.debug("Failed to validate API Key: {}", e.getMessage());
                // Invalid API key, continue to other authentication methods
            }
        }
        
        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        log.debug("Checking if endpoint is public in API Key filter: {} {}", method, path);

        // Public authentication endpoints
        if (method.equals("POST") && (
            path.endsWith("/auth/register") ||
            path.endsWith("/auth/authenticate"))) {
            log.debug("Found public authentication endpoint");
            return true;
        }

        // Swagger UI and API docs
        if (path.contains("/api-docs") ||
            path.contains("/swagger-ui") ||
            path.contains("/swagger-ui.html")) {
            log.debug("Found public Swagger/OpenAPI endpoint");
            return true;
        }

        // Health check endpoints
        if (path.contains("/health")) {
            log.debug("Found public health check endpoint");
            return true;
        }

        log.debug("Endpoint is not public");
        return false;
    }
} 
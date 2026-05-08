package com.mcpgateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        log.debug("Processing request: {} {}", request.getMethod(), request.getRequestURI());
        
        // Skip authentication for public endpoints
        if (isPublicEndpoint(request)) {
            log.debug("Skipping authentication for public endpoint");
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");
        log.debug("Authorization header: {}", authHeader != null ? "present" : "absent");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No valid Bearer token found, continuing filter chain");
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        try {
            final String username = jwtService.extractUsername(jwt);
            log.debug("Extracted username from JWT: {}", username);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                log.debug("Loaded user details for username: {}", username);

                if (!userDetails.isEnabled()) {
                    writeUnauthorizedResponse(response, "User is disabled");
                    return;
                }

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Successfully authenticated user: {}", username);
                } else {
                    writeUnauthorizedResponse(response, "Invalid JWT token");
                    return;
                }
            }
        } catch (Exception ex) {
            log.debug("JWT parsing/validation failed: {}", ex.getMessage());
            writeUnauthorizedResponse(response, "Invalid JWT token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        log.debug("Checking if endpoint is public: {} {}", method, path);

        // Public authentication endpoints
        if (method.equals("POST") && (
            path.equals("/api/v1/auth/register") ||
            path.equals("/api/v1/auth/authenticate"))) {
            log.debug("Found public authentication endpoint");
            return true;
        }

        if (method.equals("POST") &&
            (path.equals("/stripe/webhook") || path.equals("/api/v1/payments/webhook"))) {
            log.debug("Found public Stripe webhook endpoint");
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

        if (method.equals("GET") && path.startsWith("/api/ailuros/public")) {
            log.debug("Found public Ailuros endpoint");
            return true;
        }

        // Static files (dashboard, index, etc.)
        if (path.equals("/") ||
            path.equals("/index.html") ||
            path.equals("/ailuros-dashboard.html") ||
            path.equals("/favicon.ico") ||
            path.equals("/error")) {
            log.debug("Found public static file");
            return true;
        }

        if (method.equals("OPTIONS")) {
            return true;
        }

        log.debug("Endpoint is not public");
        return false;
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}

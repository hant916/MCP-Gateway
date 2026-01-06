package com.mcpgateway.controller;

import com.mcpgateway.domain.User;
import com.mcpgateway.dto.admin.QuotaManagementDTO;
import com.mcpgateway.dto.admin.UpdateQuotaRequest;
import com.mcpgateway.dto.admin.UpdateUserRequest;
import com.mcpgateway.dto.admin.UserAdminDTO;
import com.mcpgateway.ratelimit.RateLimit;
import com.mcpgateway.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Controller for admin operations
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administration APIs")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    // ==================== User Management ====================

    @GetMapping("/users")
    @Operation(summary = "Get all users")
    @RateLimit(limit = 200, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<Page<UserAdminDTO>> getAllUsers(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort by: username, email, subscriptionTier, lastLoginAt, createdAt")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @AuthenticationPrincipal User user) {

        Page<UserAdminDTO> users = adminService.getAllUsers(page, size, sortBy);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user by ID with statistics")
    @RateLimit(limit = 200, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<UserAdminDTO> getUserById(
            @PathVariable UUID userId,
            @AuthenticationPrincipal User user) {

        UserAdminDTO userDetails = adminService.getUserById(userId);
        return ResponseEntity.ok(userDetails);
    }

    @PutMapping("/users/{userId}")
    @Operation(summary = "Update user details")
    @RateLimit(limit = 100, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<UserAdminDTO> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal User user) {

        UserAdminDTO updated = adminService.updateUser(userId, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "Delete user (soft delete)")
    @RateLimit(limit = 50, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal User user) {

        adminService.deleteUser(userId);
        return ResponseEntity.ok().build();
    }

    // ==================== Quota Management ====================

    @GetMapping("/quotas")
    @Operation(summary = "Get all quotas")
    @RateLimit(limit = 200, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<Page<QuotaManagementDTO>> getAllQuotas(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Filter by: all, active, exceeded")
            @RequestParam(defaultValue = "all") String filterBy,
            @AuthenticationPrincipal User user) {

        Page<QuotaManagementDTO> quotas = adminService.getAllQuotas(page, size, filterBy);
        return ResponseEntity.ok(quotas);
    }

    @GetMapping("/quotas/{subscriptionId}")
    @Operation(summary = "Get quota by subscription ID")
    @RateLimit(limit = 200, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<QuotaManagementDTO> getQuotaById(
            @PathVariable UUID subscriptionId,
            @AuthenticationPrincipal User user) {

        QuotaManagementDTO quota = adminService.getQuotaById(subscriptionId);
        return ResponseEntity.ok(quota);
    }

    @GetMapping("/users/{userId}/quotas")
    @Operation(summary = "Get quotas for a specific user")
    @RateLimit(limit = 200, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<List<QuotaManagementDTO>> getQuotasByUserId(
            @PathVariable UUID userId,
            @AuthenticationPrincipal User user) {

        List<QuotaManagementDTO> quotas = adminService.getQuotasByUserId(userId);
        return ResponseEntity.ok(quotas);
    }

    @PutMapping("/quotas/{subscriptionId}")
    @Operation(summary = "Update quota for a subscription")
    @RateLimit(limit = 100, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<QuotaManagementDTO> updateQuota(
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody UpdateQuotaRequest request,
            @AuthenticationPrincipal User user) {

        QuotaManagementDTO updated = adminService.updateQuota(subscriptionId, request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/quotas/{subscriptionId}/reset")
    @Operation(summary = "Reset quota for a subscription")
    @RateLimit(limit = 100, window = 60, windowUnit = ChronoUnit.SECONDS, key = "user")
    public ResponseEntity<QuotaManagementDTO> resetQuota(
            @PathVariable UUID subscriptionId,
            @AuthenticationPrincipal User user) {

        QuotaManagementDTO reset = adminService.resetQuota(subscriptionId);
        return ResponseEntity.ok(reset);
    }
}

package com.mcpgateway.repository;

import com.mcpgateway.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    /**
     * Count users created in date range
     */
    Long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Get user registration trend by date
     */
    @Query("SELECT CAST(u.createdAt AS date) as date, COUNT(u) as count " +
           "FROM User u " +
           "WHERE u.createdAt BETWEEN :start AND :end " +
           "GROUP BY CAST(u.createdAt AS date) " +
           "ORDER BY date")
    List<Object[]> getUserRegistrationTrend(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Get users grouped by subscription tier
     */
    @Query("SELECT u.subscriptionTier, COUNT(u) as count " +
           "FROM User u " +
           "GROUP BY u.subscriptionTier")
    List<Object[]> getUsersBySubscriptionTier();
} 
package com.mcpgateway.repository;

import com.mcpgateway.domain.UsageRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    // 根据sessionId查询使用记录
    List<UsageRecord> findBySessionIdOrderByTimestampDesc(UUID sessionId);

    // 根据用户ID查询使用记录
    Page<UsageRecord> findByUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);

    // 根据时间范围查询
    @Query("SELECT u FROM UsageRecord u WHERE u.timestamp BETWEEN :startTime AND :endTime ORDER BY u.timestamp DESC")
    Page<UsageRecord> findByTimestampBetween(@Param("startTime") Timestamp startTime, 
                                            @Param("endTime") Timestamp endTime, 
                                            Pageable pageable);

    // 根据用户ID和时间范围查询
    @Query("SELECT u FROM UsageRecord u WHERE u.userId = :userId AND u.timestamp BETWEEN :startTime AND :endTime ORDER BY u.timestamp DESC")
    Page<UsageRecord> findByUserIdAndTimestampBetween(@Param("userId") UUID userId,
                                                     @Param("startTime") Timestamp startTime,
                                                     @Param("endTime") Timestamp endTime,
                                                     Pageable pageable);

    // 根据sessionId和时间范围查询
    @Query("SELECT u FROM UsageRecord u WHERE u.sessionId = :sessionId AND u.timestamp BETWEEN :startTime AND :endTime ORDER BY u.timestamp DESC")
    List<UsageRecord> findBySessionIdAndTimestampBetween(@Param("sessionId") UUID sessionId,
                                                        @Param("startTime") Timestamp startTime,
                                                        @Param("endTime") Timestamp endTime);

    // 根据状态查询
    List<UsageRecord> findByBillingStatusOrderByTimestampDesc(UsageRecord.BillingStatus billingStatus);

    // 根据API端点查询
    Page<UsageRecord> findByApiEndpointOrderByTimestampDesc(String apiEndpoint, Pageable pageable);

    // 统计查询 - 用户总调用次数
    @Query("SELECT COUNT(u) FROM UsageRecord u WHERE u.userId = :userId")
    Long countByUserId(@Param("userId") UUID userId);

    // 统计查询 - 用户在时间范围内的调用次数
    @Query("SELECT COUNT(u) FROM UsageRecord u WHERE u.userId = :userId AND u.timestamp BETWEEN :startTime AND :endTime")
    Long countByUserIdAndTimestampBetween(@Param("userId") UUID userId,
                                         @Param("startTime") Timestamp startTime,
                                         @Param("endTime") Timestamp endTime);

    // 统计查询 - 用户总费用
    @Query("SELECT COALESCE(SUM(u.costAmount), 0) FROM UsageRecord u WHERE u.userId = :userId")
    BigDecimal sumCostByUserId(@Param("userId") UUID userId);

    // 统计查询 - 用户在时间范围内的费用
    @Query("SELECT COALESCE(SUM(u.costAmount), 0) FROM UsageRecord u WHERE u.userId = :userId AND u.timestamp BETWEEN :startTime AND :endTime")
    BigDecimal sumCostByUserIdAndTimestampBetween(@Param("userId") UUID userId,
                                                 @Param("startTime") Timestamp startTime,
                                                 @Param("endTime") Timestamp endTime);

    // 统计查询 - 按API端点分组统计
    @Query("SELECT u.apiEndpoint, COUNT(u), COALESCE(SUM(u.costAmount), 0) FROM UsageRecord u WHERE u.userId = :userId GROUP BY u.apiEndpoint")
    List<Object[]> getUsageStatsByApiEndpoint(@Param("userId") UUID userId);

    // 统计查询 - 按日期分组统计
    @Query("SELECT DATE(u.timestamp) as date, COUNT(u) as count, COALESCE(SUM(u.costAmount), 0) as totalCost " +
           "FROM UsageRecord u WHERE u.userId = :userId AND u.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY DATE(u.timestamp) ORDER BY date DESC")
    List<Object[]> getDailyUsageStats(@Param("userId") UUID userId,
                                     @Param("startTime") Timestamp startTime,
                                     @Param("endTime") Timestamp endTime);

    // 统计查询 - 按状态分组统计
    @Query("SELECT u.billingStatus, COUNT(u), COALESCE(SUM(u.costAmount), 0) FROM UsageRecord u WHERE u.userId = :userId GROUP BY u.billingStatus")
    List<Object[]> getUsageStatsByStatus(@Param("userId") UUID userId);

    // 删除旧记录 (数据清理)
    @Query("DELETE FROM UsageRecord u WHERE u.timestamp < :cutoffTime")
    void deleteOldRecords(@Param("cutoffTime") Timestamp cutoffTime);

    // 检查是否存在重复记录 (防重复计费)
    @Query("SELECT COUNT(u) FROM UsageRecord u WHERE u.sessionId = :sessionId AND u.apiEndpoint = :apiEndpoint AND u.timestamp = :timestamp")
    Long countDuplicateRecords(@Param("sessionId") UUID sessionId,
                              @Param("apiEndpoint") String apiEndpoint,
                              @Param("timestamp") Timestamp timestamp);
} 
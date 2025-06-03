package com.mcpgateway.repository;

import com.mcpgateway.domain.BillingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingRuleRepository extends JpaRepository<BillingRule, UUID> {

    // 查询所有激活的计费规则，按优先级降序排列
    @Query("SELECT br FROM BillingRule br WHERE br.isActive = true ORDER BY br.priority DESC, br.createdAt ASC")
    List<BillingRule> findActiveRulesOrderByPriority();

    // 根据规则名称查询
    Optional<BillingRule> findByRuleName(String ruleName);

    // 根据API模式查询激活的规则
    @Query("SELECT br FROM BillingRule br WHERE br.isActive = true AND br.apiPattern = :apiPattern ORDER BY br.priority DESC")
    List<BillingRule> findActiveRulesByApiPattern(@Param("apiPattern") String apiPattern);

    // 根据HTTP方法查询激活的规则
    @Query("SELECT br FROM BillingRule br WHERE br.isActive = true AND (br.httpMethod = :httpMethod OR br.httpMethod IS NULL) ORDER BY br.priority DESC")
    List<BillingRule> findActiveRulesByHttpMethod(@Param("httpMethod") String httpMethod);

    // 查询匹配特定API路径的规则
    @Query("SELECT br FROM BillingRule br WHERE br.isActive = true AND " +
           "(:apiPath = br.apiPattern OR " +
           "(br.apiPattern LIKE '%*' AND :apiPath LIKE CONCAT(SUBSTRING(br.apiPattern, 1, LENGTH(br.apiPattern) - 1), '%'))) " +
           "ORDER BY br.priority DESC, LENGTH(br.apiPattern) DESC")
    List<BillingRule> findMatchingRules(@Param("apiPath") String apiPath);

    // 查询特定规则类型的激活规则
    @Query("SELECT br FROM BillingRule br WHERE br.isActive = true AND br.ruleType = :ruleType ORDER BY br.priority DESC")
    List<BillingRule> findActiveRulesByType(@Param("ruleType") BillingRule.RuleType ruleType);

    // 检查规则名称是否已存在
    @Query("SELECT COUNT(br) > 0 FROM BillingRule br WHERE br.ruleName = :ruleName AND br.id != :excludeId")
    Boolean existsByRuleNameAndIdNot(@Param("ruleName") String ruleName, @Param("excludeId") UUID excludeId);

    // 检查是否存在相同的API模式和HTTP方法组合
    @Query("SELECT COUNT(br) > 0 FROM BillingRule br WHERE br.apiPattern = :apiPattern AND " +
           "br.httpMethod = :httpMethod AND br.isActive = true AND br.id != :excludeId")
    Boolean existsByApiPatternAndHttpMethodAndIsActiveTrueAndIdNot(
            @Param("apiPattern") String apiPattern,
            @Param("httpMethod") String httpMethod,
            @Param("excludeId") UUID excludeId);

    // 获取默认计费规则 (通配符规则)
    @Query("SELECT br FROM BillingRule br WHERE br.isActive = true AND br.apiPattern = '*' ORDER BY br.priority DESC")
    List<BillingRule> findDefaultRules();

    // 获取最高优先级的规则
    @Query("SELECT br FROM BillingRule br WHERE br.isActive = true ORDER BY br.priority DESC LIMIT 1")
    Optional<BillingRule> findHighestPriorityRule();

    // 统计激活规则数量
    @Query("SELECT COUNT(br) FROM BillingRule br WHERE br.isActive = true")
    Long countActiveRules();

    // 统计规则类型分布
    @Query("SELECT br.ruleType, COUNT(br) FROM BillingRule br WHERE br.isActive = true GROUP BY br.ruleType")
    List<Object[]> countRulesByType();
} 
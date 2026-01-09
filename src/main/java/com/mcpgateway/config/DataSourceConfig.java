package com.mcpgateway.config;

import com.mcpgateway.config.datasource.DataSourceType;
import com.mcpgateway.config.datasource.RoutingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Database Read-Write Splitting Configuration
 *
 * Architecture:
 * - MASTER: Primary database for all write operations (INSERT, UPDATE, DELETE)
 * - REPLICA: Read replica(s) for read operations (SELECT)
 *
 * Routing Strategy:
 * - @Transactional(readOnly = false) → MASTER
 * - @Transactional(readOnly = true) → REPLICA
 * - No annotation → MASTER (safe default)
 *
 * Benefits:
 * - Reduces load on master database
 * - Scales read operations horizontally
 * - Improves overall throughput
 *
 * Configuration:
 * Enable with: spring.datasource.read-write-splitting.enabled=true
 * Set replica URL: spring.datasource.replica.url=jdbc:postgresql://replica:5432/mcpgateway
 *
 * If disabled or replica not configured, all queries go to master.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
        name = "spring.datasource.read-write-splitting.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String masterUrl;

    @Value("${spring.datasource.username}")
    private String masterUsername;

    @Value("${spring.datasource.password}")
    private String masterPassword;

    @Value("${spring.datasource.replica.url:#{null}}")
    private String replicaUrl;

    @Value("${spring.datasource.replica.username:#{null}}")
    private String replicaUsername;

    @Value("${spring.datasource.replica.password:#{null}}")
    private String replicaPassword;

    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private int minIdle;

    @Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    /**
     * Create master datasource (for writes)
     */
    @Bean
    public DataSource masterDataSource() {
        log.info("Configuring MASTER datasource: {}", masterUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(masterUrl);
        config.setUsername(masterUsername);
        config.setPassword(masterPassword);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setPoolName("MasterPool");

        // Performance tuning
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        return new HikariDataSource(config);
    }

    /**
     * Create replica datasource (for reads)
     */
    @Bean
    public DataSource replicaDataSource() {
        // If replica not configured, use master for reads too
        if (replicaUrl == null) {
            log.warn("Replica datasource not configured, using MASTER for reads");
            return masterDataSource();
        }

        log.info("Configuring REPLICA datasource: {}", replicaUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(replicaUrl);
        config.setUsername(replicaUsername != null ? replicaUsername : masterUsername);
        config.setPassword(replicaPassword != null ? replicaPassword : masterPassword);
        config.setMaximumPoolSize(maxPoolSize * 2); // More connections for read-heavy workload
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setPoolName("ReplicaPool");
        config.setReadOnly(true); // Hint to database that this is read-only

        // Performance tuning
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        return new HikariDataSource(config);
    }

    /**
     * Create routing datasource that switches between master and replica
     */
    @Primary
    @Bean
    public DataSource dataSource() {
        log.info("Configuring ROUTING datasource with read-write splitting");

        RoutingDataSource routingDataSource = new RoutingDataSource();

        // Configure target datasources
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.MASTER, masterDataSource());
        targetDataSources.put(DataSourceType.REPLICA, replicaDataSource());

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(masterDataSource()); // Default to master

        log.info("Read-write splitting enabled: MASTER={}, REPLICA={}",
                masterUrl,
                replicaUrl != null ? replicaUrl : masterUrl + " (using master)");

        return routingDataSource;
    }
}

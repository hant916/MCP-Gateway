package com.mcpgateway.config.datasource;

import lombok.extern.slf4j.Slf4j;

/**
 * ThreadLocal holder for current datasource routing context
 *
 * Usage:
 * - Set MASTER before write operations
 * - Set REPLICA before read operations
 * - Clear after transaction completes
 */
@Slf4j
public class DataSourceContextHolder {

    private static final ThreadLocal<DataSourceType> CONTEXT = new ThreadLocal<>();

    /**
     * Set the datasource type for current thread
     */
    public static void setDataSourceType(DataSourceType dataSourceType) {
        log.trace("Setting datasource type to: {}", dataSourceType);
        CONTEXT.set(dataSourceType);
    }

    /**
     * Get the current datasource type
     * Defaults to MASTER if not explicitly set
     */
    public static DataSourceType getDataSourceType() {
        DataSourceType type = CONTEXT.get();
        if (type == null) {
            log.trace("No datasource type set, defaulting to MASTER");
            return DataSourceType.MASTER;
        }
        return type;
    }

    /**
     * Clear the datasource type from current thread
     * IMPORTANT: Always call this in finally block to prevent memory leaks
     */
    public static void clearDataSourceType() {
        log.trace("Clearing datasource type");
        CONTEXT.remove();
    }
}

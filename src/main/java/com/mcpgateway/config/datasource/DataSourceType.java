package com.mcpgateway.config.datasource;

/**
 * Enum for datasource routing types
 */
public enum DataSourceType {
    /**
     * Master datasource - for write operations (INSERT, UPDATE, DELETE)
     */
    MASTER,

    /**
     * Replica datasource - for read operations (SELECT)
     */
    REPLICA
}

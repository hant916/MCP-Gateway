package com.mcpgateway.config.datasource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Dynamic datasource routing based on current transaction context
 *
 * Routes database connections to either:
 * - MASTER: for write operations (@Transactional)
 * - REPLICA: for read operations (@Transactional(readOnly = true))
 *
 * How it works:
 * 1. TransactionRoutingAspect sets DataSourceContextHolder before method execution
 * 2. determineCurrentLookupKey() is called by Spring to select datasource
 * 3. Returns MASTER or REPLICA based on ThreadLocal context
 * 4. Spring routes the connection to the appropriate datasource
 */
@Slf4j
public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceType dataSourceType = DataSourceContextHolder.getDataSourceType();
        log.trace("Routing to datasource: {}", dataSourceType);
        return dataSourceType;
    }
}

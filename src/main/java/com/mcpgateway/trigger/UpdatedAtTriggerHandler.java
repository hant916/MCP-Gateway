package com.mcpgateway.trigger;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * H2 Database trigger handler for automatic updated_at column updates
 */
public class UpdatedAtTriggerHandler implements Trigger {

    // H2 gives column ordinal positions as 1-based, arrays in fire() are 0-based.
    private int updatedAtArrayIndex = -1;

    @Override
    public void init(Connection conn, String schemaName, String triggerName,
                     String tableName, boolean before, int type) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, schemaName, tableName, null)) {
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                if ("updated_at".equalsIgnoreCase(columnName)) {
                    int ordinal = columns.getInt("ORDINAL_POSITION");
                    updatedAtArrayIndex = ordinal - 1;
                    break;
                }
            }
        }

        if (updatedAtArrayIndex < 0) {
            throw new SQLException("updated_at column not found for table: " + tableName);
        }
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        if (updatedAtArrayIndex >= 0 && updatedAtArrayIndex < newRow.length) {
            newRow[updatedAtArrayIndex] = new Timestamp(System.currentTimeMillis());
        }
    }

    @Override
    public void close() {
        // No-op
    }

    @Override
    public void remove() {
        // No-op
    }
}

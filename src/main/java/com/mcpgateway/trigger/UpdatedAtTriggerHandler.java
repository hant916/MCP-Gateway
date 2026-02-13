package com.mcpgateway.trigger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * H2 Database trigger handler for automatic updated_at column updates
 *
 * NOTE: Disabled (@Component removed) because H2 dependency has runtime scope
 * and org.h2.api.Trigger is not available at compile time.
 *
 * Alternative approach: Use JPA @PreUpdate annotation in entity classes instead.
 * This is already implemented in User.java and other entities.
 */
// @Component - Removed to fix compilation error (H2 API not available at compile time)
public class UpdatedAtTriggerHandler /* implements Trigger */ {

    // Implementation commented out - use JPA @PreUpdate instead
    /*
    @Override
    public void init(Connection conn, String schemaName, String triggerName,
                    String tableName, boolean before, int type) {
        // Initialization logic if needed
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        // Find the updated_at column index based on the table
        int updatedAtIndex = getUpdatedAtColumnIndex(newRow.length);
        newRow[updatedAtIndex] = new Timestamp(System.currentTimeMillis());
    }

    private int getUpdatedAtColumnIndex(int totalColumns) {
        // The updated_at column index for each table:
        // users: 5 (id, username, password, email, created_at, updated_at)
        // api_specifications: 7 (id, name, description, spec_type, content, version, created_at, updated_at)
        // mcp_tools: 6 (id, name, description, parameters, api_spec_id, created_at, updated_at)
        
        switch (totalColumns) {
            case 6: return 5;  // users table
            case 8: return 7;  // api_specifications table
            case 7: return 6;  // mcp_tools table
            default: return totalColumns - 1; // fallback to last column
        }
    }

    @Override
    public void close() {
        // Cleanup logic if needed
    }

    @Override
    public void remove() {
        // Removal logic if needed
    }
    */
} 
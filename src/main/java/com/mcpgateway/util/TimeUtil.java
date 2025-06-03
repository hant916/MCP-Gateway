package com.mcpgateway.util;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class TimeUtil {
    public static Timestamp toTimestamp(ZonedDateTime zonedDateTime) {
        return zonedDateTime != null ? Timestamp.from(zonedDateTime.toInstant()) : null;
    }

    public static ZonedDateTime toZonedDateTime(Timestamp timestamp) {
        return timestamp != null ? ZonedDateTime.ofInstant(timestamp.toInstant(), ZoneId.systemDefault()) : null;
    }
} 
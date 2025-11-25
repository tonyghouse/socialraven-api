package com.ghouse.socialraven.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class TimeUtil {


    public static OffsetDateTime toUTCOffsetDateTime(Long epochMillis) {
        if (epochMillis == null) {
            return null;
        }
        return Instant.ofEpochMilli(epochMillis)
                .atOffset(ZoneOffset.UTC);
    }

//    /**
//     * Convert epoch milliseconds to LocalDateTime in UTC.
//     */
//    public static LocalDateTime toUTCDateTime(Long epochMillis) {
//        if (epochMillis == null) {
//            return null;
//        }
//        return Instant.ofEpochMilli(epochMillis)
//                .atZone(ZoneOffset.UTC)
//                .toLocalDateTime();
//    }
//
//    /**
//     * Convert epoch milliseconds to ISO string in UTC.
//     */
//    public static String toUTCISOString(Long epochMillis) {
//        if (epochMillis == null) {
//            return null;
//        }
//        return Instant.ofEpochMilli(epochMillis)
//                .atOffset(ZoneOffset.UTC)
//                .toString();
//    }
//
//    /**
//     * Convert epoch milliseconds to Instant (always UTC).
//     */
//    public static Instant toInstant(Long epochMillis) {
//        if (epochMillis == null) {
//            return null;
//        }
//        return Instant.ofEpochMilli(epochMillis);
//    }
}

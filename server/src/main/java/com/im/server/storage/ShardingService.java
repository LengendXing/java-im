package com.im.server.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ShardingService {
    private static final Logger log = LoggerFactory.getLogger(ShardingService.class);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private static final int DIFFUSION_THRESHOLD = 200;

    private ShardingService() {}

    public static String getMonthlyTableName(long serverTime) {
        LocalDate date = LocalDate.ofEpochDay(serverTime / 86400000);
        return "im_message_" + MONTH_FMT.format(date);
    }

    public static String getMonthlyTableName(int year, int month) {
        return String.format("im_message_%04d%02d", year, month);
    }

    public static List<String> getTableNamesInRange(long fromTime, long toTime) {
        List<String> tables = new ArrayList<>();
        LocalDate start = LocalDate.ofEpochDay(fromTime / 86400000).withDayOfMonth(1);
        LocalDate end = LocalDate.ofEpochDay(toTime / 86400000).withDayOfMonth(1);
        while (!start.isAfter(end)) {
            tables.add("im_message_" + MONTH_FMT.format(start));
            start = start.plusMonths(1);
        }
        if (tables.isEmpty()) {
            tables.add(getMonthlyTableName(System.currentTimeMillis()));
        }
        return tables;
    }

    public static List<String> getRecentTableNames(int months) {
        List<String> tables = new ArrayList<>();
        LocalDate current = LocalDate.now().withDayOfMonth(1);
        for (int i = 0; i < months; i++) {
            tables.add("im_message_" + MONTH_FMT.format(current));
            current = current.minusMonths(1);
        }
        return tables;
    }

    public static String getCreateMonthlyTableSQL(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "msg_id BIGINT PRIMARY KEY, " +
                "session_id VARCHAR(64) NOT NULL, " +
                "sender_id BIGINT NOT NULL, " +
                "seq BIGINT NOT NULL, " +
                "content_type INT NOT NULL DEFAULT 1, " +
                "content_text TEXT, " +
                "content_url VARCHAR(512) DEFAULT '', " +
                "content_extra TEXT, " +
                "client_msg_id VARCHAR(64) DEFAULT '', " +
                "server_time BIGINT NOT NULL, " +
                "is_recalled TINYINT DEFAULT 0, " +
                "INDEX idx_session_seq (session_id, seq)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    public static boolean isLargeGroup(int memberCount) {
        return memberCount > DIFFUSION_THRESHOLD;
    }

    public static int getDiffusionThreshold() {
        return DIFFUSION_THRESHOLD;
    }
}

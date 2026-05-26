package com.im.server.common;

import java.util.concurrent.atomic.AtomicLong;

public class SnowflakeId {
    private final long workerId;
    private final long epoch = 1704067200000L; // 2024-01-01 00:00:00 UTC
    private final AtomicLong lastTimestamp = new AtomicLong(-1L);
    private long sequence = 0L;

    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    public SnowflakeId(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId out of range: " + workerId);
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis() - epoch;
        long lastTs = lastTimestamp.get();
        if (timestamp < lastTs) {
            timestamp = lastTs;
        }
        if (timestamp == lastTs) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTs);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp.set(timestamp);
        return (timestamp << TIMESTAMP_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence;
    }

    private long tilNextMillis(long lastTs) {
        long ts = System.currentTimeMillis() - epoch;
        while (ts <= lastTs) {
            ts = System.currentTimeMillis() - epoch;
        }
        return ts;
    }
}

package com.im.server.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SnowflakeIdTest {

    @Test
    void nextId_returnsPositiveValue() {
        SnowflakeId idGen = new SnowflakeId(1);
        long id = idGen.nextId();
        assertTrue(id > 0);
    }

    @Test
    void nextId_returnsMonotonicallyIncreasing() {
        SnowflakeId idGen = new SnowflakeId(1);
        long prev = idGen.nextId();
        for (int i = 0; i < 1000; i++) {
            long curr = idGen.nextId();
            assertTrue(curr > prev, "ID should be monotonically increasing");
            prev = curr;
        }
    }

    @Test
    void nextId_uniqueAcrossCalls() {
        SnowflakeId idGen = new SnowflakeId(1);
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (int i = 0; i < 10000; i++) {
            long id = idGen.nextId();
            assertTrue(ids.add(id), "Duplicate ID generated: " + id);
        }
        assertEquals(10000, ids.size());
    }

    @Test
    void differentWorkerIds_generateDifferentIds() {
        SnowflakeId idGen1 = new SnowflakeId(1);
        SnowflakeId idGen2 = new SnowflakeId(2);
        long id1 = idGen1.nextId();
        long id2 = idGen2.nextId();

        // Extract worker ID from generated ID
        long worker1 = (id1 >> 12) & 0x3FF;
        long worker2 = (id2 >> 12) & 0x3FF;
        assertEquals(1, worker1);
        assertEquals(2, worker2);
    }

    @Test
    void invalidWorkerId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeId(-1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeId(1024));
    }

    @Test
    void validWorkerIdRange() {
        assertDoesNotThrow(() -> new SnowflakeId(0));
        assertDoesNotThrow(() -> new SnowflakeId(1023));
    }
}

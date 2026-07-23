package com.siberanka.split.placeholder.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParseResultCacheTest {

    @Test
    void boundsEntriesAndInvalidatesTargetOnQuit() {
        ParseResultCache cache = new ParseResultCache(TimeUnit.SECONDS.toNanos(1), 1);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals("first", cache.getSynchronously(first, () -> "first", ignored -> { }));
        assertEquals("second", cache.getSynchronously(second, () -> "second", ignored -> { }));
        assertEquals(1, cache.size());

        cache.invalidate(second);
        assertEquals(0, cache.size());
    }

    @Test
    void rejectedSchedulerFailsClosedAndRateLimitsRetry() {
        ParseResultCache cache = new ParseResultCache(TimeUnit.SECONDS.toNanos(1), 4);
        UUID target = UUID.randomUUID();
        AtomicInteger schedules = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();

        assertEquals("", cache.getOrSchedule(
                target,
                () -> {
                    refreshes.incrementAndGet();
                    return "unexpected";
                },
                task -> {
                    schedules.incrementAndGet();
                    return false;
                },
                ignored -> { }
        ));
        assertEquals("", cache.getOrSchedule(
                target,
                () -> {
                    refreshes.incrementAndGet();
                    return "unexpected";
                },
                task -> {
                    schedules.incrementAndGet();
                    return false;
                },
                ignored -> { }
        ));

        assertEquals(1, schedules.get());
        assertEquals(0, refreshes.get());
    }
}

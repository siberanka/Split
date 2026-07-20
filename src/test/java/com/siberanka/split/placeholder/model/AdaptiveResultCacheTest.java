package com.siberanka.split.placeholder.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveResultCacheTest {

    @Test
    void refreshesOnlyAfterCooldownExpires() {
        AtomicLong clock = new AtomicLong(0L);
        AtomicInteger refreshes = new AtomicInteger();
        AdaptiveResultCache cache = new AdaptiveResultCache(100L, 16, clock::get);
        UUID player = UUID.randomUUID();

        assertEquals("value-1", cache.get(player, () -> "value-" + refreshes.incrementAndGet(), ignored -> { }));
        clock.set(99L);
        assertEquals("value-1", cache.get(player, () -> "value-" + refreshes.incrementAndGet(), ignored -> { }));
        clock.set(100L);
        assertEquals("value-2", cache.get(player, () -> "value-" + refreshes.incrementAndGet(), ignored -> { }));
        assertEquals(2, refreshes.get());
    }

    @Test
    void servesStaleValueSilentlyWhenRefreshFails() {
        AtomicLong clock = new AtomicLong(0L);
        AtomicInteger failures = new AtomicInteger();
        AdaptiveResultCache cache = new AdaptiveResultCache(100L, 16, clock::get);
        UUID player = UUID.randomUUID();

        assertEquals("last-good", cache.get(player, () -> "last-good", ignored -> { }));
        clock.set(100L);
        assertEquals("last-good", cache.get(player, () -> {
            throw new IllegalStateException("upstream failed");
        }, ignored -> failures.incrementAndGet()));
        clock.set(150L);
        assertEquals("last-good", cache.get(player, () -> "unexpected-refresh", ignored -> { }));
        assertEquals(1, failures.get());
    }

    @Test
    void remainsBoundedAcrossManyPlayerKeys() {
        AdaptiveResultCache cache = new AdaptiveResultCache(100L, 2, () -> 0L);

        for (int index = 0; index < 20; index++) {
            UUID player = new UUID(0L, index);
            cache.get(player, () -> "value", ignored -> { });
        }

        assertEquals(2, cache.size());
    }

    @Test
    void invalidationForcesTheNextRequestToRefresh() {
        AtomicInteger refreshes = new AtomicInteger();
        AdaptiveResultCache cache = new AdaptiveResultCache(100L, 16, () -> 0L);
        UUID player = UUID.randomUUID();

        assertEquals("value-1", cache.get(player, () -> "value-" + refreshes.incrementAndGet(), ignored -> { }));
        cache.invalidate(player);
        assertEquals("value-2", cache.get(player, () -> "value-" + refreshes.incrementAndGet(), ignored -> { }));
        assertEquals(2, refreshes.get());
    }

    @Test
    void coalescesConcurrentRefreshesForSamePlayer() throws Exception {
        AdaptiveResultCache cache = new AdaptiveResultCache(100L, 16, () -> 0L);
        UUID player = UUID.randomUUID();
        AtomicInteger refreshes = new AtomicInteger();
        CountDownLatch allStarted = new CountDownLatch(8);
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Future<String>> results = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                results.add(executor.submit(() -> {
                    allStarted.countDown();
                    return cache.get(player, () -> {
                        refreshes.incrementAndGet();
                        refreshStarted.countDown();
                        try {
                            if (!releaseRefresh.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("refresh release timed out");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                        return "shared";
                    }, ignored -> { });
                }));
            }

            assertTrue(allStarted.await(5, TimeUnit.SECONDS));
            assertTrue(refreshStarted.await(5, TimeUnit.SECONDS));
            releaseRefresh.countDown();
            for (Future<String> result : results) {
                assertEquals("shared", result.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, refreshes.get());
        } finally {
            releaseRefresh.countDown();
            executor.shutdownNow();
        }
    }
}

package com.siberanka.split.placeholder.model;

import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Per-placeholder, UUID-only timed cache. Cache hits are lock-free; a cache miss
 * reserves a bounded slot, and ConcurrentHashMap.compute serializes refreshes for
 * the same player so rapid scoreboard requests cannot duplicate expensive PAPI work.
 */
final class AdaptiveResultCache {

    private static final CacheEntry RESERVED = new CacheEntry("", Long.MIN_VALUE, false);

    private final ConcurrentHashMap<UUID, CacheEntry> entries = new ConcurrentHashMap<>();
    private final Object capacityLock = new Object();
    private final long cooldownNanos;
    private final int maximumEntries;
    private final LongSupplier nanoTime;

    AdaptiveResultCache(long cooldownNanos, int maximumEntries) {
        this(cooldownNanos, maximumEntries, System::nanoTime);
    }

    AdaptiveResultCache(long cooldownNanos, int maximumEntries, LongSupplier nanoTime) {
        if (cooldownNanos <= 0) {
            throw new IllegalArgumentException("Cooldown must be positive");
        }
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException("Maximum entries must be positive");
        }
        this.cooldownNanos = cooldownNanos;
        this.maximumEntries = maximumEntries;
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    String get(UUID key, Supplier<String> refresher, Consumer<RuntimeException> refreshFailureHandler) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(refresher, "refresher");
        Objects.requireNonNull(refreshFailureHandler, "refreshFailureHandler");

        long now = nanoTime.getAsLong();
        CacheEntry existing = entries.get(key);
        if (existing != null && existing.initialized() && now < existing.validUntilNanos()) {
            return existing.value();
        }

        reserveBoundedSlot(key);
        CacheEntry refreshed = entries.compute(key, (ignored, current) -> {
            long refreshTime = nanoTime.getAsLong();
            if (current != null && current.initialized() && refreshTime < current.validUntilNanos()) {
                return current;
            }

            try {
                String value = refresher.get();
                return new CacheEntry(
                        value != null ? value : "",
                        addWithoutOverflow(refreshTime, cooldownNanos),
                        true
                );
            } catch (RuntimeException exception) {
                notifyFailureSafely(refreshFailureHandler, exception);
                // Serve the last successful value (or an empty fail-closed value on
                // first failure) and delay the next retry to prevent error storms.
                String fallback = current != null && current.initialized() ? current.value() : "";
                return new CacheEntry(
                        fallback,
                        addWithoutOverflow(refreshTime, cooldownNanos),
                        true
                );
            }
        });
        return refreshed.value();
    }

    int size() {
        return entries.size();
    }

    void invalidate(UUID key) {
        if (key != null) {
            entries.remove(key);
        }
    }

    private void reserveBoundedSlot(UUID key) {
        if (entries.containsKey(key)) {
            return;
        }
        synchronized (capacityLock) {
            if (entries.containsKey(key)) {
                return;
            }
            while (entries.size() >= maximumEntries) {
                Iterator<UUID> iterator = entries.keySet().iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                UUID victim = iterator.next();
                entries.remove(victim);
            }
            entries.putIfAbsent(key, RESERVED);
        }
    }

    private static long addWithoutOverflow(long value, long increment) {
        try {
            return Math.addExact(value, increment);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static void notifyFailureSafely(
            Consumer<RuntimeException> refreshFailureHandler,
            RuntimeException exception
    ) {
        try {
            refreshFailureHandler.accept(exception);
        } catch (RuntimeException ignored) {
            // A diagnostic callback must never break placeholder delivery.
        }
    }

    private record CacheEntry(String value, long validUntilNanos, boolean initialized) {
    }
}

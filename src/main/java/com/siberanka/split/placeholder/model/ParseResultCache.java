package com.siberanka.split.placeholder.model;

import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Bounded per-target cache used by parse placeholders. It supports both immediate
 * refreshes and non-blocking scheduler refreshes, which avoids cross-region waits
 * and deadlocks on Folia.
 */
final class ParseResultCache {

    private static final CacheEntry RESERVED = new CacheEntry("", Long.MIN_VALUE, false, false);

    private final ConcurrentHashMap<UUID, CacheEntry> entries = new ConcurrentHashMap<>();
    private final Object capacityLock = new Object();
    private final long cooldownNanos;
    private final int maximumEntries;

    ParseResultCache(long cooldownNanos, int maximumEntries) {
        if (cooldownNanos <= 0) {
            throw new IllegalArgumentException("Cooldown must be positive");
        }
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException("Maximum entries must be positive");
        }
        this.cooldownNanos = cooldownNanos;
        this.maximumEntries = maximumEntries;
    }

    String getSynchronously(
            UUID key,
            Supplier<String> refresher,
            Consumer<RuntimeException> failureHandler
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(refresher, "refresher");
        Objects.requireNonNull(failureHandler, "failureHandler");

        long now = System.nanoTime();
        CacheEntry existing = entries.get(key);
        if (isFresh(existing, now)) {
            return existing.value();
        }

        reserveBoundedSlot(key);
        CacheEntry refreshed = entries.compute(key, (ignored, current) -> {
            long refreshTime = System.nanoTime();
            if (isFresh(current, refreshTime)) {
                return current;
            }
            try {
                return successful(refresher.get(), refreshTime);
            } catch (RuntimeException exception) {
                notifyFailureSafely(failureHandler, exception);
                return failed(current, refreshTime);
            }
        });
        return refreshed != null && refreshed.initialized() ? refreshed.value() : "";
    }

    String getOrSchedule(
            UUID key,
            Supplier<String> refresher,
            Predicate<Runnable> scheduler,
            Consumer<RuntimeException> failureHandler
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(refresher, "refresher");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(failureHandler, "failureHandler");

        long now = System.nanoTime();
        CacheEntry existing = entries.get(key);
        if (isFresh(existing, now)) {
            return existing.value();
        }

        reserveBoundedSlot(key);
        AtomicBoolean shouldSchedule = new AtomicBoolean();
        entries.compute(key, (ignored, current) -> {
            long refreshTime = System.nanoTime();
            if (isFresh(current, refreshTime) || (current != null && current.refreshing())) {
                return current;
            }
            shouldSchedule.set(true);
            String fallback = current != null && current.initialized() ? current.value() : "";
            boolean initialized = current != null && current.initialized();
            return new CacheEntry(fallback, Long.MIN_VALUE, initialized, true);
        });

        if (shouldSchedule.get()) {
            try {
                boolean accepted = scheduler.test(() -> completeScheduledRefresh(key, refresher, failureHandler));
                if (!accepted) {
                    completeScheduledFailure(key, null, failureHandler);
                }
            } catch (RuntimeException exception) {
                completeScheduledFailure(key, exception, failureHandler);
            }
        }

        CacheEntry current = entries.get(key);
        return current != null && current.initialized() ? current.value() : "";
    }

    void invalidate(UUID key) {
        if (key != null) {
            entries.remove(key);
        }
    }

    int size() {
        return entries.size();
    }

    private void completeScheduledRefresh(
            UUID key,
            Supplier<String> refresher,
            Consumer<RuntimeException> failureHandler
    ) {
        String value;
        try {
            value = refresher.get();
        } catch (RuntimeException exception) {
            completeScheduledFailure(key, exception, failureHandler);
            return;
        }

        long now = System.nanoTime();
        entries.computeIfPresent(key, (ignored, current) ->
                current.refreshing() ? successful(value, now) : current
        );
    }

    private void completeScheduledFailure(
            UUID key,
            RuntimeException exception,
            Consumer<RuntimeException> failureHandler
    ) {
        if (exception != null) {
            notifyFailureSafely(failureHandler, exception);
        }
        long now = System.nanoTime();
        entries.computeIfPresent(key, (ignored, current) ->
                current.refreshing() ? failed(current, now) : current
        );
    }

    private CacheEntry successful(String value, long now) {
        return new CacheEntry(
                value != null ? value : "",
                addWithoutOverflow(now, cooldownNanos),
                true,
                false
        );
    }

    private CacheEntry failed(CacheEntry current, long now) {
        String fallback = current != null && current.initialized() ? current.value() : "";
        return new CacheEntry(fallback, addWithoutOverflow(now, cooldownNanos), true, false);
    }

    private static boolean isFresh(CacheEntry entry, long now) {
        return entry != null && entry.initialized() && now < entry.validUntilNanos();
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
                entries.remove(iterator.next());
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
            Consumer<RuntimeException> failureHandler,
            RuntimeException exception
    ) {
        try {
            failureHandler.accept(exception);
        } catch (RuntimeException ignored) {
            // Diagnostics must never break placeholder delivery.
        }
    }

    private record CacheEntry(
            String value,
            long validUntilNanos,
            boolean initialized,
            boolean refreshing
    ) {
    }
}

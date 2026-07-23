package com.siberanka.split.placeholder.model;

import com.siberanka.split.SplitPlugin;
import com.siberanka.split.util.AdaptiveSpacingCalculator;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Resolves a selector in the requesting player's context, finds the exact online
 * or known offline target, and parses the selected platform value as that target.
 */
public final class ParsePlaceholder extends SplitPlaceholder {

    private static final int MAX_TARGET_NAME_LENGTH = 64;

    private final String selector;
    private final String javaValue;
    private final String bedrockValue;
    private final boolean allowOffline;
    private final long cooldownMilliseconds;
    private final int maximumCacheEntries;
    private final int maximumOutputLength;
    private final ParseResultCache resultCache;
    private final TemplateResolver templateResolver;
    private final TargetPlayerLookup targetPlayerLookup;
    private final BedrockDetector bedrockDetector;
    private final TargetExecutionRouter executionRouter;
    private final ConcurrentHashMap<String, TargetLookupEntry> targetLookups = new ConcurrentHashMap<>();
    private final Object targetLookupCapacityLock = new Object();

    public ParsePlaceholder(
            String selector,
            String javaValue,
            String bedrockValue,
            boolean allowOffline,
            long cooldownMilliseconds,
            int maximumCacheEntries,
            int maximumOutputLength
    ) {
        this(
                selector,
                javaValue,
                bedrockValue,
                allowOffline,
                cooldownMilliseconds,
                maximumCacheEntries,
                maximumOutputLength,
                PlaceholderAPI::setPlaceholders,
                new TargetPlayerLookup() {
                    @Override
                    public OfflinePlayer findOnline(SplitPlugin plugin, String exactName) {
                        return plugin.getServer().getPlayerExact(exactName);
                    }

                    @Override
                    @SuppressWarnings("deprecation")
                    public OfflinePlayer findKnownOffline(SplitPlugin plugin, String exactName) {
                        OfflinePlayer player = plugin.getServer().getOfflinePlayer(exactName);
                        String knownName = player.getName();
                        return player.hasPlayedBefore()
                                && knownName != null
                                && knownName.equalsIgnoreCase(exactName)
                                ? player
                                : null;
                    }

                    @Override
                    public OfflinePlayer findById(SplitPlugin plugin, UUID playerId) {
                        return plugin.getServer().getOfflinePlayer(playerId);
                    }
                },
                SplitPlugin::isBedrock,
                new TargetExecutionRouter() {
                    @Override
                    public boolean requiresScheduling(SplitPlugin plugin, OfflinePlayer target) {
                        return target instanceof Player
                                && (plugin.getPlatformScheduler().isFolia()
                                || !plugin.getServer().isPrimaryThread());
                    }

                    @Override
                    public boolean schedule(SplitPlugin plugin, OfflinePlayer target, Runnable task) {
                        return target instanceof Player onlineTarget
                                && plugin.getPlatformScheduler().runForSender(onlineTarget, task);
                    }
                }
        );
    }

    ParsePlaceholder(
            String selector,
            String javaValue,
            String bedrockValue,
            boolean allowOffline,
            long cooldownMilliseconds,
            int maximumCacheEntries,
            int maximumOutputLength,
            TemplateResolver templateResolver,
            TargetPlayerLookup targetPlayerLookup,
            BedrockDetector bedrockDetector,
            TargetExecutionRouter executionRouter
    ) {
        super("parse");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.javaValue = Objects.requireNonNullElse(javaValue, "");
        this.bedrockValue = Objects.requireNonNullElse(bedrockValue, "");
        this.allowOffline = allowOffline;
        this.cooldownMilliseconds = cooldownMilliseconds;
        this.maximumCacheEntries = maximumCacheEntries;
        this.maximumOutputLength = maximumOutputLength;
        this.resultCache = new ParseResultCache(
                TimeUnit.MILLISECONDS.toNanos(cooldownMilliseconds),
                maximumCacheEntries
        );
        this.templateResolver = Objects.requireNonNull(templateResolver, "templateResolver");
        this.targetPlayerLookup = Objects.requireNonNull(targetPlayerLookup, "targetPlayerLookup");
        this.bedrockDetector = Objects.requireNonNull(bedrockDetector, "bedrockDetector");
        this.executionRouter = Objects.requireNonNull(executionRouter, "executionRouter");
    }

    public String getSelector() {
        return selector;
    }

    public String getJavaValue() {
        return javaValue;
    }

    public String getBedrockValue() {
        return bedrockValue;
    }

    public boolean isAllowOffline() {
        return allowOffline;
    }

    public long getCooldownMilliseconds() {
        return cooldownMilliseconds;
    }

    public int getMaximumCacheEntries() {
        return maximumCacheEntries;
    }

    public int getMaximumOutputLength() {
        return maximumOutputLength;
    }

    @Override
    public boolean shouldResolveNestedPlaceholders() {
        // Selector and selected value are each parsed exactly once here.
        return false;
    }

    @Override
    public String resolve(SplitPlugin plugin, OfflinePlayer requestingPlayer) {
        String resolvedSelector = templateResolver.resolve(requestingPlayer, selector);
        String targetName = normalizeTargetName(resolvedSelector);
        if (targetName == null) {
            return "";
        }

        OfflinePlayer target = findTarget(plugin, targetName);
        if (target == null) {
            return "";
        }

        UUID targetId = target.getUniqueId();
        if (executionRouter.requiresScheduling(plugin, target)) {
            return resultCache.getOrSchedule(
                    targetId,
                    () -> resolveForTarget(plugin, target),
                    task -> executionRouter.schedule(plugin, target, task),
                    exception -> logFailure(plugin, targetId, exception)
            );
        }
        return resultCache.getSynchronously(
                targetId,
                () -> resolveForTarget(plugin, target),
                exception -> logFailure(plugin, targetId, exception)
        );
    }

    public void invalidatePlayer(UUID playerId) {
        resultCache.invalidate(playerId);
        if (playerId != null) {
            targetLookups.entrySet().removeIf(entry -> playerId.equals(entry.getValue().playerId()));
        }
    }

    private OfflinePlayer findTarget(SplitPlugin plugin, String targetName) {
        OfflinePlayer online = targetPlayerLookup.findOnline(plugin, targetName);
        if (online != null) {
            return online;
        }
        if (!allowOffline) {
            return null;
        }

        String lookupKey = targetName.toLowerCase(Locale.ROOT);
        TargetLookupEntry cached = targetLookups.get(lookupKey);
        if (cached == null) {
            synchronized (targetLookupCapacityLock) {
                cached = targetLookups.get(lookupKey);
                if (cached == null) {
                    // Negative results consume a permanent slot. This hard ceiling
                    // prevents placeholder-controlled names from causing unbounded
                    // OfflinePlayer profile creation in server-owned caches.
                    if (targetLookups.size() >= maximumCacheEntries) {
                        return null;
                    }
                    OfflinePlayer offline = targetPlayerLookup.findKnownOffline(plugin, targetName);
                    cached = new TargetLookupEntry(offline != null ? offline.getUniqueId() : null);
                    targetLookups.put(lookupKey, cached);
                    return offline;
                }
            }
        }
        return cached.playerId() != null
                ? targetPlayerLookup.findById(plugin, cached.playerId())
                : null;
    }

    private String resolveForTarget(SplitPlugin plugin, OfflinePlayer target) {
        String selected = bedrockDetector.isBedrock(plugin, target) ? bedrockValue : javaValue;
        String resolved = templateResolver.resolve(target, selected);
        if (resolved == null || resolved.isEmpty()) {
            return "";
        }
        return AdaptiveSpacingCalculator.truncateToCodePoints(resolved, maximumOutputLength);
    }

    private static String normalizeTargetName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > MAX_TARGET_NAME_LENGTH) {
            return null;
        }
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!(Character.isLetterOrDigit(codePoint)
                    || codePoint == '_'
                    || codePoint == '-'
                    || codePoint == '.'
                    || codePoint == ' ')) {
                return null;
            }
        }
        return normalized;
    }

    private static void logFailure(SplitPlugin plugin, UUID targetId, RuntimeException exception) {
        if (plugin != null && plugin.getConfigManager().getConfigData().isDebug()) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Parse placeholder target resolution failed safely for UUID " + targetId,
                    exception
            );
        }
    }

    @FunctionalInterface
    interface TemplateResolver {
        String resolve(OfflinePlayer player, String template);
    }

    interface TargetPlayerLookup {
        OfflinePlayer findOnline(SplitPlugin plugin, String exactName);

        OfflinePlayer findKnownOffline(SplitPlugin plugin, String exactName);

        OfflinePlayer findById(SplitPlugin plugin, UUID playerId);
    }

    @FunctionalInterface
    interface BedrockDetector {
        boolean isBedrock(SplitPlugin plugin, OfflinePlayer player);
    }

    interface TargetExecutionRouter {
        boolean requiresScheduling(SplitPlugin plugin, OfflinePlayer target);

        boolean schedule(SplitPlugin plugin, OfflinePlayer target, Runnable task);
    }

    private record TargetLookupEntry(UUID playerId) {
    }
}

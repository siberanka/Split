package com.siberanka.split.placeholder.model;

import com.siberanka.split.SplitPlugin;
import com.siberanka.split.util.AdaptiveSpacingCalculator;
import com.siberanka.split.util.AdaptiveSpacingCalculator.Mode;
import com.siberanka.split.util.AdaptiveSpacingCalculator.Rounding;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.logging.Level;

/**
 * Produces a configurable result whose size is derived from the resolved source
 * text length. The result is final and is deliberately not parsed by PAPI again;
 * this makes arbitrary configured symbols (including '%') literal and prevents
 * accidental placeholder amplification.
 */
public final class AdaptivePlaceholder extends SplitPlaceholder {

    private static final UUID NULL_PLAYER_KEY = new UUID(0L, 0L);
    private static final long FAILURE_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5);

    private final List<String> sources;
    private final String sourceSeparator;
    private final Mode mode;
    private final double ratio;
    private final double base;
    private final int minimum;
    private final int maximum;
    private final Rounding rounding;
    private final boolean trimSource;
    private final boolean stripColorCodes;
    private final boolean countWhitespace;
    private final int maxSourceCharacters;
    private final ResultType resultType;
    private final String resultValue;
    private final String resultTemplate;
    private final int maxOutputLength;
    private final long cooldownMilliseconds;
    private final AdaptiveResultCache resultCache;
    private final BiFunction<OfflinePlayer, String, String> sourceResolver;
    private final AtomicLong nextFailureLogNanos = new AtomicLong(Long.MIN_VALUE);

    /**
     * Backward-compatible constructor for integrations that create the model directly.
     */
    public AdaptivePlaceholder(
            String source,
            Mode mode,
            double ratio,
            double base,
            int minimum,
            int maximum,
            Rounding rounding,
            boolean trimSource,
            boolean stripColorCodes,
            boolean countWhitespace,
            int maxSourceCharacters,
            ResultType resultType,
            String resultValue,
            String resultTemplate,
            int maxOutputLength
    ) {
        this(
                List.of(source),
                "",
                250L,
                1_024,
                mode,
                ratio,
                base,
                minimum,
                maximum,
                rounding,
                trimSource,
                stripColorCodes,
                countWhitespace,
                maxSourceCharacters,
                resultType,
                resultValue,
                resultTemplate,
                maxOutputLength
        );
    }

    public AdaptivePlaceholder(
            List<String> sources,
            String sourceSeparator,
            long cooldownMilliseconds,
            int maxCacheEntries,
            Mode mode,
            double ratio,
            double base,
            int minimum,
            int maximum,
            Rounding rounding,
            boolean trimSource,
            boolean stripColorCodes,
            boolean countWhitespace,
            int maxSourceCharacters,
            ResultType resultType,
            String resultValue,
            String resultTemplate,
            int maxOutputLength
    ) {
        this(
                sources,
                sourceSeparator,
                cooldownMilliseconds,
                maxCacheEntries,
                mode,
                ratio,
                base,
                minimum,
                maximum,
                rounding,
                trimSource,
                stripColorCodes,
                countWhitespace,
                maxSourceCharacters,
                resultType,
                resultValue,
                resultTemplate,
                maxOutputLength,
                PlaceholderAPI::setPlaceholders
        );
    }

    AdaptivePlaceholder(
            List<String> sources,
            String sourceSeparator,
            long cooldownMilliseconds,
            int maxCacheEntries,
            Mode mode,
            double ratio,
            double base,
            int minimum,
            int maximum,
            Rounding rounding,
            boolean trimSource,
            boolean stripColorCodes,
            boolean countWhitespace,
            int maxSourceCharacters,
            ResultType resultType,
            String resultValue,
            String resultTemplate,
            int maxOutputLength,
            BiFunction<OfflinePlayer, String, String> sourceResolver
    ) {
        super("adaptive");
        this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        this.sourceSeparator = Objects.requireNonNull(sourceSeparator, "sourceSeparator");
        this.mode = mode;
        this.ratio = ratio;
        this.base = base;
        this.minimum = minimum;
        this.maximum = maximum;
        this.rounding = rounding;
        this.trimSource = trimSource;
        this.stripColorCodes = stripColorCodes;
        this.countWhitespace = countWhitespace;
        this.maxSourceCharacters = maxSourceCharacters;
        this.resultType = resultType;
        this.resultValue = resultValue;
        this.resultTemplate = resultTemplate;
        this.maxOutputLength = maxOutputLength;
        this.cooldownMilliseconds = cooldownMilliseconds;
        this.resultCache = new AdaptiveResultCache(
                TimeUnit.MILLISECONDS.toNanos(cooldownMilliseconds),
                maxCacheEntries
        );
        this.sourceResolver = Objects.requireNonNull(sourceResolver, "sourceResolver");
    }

    @Override
    public String resolve(SplitPlugin plugin, OfflinePlayer player) {
        UUID cacheKey = player != null ? player.getUniqueId() : NULL_PLAYER_KEY;
        return resultCache.get(
                cacheKey,
                () -> calculateFreshResult(player),
                exception -> logRefreshFailure(plugin, exception)
        );
    }

    private String calculateFreshResult(OfflinePlayer player) {
        String resolvedSource = resolveSources(player);

        int sourceLength = AdaptiveSpacingCalculator.countCharacters(
                resolvedSource,
                trimSource,
                stripColorCodes,
                countWhitespace,
                maxSourceCharacters
        );
        int count = AdaptiveSpacingCalculator.calculate(
                sourceLength,
                mode,
                ratio,
                base,
                minimum,
                maximum,
                rounding
        );

        return switch (resultType) {
            case NUMBER -> Integer.toString(count);
            case REPEAT -> repeatSafely(resultValue, count, maxOutputLength);
            case TEMPLATE -> renderTemplate(resolvedSource, sourceLength, count);
        };
    }

    private String resolveSources(OfflinePlayer player) {
        StringBuilder resolved = new StringBuilder(Math.min(maxSourceCharacters, 256));
        int remainingCodePoints = maxSourceCharacters;

        for (int index = 0; index < sources.size() && remainingCodePoints > 0; index++) {
            if (index > 0) {
                remainingCodePoints = appendLimited(resolved, sourceSeparator, remainingCodePoints);
            }
            if (remainingCodePoints <= 0) {
                break;
            }

            String configuredSource = sources.get(index);
            String resolvedPart = configuredSource.isEmpty()
                    ? ""
                    : sourceResolver.apply(player, configuredSource);
            if (resolvedPart != null && !resolvedPart.isEmpty()) {
                remainingCodePoints = appendLimited(resolved, resolvedPart, remainingCodePoints);
            }
        }
        return resolved.toString();
    }

    private static int appendLimited(StringBuilder target, String value, int remainingCodePoints) {
        if (value.isEmpty() || remainingCodePoints <= 0) {
            return remainingCodePoints;
        }

        int offset = 0;
        int appendedCodePoints = 0;
        while (offset < value.length() && appendedCodePoints < remainingCodePoints) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            appendedCodePoints++;
        }
        target.append(value, 0, offset);
        return remainingCodePoints - appendedCodePoints;
    }

    private void logRefreshFailure(SplitPlugin plugin, RuntimeException exception) {
        if (plugin == null || !plugin.getConfigManager().getConfigData().isDebug()) {
            return;
        }

        long now = System.nanoTime();
        long nextLog = nextFailureLogNanos.get();
        if (now < nextLog || !nextFailureLogNanos.compareAndSet(nextLog, now + FAILURE_LOG_INTERVAL_NANOS)) {
            return;
        }
        plugin.getLogger().log(
                Level.WARNING,
                "Adaptive placeholder source refresh failed; serving the last cached value safely.",
                exception
        );
    }

    @Override
    public boolean shouldResolveNestedPlaceholders() {
        return false;
    }

    public ResultType getResultType() {
        return resultType;
    }

    public List<String> getSources() {
        return sources;
    }

    public String getSourceSeparator() {
        return sourceSeparator;
    }

    public long getCooldownMilliseconds() {
        return cooldownMilliseconds;
    }

    public void invalidatePlayer(UUID playerId) {
        resultCache.invalidate(playerId);
    }

    public String getResultValue() {
        return resultValue;
    }

    public String getResultTemplate() {
        return resultTemplate;
    }

    private String renderTemplate(String resolvedSource, int sourceLength, int count) {
        String rendered = resultTemplate
                .replace("{count}", Integer.toString(count))
                .replace("{length}", Integer.toString(sourceLength))
                .replace("{value}", resultValue)
                // Source is replaced last so tokens returned by another expansion stay literal.
                .replace("{source}", resolvedSource);
        return AdaptiveSpacingCalculator.truncateToCodePoints(rendered, maxOutputLength);
    }

    private static String repeatSafely(String value, int count, int maxOutputLength) {
        if (value.isEmpty() || count <= 0 || maxOutputLength <= 0) {
            return "";
        }
        int valueLength = value.codePointCount(0, value.length());
        int safeCount = Math.min(count, maxOutputLength / valueLength);
        return safeCount <= 0 ? "" : value.repeat(safeCount);
    }

    public enum ResultType {
        REPEAT,
        NUMBER,
        TEMPLATE;

        public static ResultType parse(String value) {
            if (value == null) {
                return REPEAT;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "repeat", "character", "symbol", "space" -> REPEAT;
                case "number", "count", "numeric" -> NUMBER;
                case "template", "format", "text" -> TEMPLATE;
                default -> throw new IllegalArgumentException("Unknown adaptive result type: " + value);
            };
        }
    }
}

package com.siberanka.split.placeholder.model;

import com.siberanka.split.SplitPlugin;
import com.siberanka.split.util.AdaptiveSpacingCalculator;
import com.siberanka.split.util.AdaptiveSpacingCalculator.Mode;
import com.siberanka.split.util.AdaptiveSpacingCalculator.Rounding;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * Produces a configurable result whose size is derived from the resolved source
 * text length. The result is final and is deliberately not parsed by PAPI again;
 * this makes arbitrary configured symbols (including '%') literal and prevents
 * accidental placeholder amplification.
 */
public final class AdaptivePlaceholder extends SplitPlaceholder {

    private final String source;
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
        super("adaptive");
        this.source = source;
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
    }

    @Override
    public String resolve(SplitPlugin plugin, OfflinePlayer player) {
        String resolvedSource = source.isEmpty() ? "" : PlaceholderAPI.setPlaceholders(player, source);
        if (resolvedSource == null) {
            resolvedSource = "";
        }
        // Bound data returned by third-party expansions before counting or embedding it in a template.
        resolvedSource = AdaptiveSpacingCalculator.truncateToCodePoints(resolvedSource, maxSourceCharacters);

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

    @Override
    public boolean shouldResolveNestedPlaceholders() {
        return false;
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

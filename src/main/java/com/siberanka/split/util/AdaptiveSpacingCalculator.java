package com.siberanka.split.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure calculation helpers for adaptive placeholders. Keeping this class free of
 * Bukkit dependencies makes the hot path deterministic and straightforward to test.
 */
public final class AdaptiveSpacingCalculator {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile(
            "(?i)(?:[&§]#[0-9a-f]{6}|[&§]x(?:[&§][0-9a-f]){6})"
    );
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("(?i)[&§][0-9a-fk-or]");

    private AdaptiveSpacingCalculator() {
    }

    public static int calculate(
            int sourceLength,
            Mode mode,
            double ratio,
            double base,
            int minimum,
            int maximum,
            Rounding rounding
    ) {
        if (sourceLength < 0) {
            throw new IllegalArgumentException("Source length cannot be negative");
        }
        if (!Double.isFinite(ratio) || ratio < 0) {
            throw new IllegalArgumentException("Ratio must be finite and non-negative");
        }
        if (!Double.isFinite(base)) {
            throw new IllegalArgumentException("Base must be finite");
        }
        if (minimum < 0 || maximum < minimum) {
            throw new IllegalArgumentException("Invalid minimum/maximum range");
        }

        double delta = sourceLength * ratio;
        double raw = mode == Mode.DIRECT ? base + delta : base - delta;
        double bounded = Math.max(minimum, Math.min(maximum, raw));

        long rounded = switch (rounding) {
            case FLOOR -> (long) Math.floor(bounded);
            case CEILING -> (long) Math.ceil(bounded);
            case NEAREST -> Math.round(bounded);
        };
        return (int) Math.max(minimum, Math.min(maximum, rounded));
    }

    public static int countCharacters(
            String input,
            boolean trim,
            boolean stripColorCodes,
            boolean countWhitespace,
            int maxSourceCharacters
    ) {
        if (input == null || input.isEmpty() || maxSourceCharacters <= 0) {
            return 0;
        }

        String value = truncateToCodePoints(input, maxSourceCharacters);
        if (stripColorCodes) {
            value = HEX_COLOR_PATTERN.matcher(value).replaceAll("");
            value = LEGACY_COLOR_PATTERN.matcher(value).replaceAll("");
        }
        if (trim) {
            value = value.strip();
        }

        int count = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (countWhitespace || (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint))) {
                count++;
            }
        }
        return count;
    }

    public static String truncateToCodePoints(String value, int maximumCodePoints) {
        if (value == null || value.isEmpty() || maximumCodePoints <= 0) {
            return "";
        }
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= maximumCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maximumCodePoints));
    }

    public enum Mode {
        DIRECT,
        INVERSE;

        public static Mode parse(String value) {
            if (value == null) {
                return DIRECT;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "direct", "increase", "increasing", "duz", "düz" -> DIRECT;
                case "inverse", "reverse", "decrease", "decreasing", "ters" -> INVERSE;
                default -> throw new IllegalArgumentException("Unknown adaptive mode: " + value);
            };
        }
    }

    public enum Rounding {
        FLOOR,
        CEILING,
        NEAREST;

        public static Rounding parse(String value) {
            if (value == null) {
                return NEAREST;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "floor", "down", "asagi", "aşağı" -> FLOOR;
                case "ceil", "ceiling", "up", "yukari", "yukarı" -> CEILING;
                case "round", "nearest", "closest", "yakin", "yakın" -> NEAREST;
                default -> throw new IllegalArgumentException("Unknown rounding mode: " + value);
            };
        }
    }
}

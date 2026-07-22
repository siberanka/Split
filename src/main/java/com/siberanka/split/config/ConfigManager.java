package com.siberanka.split.config;

import com.siberanka.split.SplitPlugin;
import com.siberanka.split.placeholder.model.AdaptivePlaceholder;
import com.siberanka.split.placeholder.model.SplitPlaceholder;
import com.siberanka.split.placeholder.model.SimplePlaceholder;
import com.siberanka.split.placeholder.model.SwitchPlaceholder;
import com.siberanka.split.placeholder.model.ExpressionPlaceholder;
import com.siberanka.split.util.AdaptiveSpacingCalculator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ConfigManager {

    private static final int MAX_ADAPTIVE_COUNT = 4_096;
    private static final int MIN_ADAPTIVE_COUNT = -MAX_ADAPTIVE_COUNT;
    private static final int MAX_SOURCE_CHARACTERS = 32_768;
    private static final int MAX_OUTPUT_LENGTH = 16_384;
    private static final int MAX_CONFIGURED_TEXT_LENGTH = 4_096;
    private static final int MAX_CONFIGURED_SOURCE_TOTAL_LENGTH = 8_192;
    private static final int MAX_SOURCE_ENTRIES = 32;
    private static final int MAX_RESULT_VALUE_LENGTH = 128;
    private static final int MAX_SOURCE_SEPARATOR_LENGTH = 128;
    private static final int MIN_COOLDOWN_MILLISECONDS = 100;
    private static final int MAX_COOLDOWN_MILLISECONDS = 60_000;
    private static final int MAX_CACHE_ENTRIES = 4_096;

    private final SplitPlugin plugin;
    private volatile ConfigData configData;

    public ConfigManager(SplitPlugin plugin) {
        this.plugin = plugin;
        this.configData = ConfigData.empty();
    }

    /**
     * Initializes and loads the configuration files.
     * Throws exception if loading fails (e.g. YAML syntax errors).
     */
    public synchronized void load() throws Exception {
        // Ensure plugin data folder exists
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder: " + plugin.getDataFolder());
        }

        // Save defaults if not present
        saveDefaultResource("config.yml");
        saveDefaultResource("messages.yml");
        saveDefaultResource("placeholders.yml");

        // Files
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        File placeholdersFile = new File(plugin.getDataFolder(), "placeholders.yml");

        // Parse files. Use .load() to throw exceptions on syntax errors
        YamlConfiguration configYaml = new YamlConfiguration();
        configYaml.load(configFile);

        YamlConfiguration messagesYaml = new YamlConfiguration();
        messagesYaml.load(messagesFile);

        YamlConfiguration placeholdersYaml = new YamlConfiguration();
        placeholdersYaml.load(placeholdersFile);

        // Read config.yml
        boolean debug = configYaml.getBoolean("debug", false);
        boolean defaultToJavaOnNull = configYaml.getBoolean("default-to-java-on-null", true);

        // Read messages.yml
        Map<String, String> messages = new HashMap<>();
        String prefix = messagesYaml.getString("prefix", "&8[&bSplit&8] &r");
        messages.put("prefix", prefix);

        for (String key : messagesYaml.getKeys(true)) {
            if (key.equals("prefix")) continue;
            String val = messagesYaml.getString(key);
            if (val != null) {
                // Replace %prefix% placeholder with actual prefix value
                val = val.replace("%prefix%", prefix);
                messages.put(key, val);
            }
        }

        // Read placeholders.yml
        Map<String, SplitPlaceholder> placeholders = new HashMap<>();
        ConfigurationSection root = placeholdersYaml.getConfigurationSection("");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                if (placeholdersYaml.isConfigurationSection(key)) {
                    ConfigurationSection sec = placeholdersYaml.getConfigurationSection(key);
                    if (sec != null) {
                        String type = sec.getString("type", "").trim().toLowerCase(Locale.ROOT).replace('_', '-');

                        // Infer type if missing
                        if (type.isEmpty()) {
                            if (sec.contains("switch")) {
                                type = "switch";
                            } else if (sec.contains("formule")) {
                                type = "expression";
                            } else if (sec.contains("source") || sec.isConfigurationSection("calculation")) {
                                type = "adaptive";
                            } else {
                                type = "simple";
                            }
                        }

                        SplitPlaceholder placeholderObj = null;
                        switch (type) {
                            case "switch":
                                String switchVal = sec.getString("switch", "");
                                Map<String, String> cases = new HashMap<>();
                                ConfigurationSection caseSec = sec.getConfigurationSection("case");
                                if (caseSec != null) {
                                    for (String caseKey : caseSec.getKeys(false)) {
                                        // Store in lowercase and as-is for maximum compatibility
                                        String caseVal = caseSec.getString(caseKey, "");
                                        cases.put(caseKey.toLowerCase(Locale.ROOT), caseVal);
                                        cases.put(caseKey, caseVal);
                                    }
                                }
                                placeholderObj = new SwitchPlaceholder(switchVal, cases);
                                break;
                            case "expression":
                                String formule = sec.getString("formule", "");
                                String trueVal = sec.getString("true", "");
                                String falseVal = sec.getString("false", "");
                                placeholderObj = new ExpressionPlaceholder(formule, trueVal, falseVal);
                                break;
                            case "adaptive":
                            case "adaptive-space":
                            case "adaptive-spacing":
                                placeholderObj = parseAdaptivePlaceholder(key, sec);
                                break;
                            case "simple":
                                String javaVal = sec.getString("java", "");
                                String bedrockVal = sec.getString("bedrock", "");
                                placeholderObj = new SimplePlaceholder(javaVal, bedrockVal);
                                break;
                            default:
                                throw new IllegalArgumentException(
                                        "Placeholder '" + key + "' has unknown type: " + type
                                );
                        }

                        if (placeholderObj != null) {
                            placeholders.put(key.toLowerCase(Locale.ROOT), placeholderObj);
                        }
                    }
                }
            }
        }

        // Atomic swap
        this.configData = new ConfigData(debug, defaultToJavaOnNull, messages, placeholders);
    }

    static AdaptivePlaceholder parseAdaptivePlaceholder(String key, ConfigurationSection section) {
        String context = "Adaptive placeholder '" + key + "'";
        List<String> sources = readStringList(section, "source");
        if (sources.isEmpty() || sources.size() > MAX_SOURCE_ENTRIES) {
            throw new IllegalArgumentException(context + " source must contain between 1 and "
                    + MAX_SOURCE_ENTRIES + " entries");
        }
        int configuredSourceLength = 0;
        for (int index = 0; index < sources.size(); index++) {
            String source = sources.get(index);
            validateConfiguredText(
                    context + " source[" + index + "]",
                    source,
                    MAX_CONFIGURED_TEXT_LENGTH,
                    false
            );
            configuredSourceLength += source.codePointCount(0, source.length());
            if (configuredSourceLength > MAX_CONFIGURED_SOURCE_TOTAL_LENGTH) {
                throw new IllegalArgumentException(context + " combined source exceeds "
                        + MAX_CONFIGURED_SOURCE_TOTAL_LENGTH + " characters");
            }
        }

        AdaptiveSpacingCalculator.Mode mode = AdaptiveSpacingCalculator.Mode.parse(
                readString(section, "direct", "calculation.mode", "mode")
        );
        double ratio = readDouble(section, 1.0D, "calculation.ratio", "ratio");
        int minimum = readInteger(section, 0, "calculation.minimum", "minimum", "min-spaces", "min");
        int maximum = readInteger(section, 64, "calculation.maximum", "maximum", "max-spaces", "max");
        double defaultBase = switch (mode) {
            case REVERSE, INVERSE -> maximum;
            case DIRECT, MAP -> minimum;
        };
        double base = readDouble(section, defaultBase, "calculation.base", "base-spaces", "base");
        AdaptiveSpacingCalculator.Rounding rounding = AdaptiveSpacingCalculator.Rounding.parse(
                readString(section, "nearest", "calculation.rounding", "rounding")
        );

        if (!Double.isFinite(ratio) || ratio < 0) {
            throw new IllegalArgumentException(context + " ratio must be finite and non-negative");
        }
        if (!Double.isFinite(base)) {
            throw new IllegalArgumentException(context + " base must be finite");
        }
        if (minimum < MIN_ADAPTIVE_COUNT
                || minimum > MAX_ADAPTIVE_COUNT
                || maximum < MIN_ADAPTIVE_COUNT
                || maximum > MAX_ADAPTIVE_COUNT) {
            throw new IllegalArgumentException(context + " minimum and maximum must each be between "
                    + MIN_ADAPTIVE_COUNT + " and " + MAX_ADAPTIVE_COUNT);
        }
        if (mode != AdaptiveSpacingCalculator.Mode.MAP && maximum < minimum) {
            throw new IllegalArgumentException(context
                    + " minimum cannot exceed maximum in direct or reverse mode");
        }

        boolean trimSource = readBoolean(section, false, "source-options.trim", "trim-source");
        boolean stripColorCodes = readBoolean(
                section,
                true,
                "source-options.strip-color-codes",
                "strip-color-codes",
                "ignore-color-codes"
        );
        boolean countWhitespace = readBoolean(
                section,
                true,
                "source-options.count-whitespace",
                "count-whitespace"
        );
        int maxSourceCharacters = readInteger(
                section,
                8_192,
                "source-options.max-characters",
                "max-source-characters"
        );
        if (maxSourceCharacters < 1 || maxSourceCharacters > MAX_SOURCE_CHARACTERS) {
            throw new IllegalArgumentException(context + " source character limit must be between 1 and "
                    + MAX_SOURCE_CHARACTERS);
        }
        int mapSourceMinimum = readInteger(
                section,
                0,
                "calculation.source-minimum",
                "source-minimum",
                "input-minimum"
        );
        int mapSourceMaximum = readInteger(
                section,
                maxSourceCharacters,
                "calculation.source-maximum",
                "source-maximum",
                "input-maximum"
        );
        if (mapSourceMinimum < 0
                || mapSourceMaximum <= mapSourceMinimum
                || mapSourceMaximum > maxSourceCharacters) {
            throw new IllegalArgumentException(context + " map source range must satisfy 0 <= source-minimum"
                    + " < source-maximum <= source-options.max-characters");
        }
        String sourceSeparator = readString(
                section,
                "",
                "source-options.separator",
                "source-separator",
                "separator"
        );
        validateConfiguredText(
                context + " source separator",
                sourceSeparator,
                MAX_SOURCE_SEPARATOR_LENGTH,
                true
        );
        int cooldownMilliseconds = readInteger(
                section,
                250,
                "source-options.cooldown-milliseconds",
                "cooldown-milliseconds",
                "cooldown-ms",
                "cooldown"
        );
        if (cooldownMilliseconds < MIN_COOLDOWN_MILLISECONDS
                || cooldownMilliseconds > MAX_COOLDOWN_MILLISECONDS) {
            throw new IllegalArgumentException(context + " cooldown must be between "
                    + MIN_COOLDOWN_MILLISECONDS + " and " + MAX_COOLDOWN_MILLISECONDS + " milliseconds");
        }
        int maxCacheEntries = readInteger(
                section,
                1_024,
                "source-options.max-cache-entries",
                "max-cache-entries",
                "cache-size"
        );
        if (maxCacheEntries < 1 || maxCacheEntries > MAX_CACHE_ENTRIES) {
            throw new IllegalArgumentException(context + " max cache entries must be between 1 and "
                    + MAX_CACHE_ENTRIES);
        }

        AdaptivePlaceholder.ResultType resultType = AdaptivePlaceholder.ResultType.parse(
                readString(section, "repeat", "result.type", "output-mode")
        );
        String resultValue = readString(
                section,
                " ",
                "result.value",
                "value",
                "output-value",
                "space-character"
        );
        String resultTemplate = readString(
                section,
                "{count}",
                "result.template",
                "template",
                "output-template"
        );
        int maxOutputLength = readInteger(
                section,
                8_192,
                "result.max-length",
                "max-output-length"
        );
        validateConfiguredText(context + " result value", resultValue, MAX_RESULT_VALUE_LENGTH, true);
        validateConfiguredText(context + " result template", resultTemplate, MAX_CONFIGURED_TEXT_LENGTH, true);
        if (maxOutputLength < 1 || maxOutputLength > MAX_OUTPUT_LENGTH) {
            throw new IllegalArgumentException(context + " output length limit must be between 1 and "
                    + MAX_OUTPUT_LENGTH);
        }

        return new AdaptivePlaceholder(
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
                mapSourceMinimum,
                mapSourceMaximum,
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

    private static List<String> readStringList(ConfigurationSection section, String path) {
        if (!section.contains(path)) {
            return Collections.emptyList();
        }
        Object value = section.get(path);
        if (value instanceof String text) {
            return List.of(text);
        }
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException("Expected text or a text list at '" + path + "'");
        }

        List<String> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Object entry = values.get(index);
            if (!(entry instanceof String text)) {
                throw new IllegalArgumentException("Expected text at '" + path + "[" + index + "]'");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private static void validateConfiguredText(String field, String value, int maximumCodePoints, boolean allowEmpty) {
        if (!allowEmpty && value.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }
        if (value.codePointCount(0, value.length()) > maximumCodePoints) {
            throw new IllegalArgumentException(field + " exceeds " + maximumCodePoints + " characters");
        }
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException(field + " cannot contain control characters");
            }
        }
    }

    private static String readString(ConfigurationSection section, String defaultValue, String... paths) {
        Object value = firstDefined(section, paths);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Expected text at '" + firstDefinedPath(section, paths) + "'");
        }
        return (String) value;
    }

    private static double readDouble(ConfigurationSection section, double defaultValue, String... paths) {
        Object value = firstDefined(section, paths);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Expected a number at '" + firstDefinedPath(section, paths) + "'", exception);
        }
    }

    private static int readInteger(ConfigurationSection section, int defaultValue, String... paths) {
        Object value = firstDefined(section, paths);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            double numericValue = number.doubleValue();
            int integerValue = number.intValue();
            if (!Double.isFinite(numericValue) || numericValue != integerValue) {
                throw new IllegalArgumentException("Expected a whole number at '" + firstDefinedPath(section, paths) + "'");
            }
            return integerValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Expected a whole number at '" + firstDefinedPath(section, paths) + "'", exception);
        }
    }

    private static boolean readBoolean(ConfigurationSection section, boolean defaultValue, String... paths) {
        Object value = firstDefined(section, paths);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            if (text.equalsIgnoreCase("true")) {
                return true;
            }
            if (text.equalsIgnoreCase("false")) {
                return false;
            }
        }
        throw new IllegalArgumentException("Expected true or false at '" + firstDefinedPath(section, paths) + "'");
    }

    private static Object firstDefined(ConfigurationSection section, String... paths) {
        for (String path : paths) {
            if (section.contains(path)) {
                return section.get(path);
            }
        }
        return null;
    }

    private static String firstDefinedPath(ConfigurationSection section, String... paths) {
        for (String path : paths) {
            if (section.contains(path)) {
                return path;
            }
        }
        return paths.length == 0 ? "unknown" : paths[0];
    }

    private void saveDefaultResource(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
    }

    public ConfigData getConfigData() {
        return configData;
    }

    public static class ConfigData {
        private final boolean debug;
        private final boolean defaultToJavaOnNull;
        private final Map<String, String> messages;
        private final Map<String, SplitPlaceholder> placeholders;

        public ConfigData(boolean debug, boolean defaultToJavaOnNull, Map<String, String> messages, Map<String, SplitPlaceholder> placeholders) {
            this.debug = debug;
            this.defaultToJavaOnNull = defaultToJavaOnNull;
            this.messages = Collections.unmodifiableMap(new HashMap<>(messages));
            this.placeholders = Collections.unmodifiableMap(new HashMap<>(placeholders));
        }

        private static ConfigData empty() {
            return new ConfigData(false, true, Collections.emptyMap(), Collections.emptyMap());
        }

        public boolean isDebug() {
            return debug;
        }

        public boolean isDefaultToJavaOnNull() {
            return defaultToJavaOnNull;
        }

        public String getMessage(String key, String def) {
            return messages.getOrDefault(key, def);
        }

        public SplitPlaceholder getPlaceholder(String key) {
            return placeholders.get(key);
        }

        public Map<String, SplitPlaceholder> getPlaceholders() {
            return placeholders;
        }

        public void invalidatePlayer(UUID playerId) {
            for (SplitPlaceholder placeholder : placeholders.values()) {
                if (placeholder instanceof AdaptivePlaceholder adaptivePlaceholder) {
                    adaptivePlaceholder.invalidatePlayer(playerId);
                }
            }
        }
    }
}

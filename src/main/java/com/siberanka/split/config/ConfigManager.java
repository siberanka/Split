package com.siberanka.split.config;

import com.siberanka.split.SplitPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final SplitPlugin plugin;
    private volatile ConfigData configData;

    public ConfigManager(SplitPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes and loads the configuration files.
     * Throws exception if loading fails (e.g. YAML syntax errors).
     */
    public void load() throws Exception {
        // Ensure plugin data folder exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
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
        Map<String, PlaceholderPair> placeholders = new HashMap<>();
        ConfigurationSection root = placeholdersYaml.getConfigurationSection("");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                if (placeholdersYaml.isConfigurationSection(key)) {
                    ConfigurationSection sec = placeholdersYaml.getConfigurationSection(key);
                    if (sec != null) {
                        String javaVal = sec.getString("java", "");
                        String bedrockVal = sec.getString("bedrock", "");
                        placeholders.put(key.toLowerCase(), new PlaceholderPair(javaVal, bedrockVal));
                    }
                }
            }
        }

        // Atomic swap
        this.configData = new ConfigData(debug, defaultToJavaOnNull, messages, placeholders);
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

    public static class PlaceholderPair {
        private final String javaVal;
        private final String bedrockVal;

        public PlaceholderPair(String javaVal, String bedrockVal) {
            this.javaVal = javaVal != null ? javaVal : "";
            this.bedrockVal = bedrockVal != null ? bedrockVal : "";
        }

        public String getJavaVal() {
            return javaVal;
        }

        public String getBedrockVal() {
            return bedrockVal;
        }
    }

    public static class ConfigData {
        private final boolean debug;
        private final boolean defaultToJavaOnNull;
        private final Map<String, String> messages;
        private final Map<String, PlaceholderPair> placeholders;

        public ConfigData(boolean debug, boolean defaultToJavaOnNull, Map<String, String> messages, Map<String, PlaceholderPair> placeholders) {
            this.debug = debug;
            this.defaultToJavaOnNull = defaultToJavaOnNull;
            this.messages = Collections.unmodifiableMap(messages);
            this.placeholders = Collections.unmodifiableMap(placeholders);
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

        public PlaceholderPair getPlaceholder(String key) {
            return placeholders.get(key);
        }

        public Map<String, PlaceholderPair> getPlaceholders() {
            return placeholders;
        }
    }
}

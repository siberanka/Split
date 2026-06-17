package com.siberanka.split.config;

import com.siberanka.split.SplitPlugin;
import com.siberanka.split.placeholder.model.SplitPlaceholder;
import com.siberanka.split.placeholder.model.SimplePlaceholder;
import com.siberanka.split.placeholder.model.SwitchPlaceholder;
import com.siberanka.split.placeholder.model.ExpressionPlaceholder;
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
        Map<String, SplitPlaceholder> placeholders = new HashMap<>();
        ConfigurationSection root = placeholdersYaml.getConfigurationSection("");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                if (placeholdersYaml.isConfigurationSection(key)) {
                    ConfigurationSection sec = placeholdersYaml.getConfigurationSection(key);
                    if (sec != null) {
                        String type = sec.getString("type", "").toLowerCase();

                        // Infer type if missing
                        if (type.isEmpty()) {
                            if (sec.contains("switch")) {
                                type = "switch";
                            } else if (sec.contains("formule")) {
                                type = "expression";
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
                                        cases.put(caseKey.toLowerCase(), caseVal);
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
                            case "simple":
                            default:
                                String javaVal = sec.getString("java", "");
                                String bedrockVal = sec.getString("bedrock", "");
                                placeholderObj = new SimplePlaceholder(javaVal, bedrockVal);
                                break;
                        }

                        if (placeholderObj != null) {
                            placeholders.put(key.toLowerCase(), placeholderObj);
                        }
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

    public static class ConfigData {
        private final boolean debug;
        private final boolean defaultToJavaOnNull;
        private final Map<String, String> messages;
        private final Map<String, SplitPlaceholder> placeholders;

        public ConfigData(boolean debug, boolean defaultToJavaOnNull, Map<String, String> messages, Map<String, SplitPlaceholder> placeholders) {
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

        public SplitPlaceholder getPlaceholder(String key) {
            return placeholders.get(key);
        }

        public Map<String, SplitPlaceholder> getPlaceholders() {
            return placeholders;
        }
    }
}

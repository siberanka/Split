package com.siberanka.split.placeholder;

import com.siberanka.split.SplitPlugin;
import com.siberanka.split.config.ConfigManager;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.HashSet;
import java.util.Set;

public class SplitPlaceholderExpansion extends PlaceholderExpansion {

    private final SplitPlugin plugin;
    // ThreadLocal recursion tracker to safeguard the server against infinite loop configuration errors
    private final ThreadLocal<Set<String>> resolvingPlaceholders = ThreadLocal.withInitial(HashSet::new);

    public SplitPlaceholderExpansion(SplitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "split";
    }

    @Override
    public String getAuthor() {
        return "SiberAnka";
    }

    @Override
    public String getVersion() {
        return "1.0.0-beta";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        ConfigManager.ConfigData config = plugin.getConfigManager().getConfigData();
        if (config == null) {
            return null;
        }

        String paramLower = params.toLowerCase();
        ConfigManager.PlaceholderPair pair = config.getPlaceholder(paramLower);
        if (pair == null) {
            return null; // Placeholder key not registered
        }

        // Circular reference recursion check
        Set<String> active = resolvingPlaceholders.get();
        if (!active.add(paramLower)) {
            if (config.isDebug()) {
                plugin.getLogger().warning("Circular placeholder reference loop detected: %split_" + params + "%");
            }
            return "[Split Loop: " + params + "]";
        }

        try {
            boolean isBedrock = plugin.isBedrock(player);
            String template = isBedrock ? pair.getBedrockVal() : pair.getJavaVal();

            if (template == null || template.isEmpty()) {
                return "";
            }

            // Parse nested placeholders (like %player_name% or %vault_prefix%) inside the returned value
            return PlaceholderAPI.setPlaceholders(player, template);
        } finally {
            active.remove(paramLower);
        }
    }
}


package com.siberanka.split.placeholder;

import com.siberanka.split.SplitPlugin;
import com.siberanka.split.config.ConfigManager;
import com.siberanka.split.placeholder.model.SplitPlaceholder;
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
        // Retrieve version dynamically from the plugin description (pom.xml filtered)
        return plugin.getDescription().getVersion();
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
        SplitPlaceholder placeholder = config.getPlaceholder(paramLower);
        if (placeholder == null) {
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
            // Resolve placeholder based on its polymorphic type rules (simple, switch, expression)
            String template = placeholder.resolve(plugin, player);

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

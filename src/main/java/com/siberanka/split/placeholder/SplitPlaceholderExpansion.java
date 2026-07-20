package com.siberanka.split.placeholder;

import com.siberanka.split.SplitPlugin;
import com.siberanka.split.config.ConfigManager;
import com.siberanka.split.placeholder.model.SplitPlaceholder;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

public class SplitPlaceholderExpansion extends PlaceholderExpansion {

    private static final int MAX_PARAMETER_LENGTH = 128;
    private static final int MAX_RESOLUTION_DEPTH = 32;

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
        if (params == null || params.isEmpty() || params.length() > MAX_PARAMETER_LENGTH) {
            return null;
        }

        ConfigManager.ConfigData config = plugin.getConfigManager().getConfigData();
        String paramLower = params.toLowerCase(Locale.ROOT);
        SplitPlaceholder placeholder = config.getPlaceholder(paramLower);
        if (placeholder == null) {
            return null; // Placeholder key not registered
        }

        // Circular reference recursion check
        Set<String> active = resolvingPlaceholders.get();
        if (active.size() >= MAX_RESOLUTION_DEPTH) {
            if (config.isDebug()) {
                plugin.getLogger().warning("Placeholder resolution depth exceeded for: " + sanitizeForLog(params));
            }
            return "[Split Depth Limit]";
        }
        if (!active.add(paramLower)) {
            if (config.isDebug()) {
                plugin.getLogger().warning("Circular placeholder reference loop detected: %split_"
                        + sanitizeForLog(params) + "%");
            }
            return "[Split Loop: " + sanitizeForLog(params) + "]";
        }

        try {
            // Resolve placeholder based on its polymorphic type rules (simple, switch, expression)
            String template = placeholder.resolve(plugin, player);

            if (template == null || template.isEmpty()) {
                return "";
            }

            // Parse nested placeholders (like %player_name% or %vault_prefix%) inside the returned value
            if (!placeholder.shouldResolveNestedPlaceholders()) {
                return template;
            }
            String resolved = PlaceholderAPI.setPlaceholders(player, template);
            return resolved != null ? resolved : "";
        } catch (RuntimeException exception) {
            if (config.isDebug()) {
                plugin.getLogger().log(
                        Level.WARNING,
                        "Placeholder resolution failed safely for: " + sanitizeForLog(params),
                        exception
                );
            }
            return "";
        } finally {
            active.remove(paramLower);
            if (active.isEmpty()) {
                resolvingPlaceholders.remove();
            }
        }
    }

    private static String sanitizeForLog(String value) {
        StringBuilder sanitized = new StringBuilder(Math.min(value.length(), 64));
        for (int offset = 0; offset < value.length() && sanitized.length() < 64; ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!Character.isISOControl(codePoint)) {
                sanitized.appendCodePoint(codePoint);
            }
        }
        return sanitized.toString();
    }
}

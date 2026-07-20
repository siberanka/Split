package com.siberanka.split.placeholder.model;

import com.siberanka.split.SplitPlugin;
import org.bukkit.OfflinePlayer;

public abstract class SplitPlaceholder {
    private final String type;

    public SplitPlaceholder(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    /**
     * Whether the value returned by {@link #resolve(SplitPlugin, OfflinePlayer)}
     * still contains templates that PlaceholderAPI should resolve.
     */
    public boolean shouldResolveNestedPlaceholders() {
        return true;
    }

    /**
     * Resolves the raw template value based on placeholder rules and the player.
     *
     * @param plugin The Split plugin instance
     * @param player The player requesting the placeholder (can be null or offline)
     * @return Resolved template containing nested placeholders or values
     */
    public abstract String resolve(SplitPlugin plugin, OfflinePlayer player);
}

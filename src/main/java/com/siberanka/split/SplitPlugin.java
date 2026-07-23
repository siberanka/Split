package com.siberanka.split;

import com.siberanka.split.command.SplitCommand;
import com.siberanka.split.config.ConfigManager;
import com.siberanka.split.documentation.DocumentationManager;
import com.siberanka.split.listener.PlayerCacheListener;
import com.siberanka.split.platform.PlatformScheduler;
import com.siberanka.split.placeholder.SplitPlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class SplitPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private DocumentationManager documentationManager;
    private PlatformScheduler platformScheduler;
    private SplitPlaceholderExpansion placeholderExpansion;
    private boolean floodgatePresent;
    private boolean placeholderApiPresent;

    @Override
    public void onEnable() {
        this.platformScheduler = new PlatformScheduler(this);
        this.documentationManager = new DocumentationManager(this);
        refreshDocumentation();

        // Initialize Configuration Manager
        this.configManager = new ConfigManager(this);
        try {
            this.configManager.load();
            getLogger().info("Configurations loaded successfully.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not load configuration files! Using internal defaults where possible.", e);
        }

        // Check for integrations
        checkIntegrations();

        // Remove per-player adaptive/parse cache entries immediately when sessions end.
        getServer().getPluginManager().registerEvents(new PlayerCacheListener(this), this);

        // Register Command
        SplitCommand commandExecutor = new SplitCommand(this);
        if (getCommand("split") != null) {
            getCommand("split").setExecutor(commandExecutor);
            getCommand("split").setTabCompleter(commandExecutor);
        }

        // Register Placeholders
        if (placeholderApiPresent) {
            this.placeholderExpansion = new SplitPlaceholderExpansion(this);
            this.placeholderExpansion.register();
            getLogger().info("PlaceholderAPI expansion registered successfully.");
        } else {
            getLogger().warning("PlaceholderAPI not found! Custom placeholders (%split_<key>%) will not be registered.");
        }

        String platform = platformScheduler.isFolia() ? "Folia" : "Bukkit/Paper";
        getLogger().info("Split plugin version " + getDescription().getVersion()
                + " has been enabled with " + platform + " scheduler support.");
    }

    @Override
    public void onDisable() {
        if (platformScheduler != null) {
            platformScheduler.shutdown();
        }

        // Unregister PAPI Expansion to prevent memory/classloader leaks during reloads
        if (placeholderExpansion != null) {
            try {
                placeholderExpansion.unregister();
                getLogger().info("PlaceholderAPI expansion unregistered successfully.");
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Error unregistering PlaceholderAPI expansion:", t);
            }
            this.placeholderExpansion = null;
        }

        getLogger().info("Split plugin has been disabled.");
    }

    private void checkIntegrations() {
        // Check Floodgate API
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            this.floodgatePresent = getServer().getPluginManager().isPluginEnabled("floodgate");
        } catch (ClassNotFoundException e) {
            this.floodgatePresent = false;
        }

        if (this.floodgatePresent) {
            getLogger().info("Floodgate API integration verified. Bedrock player detection enabled.");
        } else {
            getLogger().info("Floodgate API not found. All players will resolve as Java players.");
        }

        // Check PlaceholderAPI
        this.placeholderApiPresent = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    /**
     * Checks if the given player is connected via Bedrock Edition using the Floodgate API.
     *
     * @param player the player to check
     * @return true if the player is Bedrock; false if Java or cannot be verified
     */
    public boolean isBedrock(OfflinePlayer player) {
        if (player == null) {
            return false;
        }
        if (!floodgatePresent) {
            return false;
        }
        try {
            return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Throwable t) {
            if (configManager.getConfigData().isDebug()) {
                getLogger().log(Level.WARNING, "Failed to check Bedrock status for player " + player.getName(), t);
            }
            return false;
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlatformScheduler getPlatformScheduler() {
        return platformScheduler;
    }

    /**
     * Synchronizes the bundled wiki.yml without making documentation availability
     * a prerequisite for normal plugin operation.
     */
    public void refreshDocumentation() {
        try {
            if (documentationManager.sync()) {
                getLogger().info("wiki.yml documentation created or updated successfully.");
            }
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "Could not create or refresh wiki.yml documentation.", exception);
        }
    }

    public boolean isFloodgatePresent() {
        return floodgatePresent;
    }

    public boolean isPlaceholderApiPresent() {
        return placeholderApiPresent;
    }
}

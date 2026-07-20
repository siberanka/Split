package com.siberanka.split.listener;

import com.siberanka.split.SplitPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Clears bounded adaptive cache entries as soon as a player session ends. */
public final class PlayerCacheListener implements Listener {

    private final SplitPlugin plugin;

    public PlayerCacheListener(SplitPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getConfigManager().getConfigData().invalidatePlayer(event.getPlayer().getUniqueId());
    }
}

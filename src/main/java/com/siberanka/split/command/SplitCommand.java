package com.siberanka.split.command;

import com.siberanka.split.SplitPlugin;
import com.siberanka.split.config.ConfigManager;
import com.siberanka.split.util.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class SplitCommand implements CommandExecutor, TabCompleter {

    private final SplitPlugin plugin;
    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);

    public SplitCommand(SplitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager.ConfigData config = plugin.getConfigManager().getConfigData();

        if (!sender.hasPermission("split.admin")) {
            String msg = config.getMessage("no-permission", "&cBu komutu kullanmak için yetkiniz yok!");
            sender.sendMessage(ColorUtils.colorize(msg));
            return true;
        }

        if (args.length == 0) {
            String usage = config.getMessage("invalid-usage", "&cGeçersiz kullanım! &fKullanım: /split reload");
            sender.sendMessage(ColorUtils.colorize(usage));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!reloadInProgress.compareAndSet(false, true)) {
                String msg = config.getMessage("reload-in-progress", "&eBir yapılandırma yenilemesi zaten devam ediyor.");
                sender.sendMessage(ColorUtils.colorize(msg));
                return true;
            }

            String senderName = sender.getName();
            // Disk parsing stays off tick threads on Bukkit/Paper and Folia.
            try {
                plugin.getPlatformScheduler().runAsync(() -> {
                    String response;
                    try {
                        plugin.refreshDocumentation();
                        plugin.getConfigManager().load();
                        ConfigManager.ConfigData newConfig = plugin.getConfigManager().getConfigData();
                        response = newConfig.getMessage(
                                "reload-success",
                                "&aYapılandırma dosyaları başarıyla yenilendi."
                        );
                        if (newConfig.isDebug()) {
                            plugin.getLogger().info("Plugin configuration reloaded successfully by " + senderName);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "An error occurred while reloading the configurations:", e);
                        response = config.getMessage(
                                "reload-failure",
                                "&cYapılandırma dosyaları yenilenirken bir hata oluştu!"
                        );
                    } finally {
                        reloadInProgress.set(false);
                    }

                    sendOnOwningThread(sender, response);
                });
            } catch (RuntimeException exception) {
                reloadInProgress.set(false);
                plugin.getLogger().log(Level.SEVERE, "Could not schedule the configuration reload safely.", exception);
                String message = config.getMessage(
                        "reload-failure",
                        "&cYapılandırma dosyaları yenilenirken bir hata oluştu!"
                );
                // onCommand already runs in the sender's valid command context.
                sender.sendMessage(ColorUtils.colorize(message));
            }
            return true;
        }

        String usage = config.getMessage("invalid-usage", "&cGeçersiz kullanım! &fKullanım: /split reload");
        sender.sendMessage(ColorUtils.colorize(usage));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("split.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if ("reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                list.add("reload");
            }
            return list;
        }

        return Collections.emptyList();
    }

    private void sendOnOwningThread(CommandSender sender, String message) {
        try {
            boolean scheduled = plugin.getPlatformScheduler().runForSender(
                    sender,
                    () -> sender.sendMessage(ColorUtils.colorize(message))
            );
            if (!scheduled && plugin.getConfigManager().getConfigData().isDebug()) {
                plugin.getLogger().fine("Reload response was dropped because its sender is no longer active.");
            }
        } catch (RuntimeException exception) {
            // Never access an entity directly from this asynchronous completion path.
            plugin.getLogger().log(Level.WARNING, "Could not deliver the reload response on its owning thread.", exception);
        }
    }
}

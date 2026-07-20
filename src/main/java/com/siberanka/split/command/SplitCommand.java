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
            // Run config reloading asynchronously to prevent blocking the main server thread (disk I/O)
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    plugin.getConfigManager().load();
                    // Load successful
                    ConfigManager.ConfigData newConfig = plugin.getConfigManager().getConfigData();
                    String msg = newConfig.getMessage("reload-success", "&aYapılandırma dosyaları başarıyla yenilendi.");
                    sendOnServerThread(sender, msg);
                    if (newConfig.isDebug()) {
                        plugin.getLogger().info("Plugin configuration reloaded successfully by " + senderName);
                    }
                } catch (Exception e) {
                    // Load failed
                    plugin.getLogger().log(Level.SEVERE, "An error occurred while reloading the configurations:", e);
                    String msg = config.getMessage("reload-failure", "&cYapılandırma dosyaları yenilenirken bir hata oluştu!");
                    sendOnServerThread(sender, msg);
                } finally {
                    reloadInProgress.set(false);
                }
            });
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

    private void sendOnServerThread(CommandSender sender, String message) {
        plugin.getServer().getScheduler().runTask(
                plugin,
                () -> sender.sendMessage(ColorUtils.colorize(message))
        );
    }
}

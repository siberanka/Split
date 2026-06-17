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
import java.util.logging.Level;

public class SplitCommand implements CommandExecutor, TabCompleter {

    private final SplitPlugin plugin;

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
            // Run config reloading asynchronously to prevent blocking the main server thread (disk I/O)
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    plugin.getConfigManager().load();
                    // Load successful
                    ConfigManager.ConfigData newConfig = plugin.getConfigManager().getConfigData();
                    String msg = newConfig.getMessage("reload-success", "&aYapılandırma dosyaları başarıyla yenilendi.");
                    sender.sendMessage(ColorUtils.colorize(msg));
                    if (newConfig.isDebug()) {
                        plugin.getLogger().info("Plugin configuration reloaded successfully by " + sender.getName());
                    }
                } catch (Exception e) {
                    // Load failed
                    plugin.getLogger().log(Level.SEVERE, "An error occurred while reloading the configurations:", e);
                    String msg = config.getMessage("reload-failure", "&cYapılandırma dosyaları yenilenirken bir hata oluştu!");
                    sender.sendMessage(ColorUtils.colorize(msg));
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
            if ("reload".startsWith(args[0].toLowerCase())) {
                list.add("reload");
            }
            return list;
        }

        return Collections.emptyList();
    }
}

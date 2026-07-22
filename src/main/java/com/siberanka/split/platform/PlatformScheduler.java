package com.siberanka.split.platform;

import com.siberanka.split.SplitPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Routes work to Bukkit or Folia ownership-aware schedulers. */
public final class PlatformScheduler {

    private final SplitPlugin plugin;
    private final boolean folia;
    private final FoliaSchedulerBridge foliaBridge;
    private final AtomicBoolean acceptingTasks = new AtomicBoolean(true);

    public PlatformScheduler(SplitPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.folia = FoliaSchedulerBridge.isFolia();
        this.foliaBridge = folia ? new FoliaSchedulerBridge(plugin.getServer(), plugin) : null;
    }

    public boolean isFolia() {
        return folia;
    }

    public void runAsync(Runnable task) {
        Runnable guardedTask = guard(task);
        ensureAcceptingTasks();
        if (folia) {
            foliaBridge.runAsync(guardedTask);
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, guardedTask);
        }
    }

    /**
     * Schedules a response on the entity-owning region for entity senders and on
     * the global region for console-like senders.
     *
     * @return false when shutdown began or the entity scheduler was retired
     */
    public boolean runForSender(CommandSender sender, Runnable task) {
        Objects.requireNonNull(sender, "sender");
        if (!acceptingTasks.get()) {
            return false;
        }
        Runnable guardedTask = guard(task);
        if (folia) {
            if (sender instanceof Entity entity) {
                return foliaBridge.runEntity(entity, guardedTask);
            }
            foliaBridge.runGlobal(guardedTask);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, guardedTask);
        }
        return true;
    }

    public void shutdown() {
        if (!acceptingTasks.compareAndSet(true, false)) {
            return;
        }
        try {
            if (folia) {
                foliaBridge.cancelAll();
            } else {
                plugin.getServer().getScheduler().cancelTasks(plugin);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not cancel all Split scheduler tasks cleanly.", exception);
        }
    }

    private Runnable guard(Runnable task) {
        Objects.requireNonNull(task, "task");
        return () -> {
            if (acceptingTasks.get() && plugin.isEnabled()) {
                task.run();
            }
        };
    }

    private void ensureAcceptingTasks() {
        if (!acceptingTasks.get()) {
            throw new IllegalStateException("Split is shutting down and no longer accepts scheduled work");
        }
    }
}

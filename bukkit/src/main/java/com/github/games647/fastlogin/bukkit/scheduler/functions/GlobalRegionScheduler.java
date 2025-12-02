package com.github.games647.fastlogin.bukkit.scheduler.functions;

import com.github.games647.fastlogin.bukkit.scheduler.task.NekoTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

public class GlobalRegionScheduler {
    private BukkitScheduler bukkitScheduler;
    private io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler globalRegionScheduler;

    public GlobalRegionScheduler() {
        if (Scheduler.isFolia()) {
            this.globalRegionScheduler = Bukkit.getGlobalRegionScheduler();
        } else {
            this.bukkitScheduler = Bukkit.getScheduler();
        }

    }

    public NekoTask runTask(@NotNull Plugin plugin, @NotNull Runnable task) {
        return !Scheduler.isFolia() ? new NekoTask(this.bukkitScheduler.runTask(plugin, task)) : new NekoTask(this.globalRegionScheduler.run(plugin, (o) -> task.run()));
    }

    public NekoTask runTaskLater(@NotNull Plugin plugin, @NotNull Runnable task, long delay) {
        if (delay < 1L) {
            delay = 1L;
        }

        return !Scheduler.isFolia() ? new NekoTask(this.bukkitScheduler.runTaskLater(plugin, task, delay)) : new NekoTask(this.globalRegionScheduler.runDelayed(plugin, (o) -> task.run(), delay));
    }

    public NekoTask runTaskTimer(@NotNull Plugin plugin, @NotNull Runnable task, long initialDelayTicks, long periodTicks) {
        if (initialDelayTicks < 1L) {
            initialDelayTicks = 1L;
        }

        if (periodTicks < 1L) {
            periodTicks = 1L;
        }

        return !Scheduler.isFolia() ? new NekoTask(this.bukkitScheduler.runTaskTimer(plugin, task, initialDelayTicks, periodTicks)) : new NekoTask(this.globalRegionScheduler.runAtFixedRate(plugin, (o) -> task.run(), initialDelayTicks, periodTicks));
    }

    public void cancel(@NotNull Plugin plugin) {
        if (!Scheduler.isFolia()) {
            Bukkit.getScheduler().cancelTasks(plugin);
        } else {
            this.globalRegionScheduler.cancelTasks(plugin);
        }
    }
}

package com.github.games647.fastlogin.bukkit.scheduler.functions;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

public class AsyncScheduler {
    private BukkitScheduler bukkitScheduler;
    private io.papermc.paper.threadedregions.scheduler.AsyncScheduler asyncScheduler;

    public AsyncScheduler() {
        if (Scheduler.isFolia()) {
            this.asyncScheduler = Bukkit.getAsyncScheduler();
        } else {
            this.bukkitScheduler = Bukkit.getScheduler();
        }

    }

    public NekoTask runTask(@NotNull Plugin plugin, @NotNull Runnable task) {
        return !Scheduler.isFolia() ? new NekoTask(this.bukkitScheduler.runTaskAsynchronously(plugin, task)) : new NekoTask(this.asyncScheduler.runNow(plugin, (o) -> task.run()));
    }

    public NekoTask runTaskLater(@NotNull Plugin plugin, @NotNull Runnable task, long delay) {
        return !Scheduler.isFolia() ? new NekoTask(this.bukkitScheduler.runTaskLaterAsynchronously(plugin, task, delay)) : new NekoTask(this.asyncScheduler.runDelayed(plugin, (o) -> task.run(), delay * 50L, TimeUnit.MILLISECONDS));
    }

    public NekoTask runTaskTimer(@NotNull Plugin plugin, @NotNull Runnable task, long delay, long period) {
        if (period < 1L) {
            period = 1L;
        }

        return !Scheduler.isFolia() ? new NekoTask(this.bukkitScheduler.runTaskTimerAsynchronously(plugin, task, delay, period)) : new NekoTask(this.asyncScheduler.runAtFixedRate(plugin, (o) -> task.run(), delay * 50L, period * 50L, TimeUnit.MILLISECONDS));
    }

    public void cancel(@NotNull Plugin plugin) {
        if (!Scheduler.isFolia()) {
            this.bukkitScheduler.cancelTasks(plugin);
        } else {
            this.asyncScheduler.cancelTasks(plugin);
        }
    }
}

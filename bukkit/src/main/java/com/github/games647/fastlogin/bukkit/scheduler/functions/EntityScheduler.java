package com.github.games647.fastlogin.bukkit.scheduler.functions;

import com.github.games647.fastlogin.bukkit.scheduler.task.NekoTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EntityScheduler {
    private BukkitScheduler bukkitScheduler;

    public EntityScheduler() {
        if (!Scheduler.isFolia()) {
            this.bukkitScheduler = Bukkit.getScheduler();
        }

    }

    public NekoTask runTask(@NotNull Plugin plugin, @NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        return !Scheduler.isFolia() ? new NekoTask(this.bukkitScheduler.runTask(plugin, task)) : new NekoTask(entity.getScheduler().run(plugin, (o) -> task.run(), retired));
    }

    public NekoTask runTaskLater(@NotNull Plugin plugin, @NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks) {
        if (delayTicks < 1L) {
            delayTicks = 1L;
        }

        return !Scheduler.isFolia() ? new NekoTask(this.bukkitScheduler.runTaskLater(plugin, task, delayTicks)) : new NekoTask(entity.getScheduler().runDelayed(plugin, (o) -> task.run(), retired, delayTicks));
    }

    public NekoTask runTaskTimer(@NotNull Plugin plugin, @NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long initialDelayTicks, long periodTicks) {
        if (initialDelayTicks < 1L) {
            initialDelayTicks = 1L;
        }

        if (periodTicks < 1L) {
            periodTicks = 1L;
        }

        return !Scheduler.isFolia() ? new NekoTask(this.bukkitScheduler.runTaskTimer(plugin, task, initialDelayTicks, periodTicks)) : new NekoTask(entity.getScheduler().runAtFixedRate(plugin, (o) -> task.run(), retired, initialDelayTicks, periodTicks));
    }
}

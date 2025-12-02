package com.github.games647.fastlogin.bukkit.scheduler.functions;

import com.github.games647.fastlogin.bukkit.scheduler.task.NekoTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

public final class RegionScheduler {
    private BukkitScheduler bukkitScheduler;
    private io.papermc.paper.threadedregions.scheduler.RegionScheduler regionScheduler;

    public RegionScheduler() {
        if (Scheduler.isFolia()) {
            this.regionScheduler = Bukkit.getRegionScheduler();
        } else {
            this.bukkitScheduler = Bukkit.getScheduler();
        }

    }

    public NekoTask runTask(@NotNull Plugin plugin, @NotNull World world, int chunkX, int chunkZ, @NotNull Runnable task) {
        return !Scheduler.isFolia() ? new NekoTask(this.bukkitScheduler.runTask(plugin, task)) : new NekoTask(this.regionScheduler.run(plugin, world, chunkX, chunkZ, (o) -> task.run()));
    }

    public NekoTask runTask(@NotNull Plugin plugin, @NotNull Location location, @NotNull Runnable task) {
        return !Scheduler.isFolia() ? new NekoTask(this.bukkitScheduler.runTask(plugin, task)) : new NekoTask(this.regionScheduler.run(plugin, location, (o) -> task.run()));
    }

    public NekoTask runTaskLater(@NotNull Plugin plugin, @NotNull World world, int chunkX, int chunkZ, @NotNull Runnable task, long delayTicks) {
        if (delayTicks < 1L) {
            delayTicks = 1L;
        }

        return !Scheduler.isFolia() ? new NekoTask(this.bukkitScheduler.runTaskLater(plugin, task, delayTicks)) : new NekoTask(this.regionScheduler.runDelayed(plugin, world, chunkX, chunkZ, (o) -> task.run(), delayTicks));
    }

    public NekoTask runTaskLater(@NotNull Plugin plugin, @NotNull Location location, @NotNull Runnable task, long delayTicks) {
        if (delayTicks < 1L) {
            delayTicks = 1L;
        }

        return !Scheduler.isFolia() ? new NekoTask(this.bukkitScheduler.runTaskLater(plugin, task, delayTicks)) : new NekoTask(this.regionScheduler.runDelayed(plugin, location, (o) -> task.run(), delayTicks));
    }

    public NekoTask runTaskTimer(@NotNull Plugin plugin, @NotNull World world, int chunkX, int chunkZ, @NotNull Runnable task, long initialDelayTicks, long periodTicks) {
        if (initialDelayTicks < 1L) {
            initialDelayTicks = 1L;
        }

        if (periodTicks < 1L) {
            periodTicks = 1L;
        }

        return !Scheduler.isFolia() ? new NekoTask(this.bukkitScheduler.runTaskTimer(plugin, task, initialDelayTicks, periodTicks)) : new NekoTask(this.regionScheduler.runAtFixedRate(plugin, world, chunkX, chunkZ, (o) -> task.run(), initialDelayTicks, periodTicks));
    }

    public NekoTask runTaskTimer(@NotNull Plugin plugin, @NotNull Location location, @NotNull Runnable task, long initialDelayTicks, long periodTicks) {
        if (initialDelayTicks < 1L) {
            initialDelayTicks = 1L;
        }

        if (periodTicks < 1L) {
            periodTicks = 1L;
        }

        return !Scheduler.isFolia() ? new NekoTask(this.bukkitScheduler.runTaskTimer(plugin, task, initialDelayTicks, periodTicks)) : new NekoTask(this.regionScheduler.runAtFixedRate(plugin, location, (o) -> task.run(), initialDelayTicks, periodTicks));
    }
}

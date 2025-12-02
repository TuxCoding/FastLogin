package com.github.games647.fastlogin.bukkit.scheduler.task;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class NekoTask implements Task {
    private BukkitTask bukkitTask;
    private ScheduledTask scheduledTask;

    public NekoTask(BukkitTask bukkitTask) {
        this.bukkitTask = bukkitTask;
    }

    public NekoTask(ScheduledTask scheduledTask) {
        this.scheduledTask = scheduledTask;
    }

    public Plugin getOwner() {
        return this.bukkitTask != null ? this.bukkitTask.getOwner() : this.scheduledTask.getOwningPlugin();
    }

    public boolean isCancelled() {
        return this.bukkitTask != null ? this.bukkitTask.isCancelled() : this.scheduledTask.isCancelled();
    }

    public void cancel() {
        if (this.bukkitTask != null) {
            this.bukkitTask.cancel();
        } else {
            this.scheduledTask.cancel();
        }

    }
}

package com.github.games647.fastlogin.bukkit.scheduler.runnable;

import com.github.games647.fastlogin.bukkit.scheduler.functions.Scheduler;
import com.github.games647.fastlogin.bukkit.scheduler.task.NekoTask;
import lombok.Getter;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.Runnable;

@Getter
public abstract class NekoRunnable implements Runnable {
    public NekoTask task;

    public void runTask(JavaPlugin plugin) {
        this.task = Scheduler.getGlobalRegionScheduler().runTask(plugin, this::run);
    }

    public void runTaskLater(JavaPlugin plugin, long delay) {
        this.task = Scheduler.getGlobalRegionScheduler().runTaskLater(plugin, this::run, delay);
    }

    public void runTaskTimer(JavaPlugin plugin, long delay, long period) {
        this.task = Scheduler.getGlobalRegionScheduler().runTaskTimer(plugin, this::run, delay, period);
    }

    public void runTaskAsynchronously(JavaPlugin plugin) {
        this.task = Scheduler.getAsyncScheduler().runTask(plugin, this::run);
    }

    public void runTaskLaterAsynchronously(JavaPlugin plugin, long delay) {
        this.task = Scheduler.getAsyncScheduler().runTaskLater(plugin, this::run, delay);
    }

    public void runTaskTimerAsynchronously(JavaPlugin plugin, long delay, long period) {
        this.task = Scheduler.getAsyncScheduler().runTaskTimer(plugin, this::run, delay, period);
    }

    public Plugin getOwner() {
        return this.task.getOwner();
    }

    public boolean isCancelled() {
        return this.task.isCancelled();
    }

    public void cancel() {
        this.task.cancel();
    }

}
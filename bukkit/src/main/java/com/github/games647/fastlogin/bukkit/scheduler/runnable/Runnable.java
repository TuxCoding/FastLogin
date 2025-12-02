package com.github.games647.fastlogin.bukkit.scheduler.runnable;

import com.github.games647.fastlogin.bukkit.scheduler.task.Task;
import org.bukkit.plugin.java.JavaPlugin;

public interface Runnable extends Task {
    void run();

    void runTask(JavaPlugin var1);

    void runTaskLater(JavaPlugin var1, long var2);

    void runTaskTimer(JavaPlugin var1, long var2, long var4);

    void runTaskAsynchronously(JavaPlugin var1);

    void runTaskLaterAsynchronously(JavaPlugin var1, long var2);

    void runTaskTimerAsynchronously(JavaPlugin var1, long var2, long var4);
}

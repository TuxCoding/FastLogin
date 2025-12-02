package com.github.games647.fastlogin.bukkit.scheduler.task;

import org.bukkit.plugin.Plugin;

public interface Task {
    Plugin getOwner();

    boolean isCancelled();

    void cancel();
}

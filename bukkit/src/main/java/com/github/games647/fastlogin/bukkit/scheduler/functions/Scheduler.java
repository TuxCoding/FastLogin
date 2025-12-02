package com.github.games647.fastlogin.bukkit.scheduler.functions;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Scheduler {
    private static boolean folia;
    private static AsyncScheduler asyncScheduler;
    private static EntityScheduler entityScheduler;
    private static GlobalRegionScheduler globalRegionScheduler;
    private static RegionScheduler regionScheduler;

    public static AsyncScheduler getAsyncScheduler() {
        if (asyncScheduler == null) {
            asyncScheduler = new AsyncScheduler();
        }

        return asyncScheduler;
    }

    public static EntityScheduler getEntityScheduler() {
        if (entityScheduler == null) {
            entityScheduler = new EntityScheduler();
        }

        return entityScheduler;
    }

    public static GlobalRegionScheduler getGlobalRegionScheduler() {
        if (globalRegionScheduler == null) {
            globalRegionScheduler = new GlobalRegionScheduler();
        }

        return globalRegionScheduler;
    }

    public static RegionScheduler getRegionScheduler() {
        if (regionScheduler == null) {
            regionScheduler = new RegionScheduler();
        }

        return regionScheduler;
    }

    public static void cancel(@NotNull Plugin plugin) {
        getGlobalRegionScheduler().cancel(plugin);
        getAsyncScheduler().cancel(plugin);
    }

    public static boolean isFolia() {
        return folia;
    }

    static {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException var1) {
            folia = false;
        }

    }
}

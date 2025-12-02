/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.fastlogin.bukkit.listener;

import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.scheduler.functions.Scheduler;
import com.github.games647.fastlogin.bukkit.task.FloodgateAuthTask;
import com.github.games647.fastlogin.bukkit.task.ForceLoginTask;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.Metadatable;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

/**
 * This listener tells authentication plugins weather the player has a premium account. So the
 * plugin can skip authentication.
 */
public class ConnectionListener implements Listener {

    private static final long DELAY_LOGIN = 20L / 2;

    private final FastLoginBukkit plugin;

    public ConnectionListener(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent loginEvent) {
        removeBlockedStatus(loginEvent.getPlayer());
        if (loginEvent.getResult() == Result.ALLOWED && !plugin.isServerFullyStarted()) {
            loginEvent.disallow(Result.KICK_OTHER, plugin.getCore().getMessage("not-started"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent joinEvent) {
        Player player = joinEvent.getPlayer();
        Scheduler.getAsyncScheduler().runTaskLater(plugin, () -> {
            delayForceLogin(player);
            // delay the login process to let auth plugins initialize the player
            // Magic number however as there is no direct event from those plugins
        }, DELAY_LOGIN);
    }

    private void delayForceLogin(Player player) {
        // session exists so the player is ready for force login
        // cases: Paper (firing BungeeCord message before PlayerJoinEvent) or not running BungeeCord and already
        // having the login session from the login process
        BukkitLoginSession session = plugin.getSession(player.spigot().getRawAddress());

        if (session == null) {
            // Floodgate players usually don't have a session at this point
            // exception: if force login by bungee message had been delayed
            FloodgateService floodgateService = plugin.getFloodgateService();
            if (floodgateService != null) {
                FloodgatePlayer floodgatePlayer = floodgateService.getBedrockPlayer(player.getUniqueId());
                if (floodgatePlayer != null) {
                    Runnable floodgateAuthTask = new FloodgateAuthTask(plugin.getCore(), player, floodgatePlayer);
                    Scheduler.getAsyncScheduler().runTask(plugin, floodgateAuthTask);
                    plugin.getBungeeManager().markJoinEventFired(player);
                    return;
                }
            }

            String sessionId = plugin.getSessionId(player.spigot().getRawAddress());
            plugin.getLog().info("No on-going login session for player: {} with ID {}. ", player, sessionId);
            plugin.getLog().info("Setups using Minecraft proxies will start delayed "
                + "when the command from the proxy is received");
        } else {
            Runnable forceLoginTask = new ForceLoginTask(plugin.getCore(), player, session);
            Scheduler.getAsyncScheduler().runTask(plugin, forceLoginTask);
        }

        plugin.getBungeeManager().markJoinEventFired(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent quitEvent) {
        Player player = quitEvent.getPlayer();

        removeBlockedStatus(player);
        plugin.getCore().getPendingConfirms().remove(player.getUniqueId());
        plugin.getPremiumPlayers().remove(player.getUniqueId());
        plugin.getBungeeManager().cleanup(player);
    }

    private void removeBlockedStatus(Metadatable player) {
        player.removeMetadata(plugin.getName(), plugin);
    }
}

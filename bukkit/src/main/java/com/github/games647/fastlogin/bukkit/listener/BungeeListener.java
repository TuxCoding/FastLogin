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
import com.github.games647.fastlogin.bukkit.task.ForceLoginTask;
import com.github.games647.fastlogin.core.PremiumStatus;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.message.LoginActionMessage.Type;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Responsible for receiving messages from a BungeeCord instance.
 * <p>
 * This class also receives the plugin message from the bungeecord version of this plugin in order to get notified if
 * the connection is in online mode.
 */
public class BungeeListener implements PluginMessageListener {

    private final FastLoginBukkit plugin;

    public BungeeListener(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, Player player, byte[] message) {
        ByteArrayDataInput dataInput = ByteStreams.newDataInput(message);

        LoginActionMessage loginMessage = new LoginActionMessage();
        loginMessage.readFrom(dataInput);

        plugin.getLog().debug("Received plugin message {}", loginMessage);

        Player targetPlayer = player;
        if (!loginMessage.getPlayerName().equals(player.getName())) {
            targetPlayer = Bukkit.getPlayerExact(loginMessage.getPlayerName());
        }

        if (targetPlayer == null) {
            plugin.getLog().warn("Force action player {} not found", loginMessage.getPlayerName());
            return;
        }

        // fail if target player is blocked because already authenticated or wrong bungeecord id
        if (targetPlayer.hasMetadata(plugin.getName())) {
            plugin.getLog().warn("Received message {} from a blocked player {}", loginMessage, targetPlayer);
        } else {
            UUID sourceId = loginMessage.getProxyId();
            if (plugin.getBungeeManager().isProxyAllowed(sourceId)) {
                readMessage(targetPlayer, loginMessage);
            } else {
                plugin.getLog().warn("Received proxy id: {} that doesn't exist in the proxy file", sourceId);
            }
        }
    }

    private void readMessage(Player player, LoginActionMessage message) {
        String playerName = message.getPlayerName();
        Type type = message.getType();

        plugin.getLog().info("Player info {} command for {} from proxy", type, playerName);
        if (type == Type.LOGIN) {
            onLoginMessage(player, playerName);
        } else if (type == Type.REGISTER) {
            onRegisterMessage(player, playerName);
        } else if (type == Type.CRACKED) {
            //we don't start a force login task here so update it manually
            plugin.getPremiumPlayers().put(player.getUniqueId(), PremiumStatus.CRACKED);
        }
    }

    private void onLoginMessage(Player player, String playerName) {
        BukkitLoginSession playerSession = new BukkitLoginSession(playerName, true);
        startLoginTaskIfReady(player, playerSession);
    }

    private void onRegisterMessage(Player player, String playerName) {
       Scheduler.getAsyncScheduler().runTask(plugin, () -> {
            AuthPlugin<Player> authPlugin = plugin.getCore().getAuthPluginHook();
            try {
                //we need to check if the player is registered on Bukkit too
                if (authPlugin == null || !authPlugin.isRegistered(playerName)) {
                    BukkitLoginSession playerSession = new BukkitLoginSession(playerName, false);
                    startLoginTaskIfReady(player, playerSession);
                }
            } catch (Exception ex) {
                plugin.getLog().error("Failed to query isRegistered for player: {}", player, ex);
            }
        });
    }

    private void startLoginTaskIfReady(Player player, BukkitLoginSession session) {
        session.setVerifiedPremium(true);
        plugin.putSession(player.spigot().getRawAddress(), session);

        // only start a new login task if the join event fired earlier. This event then didn't
        boolean result = plugin.getBungeeManager().didJoinEventFired(player);
        plugin.getLog().info("Delaying force login until join event fired?: {}", result);
        if (result) {
            Runnable forceLoginTask = new ForceLoginTask(plugin.getCore(), player, session);
            Scheduler.getAsyncScheduler().runTask(plugin, forceLoginTask);
        }
    }
}

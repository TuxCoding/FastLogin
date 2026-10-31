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
package com.github.games647.fastlogin.bungee.listener;

import com.github.games647.fastlogin.bungee.BungeeLoginSession;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.bungee.task.AsyncToggleMessage;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.message.ChangePremiumMessage;
import com.github.games647.fastlogin.core.message.NamespaceKey;
import com.github.games647.fastlogin.core.message.SuccessMessage;
import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.message.PreJoinMessage;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.Arrays;
import java.util.UUID;

public class PluginMessageListener implements Listener {

    private final FastLoginBungee plugin;

    private final String successChannel;
    private final String changeChannel;
    private final String preJoinChannel;

    public PluginMessageListener(FastLoginBungee plugin) {
        this.plugin = plugin;

        this.successChannel = new NamespaceKey(plugin.getName(), SuccessMessage.SUCCESS_CHANNEL).getCombinedName();
        this.changeChannel = new NamespaceKey(plugin.getName(), ChangePremiumMessage.CHANGE_CHANNEL).getCombinedName();
        this.preJoinChannel = NamespaceKey.getCombined(plugin.getName(), PreJoinMessage.CHANNEL);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent pluginMessageEvent) {
        String channel = pluginMessageEvent.getTag();
        if (pluginMessageEvent.isCancelled() || !channel.startsWith(plugin.getName().toLowerCase())) {
            return;
        }

        //the client shouldn't be able to read the messages in order to know something about server internal states
        //moreover the client shouldn't be able to fake a running premium check by sending the result message
        pluginMessageEvent.setCancelled(true);

        if (!(pluginMessageEvent.getSender() instanceof Server)) {
            //check if the message is sent from the server
            return;
        }

        if (preJoinChannel.equals(channel)) {
            onPreJoinRequest((Server) pluginMessageEvent.getSender(),
                    (ProxiedPlayer) pluginMessageEvent.getReceiver(), pluginMessageEvent.getData());
            return;
        }

        //so that we can safely process this in the background
        byte[] data = Arrays.copyOf(pluginMessageEvent.getData(), pluginMessageEvent.getData().length);
        ProxiedPlayer forPlayer = (ProxiedPlayer) pluginMessageEvent.getReceiver();

        plugin.getScheduler().runAsync(() -> readMessage(forPlayer, channel, data));
    }

    private void onPreJoinRequest(Server source, ProxiedPlayer player, byte[] data) {
        try {
            PreJoinMessage request = new PreJoinMessage();
            request.readFrom(ByteStreams.newDataInput(data));
            if (request.getLoginAction() != null) {
                return;
            }
            BungeeLoginSession session = plugin.getSession().get(player.getPendingConnection());
            LoginActionMessage.Type type = LoginActionMessage.Type.CRACKED;
            if (session != null && player.getPendingConnection().isOnlineMode()) {
                type = session.needsRegistration() ? LoginActionMessage.Type.REGISTER : LoginActionMessage.Type.LOGIN;
            }
            UUID proxyId = UUID.fromString(plugin.getProxy().getConfig().getUuid());
            LoginActionMessage action = new LoginActionMessage(type, player.getName(), proxyId);
            plugin.sendPluginMessage(source, new PreJoinMessage(request.getRequestId(), action));
        } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException ex) {
            plugin.getLog().debug("Ignoring malformed FastLogin pre-join request", ex);
        }
    }

    private void readMessage(ProxiedPlayer forPlayer, String channel, byte[] data) {
        FastLoginCore<ProxiedPlayer, CommandSender, FastLoginBungee> core = plugin.getCore();

        ByteArrayDataInput dataInput = ByteStreams.newDataInput(data);
        if (successChannel.equals(channel)) {
            onSuccessMessage(forPlayer);
        } else if (changeChannel.equals(channel)) {
            ChangePremiumMessage changeMessage = new ChangePremiumMessage();
            changeMessage.readFrom(dataInput);

            String playerName = changeMessage.getPlayerName();
            boolean isSourceInvoker = changeMessage.isSourceInvoker();
            if (changeMessage.shouldEnable()) {
                if (playerName.equals(forPlayer.getName()) && plugin.getCore().getConfig().get("premium-warning", true)
                        && !core.getPendingConfirms().contains(forPlayer.getUniqueId())) {
                    String message = core.getMessage("premium-warning");
                    forPlayer.sendMessage(TextComponent.fromLegacyText(message));
                    core.getPendingConfirms().add(forPlayer.getUniqueId());
                    return;
                }

                core.getPendingConfirms().remove(forPlayer.getUniqueId());
                Runnable task = new AsyncToggleMessage(core, forPlayer, playerName, true, isSourceInvoker);
                plugin.getScheduler().runAsync(task);
            } else {
                Runnable task = new AsyncToggleMessage(core, forPlayer, playerName, false, isSourceInvoker);
                plugin.getScheduler().runAsync(task);
            }
        }
    }

    private void onSuccessMessage(ProxiedPlayer forPlayer) {
        boolean shouldPersist = forPlayer.getPendingConnection().isOnlineMode();

        FloodgateService floodgateService = plugin.getFloodgateService();
        if (!shouldPersist && floodgateService != null) {
            // always save floodgate players to lock this username
            shouldPersist = floodgateService.isBedrockPlayer(forPlayer.getUniqueId());
        }

        if (shouldPersist) {
            //bukkit module successfully received and force logged in the user
            //update only on success to prevent corrupt data
            BungeeLoginSession loginSession = plugin.getSession().get(forPlayer.getPendingConnection());
            StoredProfile playerProfile = loginSession.getProfile();
            loginSession.setRegistered(true);

            if (!loginSession.isAlreadySaved()) {
                playerProfile.setOnlinemodePreferred(true);
                plugin.getCore().getStorage().save(playerProfile);
                loginSession.setAlreadySaved(true);
            }
        }
    }
}

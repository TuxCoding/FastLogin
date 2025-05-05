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
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.message.ChangePremiumMessage;
import com.github.games647.fastlogin.core.message.SuccessMessage;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import com.github.games647.fastlogin.velocity.task.AsyncToggleMessage;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Arrays;

public class PluginMessageListener {

    private final FastLoginVelocity plugin;

    private final String successChannel;
    private final String changeChannel;

    public PluginMessageListener(FastLoginVelocity plugin) {
        this.plugin = plugin;

        String prefix = plugin.getName();
        this.successChannel = MinecraftChannelIdentifier.create(prefix, SuccessMessage.SUCCESS_CHANNEL).getId();
        this.changeChannel = MinecraftChannelIdentifier.create(prefix, ChangePremiumMessage.CHANGE_CHANNEL).getId();
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent pluginMessageEvent) {
        String channel = pluginMessageEvent.getIdentifier().getId();
        if (!pluginMessageEvent.getResult().isAllowed() || !channel.startsWith(plugin.getName().toLowerCase())) {
            return;
        }

        //the client shouldn't be able to read the messages in order to know something about server internal states
        //moreover the client shouldn't be able to fake a running premium check by sending the result message
        pluginMessageEvent.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(pluginMessageEvent.getSource() instanceof ServerConnection)) {
            //check if the message is sent from the server
            return;
        }

        //so that we can safely process this in the background
        byte[] data = Arrays.copyOf(pluginMessageEvent.getData(), pluginMessageEvent.getData().length);
        Player forPlayer = (Player) pluginMessageEvent.getTarget();

        plugin.getScheduler().runAsync(() -> readMessage(forPlayer, channel, data));
    }

    private void readMessage(Player sender, String channel, byte[] data) {
        FastLoginCore<Player, CommandSource, FastLoginVelocity> core = plugin.getCore();

        ByteArrayDataInput dataInput = ByteStreams.newDataInput(data);
        if (successChannel.equals(channel)) {
            onSuccessMessage(sender);
        } else if (changeChannel.equals(channel)) {
            ChangePremiumMessage changeMessage = new ChangePremiumMessage();
            changeMessage.readFrom(dataInput);

            String playerName = changeMessage.getPlayerName();
            boolean isSourceInvoker = changeMessage.isSourceInvoker();
            if (changeMessage.shouldEnable()) {
                Boolean premiumWarning = plugin.getCore().getConfig().get("premium-warning", true);
                if (playerName.equals(sender.getUsername()) && premiumWarning
                    && !core.getPendingConfirms().contains(sender.getUniqueId())) {
                    String message = core.getMessage("premium-warning");
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
                    core.getPendingConfirms().add(sender.getUniqueId());
                    return;
                }

                core.getPendingConfirms().remove(sender.getUniqueId());
                Runnable task = new AsyncToggleMessage(core, sender, playerName, true, isSourceInvoker);
                plugin.getScheduler().runAsync(task);
            } else {
                Runnable task = new AsyncToggleMessage(core, sender, playerName, false, isSourceInvoker);
                plugin.getScheduler().runAsync(task);
            }
        }
    }

    private void onSuccessMessage(Player forPlayer) {
        boolean shouldPersist = forPlayer.isOnlineMode();

        FloodgateService floodgateService = plugin.getFloodgateService();
        if (!shouldPersist && floodgateService != null) {
            // always save floodgate players to lock this username
            shouldPersist = floodgateService.isBedrockPlayer(forPlayer.getUniqueId());
        }

        if (shouldPersist) {
            //bukkit module successfully received and force logged in the user
            //update only on success to prevent corrupt data
            VelocityLoginSession loginSession = plugin.getSession().get(forPlayer);
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

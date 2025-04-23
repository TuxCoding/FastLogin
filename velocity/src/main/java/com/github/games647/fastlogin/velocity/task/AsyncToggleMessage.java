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
package com.github.games647.fastlogin.velocity.task;

import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.event.FastLoginPremiumToggleEvent.PremiumToggleReason;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.event.VelocityFastLoginPremiumToggleEvent;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class AsyncToggleMessage implements Runnable {

    private final FastLoginCore<Player, CommandSource, FastLoginVelocity> core;
    private final Player sender;
    private final String senderName;
    private final String targetPlayer;
    private final boolean toPremium;
    private final boolean isPlayerSender;

    public AsyncToggleMessage(FastLoginCore<Player, CommandSource, FastLoginVelocity> core,
                              Player sender, String playerName, boolean toPremium, boolean playerSender) {
        this.core = core;
        this.sender = sender;
        this.targetPlayer = playerName;
        this.toPremium = toPremium;
        this.isPlayerSender = playerSender;
        this.senderName = sender.getUsername();
    }

    @Override
    public void run() {
        if (toPremium) {
            activatePremium();
        } else {
            turnOffPremium();
        }
    }

    private void turnOffPremium() {
        StoredProfile playerProfile = core.getStorage().loadProfile(targetPlayer);
        //existing player is already cracked
        if (playerProfile.isExistingPlayer() && !playerProfile.isOnlinemodePreferred()) {
            sendMessage("not-premium");
            return;
        }

        playerProfile.setOnlinemodePreferred(false);
        playerProfile.setId(null);
        core.getStorage().save(playerProfile);
        PremiumToggleReason reason = (!isPlayerSender || !senderName.equalsIgnoreCase(playerProfile.getName()))
            ? PremiumToggleReason.COMMAND_OTHER : PremiumToggleReason.COMMAND_SELF;
        core.getPlugin().getProxy().getEventManager().fire(
            new VelocityFastLoginPremiumToggleEvent(playerProfile, reason));

        if (isPlayerSender && core.getConfig().getBoolean("kick-toggle", true)) {
            TextComponent msg = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(core.getMessage("remove-premium"));
            sender.disconnect(msg);
        } else {
            sendMessage("remove-premium");
        }
    }

    private void activatePremium() {
        StoredProfile playerProfile = core.getStorage().loadProfile(targetPlayer);
        if (playerProfile.isOnlinemodePreferred()) {
            sendMessage("already-exists");
            return;
        }

        playerProfile.setOnlinemodePreferred(true);
        core.getStorage().save(playerProfile);
        PremiumToggleReason reason = (!isPlayerSender || !senderName.equalsIgnoreCase(playerProfile.getName()))
            ? PremiumToggleReason.COMMAND_OTHER : PremiumToggleReason.COMMAND_SELF;
        core.getPlugin().getProxy().getEventManager().fire(
            new VelocityFastLoginPremiumToggleEvent(playerProfile, reason));
        sendMessage("add-premium");
    }

    private void sendMessage(String localeId) {
        String message = core.getMessage(localeId);
        if (isPlayerSender) {
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
        } else {
            ConsoleCommandSource console = core.getPlugin().getProxy().getConsoleCommandSource();
            console.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
        }
    }
}

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
package com.github.games647.fastlogin.bukkit.command;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginPremiumToggleEvent;
import com.github.games647.fastlogin.core.shared.event.FastLoginPremiumToggleEvent.PremiumToggleReason;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Let users activate fast login by command. This only be accessible if
 * the user has access to its account. So we can make sure that not another
 * person with a paid account and the same username can steal their account.
 */
public class PremiumCommand extends ToggleCommand {

    public PremiumCommand(FastLoginBukkit plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             String[] args) {
        if (args.length == 0) {
            onPremiumSelf(sender);
        } else {
            onPremiumOther(sender, command, args);
        }

        return true;
    }

    private void onPremiumSelf(CommandSender sender) {
        if (isConsole(sender)) {
            return;
        }

        if (forwardPremiumCommand(sender, sender.getName())) {
            return;
        }

        Player player = (Player) sender;
        UUID id = player.getUniqueId();
        if (plugin.getConfig().getBoolean("premium-warning") && !plugin.getCore().getPendingConfirms().contains(id)) {
            sender.sendMessage(plugin.getCore().getMessage("premium-warning"));
            plugin.getCore().getPendingConfirms().add(id);
            return;
        }

        plugin.getCore().getPendingConfirms().remove(id);
        //todo: load async
        StoredProfile profile = plugin.getCore().getStorage().loadProfile(sender.getName());
        if (profile.isOnlinemodePreferred()) {
            plugin.getCore().sendLocaleMessage("already-exists", sender);
        } else {
            //todo: resolve uuid
            profile.setOnlinemodePreferred(true);
            plugin.getScheduler().runAsync(() -> {
                plugin.getCore().getStorage().save(profile);
                plugin.getServer().getPluginManager().callEvent(
                        new BukkitFastLoginPremiumToggleEvent(sender, profile, PremiumToggleReason.COMMAND_SELF)
                );

                plugin.getScheduler().getSyncExecutor().execute(() -> {
                    if (plugin.getCore().getConfig().getBoolean("kick-toggle", true)) {
                        player.kickPlayer(plugin.getCore().getMessage("remove-premium"));
                    } else {
                        plugin.getCore().sendLocaleMessage("add-premium", sender);
                    }
                });
            });
        }
    }

    private void onPremiumOther(CommandSender sender, Command command, String[] args) {
        if (!hasOtherPermission(sender, command)) {
            return;
        }

        if (forwardPremiumCommand(sender, args[0])) {
            return;
        }

        //todo: load async
        StoredProfile profile = plugin.getCore().getStorage().loadProfile(args[0]);
        if (profile == null) {
            plugin.getCore().sendLocaleMessage("player-unknown", sender);
            return;
        }

        if (profile.isOnlinemodePreferred()) {
            plugin.getCore().sendLocaleMessage("already-exists-other", sender);
        } else {
            //todo: resolve uuid
            profile.setOnlinemodePreferred(true);
            plugin.getScheduler().runAsync(() -> {
                plugin.getCore().getStorage().save(profile);
                plugin.getServer().getPluginManager().callEvent(
                        new BukkitFastLoginPremiumToggleEvent(sender, profile, PremiumToggleReason.COMMAND_OTHER)
                );
            });

            plugin.getCore().sendLocaleMessage("add-premium-other", sender);
        }
    }

    private boolean forwardPremiumCommand(CommandSender sender, String target) {
        return forwardBungeeCommand(sender, target, true);
    }
}

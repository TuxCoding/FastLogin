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

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;

public class DeleteCommand implements TabExecutor {
    private final FastLoginBukkit plugin;

    public DeleteCommand(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles the command to delete profiles.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission(command.getPermission())) {
            plugin.getCore().sendLocaleMessage("no-permission", sender);
            return true;
        }

        if (plugin.getBungeeManager().isEnabled()) {
            sender.sendMessage("Error: Cannot delete profile entries when using BungeeCord!");
            return false;
        }

        if (args.length < 1) {
            sender.sendMessage("Error: Must supply username to delete!");
            return false;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int count = plugin.getCore().getStorage().deleteProfile(args[0]);
            if (!(sender instanceof ConsoleCommandSender)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (count == 0) {
                        sender.sendMessage("Error: No profile entries found!");
                    } else {
                        sender.sendMessage("Deleted " + count + " matching profile entries");
                    }
                });
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(args[0])) {
                list.add(p.getName());
            }
        }
        return null;
    }
}

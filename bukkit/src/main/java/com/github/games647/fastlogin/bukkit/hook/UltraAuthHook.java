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
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.scheduler.functions.Scheduler;
import com.github.games647.fastlogin.bukkit.scheduler.task.TaskCallback;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import org.bukkit.entity.Player;
import ultraauth.api.UltraAuthAPI;
import ultraauth.managers.PlayerManager;

/**
 * Project page:
 * <p>
 * <a href="https://dev.bukkit.org/bukkit-plugins/ultraauth-aa/">Bukkit</a>
 * <p>
 * <a href="https://www.spigotmc.org/resources/ultraauth.17044/">Spigot</a>
 */
public class UltraAuthHook implements AuthPlugin<Player> {

    private final FastLoginBukkit plugin;

    public UltraAuthHook(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean forceLogin(Player player) {
        //not thread-safe
        TaskCallback<Boolean> callback = new TaskCallback<>();
        Scheduler.getAsyncScheduler().runTask(plugin, () -> {
            if (UltraAuthAPI.isAuthenticated(player)) {
                plugin.getLog().warn(ALREADY_AUTHENTICATED, player);
                callback.setCallBack(false);
            }

            UltraAuthAPI.authenticatedPlayer(player);
            callback.setCallBack(UltraAuthAPI.isAuthenticated(player));
        });
        return callback.getCallBack();
    }

    @Override
    public boolean isRegistered(String playerName) {
        return UltraAuthAPI.isRegisterd(playerName);
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        UltraAuthAPI.setPlayerPasswordOnline(player, password);
        //the register method silents any exception so check if our entry was saved
        return PlayerManager.getInstance().checkPlayerPassword(player, password) && forceLogin(player);
    }
}

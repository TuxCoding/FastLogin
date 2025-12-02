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
import de.luricos.bukkit.xAuth.xAuth;
import de.luricos.bukkit.xAuth.xAuthPlayer;
import org.bukkit.entity.Player;

/**
 * GitHub: <a href="https://github.com/LycanDevelopment/xAuth/">...</a>
 * <p>
 * Project page:
 * <p>
 * <a href="https://dev.bukkit.org/bukkit-plugins/xauth/">Bukkit</a>
 */
public class XAuthHook implements AuthPlugin<Player> {

    private final xAuth xAuthPlugin = xAuth.getPlugin();
    private final FastLoginBukkit plugin;

    public XAuthHook(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean forceLogin(Player player) {
        //not thread-safe
        TaskCallback<Boolean> callback = new TaskCallback<>();
        Scheduler.getAsyncScheduler().runTask(plugin, () -> {
            xAuthPlayer xAuthPlayer = xAuthPlugin.getPlayerManager().getPlayer(player);
            if (xAuthPlayer != null) {
                if (xAuthPlayer.isAuthenticated()) {
                    plugin.getLog().warn(ALREADY_AUTHENTICATED, player);
                    callback.setCallBack(false);
                }

                //we checked that the player is premium (paid account)
                xAuthPlayer.setPremium(true);

                //unprotect the inventory, op status...
                callback.setCallBack(xAuthPlugin.getPlayerManager().doLogin(xAuthPlayer));
            }

            callback.setCallBack(false);
        });

        return callback.getCallBack();
    }

    @Override
    public boolean isRegistered(String playerName) {
        //this will load the player if it's not in the cache
        xAuthPlayer xAuthPlayer = xAuthPlugin.getPlayerManager().getPlayer(playerName);
        return xAuthPlayer != null && xAuthPlayer.isRegistered();
    }

    @Override
    public boolean forceRegister(Player player, final String password) {
        //not thread-safe
        TaskCallback<Boolean> callback = new TaskCallback<>();
        Scheduler.getAsyncScheduler().runTask(plugin, () -> {
            xAuthPlayer xAuthPlayer = xAuthPlugin.getPlayerManager().getPlayer(player);
            //this should run async because the plugin executes a sql query, but the method
            //accesses non thread-safe collections :(
            callback.setCallBack(xAuthPlayer != null
                    && xAuthPlugin.getAuthClass(xAuthPlayer).adminRegister(player.getName(), password, null));

        });
        // login in the player after registration
        return callback.getCallBack() && forceLogin(player);
    }
}
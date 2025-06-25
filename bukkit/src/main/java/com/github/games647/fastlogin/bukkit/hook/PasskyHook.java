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
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.rabbitcomapny.api.Identifier;
import com.rabbitcomapny.api.LoginResult;
import com.rabbitcomapny.api.PasskyAPI;
import com.rabbitcomapny.api.RegisterResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class PasskyHook implements AuthPlugin<Player> {

    private final FastLoginBukkit plugin;

    public PasskyHook(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean forceLogin(Player player) {
        Future<Boolean> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
            LoginResult result = PasskyAPI.forceLogin(new Identifier(player), true);
            if (!result.success) {
                plugin.getLog().error("Failed to force login {} via Passky: {}", player.getName(), result.status);
            }
            return result.success;
        });

        try {
            return future.get();
        } catch (InterruptedException | ExecutionException ex) {
            plugin.getLog().error("Failed to forceLogin player: {}", player, ex);
            return false;
        }
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        Future<Boolean> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
            RegisterResult result = PasskyAPI.forceRegister(new Identifier(player), password, true);
            if (!result.success) {
                plugin.getLog().error("Failed to register {} via Passky: {}", player.getName(), result.status);
            }
            return result.success;
        });

        try {
            return future.get();
        } catch (InterruptedException | ExecutionException ex) {
            plugin.getLog().error("Failed to forceRegister player: {}", player, ex);
            return false;
        }
    }

    @Override
    public boolean isRegistered(String playerName) {
        return PasskyAPI.isRegistered(new Identifier(playerName));
    }
}

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

import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.scheduler.functions.Scheduler;
import com.github.games647.fastlogin.bukkit.scheduler.task.TaskCallback;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import de.st_ddt.crazylogin.CrazyLogin;
import de.st_ddt.crazylogin.data.LoginPlayerData;
import de.st_ddt.crazylogin.databases.CrazyLoginDataDatabase;
import de.st_ddt.crazylogin.listener.PlayerListener;
import de.st_ddt.crazylogin.metadata.Authenticated;
import lombok.var;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * GitHub: <a href="https://github.com/ST-DDT/CrazyLogin">...</a>
 * <p>
 * Project page:
 * <p>
 * <a href="https://dev.bukkit.org/server-mods/crazylogin/">Bukkit</a>
 */
public class CrazyLoginHook implements AuthPlugin<Player> {

    private final FastLoginBukkit plugin;

    private final CrazyLogin crazyLoginPlugin;
    private final PlayerListener playerListener;

    public CrazyLoginHook(FastLoginBukkit plugin) {
        this.plugin = plugin;

        crazyLoginPlugin = CrazyLogin.getPlugin();
        playerListener = getListener();
    }

    @Override
    public boolean forceLogin(Player player) {
        //not thread-safe operation
        TaskCallback<Optional<LoginPlayerData>> callback = new TaskCallback<>();
        Scheduler.getAsyncScheduler().runTask(plugin, () -> {
            LoginPlayerData playerData = crazyLoginPlugin.getPlayerData(player);
            if (playerData != null) {
                //mark the account as logged in
                playerData.setLoggedIn(true);

                String ip = Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress();
//this should be done after login to restore the inventory, show players, prevent potential memory leaks...
//from: https://github.com/ST-DDT/CrazyLogin/blob/master/src/main/java/de/st_ddt/crazylogin/CrazyLogin.java#L1948
                playerData.resetLoginFails();
                player.setFireTicks(0);

                if (playerListener != null) {
                    playerListener.removeMovementBlocker(player);
                    playerListener.disableHidenInventory(player);
                    playerListener.disableSaveLogin(player);
                    playerListener.unhidePlayer(player);
                }

//loginFailuresPerIP.remove(IP);
//illegalCommandUsesPerIP.remove(IP);
//tempBans.remove(IP);
                playerData.addIP(ip);
                player.setMetadata("Authenticated", new Authenticated(crazyLoginPlugin, player));
                crazyLoginPlugin.unregisterDynamicHooks();
                callback.setCallBack(Optional.of(playerData));
            }

          callback.setCallBack(Optional.empty());
        });

            var result = callback.getCallBack().filter(LoginPlayerData::isLoggedIn);
            if (result.isPresent()) {
                //SQL-Queries should run async
                crazyLoginPlugin.getCrazyDatabase().saveWithoutPassword(result.get());
                return true;
            }

            return false;
    }

    @Override
    public boolean isRegistered(String playerName) {
        return crazyLoginPlugin.getPlayerData(playerName) != null;
    }

    @Override
    public boolean forceRegister(Player player, String password) {
        CrazyLoginDataDatabase crazyDatabase = crazyLoginPlugin.getCrazyDatabase();

        //this executes a sql query and accesses only thread safe collections, so we can run it async
        LoginPlayerData playerData = crazyLoginPlugin.getPlayerData(player.getName());
        if (playerData == null) {
            //create a fake account - this will be saved to the database with the password=FAILEDLOADING
            //user cannot log in with that password unless the admin uses plain text
            //this automatically marks the player as logged in
            crazyDatabase.save(new LoginPlayerData(player));
            return forceLogin(player);
        }

        return false;
    }

    protected PlayerListener getListener() {
        FieldAccessor accessor = Accessors.getFieldAccessor(crazyLoginPlugin.getClass(), PlayerListener.class, true);
        return (PlayerListener) accessor.get(crazyLoginPlugin);
    }
}

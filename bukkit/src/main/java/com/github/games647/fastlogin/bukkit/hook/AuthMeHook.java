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

import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.message.NamespaceKey;
import com.github.games647.fastlogin.core.message.PreJoinMessage;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.events.RestoreSessionEvent;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.process.register.executors.ApiPasswordRegisterParams;
import fr.xephi.authme.process.register.executors.RegistrationMethod;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * GitHub: <a href="https://github.com/Xephi/AuthMeReloaded/">...</a>
 * <p>
 * Project page:
 * <p>
 * <a href="https://dev.bukkit.org/bukkit-plugins/authme-reloaded/">Bukkit</a>
 * <p>
 * <a href="https://www.spigotmc.org/resources/authme-reloaded.6269/">Spigot</a>
 */
public class AuthMeHook implements AuthPlugin<Player>, Listener {

    private final FastLoginBukkit plugin;

    private final AuthMeApi authmeAPI;
    private Management authmeManagement;

    // AuthMe 6.0+ pre-join dialog support: forceLoginFromProxy(String) on the API
    private Method forceLoginFromProxyMethod;
    private Object preJoinLoginTarget;
    private Method preJoinLoginMethod;

    public AuthMeHook(FastLoginBukkit plugin) {
        this.plugin = plugin;
        this.authmeAPI = AuthMeApi.getInstance();

        if (plugin.getCore().getConfig().getBoolean("respectIpLimit", false)) {
            try {
                Field managementField = this.authmeAPI.getClass().getDeclaredField("management");
                managementField.setAccessible(true);
                this.authmeManagement = (Management) managementField.get(this.authmeAPI);
            } catch (NoSuchFieldException | IllegalAccessException exception) {
                this.authmeManagement = null;
            }
        }

        // Detect AuthMe 6.0+ forceLoginFromProxy(String) API for pre-join dialog compatibility
        try {
            forceLoginFromProxyMethod = authmeAPI.getClass().getMethod("forceLoginFromProxy", String.class);
            preJoinLoginTarget = authmeAPI;
            preJoinLoginMethod = forceLoginFromProxyMethod;
        } catch (NoSuchMethodException ignored) {
            forceLoginFromProxyMethod = null;
        }
        enablePreJoinSupport();
    }

    private void enablePreJoinSupport() {
        try {
            Class<?> configureEvent = Class.forName(
                    "io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent");
            Class<?> connection = Class.forName("io.papermc.paper.connection.PlayerConnection");
            connection.getMethod("isConnected");
            Class.forName("io.papermc.paper.connection.PlayerConfigurationConnection")
                    .getMethod("sendPluginMessage", org.bukkit.plugin.Plugin.class, String.class, byte[].class);
            org.bukkit.plugin.messaging.PluginMessageListener.class
                    .getMethod("onPluginMessageReceived", String.class, connection, byte[].class);
            if (preJoinLoginMethod == null && !findLegacyPreJoinQueue(configureEvent)) {
                return;
            }
            AuthMePreJoinListener listener = new AuthMePreJoinListener(plugin, this);
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            if (plugin.getBungeeManager().isEnabled()) {
                String channel = NamespaceKey.getCombined(plugin.getName(), PreJoinMessage.CHANNEL);
                plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel, listener);
                plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
            }
            plugin.getLog().info("Enabled AuthMe pre-join dialog compatibility");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Older Paper and Spigot keep the ordinary post-join flow.
        } catch (ReflectiveOperationException | SecurityException ex) {
            plugin.getLog().warn("Could not enable AuthMe pre-join dialog compatibility", ex);
        }
    }

    private boolean findLegacyPreJoinQueue(Class<?> configureEvent) throws ReflectiveOperationException {
        Plugin authme = plugin.getServer().getPluginManager().getPlugin("AuthMe");
        if (authme == null) {
            return false;
        }

        HandlerList handlers = (HandlerList) configureEvent.getMethod("getHandlerList").invoke(null);
        boolean hasAuthMeListener = false;
        for (RegisteredListener registration : handlers.getRegisteredListeners()) {
            if (registration.getPlugin() != authme) {
                continue;
            }
            hasAuthMeListener = true;
            Listener listener = registration.getListener();
            try {
                // Builds such as 6.0.0-b2734 check this queue before opening either dialog,
                // but do not yet expose forceLoginFromProxy(String) on AuthMeApi.
                Field sessionsField = listener.getClass().getDeclaredField("proxySessionManager");
                sessionsField.setAccessible(true);
                Object sessions = sessionsField.get(listener);
                if (sessions != null) {
                    Method queueLogin = sessions.getClass().getMethod("processProxySessionMessage", String.class);
                    preJoinLoginTarget = sessions;
                    preJoinLoginMethod = queueLogin;
                    plugin.getLog().info("Using AuthMe session queue for pre-join dialog compatibility");
                    return true;
                }
            } catch (NoSuchFieldException | NoSuchMethodException ignored) {
                // Only the dialog listener exposes the shared session queue.
            }
        }
        if (hasAuthMeListener) {
            plugin.getLog().warn("AuthMe configuration listener found, but no compatible pre-join login API or queue");
        }
        return false;
    }

    public void forceLoginBeforeJoin(String playerName) throws ReflectiveOperationException {
        preJoinLoginMethod.invoke(preJoinLoginTarget, playerName);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSessionRestore(RestoreSessionEvent restoreSessionEvent) {
        Player player = restoreSessionEvent.getPlayer();

        BukkitLoginSession session = plugin.getSession(player.spigot().getRawAddress());
        if (session != null && session.isVerifiedPremium()) {
            restoreSessionEvent.setCancelled(true);
        }
    }

    @Override
    public boolean forceLogin(Player player) {
        if (authmeAPI.isAuthenticated(player)) {
            // AuthMe may have consumed the pre-join request before our delayed task runs.
            BukkitLoginSession session = preJoinLoginMethod == null ? null
                    : plugin.getSession(player.spigot().getRawAddress());
            if (session != null && session.isVerifiedPremium() && session.isPreJoinLoginQueued()
                    && player.getName().equals(session.getUsername())) {
                return true;
            }
            plugin.getLog().warn(ALREADY_AUTHENTICATED, player);
            return false;
        }

        if (plugin.getBungeeManager().isEnabled() && forceLoginFromProxyMethod != null) {
            try {
                forceLoginBeforeJoin(player.getName());
                return true;
            } catch (ReflectiveOperationException ex) {
                plugin.getLog().warn("Failed to invoke AuthMe forceLoginFromProxy for {}. "
                        + "Falling back to standard forceLogin.", player.getName(), ex);
            }
        }

        //skips registration and login
        authmeAPI.forceLogin(player);
        return true;
    }

    @Override
    public boolean isRegistered(String playerName) {
        return authmeAPI.isRegistered(playerName);
    }

    @Override
    //this automatically login the player too
    public boolean forceRegister(Player player, String password) {
        //if we have the management - we can trigger register with IP limit checks
        if (authmeManagement != null) {
            authmeManagement.performRegister(RegistrationMethod.PASSWORD_REGISTRATION,
                    ApiPasswordRegisterParams.of(player, password, true));
        } else {
            authmeAPI.forceRegister(player, password);
        }

        return true;
    }
}

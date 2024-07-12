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

import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.fastlogin.bungee.BungeeLoginSession;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.bungee.task.AsyncPremiumCheck;
import com.github.games647.fastlogin.bungee.task.FloodgateAuthTask;
import com.github.games647.fastlogin.bungee.task.ForceLoginTask;
import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.shared.LoginSession;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.google.common.base.Throwables;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import net.md_5.bungee.protocol.Property;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Enables online mode logins for specified users and sends plugin message to the Bukkit version of this plugin in
 * order to clear that the connection is online mode.
 */
public class ConnectListener implements Listener {

    private static final String UUID_FIELD_NAME = "uniqueId";
    protected static final MethodHandle UNIQUE_ID_SETTER;

    private static final String REWRITE_ID_NAME = "rewriteId";
    protected static final MethodHandle REWRITE_ID_SETTER;

    static {
        MethodHandle uniqueIdHandle = null;
        MethodHandle rewriterHandle = null;
        try {
            Lookup lookup = MethodHandles.lookup();

            // test for implementation class availability
            Class.forName("net.md_5.bungee.connection.InitialHandler");
            uniqueIdHandle = getHandlerSetter(lookup, UUID_FIELD_NAME);
            try {
                rewriterHandle = getHandlerSetter(lookup, REWRITE_ID_NAME);
            } catch (NoSuchFieldException noSuchFieldEx) {
                Logger logger = LoggerFactory.getLogger(ConnectListener.class);
                logger.error(
                        "Rewrite field not found. Setting only legacy BungeeCord field"
                );
            }
        } catch (ReflectiveOperationException reflectiveOperationException) {
            Logger logger = LoggerFactory.getLogger(ConnectListener.class);
            logger.error(
                    "Cannot find Bungee UUID field implementation; Disabling premium UUID and skin won't work.",
                    reflectiveOperationException
            );
        }

        UNIQUE_ID_SETTER = uniqueIdHandle;
        REWRITE_ID_SETTER = rewriterHandle;
    }

    private static MethodHandle getHandlerSetter(Lookup lookup, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        Field uuidField = InitialHandler.class.getDeclaredField(fieldName);
        uuidField.setAccessible(true);
        return lookup.unreflectSetter(uuidField);
    }

    private final FastLoginBungee plugin;
    private final AntiBotService antiBotService;
    private final Property[] emptyProperties = {};

    public ConnectListener(FastLoginBungee plugin, AntiBotService antiBotService) {
        this.plugin = plugin;
        this.antiBotService = antiBotService;
    }

    @EventHandler
    public void onPreLogin(PreLoginEvent preLoginEvent) {
        PendingConnection connection = preLoginEvent.getConnection();
        if (preLoginEvent.isCancelled()) {
            return;
        }

        InetSocketAddress address = preLoginEvent.getConnection().getAddress();
        String username = connection.getName();

        plugin.getLog().info("Incoming login request for {} from {}", username, connection.getSocketAddress());

        Action action = antiBotService.onIncomingConnection(address, username);
        switch (action) {
            case Ignore:
                // just ignore
                return;
            case Block:
                String message = plugin.getCore().getMessage("kick-antibot");
                preLoginEvent.setCancelReason(TextComponent.fromLegacyText(message));
                preLoginEvent.setCancelled(true);
                break;
            case Continue:
            default:
                preLoginEvent.registerIntent(plugin);
                Runnable asyncPremiumCheck = new AsyncPremiumCheck(plugin, preLoginEvent, connection, username);
                plugin.getScheduler().runAsync(asyncPremiumCheck);
                break;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(LoginEvent loginEvent) {
        if (loginEvent.isCancelled()) {
            return;
        }

        //use the login event instead of the post login event in order to send the login success packet to the client
        //with the offline uuid this makes it possible to set the skin then
        PendingConnection connection = loginEvent.getConnection();
        if (connection.isOnlineMode()) {
            LoginSession session = plugin.getSession().get(connection);

            UUID verifiedUUID = connection.getUniqueId();
            String verifiedUsername = connection.getName();
            session.setUuid(verifiedUUID);
            session.setVerifiedUsername(verifiedUsername);

            StoredProfile playerProfile = session.getProfile();
            playerProfile.setId(verifiedUUID);
        }
    }

    protected void setOfflineId(InitialHandler connection, String username) {
        try {
            UUID oldPremiumId = connection.getUniqueId();
            UUID offlineUUID = UUIDAdapter.generateOfflineId(username);

            // BungeeCord only allows setting the UUID in PreLogin events and before requesting online mode
            // However if online mode is requested, it will override previous values
            // So we have to do it with reflection
            UNIQUE_ID_SETTER.invokeExact(connection, offlineUUID);

            // if available set rewrite id to forward the UUID for newer BungeeCord versions since
            // https://github.com/SpigotMC/BungeeCord/commit/1be25b6c74ec2be4b15adf8ca53a0497f01e2afe
            if (REWRITE_ID_SETTER != null) {
                REWRITE_ID_SETTER.invokeExact(connection, offlineUUID);
            }

            String format = "Overridden UUID from {} to {} (based of {}) on {}";
            plugin.getLog().info(format, oldPremiumId, offlineUUID, username, connection);
        } catch (Exception ex) {
            plugin.getLog().error("Failed to set offline uuid of {}", username, ex);
        } catch (Throwable throwable) {
            // throw remaining exceptions like out of memory that we shouldn't handle ourselves
            Throwables.throwIfUnchecked(throwable);
        }
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent serverConnectedEvent) {
        ProxiedPlayer player = serverConnectedEvent.getPlayer();
        Server server = serverConnectedEvent.getServer();

        FloodgateService floodgateService = plugin.getFloodgateService();
        if (floodgateService != null) {
            FloodgatePlayer floodgatePlayer = floodgateService.getBedrockPlayer(player.getUniqueId());
            if (floodgatePlayer != null) {
                Runnable floodgateAuthTask = new FloodgateAuthTask(plugin.getCore(), player, floodgatePlayer, server);
                plugin.getScheduler().runAsync(floodgateAuthTask);
                return;
            }
        }

        BungeeLoginSession session = plugin.getSession().get(player.getPendingConnection());
        if (session == null) {
            return;
        }

        // delay sending force command, because Paper will process the login event asynchronously
        // In this case it means that the force command (plugin message) is already received and processed while
        // player is still in the login phase and reported to be offline.
        Runnable loginTask = new ForceLoginTask(plugin.getCore(), player, server, session);
        plugin.getScheduler().runAsync(loginTask);
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent disconnectEvent) {
        ProxiedPlayer player = disconnectEvent.getPlayer();
        plugin.getSession().remove(player.getPendingConnection());
        plugin.getCore().getPendingConfirms().remove(player.getUniqueId());
    }
}

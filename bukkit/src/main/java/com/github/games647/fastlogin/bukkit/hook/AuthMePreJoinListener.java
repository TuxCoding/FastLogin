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
import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginAutoLoginEvent;
import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.message.NamespaceKey;
import com.github.games647.fastlogin.core.message.PreJoinMessage;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

/**
 * Runs before AuthMe's blocking configuration dialog. Only loaded on Paper versions with this API.
 */
public class AuthMePreJoinListener implements Listener, PluginMessageListener {

    private final FastLoginBukkit plugin;
    private final AuthMeHook hook;
    private final String channel;
    private final ConcurrentMap<PlayerConfigurationConnection, PendingRequest> pending = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;
    private Long proxyTimeoutNanos;
    private PendingRequest proxyProbe;

    public AuthMePreJoinListener(FastLoginBukkit plugin, AuthMeHook hook) {
        this(plugin, hook, System::nanoTime);
    }

    AuthMePreJoinListener(FastLoginBukkit plugin, AuthMeHook hook, LongSupplier nanoTime) {
        this.plugin = plugin;
        this.hook = hook;
        this.nanoTime = nanoTime;
        this.channel = NamespaceKey.getCombined(plugin.getName(), PreJoinMessage.CHANNEL);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        if (!plugin.getCore().getConfig().get("autoLogin", true)) {
            return;
        }

        PlayerConfigurationConnection connection = event.getConnection();
        String name = connection.getProfile().getName();
        if (name == null || !(connection.getAddress() instanceof InetSocketAddress)) {
            return;
        }

        InetSocketAddress address = (InetSocketAddress) connection.getAddress();
        BukkitLoginSession session;
        if (plugin.getBungeeManager().isEnabled()) {
            session = requestProxySession(connection);
        } else {
            session = plugin.getSession(address);
        }

        if (session == null || !session.isVerifiedPremium() || !name.equals(session.getUsername())
                || !connection.isConnected()) {
            return;
        }

        try {
            // A proxy REGISTER only establishes a new account. It must never authenticate
            // an existing AuthMe account, even if the premium name is verified by the proxy.
            if (session.needsRegistration() && hook.isRegistered(name)) {
                return;
            }
            plugin.putSession(address, session);
            boolean registration = session.needsRegistration()
                    || (plugin.getCore().getConfig().get("auto-register-unknown", false) && !hook.isRegistered(name));
            if (!registration) {
                BukkitFastLoginAutoLoginEvent loginEvent =
                        new BukkitFastLoginAutoLoginEvent(session, session.getProfile());
                plugin.getServer().getPluginManager().callEvent(loginEvent);
                session.setPreJoinAutoLoginAllowed(!loginEvent.isCancelled());
                if (loginEvent.isCancelled()) {
                    return;
                }
            }

            // Queue before AuthMe opens either the login or the registration dialog. Registration
            // and its IP limits still run through the normal FastLogin task after the player joins.
            hook.forceLoginBeforeJoin(name);
            session.setPreJoinLoginQueued(true);
        } catch (ReflectiveOperationException ex) {
            plugin.getLog().warn("Could not prepare AuthMe pre-join login for {}", name, ex);
        }
    }

    private synchronized PendingRequest createProxyRequest() {
        if (proxyProbe != null || (proxyTimeoutNanos != null
                && nanoTime.getAsLong() - proxyTimeoutNanos < TimeUnit.SECONDS.toNanos(30))) {
            return null;
        }
        PendingRequest request = new PendingRequest();
        if (proxyTimeoutNanos != null) {
            proxyProbe = request;
        }
        return request;
    }

    private BukkitLoginSession requestProxySession(PlayerConfigurationConnection connection) {
        PendingRequest request = createProxyRequest();
        if (request == null) {
            return null;
        }
        pending.put(connection, request);
        try {
            ByteArrayDataOutput output = ByteStreams.newDataOutput();
            new PreJoinMessage(request.id, null).writeTo(output);
            connection.sendPluginMessage(plugin, channel, output.toByteArray());
            LoginActionMessage action = request.response.get(5, TimeUnit.SECONDS);
            synchronized (this) {
                if (proxyProbe == request) {
                    proxyTimeoutNanos = null;
                }
            }
            if (action.getType() == LoginActionMessage.Type.CRACKED) {
                return null;
            }
            BukkitLoginSession session = new BukkitLoginSession(action.getPlayerName(),
                    action.getType() == LoginActionMessage.Type.LOGIN);
            session.setVerifiedPremium(true);
            return session;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ex) {
            request.response.cancel(false);
            synchronized (this) {
                proxyTimeoutNanos = nanoTime.getAsLong();
            }
            plugin.getLog().warn("No pre-join response from FastLogin proxy for {}; keeping AuthMe authentication "
                            + "and skipping further pre-join requests for 30 seconds",
                    connection.getProfile().getName());
        } finally {
            pending.remove(connection, request);
            synchronized (this) {
                if (proxyProbe == request) {
                    proxyProbe = null;
                }
            }
        }
        return null;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // This channel is only used during configuration.
    }

    @Override
    public void onPluginMessageReceived(String channel, PlayerConnection connection, byte[] message) {
        if (!this.channel.equals(channel) || !(connection instanceof PlayerConfigurationConnection)) {
            return;
        }
        PendingRequest request = pending.get(connection);
        if (request == null) {
            return;
        }
        try {
            PreJoinMessage response = new PreJoinMessage();
            response.readFrom(ByteStreams.newDataInput(message));
            LoginActionMessage action = response.getLoginAction();
            String name = ((PlayerConfigurationConnection) connection).getProfile().getName();
            if (request.id.equals(response.getRequestId()) && action != null
                    && action.getPlayerName().equals(name)
                    && plugin.getBungeeManager().isProxyAllowed(action.getProxyId())) {
                request.response.complete(action);
            }
        } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException ex) {
            plugin.getLog().debug("Ignoring malformed FastLogin pre-join response", ex);
        }
    }

    private static final class PendingRequest {

        private final UUID id = UUID.randomUUID();
        private final CompletableFuture<LoginActionMessage> response = new CompletableFuture<>();
    }
}

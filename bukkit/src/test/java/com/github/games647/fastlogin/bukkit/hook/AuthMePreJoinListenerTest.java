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

import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginAutoLoginEvent;
import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.message.PreJoinMessage;
import com.google.common.io.ByteStreams;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import org.bukkit.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class AuthMePreJoinListenerTest {

    private final FastLoginBukkit plugin = mock(FastLoginBukkit.class, RETURNS_DEEP_STUBS);
    private final AuthMeHook hook = mock(AuthMeHook.class);
    private final PlayerConfigurationConnection connection = mock(PlayerConfigurationConnection.class);
    private final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
    private AuthMePreJoinListener listener;
    private BukkitLoginSession session;
    private final net.md_5.bungee.config.Configuration config = new net.md_5.bungee.config.Configuration();

    @BeforeEach
    void setUp() {
        when(plugin.getName()).thenReturn("FastLogin");
        config.set("autoLogin", true);
        when(plugin.getCore().getConfig()).thenReturn(config);
        when(connection.getAddress()).thenReturn(address);
        when(connection.isConnected()).thenReturn(true);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.getName()).thenReturn("PremiumPlayer");
        when(connection.getProfile()).thenReturn(profile);
        session = new BukkitLoginSession("PremiumPlayer", true);
        session.setVerifiedPremium(true);
        when(plugin.getSession(address)).thenReturn(session);
        listener = new AuthMePreJoinListener(plugin, hook);
    }

    @Test
    void verifiedPremiumSkipsDialogBeforeJoin() throws ReflectiveOperationException {
        listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));
        verify(hook).forceLoginBeforeJoin("PremiumPlayer");
        assertTrue(session.isPreJoinLoginQueued());
    }

    @Test
    void failedQueueDoesNotMarkSessionAsQueued() throws ReflectiveOperationException {
        doThrow(new ReflectiveOperationException("queue unavailable")).when(hook)
                .forceLoginBeforeJoin("PremiumPlayer");
        listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));
        assertFalse(session.isPreJoinLoginQueued());
    }

    @Test
    void unverifiedPlayerKeepsDialog() throws ReflectiveOperationException {
        session.setVerifiedPremium(false);
        listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));
        verify(hook, never()).forceLoginBeforeJoin(anyString());
    }

    @Test
    void disabledAutoLoginKeepsDialog() throws ReflectiveOperationException {
        config.set("autoLogin", false);
        listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));
        verify(hook, never()).forceLoginBeforeJoin(anyString());
    }

    @Test
    void differentSessionNameKeepsDialog() throws ReflectiveOperationException {
        when(connection.getProfile().getName()).thenReturn("AnotherPlayer");
        listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));
        verify(hook, never()).forceLoginBeforeJoin(anyString());
    }

    @Test
    void cancelledAutoLoginKeepsDialog() throws ReflectiveOperationException {
        org.bukkit.plugin.PluginManager manager = plugin.getServer().getPluginManager();
        doAnswer(invocation -> {
            ((BukkitFastLoginAutoLoginEvent) invocation.getArgument(0)).setCancelled(true);
            return null;
        }).when(manager).callEvent(any(Event.class));
        listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));
        verify(hook, never()).forceLoginBeforeJoin(anyString());
    }

    @Test
    void newPremiumCanReachPostJoinRegistration() throws ReflectiveOperationException {
        session = new BukkitLoginSession("PremiumPlayer", false);
        session.setVerifiedPremium(true);
        when(plugin.getSession(address)).thenReturn(session);
        listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));
        verify(hook).forceLoginBeforeJoin("PremiumPlayer");
        verify(plugin.getServer().getPluginManager(), never()).callEvent(any(Event.class));
    }

    @Test
    void proxyReplyMustMatchConnectionRequestAndAllowedProxy() throws ReflectiveOperationException {
        when(plugin.getBungeeManager().isEnabled()).thenReturn(true);
        UUID allowedProxy = UUID.randomUUID();
        when(plugin.getBungeeManager().isProxyAllowed(allowedProxy)).thenReturn(true);
        PlayerConfigurationConnection otherConnection = mock(PlayerConfigurationConnection.class);
        doAnswer(invocation -> {
            PreJoinMessage request = new PreJoinMessage();
            request.readFrom(ByteStreams.newDataInput(invocation.<byte[]>getArgument(2)));
            listener.onPluginMessageReceived("fastlogin:prejoin", connection, new byte[] {1});
            reply(connection, UUID.randomUUID(), allowedProxy, "PremiumPlayer");
            reply(otherConnection, request.getRequestId(), allowedProxy, "PremiumPlayer");
            reply(connection, request.getRequestId(), UUID.randomUUID(), "PremiumPlayer");
            reply(connection, request.getRequestId(), allowedProxy, "AnotherPlayer");
            verify(hook, never()).forceLoginBeforeJoin(anyString());
            reply(connection, request.getRequestId(), allowedProxy, "PremiumPlayer");
            return null;
        }).when(connection).sendPluginMessage(eq(plugin), eq("fastlogin:prejoin"), any(byte[].class));

        listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));

        verify(hook).forceLoginBeforeJoin("PremiumPlayer");
        verify(plugin).putSession(eq(address), any(BukkitLoginSession.class));
    }

    @Test
    void validProxyResponsesKeepPreJoinEnabled() throws ReflectiveOperationException {
        when(plugin.getBungeeManager().isEnabled()).thenReturn(true);
        UUID allowedProxy = UUID.randomUUID();
        when(plugin.getBungeeManager().isProxyAllowed(allowedProxy)).thenReturn(true);
        doAnswer(invocation -> {
            PreJoinMessage request = new PreJoinMessage();
            request.readFrom(ByteStreams.newDataInput(invocation.<byte[]>getArgument(2)));
            reply(connection, request.getRequestId(), allowedProxy, "PremiumPlayer");
            return null;
        }).when(connection).sendPluginMessage(eq(plugin), eq("fastlogin:prejoin"), any(byte[].class));

        listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));
        listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));

        verify(hook, times(2)).forceLoginBeforeJoin("PremiumPlayer");
    }

    @Test
    void registrationCannotAuthenticateAnExistingAuthMeAccount() throws ReflectiveOperationException {
        when(plugin.getBungeeManager().isEnabled()).thenReturn(true);
        UUID allowedProxy = UUID.randomUUID();
        when(plugin.getBungeeManager().isProxyAllowed(allowedProxy)).thenReturn(true);
        when(hook.isRegistered("PremiumPlayer")).thenReturn(true);
        doAnswer(invocation -> {
            PreJoinMessage request = new PreJoinMessage();
            request.readFrom(ByteStreams.newDataInput(invocation.<byte[]>getArgument(2)));
            PreJoinMessage response = new PreJoinMessage(request.getRequestId(),
                    new LoginActionMessage(LoginActionMessage.Type.REGISTER, "PremiumPlayer", allowedProxy));
            com.google.common.io.ByteArrayDataOutput output = ByteStreams.newDataOutput();
            response.writeTo(output);
            listener.onPluginMessageReceived("fastlogin:prejoin", connection, output.toByteArray());
            return null;
        }).when(connection).sendPluginMessage(eq(plugin), eq("fastlogin:prejoin"), any(byte[].class));
        listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));
        verify(hook, never()).forceLoginBeforeJoin(anyString());
        verify(plugin, never()).putSession(eq(address), any(BukkitLoginSession.class));
    }

    private void reply(PlayerConfigurationConnection source, UUID requestId, UUID proxyId, String name) {
        PreJoinMessage response = new PreJoinMessage(requestId,
                new LoginActionMessage(LoginActionMessage.Type.LOGIN, name, proxyId));
        com.google.common.io.ByteArrayDataOutput output = ByteStreams.newDataOutput();
        response.writeTo(output);
        listener.onPluginMessageReceived("fastlogin:prejoin", source, output.toByteArray());
    }

    @Test
    void replyAlreadyBeingValidatedCannotClearTimeoutCooldown() throws Exception {
        when(plugin.getBungeeManager().isEnabled()).thenReturn(true);
        UUID allowedProxy = UUID.randomUUID();
        CountDownLatch validating = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicReference<CompletableFuture<Void>> response = new AtomicReference<>();
        when(plugin.getBungeeManager().isProxyAllowed(allowedProxy)).thenAnswer(invocation -> {
            validating.countDown();
            assertTrue(resume.await(10, TimeUnit.SECONDS));
            return true;
        });
        doAnswer(invocation -> {
            PreJoinMessage request = new PreJoinMessage();
            request.readFrom(ByteStreams.newDataInput(invocation.<byte[]>getArgument(2)));
            response.set(CompletableFuture.runAsync(
                    () -> reply(connection, request.getRequestId(), allowedProxy, "PremiumPlayer")));
            assertTrue(validating.await(1, TimeUnit.SECONDS));
            return null;
        }).when(connection).sendPluginMessage(eq(plugin), eq("fastlogin:prejoin"), any(byte[].class));

        try {
            listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));
        } finally {
            resume.countDown();
        }
        response.get().get(1, TimeUnit.SECONDS);
        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection)));
        verify(connection).sendPluginMessage(eq(plugin), eq("fastlogin:prejoin"), any(byte[].class));
        verify(hook, never()).forceLoginBeforeJoin(anyString());
    }

    @Test
    void expiredProxyRequestCannotApproveALaterConnection() throws ReflectiveOperationException {
        AtomicLong clock = new AtomicLong();
        listener = new AuthMePreJoinListener(plugin, hook, clock::get);
        when(plugin.getBungeeManager().isEnabled()).thenReturn(true);
        UUID allowedProxy = UUID.randomUUID();
        when(plugin.getBungeeManager().isProxyAllowed(allowedProxy)).thenReturn(true);
        doAnswer(invocation -> {
            clock.set(TimeUnit.SECONDS.toNanos(10));
            return null;
        }).when(connection).sendPluginMessage(eq(plugin), eq("fastlogin:prejoin"), any(byte[].class));
        org.mockito.ArgumentCaptor<byte[]> request = org.mockito.ArgumentCaptor.forClass(byte[].class);
        listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));
        verify(connection).sendPluginMessage(eq(plugin), eq("fastlogin:prejoin"), request.capture());
        PreJoinMessage message = new PreJoinMessage();
        message.readFrom(ByteStreams.newDataInput(request.getValue()));
        reply(connection, message.getRequestId(), allowedProxy, "PremiumPlayer");
        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection)));
        verify(connection).sendPluginMessage(eq(plugin), eq("fastlogin:prejoin"), any(byte[].class));
        verify(hook, never()).forceLoginBeforeJoin(anyString());
        clock.set(TimeUnit.SECONDS.toNanos(40) - 1);
        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection)));
        verify(connection).sendPluginMessage(eq(plugin), eq("fastlogin:prejoin"), any(byte[].class));

        PlayerConfigurationConnection competing = mock(PlayerConfigurationConnection.class, RETURNS_DEEP_STUBS);
        when(competing.getProfile().getName()).thenReturn("AnotherPlayer");
        when(competing.getAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12346));
        AtomicBoolean recoveryProbe = new AtomicBoolean(true);
        doAnswer(invocation -> {
            PreJoinMessage retry = new PreJoinMessage();
            retry.readFrom(ByteStreams.newDataInput(invocation.<byte[]>getArgument(2)));
            if (recoveryProbe.getAndSet(false)) {
                assertTimeoutPreemptively(Duration.ofSeconds(1),
                        () -> listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(competing)));
                verify(competing, never()).sendPluginMessage(eq(plugin), eq("fastlogin:prejoin"), any(byte[].class));
            }
            reply(connection, retry.getRequestId(), allowedProxy, "PremiumPlayer");
            return null;
        }).when(connection).sendPluginMessage(eq(plugin), eq("fastlogin:prejoin"), any(byte[].class));
        clock.set(TimeUnit.SECONDS.toNanos(40));
        listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));
        listener.onConfigure(new AsyncPlayerConnectionConfigureEvent(connection));
        verify(connection, times(3)).sendPluginMessage(eq(plugin), eq("fastlogin:prejoin"), any(byte[].class));
        verify(hook, times(2)).forceLoginBeforeJoin("PremiumPlayer");
    }
}

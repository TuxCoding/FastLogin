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
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.message.PreJoinMessage;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PreJoinMessageListenerTest {

    @Test
    void premiumReplyUsesOriginatingConnectionWithoutCompletingLogin() {
        assertReply(true, true, LoginActionMessage.Type.LOGIN);
    }

    @Test
    void unknownPremiumCanRegisterAfterConfiguration() {
        assertReply(true, false, LoginActionMessage.Type.REGISTER);
    }

    @Test
    void offlinePlayerCannotClaimPremiumSession() {
        assertReply(false, true, LoginActionMessage.Type.CRACKED);
    }

    @Test
    void clientCannotRequestAuthentication() {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        when(plugin.getName()).thenReturn("fastlogin");
        Player player = mock(Player.class);
        ServerConnection server = mock(ServerConnection.class);
        PluginMessageEvent event = new PluginMessageEvent(player, server,
                MinecraftChannelIdentifier.from("fastlogin:prejoin"), new byte[0]);
        new PluginMessageListener(plugin).onPluginMessage(event);
        assertFalse(event.getResult().isAllowed());
        verifyNoInteractions(server);
    }

    private void assertReply(boolean onlineMode, boolean registered, LoginActionMessage.Type expected) {
        FastLoginVelocity plugin = mock(FastLoginVelocity.class, RETURNS_DEEP_STUBS);
        when(plugin.getName()).thenReturn("fastlogin");
        Player player = mock(Player.class);
        when(player.getUsername()).thenReturn("PremiumPlayer");
        when(player.isOnlineMode()).thenReturn(onlineMode);
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 23456);
        when(player.getRemoteAddress()).thenReturn(address);
        ServerConnection source = mock(ServerConnection.class);
        when(source.getPlayer()).thenReturn(player);
        ConcurrentHashMap<InetSocketAddress, VelocityLoginSession> sessions = new ConcurrentHashMap<>();
        VelocityLoginSession session = new VelocityLoginSession("PremiumPlayer", registered, null);
        sessions.put(address, session);
        when(plugin.getSession()).thenReturn(sessions);
        UUID proxyId = UUID.randomUUID();
        when(plugin.getProxyId()).thenReturn(proxyId);
        UUID requestId = UUID.randomUUID();
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        new PreJoinMessage(requestId, null).writeTo(output);
        PluginMessageEvent event = new PluginMessageEvent(source, player,
                MinecraftChannelIdentifier.from("fastlogin:prejoin"), output.toByteArray());

        new PluginMessageListener(plugin).onPluginMessage(event);

        ArgumentCaptor<PreJoinMessage> response = ArgumentCaptor.forClass(PreJoinMessage.class);
        verify(plugin).sendPluginMessage(eq(source), response.capture());
        assertFalse(event.getResult().isAllowed());
        assertEquals(requestId, response.getValue().getRequestId());
        assertEquals("PremiumPlayer", response.getValue().getLoginAction().getPlayerName());
        assertEquals(expected, response.getValue().getLoginAction().getType());
        assertEquals(proxyId, response.getValue().getLoginAction().getProxyId());
        assertFalse(session.isAlreadyLogged());
        assertFalse(session.isAlreadySaved());
    }
}

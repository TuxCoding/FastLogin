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

import com.github.games647.fastlogin.bungee.BungeeLoginSession;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.message.PreJoinMessage;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        FastLoginBungee plugin = mock(FastLoginBungee.class);
        when(plugin.getName()).thenReturn("FastLogin");
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        Server server = mock(Server.class);
        PluginMessageEvent event = new PluginMessageEvent(player, server, "fastlogin:prejoin", new byte[0]);
        new PluginMessageListener(plugin).onPluginMessage(event);
        assertTrue(event.isCancelled());
        verifyNoInteractions(server);
    }

    private void assertReply(boolean onlineMode, boolean registered, LoginActionMessage.Type expected) {
        FastLoginBungee plugin = mock(FastLoginBungee.class, RETURNS_DEEP_STUBS);
        when(plugin.getName()).thenReturn("FastLogin");
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getName()).thenReturn("PremiumPlayer");
        PendingConnection connection = mock(PendingConnection.class);
        when(connection.isOnlineMode()).thenReturn(onlineMode);
        when(player.getPendingConnection()).thenReturn(connection);
        Server source = mock(Server.class);
        ConcurrentHashMap<PendingConnection, BungeeLoginSession> sessions = new ConcurrentHashMap<>();
        BungeeLoginSession session = new BungeeLoginSession("PremiumPlayer", registered, null);
        sessions.put(connection, session);
        when(plugin.getSession()).thenReturn(sessions);
        UUID proxyId = UUID.randomUUID();
        when(plugin.getProxy().getConfig().getUuid()).thenReturn(proxyId.toString());
        UUID requestId = UUID.randomUUID();
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        new PreJoinMessage(requestId, null).writeTo(output);
        PluginMessageEvent event = new PluginMessageEvent(source, player, "fastlogin:prejoin", output.toByteArray());

        new PluginMessageListener(plugin).onPluginMessage(event);

        ArgumentCaptor<PreJoinMessage> response = ArgumentCaptor.forClass(PreJoinMessage.class);
        verify(plugin).sendPluginMessage(eq(source), response.capture());
        assertTrue(event.isCancelled());
        assertEquals(requestId, response.getValue().getRequestId());
        assertEquals("PremiumPlayer", response.getValue().getLoginAction().getPlayerName());
        assertEquals(expected, response.getValue().getLoginAction().getType());
        assertEquals(proxyId, response.getValue().getLoginAction().getProxyId());
        assertFalse(session.isAlreadyLogged());
        assertFalse(session.isAlreadySaved());
    }
}

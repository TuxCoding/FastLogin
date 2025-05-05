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
package com.github.games647.fastlogin.velocity.task;

import com.github.games647.fastlogin.core.shared.JoinManagement;
import com.github.games647.fastlogin.core.shared.event.FastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import com.github.games647.fastlogin.velocity.VelocityLoginSource;
import com.github.games647.fastlogin.velocity.event.VelocityFastLoginPreLoginEvent;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;

import java.util.concurrent.ExecutionException;

public class AsyncPremiumCheck extends JoinManagement<Player, CommandSource, VelocityLoginSource>
        implements Runnable {

    private final FastLoginVelocity plugin;
    private final String username;
    private final PreLoginEvent preLoginEvent;
    private final InboundConnection connection;

    public AsyncPremiumCheck(FastLoginVelocity plugin, InboundConnection connection, String username,
                             PreLoginEvent preLoginEvent) {
        super(plugin.getCore(), plugin.getCore().getAuthPluginHook(), plugin.getBedrockService());
        this.plugin = plugin;
        this.connection = connection;
        this.username = username;
        this.preLoginEvent = preLoginEvent;
    }

    @Override
    public void run() {
        plugin.getSession().remove(connection);
        super.onLogin(username, new VelocityLoginSource(connection, preLoginEvent));
    }

    @Override
    public FastLoginPreLoginEvent callFastLoginPreLoginEvent(String username, VelocityLoginSource source,
                                                             StoredProfile profile) {
        VelocityFastLoginPreLoginEvent event = new VelocityFastLoginPreLoginEvent(username, source, profile);
        try {
            return plugin.getProxy().getEventManager().fire(event).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupt flag
            return event;
        } catch (ExecutionException e) {
            core.getPlugin().getLog().error("Error firing event", e);
            return event;
        }
    }

    @Override
    public void requestPremiumLogin(VelocityLoginSource source, StoredProfile profile,
                                    String username, boolean registered) {
        source.enableOnlinemode();
        VelocityLoginSession session = new VelocityLoginSession(username, registered, profile);
        plugin.getSession().put(source.getConnection(), session);

        String ip = source.getAddress().getAddress().getHostAddress();
        plugin.getCore().addLoginAttempt(ip, username);
    }

    @Override
    public void startCrackedSession(VelocityLoginSource source, StoredProfile profile, String username) {
        VelocityLoginSession session = new VelocityLoginSession(username, false, profile);
        plugin.getSession().put(source.getConnection(), session);
    }
}

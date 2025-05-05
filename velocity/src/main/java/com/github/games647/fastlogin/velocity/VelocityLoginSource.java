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
package com.github.games647.fastlogin.velocity;

import com.github.games647.fastlogin.core.shared.LoginSource;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.net.InetSocketAddress;

public class VelocityLoginSource implements LoginSource {

    private final InboundConnection connection;
    private final PreLoginEvent preLoginEvent;

    public VelocityLoginSource(InboundConnection connection, PreLoginEvent preLoginEvent) {
        this.connection = connection;
        this.preLoginEvent = preLoginEvent;
    }

    @Override
    public void enableOnlinemode() {
        preLoginEvent.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
    }

    @Override
    public void kick(String message) {
        if (message == null) {
            preLoginEvent.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text("Kicked").color(NamedTextColor.WHITE))
            );
        } else {
            preLoginEvent.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(message))
            );
        }
    }

    @Override
    public InetSocketAddress getAddress() {
        return connection.getRemoteAddress();
    }

    public InboundConnection getConnection() {
        return connection;
    }
}

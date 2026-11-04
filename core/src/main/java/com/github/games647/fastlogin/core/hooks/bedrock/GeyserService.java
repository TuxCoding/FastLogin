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
package com.github.games647.fastlogin.core.hooks.bedrock;

import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.LoginSource;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.network.AuthType;

import java.util.UUID;

public class GeyserService extends BedrockService<GeyserConnection> {

    private final GeyserApi geyser;
    private final FastLoginCore<?, ?, ?> core;

    public GeyserService(GeyserApi geyser, FastLoginCore<?, ?, ?> core) {
        super(core);
        this.geyser = geyser;
        this.core = core;
    }

    @Override
    public boolean performChecks(String username, LoginSource source) {
        // AuthType.FLOODGATE will be handled by FloodgateService
        AuthType authType = geyser.defaultRemoteServer().authType();
        if (authType == AuthType.ONLINE) {
            // authenticate everyone, as if they were Java players, since they have signed
            // in through Mojang
            return false;
        }

        if ("true".equals(allowConflict)) {
            core.getPlugin().getLog().info("Skipping name conflict checking for player {}", username);
        } else {
            super.checkNameConflict(username, source);
        }
        return true;
    }

    @Override
    public GeyserConnection getBedrockPlayer(String username) {
        for (GeyserConnection connection : geyser.onlineConnections()) {
            if (username.equals(connection.name())) {
                return connection;
            }
        }
        return null;
    }

    @Override
    public GeyserConnection getBedrockPlayer(UUID uuid) {
        return geyser.connectionByUuid(uuid);
    }
}

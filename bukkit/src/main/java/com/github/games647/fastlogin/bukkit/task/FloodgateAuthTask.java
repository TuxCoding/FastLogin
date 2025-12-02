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
package com.github.games647.fastlogin.bukkit.task;

import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.scheduler.functions.Scheduler;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.FloodgateManagement;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.net.InetSocketAddress;
import java.util.UUID;

public class FloodgateAuthTask extends FloodgateManagement<Player, CommandSender, BukkitLoginSession, FastLoginBukkit> {

    public FloodgateAuthTask(FastLoginCore<Player, CommandSender, FastLoginBukkit> core, Player player,
                             FloodgatePlayer floodgatePlayer) {
        super(core, player, floodgatePlayer);
    }

    @Override
    protected void startLogin() {
        BukkitLoginSession session = new BukkitLoginSession(player.getName(), isRegistered, profile);

        // enable auto login based on the value of 'autoLoginFloodgate' in config.yml
        session.setVerifiedPremium(isAutoAuthAllowed(autoLoginFloodgate));

        // run login task
        Runnable forceLoginTask = new ForceLoginTask(core.getPlugin().getCore(), player, session);
        Scheduler.getAsyncScheduler().runTask(core.getPlugin(), forceLoginTask);
    }

    protected String getName(Player player) {
        return player.getName();
    }

    protected UUID getUUID(Player player) {
        return player.getUniqueId();
    }

    protected InetSocketAddress getAddress(Player player) {
        return player.getAddress();
    }

}

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

import com.github.games647.fastlogin.core.hooks.bedrock.BedrockService;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.hooks.bedrock.GeyserService;
import com.github.games647.fastlogin.core.message.ChangePremiumMessage;
import com.github.games647.fastlogin.core.message.ChannelMessage;
import com.github.games647.fastlogin.core.message.SuccessMessage;
import com.github.games647.fastlogin.core.scheduler.AsyncScheduler;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.PlatformPlugin;
import com.github.games647.fastlogin.velocity.listener.ConnectListener;
import com.github.games647.fastlogin.velocity.listener.PluginMessageListener;
import com.google.common.collect.MapMaker;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.GeyserImpl;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

//TODO: Support for floodgate
@Plugin(id = PomData.NAME, name = PomData.DISPLAY_NAME, description = PomData.DESCRIPTION, url = PomData.URL,
        version = PomData.VERSION, authors = {"games647", "https://github.com/games647/FastLogin/graphs/contributors"})
public class FastLoginVelocity implements PlatformPlugin<CommandSource> {

    private final ProxyServer server;
    private final Path dataDirectory;
    private final Logger logger;
    private final ConcurrentMap<InetSocketAddress, VelocityLoginSession> session = new MapMaker().weakKeys().makeMap();
    private static final String PROXY_ID_FILE = "proxyId.txt";

    private FastLoginCore<Player, CommandSource, FastLoginVelocity> core;
    private AsyncScheduler scheduler;
    private FloodgateService floodgateService;
    private GeyserService geyserService;
    private UUID proxyId;

    @Inject
    public FastLoginVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        scheduler = new AsyncScheduler(logger, task -> server.getScheduler().buildTask(this, task).schedule());
        core = new FastLoginCore<>(this);
        core.load();
        loadOrGenerateProxyId();
        if (!core.setupDatabase() || proxyId == null) {
            return;
        }

        if (isPluginInstalled("floodgate")) {
            floodgateService = new FloodgateService(FloodgateApi.getInstance(), core);
        }

        if (isPluginInstalled("Geyser-Velocity")) {
            geyserService = new GeyserService(GeyserImpl.getInstance(), core);
        }

        server.getEventManager().register(this, new ConnectListener(this, core.getAntiBotService()));
        server.getEventManager().register(this, new PluginMessageListener(this));

        ChannelRegistrar channelRegistry = server.getChannelRegistrar();
        channelRegistry.register(MinecraftChannelIdentifier.create(getName(), ChangePremiumMessage.CHANGE_CHANNEL));
        channelRegistry.register(MinecraftChannelIdentifier.create(getName(), SuccessMessage.SUCCESS_CHANNEL));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (core != null) {
            core.close();
        }
    }

    @Override
    public String getName() {
        return PomData.NAME;
    }

    @Override
    public Path getPluginFolder() {
        return dataDirectory;
    }

    @Override
    public Logger getLog() {
        return logger;
    }

    @Override
    public void sendMessage(CommandSource receiver, String message) {
        receiver.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    @Override
    public AsyncScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public boolean isPluginInstalled(String name) {
        return server.getPluginManager().isLoaded(name);
    }

    public FloodgateService getFloodgateService() {
        return floodgateService;
    }

    public GeyserService getGeyserService() {
        return geyserService;
    }

    @Override
    public BedrockService<?> getBedrockService() {
        if (floodgateService != null) {
            return floodgateService;
        }

        return geyserService;
    }

    public FastLoginCore<Player, CommandSource, FastLoginVelocity> getCore() {
        return core;
    }

    public ConcurrentMap<InetSocketAddress, VelocityLoginSession> getSession() {
        return session;
    }

    public ProxyServer getProxy() {
        return server;
    }

    public void sendPluginMessage(ChannelMessageSink server, ChannelMessage message) {
        if (server != null) {
            ByteArrayDataOutput dataOutput = ByteStreams.newDataOutput();
            message.writeTo(dataOutput);

            MinecraftChannelIdentifier channel = MinecraftChannelIdentifier.create(getName(), message.getChannelName());
            server.sendPluginMessage(channel, dataOutput.toByteArray());
        }
    }

    private void loadOrGenerateProxyId() {
        Path idFile = dataDirectory.resolve(PROXY_ID_FILE);
        boolean shouldGenerate = false;

        if (Files.exists(idFile)) {
            try {
                List<String> lines = Files.readAllLines(idFile, StandardCharsets.UTF_8);
                if (lines.isEmpty()) {
                    shouldGenerate = true;
                } else {
                    proxyId = UUID.fromString(lines.get(0));
                }
            } catch (IOException e) {
                logger.error("Unable to load proxy id from '{}'", idFile.toAbsolutePath());
                logger.error("Detailed exception:", e);
            } catch (IllegalArgumentException e) {
                Path filePath = idFile.toAbsolutePath();
                logger.error("'{}' contains an invalid uuid! FastLogin will not work without a valid id.", filePath);
            }
        } else {
            shouldGenerate = true;
        }

        if (shouldGenerate) {
            proxyId = UUID.randomUUID();
            try {
                Files.write(idFile, Collections.singletonList(proxyId.toString()), StandardOpenOption.CREATE);
            } catch (IOException e) {
                logger.error("Unable to save proxy id to '{}'", idFile.toAbsolutePath());
                logger.error("Detailed exception:", e);
            }
        }
    }

    public UUID getProxyId() {
        return proxyId;
    }

    public ProxyServer getServer() {
        return server;
    }
}

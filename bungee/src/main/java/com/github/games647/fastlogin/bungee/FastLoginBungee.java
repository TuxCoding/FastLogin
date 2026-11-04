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
package com.github.games647.fastlogin.bungee;

import com.github.games647.fastlogin.bungee.hook.BungeeAuthHook;
import com.github.games647.fastlogin.bungee.listener.ConnectListener;
import com.github.games647.fastlogin.bungee.listener.PluginMessageListener;
import com.github.games647.fastlogin.core.CommonUtil;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.hooks.bedrock.BedrockService;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.hooks.bedrock.GeyserService;
import com.github.games647.fastlogin.core.message.ChangePremiumMessage;
import com.github.games647.fastlogin.core.message.ChannelMessage;
import com.github.games647.fastlogin.core.message.NamespaceKey;
import com.github.games647.fastlogin.core.message.SuccessMessage;
import com.github.games647.fastlogin.core.scheduler.AsyncScheduler;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.PlatformPlugin;
import com.google.common.collect.MapMaker;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.api.scheduler.GroupedThreadFactory;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.api.GeyserApi;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;

/**
 * BungeeCord version of FastLogin. This plugin keeps track on online mode connections.
 */
public class FastLoginBungee extends Plugin implements PlatformPlugin<CommandSender> {

    private final ConcurrentMap<PendingConnection, BungeeLoginSession> session = new MapMaker().weakKeys().makeMap();

    private FastLoginCore<ProxiedPlayer, CommandSender, FastLoginBungee> core;
    private AsyncScheduler scheduler;
    private FloodgateService floodgateService;
    private GeyserService geyserService;
    private Logger logger;

    @Override
    public void onEnable() {
        logger = CommonUtil.initializeLoggerService(getLogger());
        scheduler = new AsyncScheduler(logger, task -> getProxy().getScheduler().runAsync(this, task));

        core = new FastLoginCore<>(this);
        core.load();
        if (!core.setupDatabase()) {
            return;
        }

        if (isPluginInstalled("floodgate")) {
            floodgateService = new FloodgateService(FloodgateApi.getInstance(), core);
        }

        if (isPluginInstalled("Geyser-BungeeCord")) {
            geyserService = new GeyserService(GeyserApi.api(), core);
        }

        //events
        PluginManager pluginManager = getProxy().getPluginManager();

        Listener connectListener = new ConnectListener(this, core.getAntiBotService());
        pluginManager.registerListener(this, connectListener);
        pluginManager.registerListener(this, new PluginMessageListener(this));

        //this is required to listen to incoming messages from the server
        getProxy().registerChannel(NamespaceKey.getCombined(getName(), ChangePremiumMessage.CHANGE_CHANNEL));
        getProxy().registerChannel(NamespaceKey.getCombined(getName(), SuccessMessage.SUCCESS_CHANNEL));

        registerHook();
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.close();
        }
    }

    public FastLoginCore<ProxiedPlayer, CommandSender, FastLoginBungee> getCore() {
        return core;
    }

    public ConcurrentMap<PendingConnection, BungeeLoginSession> getSession() {
        return session;
    }

    private void registerHook() {
        try {
            List<Class<? extends AuthPlugin<ProxiedPlayer>>> hooks = Collections.singletonList(
                    BungeeAuthHook.class
            );

            for (Class<? extends AuthPlugin<ProxiedPlayer>> clazz : hooks) {
                String pluginName = clazz.getSimpleName();
                pluginName = pluginName.substring(0, pluginName.length() - "Hook".length());
                //uses only member classes which uses AuthPlugin interface (skip interfaces)
                Plugin plugin = getProxy().getPluginManager().getPlugin(pluginName);
                if (plugin != null) {
                    logger.info("Hooking into auth plugin: {}", pluginName);
                    core.setAuthPluginHook(
                            clazz.getDeclaredConstructor(FastLoginBungee.class).newInstance(this));
                    break;
                }
            }
        } catch (ReflectiveOperationException ex) {
            logger.error("Couldn't load the auth hook class", ex);
        }
    }

    public void sendPluginMessage(Server server, ChannelMessage message) {
        if (server != null) {
            ByteArrayDataOutput dataOutput = ByteStreams.newDataOutput();
            message.writeTo(dataOutput);

            NamespaceKey channel = new NamespaceKey(getName(), message.getChannelName());
            server.sendData(channel.getCombinedName(), dataOutput.toByteArray());
        }
    }

    @Override
    public String getName() {
        return getDescription().getName();
    }

    @Override
    public Path getPluginFolder() {
        return getDataFolder().toPath();
    }

    @Override
    public Logger getLog() {
        return logger;
    }

    @Override
    public void sendMessage(CommandSender receiver, String message) {
        receiver.sendMessage(TextComponent.fromLegacyText(message));
    }

    @Override
    @SuppressWarnings("deprecation")
    public ThreadFactory getThreadFactory() {
        return new ThreadFactoryBuilder()
                .setNameFormat(getName() + " Pool Thread #%1$d")
                //Hikari create daemons by default
                .setDaemon(true)
                .setThreadFactory(new GroupedThreadFactory(this, getName()))
                .build();
    }

    @Override
    public AsyncScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public boolean isPluginInstalled(String name) {
        return getProxy().getPluginManager().getPlugin(name) != null;
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
}

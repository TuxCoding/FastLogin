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
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.bukkit.listener.BungeeListener;
import com.github.games647.fastlogin.core.message.ChannelMessage;
import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.message.NamespaceKey;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import io.papermc.paper.configuration.ServerConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageRecipient;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.github.games647.fastlogin.core.message.ChangePremiumMessage.CHANGE_CHANNEL;
import static com.github.games647.fastlogin.core.message.SuccessMessage.SUCCESS_CHANNEL;
import static java.util.stream.Collectors.toSet;

public class BungeeManager {

    private static final String LEGACY_FILE_NAME = "proxy-whitelist.txt";
    private static final String FILE_NAME = "allowed-proxies.txt";

    //null if proxies allowed list is empty so bungeecord support is disabled
    private Set<UUID> proxyIds;

    private final FastLoginBukkit plugin;
    private boolean enabled;

    private final Collection<UUID> firedJoinEvents = new HashSet<>();

    public BungeeManager(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    public void cleanup() {
        //remove old blocked status
        Bukkit.getOnlinePlayers().forEach(player -> player.removeMetadata(plugin.getName(), plugin));
    }

    public void sendPluginMessage(PluginMessageRecipient player, ChannelMessage message) {
        if (player != null) {
            ByteArrayDataOutput dataOutput = ByteStreams.newDataOutput();
            message.writeTo(dataOutput);

            NamespaceKey channel = new NamespaceKey(plugin.getName(), message.getChannelName());
            player.sendPluginMessage(plugin, channel.getCombinedName(), dataOutput.toByteArray());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void initialize() {
        enabled = detectProxy();

        if (enabled) {
            proxyIds = loadBungeeCordIds();
            if (proxyIds.isEmpty()) {
                plugin.getLog().info("No valid IDs found. Minecraft proxy support cannot work in the current state");
            }

            registerPluginChannels();
            plugin.getLog().info("Found enabled proxy configuration");
            plugin.getLog().info("Remember to follow the proxy guide to complete your setup");
        } else {
            plugin.getLog().warn("Disabling Minecraft proxy configuration. Assuming direct connections from now on.");
        }
    }

    private boolean isProxySupported(String className, String fieldName)
        throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        return Class.forName(className).getDeclaredField(fieldName).getBoolean(null);
    }

    private boolean isVelocityEnabled()
        throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException, ClassNotFoundException,
        NoSuchMethodException, InvocationTargetException {
        try {
            Class<?> globalConfig = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
            Object global = globalConfig.getDeclaredMethod("get").invoke(null);
            Object proxiesConfiguration = global.getClass().getDeclaredField("proxies").get(global);

            Field velocitySectionField = proxiesConfiguration.getClass().getDeclaredField("velocity");
            Object velocityConfig = velocitySectionField.get(proxiesConfiguration);

            return velocityConfig.getClass().getDeclaredField("enabled").getBoolean(velocityConfig);
        } catch (ClassNotFoundException classNotFoundException) {
            // try again using the older Paper configuration, because the old class file still exists in newer versions
            if (isProxySupported("com.destroystokyo.paper.PaperConfig", "velocitySupport")) {
                return true;
            }
        }

        return false;
    }

    private boolean detectProxy() {
        try {
            ServerConfiguration.class.getDeclaredMethod("isProxyEnabled");
            return Bukkit.getServerConfig().isProxyEnabled();
        } catch (NoClassDefFoundError | NoSuchMethodException noSuchClassMethodEx) {
            // Ignore continue below
        }

        try {
            if (isProxySupported("org.spigotmc.SpigotConfig", "bungee")) {
                return true;
            }
        } catch (ClassNotFoundException classNotFoundException) {
            // leave stacktrace for class not found out
            plugin.getLog().warn("Cannot check for BungeeCord support: {}", classNotFoundException.getMessage());
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            plugin.getLog().warn("Cannot check for BungeeCord support", ex);
        }

        try {
            return isVelocityEnabled();
        } catch (ClassNotFoundException classNotFoundException) {
            plugin.getLog().warn("Cannot check for Velocity support in Paper: {}", classNotFoundException.getMessage());
        } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException ex) {
            plugin.getLog().warn("Cannot check for Velocity support in Paper", ex);
        }

        return false;
    }

    private void registerPluginChannels() {
        Server server = Bukkit.getServer();

        // check for incoming messages from the bungeecord version of this plugin
        String groupId = plugin.getName();
        String forceChannel = NamespaceKey.getCombined(groupId, LoginActionMessage.FORCE_CHANNEL);
        server.getMessenger().registerIncomingPluginChannel(plugin, forceChannel, new BungeeListener(plugin));

        // outgoing
        String successChannel = new NamespaceKey(groupId, SUCCESS_CHANNEL).getCombinedName();
        String changeChannel = new NamespaceKey(groupId, CHANGE_CHANNEL).getCombinedName();
        server.getMessenger().registerOutgoingPluginChannel(plugin, successChannel);
        server.getMessenger().registerOutgoingPluginChannel(plugin, changeChannel);
    }

    private Set<UUID> loadBungeeCordIds() {
        Path proxiesFile = plugin.getPluginFolder().resolve(FILE_NAME);
        Path legacyFile = plugin.getPluginFolder().resolve(LEGACY_FILE_NAME);
        try {
            if (Files.notExists(proxiesFile)) {
                if (Files.exists(legacyFile)) {
                    Files.move(legacyFile, proxiesFile);
                }

                if (Files.notExists(legacyFile)) {
                    Files.createFile(proxiesFile);
                }
            }

            Files.deleteIfExists(legacyFile);
            try (Stream<String> lines = Files.lines(proxiesFile)) {
                return lines.map(String::trim).map(UUID::fromString).collect(toSet());
            }
        } catch (IOException ex) {
            plugin.getLog().error("Failed to read proxies", ex);
        } catch (Exception ex) {
            plugin.getLog().error("Failed to retrieve proxy Id. Disabling BungeeCord support", ex);
        }

        return Collections.emptySet();
    }

    public boolean isProxyAllowed(UUID proxyId) {
        return proxyIds != null && proxyIds.contains(proxyId);
    }

    /**
     * Mark the event to be fired including the task delay.
     *
     * @param player joining player
     */
    public synchronized void markJoinEventFired(Player player) {
        firedJoinEvents.add(player.getUniqueId());
    }

    /**
     * Check if the event fired including with the task delay. This necessary to restore the order of processing the
     * BungeeCord messages after the PlayerJoinEvent fires including the delay.
     * <p>
     * If the join event fired, the delay exceeded, but it ran earlier and couldn't find the recently started login
     * session. If not fired, we can start a new force login task. This will still match the requirement that we wait
     * a certain time after the player join event fired.
     *
     * @param player joining player
     * @return event fired including delay
     */
    public synchronized boolean didJoinEventFired(Player player) {
        return firedJoinEvents.contains(player.getUniqueId());
    }

    /**
     * Player quit clean up
     *
     * @param player joining player
     */
    public synchronized void cleanup(Player player) {
        firedJoinEvents.remove(player.getUniqueId());
    }
}

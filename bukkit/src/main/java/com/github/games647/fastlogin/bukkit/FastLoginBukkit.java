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

import com.github.games647.fastlogin.bukkit.auth.AuthenticationBackend;
import com.github.games647.fastlogin.bukkit.auth.ConnectionListener;
import com.github.games647.fastlogin.bukkit.auth.protocollib.ProtocolAuthentication;
import com.github.games647.fastlogin.bukkit.auth.proxy.ProxyAuthentication;
import com.github.games647.fastlogin.bukkit.command.CrackedCommand;
import com.github.games647.fastlogin.bukkit.command.PremiumCommand;
import com.github.games647.fastlogin.bukkit.hook.DelayedAuthHook;
import com.github.games647.fastlogin.core.CommonUtil;
import com.github.games647.fastlogin.core.PremiumStatus;
import com.github.games647.fastlogin.core.hooks.bedrock.BedrockService;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.hooks.bedrock.GeyserService;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.PlatformPlugin;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.GeyserImpl;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * This plugin checks if a player has a paid account and if so tries to skip offline mode authentication.
 */
public class FastLoginBukkit extends JavaPlugin implements PlatformPlugin<CommandSender> {

    //1 minutes should be enough as a timeout for bad internet connection (Server, Client and Mojang)
    private final ConcurrentMap<String, BukkitLoginSession> loginSession = CommonUtil.buildCache(
            Duration.ofMinutes(1), -1
    );

    @Getter
    private final Map<UUID, PremiumStatus> premiumPlayers = new ConcurrentHashMap<>();
    private final Logger logger;

    private final BukkitScheduler scheduler;

    @Getter
    private final Collection<UUID> pendingConfirms = new HashSet<>();

    @Getter
    private FastLoginCore<Player, CommandSender, FastLoginBukkit> core;

    @Getter
    private FloodgateService floodgateService;
    private GeyserService geyserService;

    private PremiumPlaceholder premiumPlaceholder;

    @Getter
    private AuthenticationBackend backend;

    @Getter
    private boolean initialized;

    public FastLoginBukkit() {
        this.logger = CommonUtil.initializeLoggerService(getLogger());
        this.scheduler = new BukkitScheduler(this, logger);
    }

    @Override
    public void onEnable() {
        core = new FastLoginCore<>(this);
        core.load();

        if (getServer().getOnlineMode()) {
            //we need to require offline to prevent a loginSession request for an offline player
            logger.error("Server has to be in offline mode");
            setEnabled(false);
            return;
        }

        if (!initializeFloodgate()) {
            setEnabled(false);
        }

        backend = initializeAuthenticationBackend();
        if (backend == null) {
            logger.warn("Either ProtocolLib or ProtocolSupport have to be installed if you don't use BungeeCord");
            setEnabled(false);
            return;
        }

        backend.init(getServer().getPluginManager());
        PluginManager pluginManager = getServer().getPluginManager();

        pluginManager.registerEvents(new ConnectionListener(this), this);

        registerCommands();

        if (pluginManager.isPluginEnabled("PlaceholderAPI")) {
            premiumPlaceholder = new PremiumPlaceholder(this);
            premiumPlaceholder.register();
        }

        // delay dependency setup because we load the plugin very early where plugins are initialized yet
        getServer().getScheduler().runTaskLater(this, new DelayedAuthHook(this), 5L);
    }

    private AuthenticationBackend initializeAuthenticationBackend() {
        AuthenticationBackend proxyVerifier = new ProxyAuthentication(this);
        if (proxyVerifier.isAvailable()) {
            return proxyVerifier;
        }

        logger.warn("Disabling Minecraft proxy configuration. Assuming direct connections from now on.");
        AuthenticationBackend protocolAuthentication = new ProtocolAuthentication(this);
        if (protocolAuthentication.isAvailable()) {
            return protocolAuthentication;
        }

        return null;
    }

    private void registerCommands() {
        //register commands using a unique name
        Optional.ofNullable(getCommand("premium")).ifPresent(c -> c.setExecutor(new PremiumCommand(this)));
        Optional.ofNullable(getCommand("cracked")).ifPresent(c -> c.setExecutor(new CrackedCommand(this)));
    }

    private boolean initializeFloodgate() {
        if (getServer().getPluginManager().getPlugin("Geyser-Spigot") != null) {
            geyserService = new GeyserService(GeyserImpl.getInstance(), core);
        }

        if (getServer().getPluginManager().getPlugin("floodgate") != null) {
            floodgateService = new FloodgateService(FloodgateApi.getInstance(), core);

            // Check Floodgate config values and return
            return floodgateService.isValidFloodgateConfigString("autoLoginFloodgate")
                    && floodgateService.isValidFloodgateConfigString("allowFloodgateNameConflict");
        }

        return true;
    }

    @Override
    public void onDisable() {
        loginSession.clear();
        premiumPlayers.clear();

        if (core != null) {
            core.close();
        }

        if (backend != null) {
            backend.stop();
        }

        if (premiumPlaceholder != null && getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            premiumPlaceholder.unregister();
        }
    }

    /**
     * Gets a thread-safe map about players which are connecting to the server are being checked to be premium (paid
     * account)
     *
     * @return a thread-safe loginSession map
     */
    public ConcurrentMap<String, BukkitLoginSession> getLoginSessions() {
        return loginSession;
    }

    public BukkitLoginSession getSession(InetSocketAddress address) {
        String id = getSessionId(address);
        return loginSession.get(id);
    }

    public String getSessionId(InetSocketAddress address) {
        return address.getAddress().getHostAddress() + ':' + address.getPort();
    }

    public void putSession(InetSocketAddress address, BukkitLoginSession session) {
        String id = getSessionId(address);
        loginSession.put(id, session);
    }

    public void removeSession(InetSocketAddress address) {
        String id = getSessionId(address);
        loginSession.remove(id);
    }

    /**
     * Fetches the premium status of an online player.
     * {@snippet :
     * // Bukkit's players object after successful authentication i.e. PlayerJoinEvent
     * // except for proxies like BungeeCord and Velocity where the details are sent delayed (1-2 seconds)
     * Player player;
     * PremiumStatus status = JavaPlugin.getPlugin(FastLoginBukkit.class).getStatus(player.getUniqueId());
     * switch (status) {
     *     case CRACKED:
     *         // player is offline
     *         break;
     *     case PREMIUM:
     *         // account is premium and player passed the verification
     *         break;
     *     case UNKNOWN:
     *         // no record about this player
     * }
     * }
     *
     * @param onlinePlayer player that is currently online player (play state)
     * @return the online status or unknown if an error happened, the player isn't online or BungeeCord doesn't send
     * us the status message yet (This means you cannot check the login status on the PlayerJoinEvent).
     */
    public @NotNull PremiumStatus getStatus(@NotNull UUID onlinePlayer) {
        return premiumPlayers.getOrDefault(onlinePlayer, PremiumStatus.UNKNOWN);
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
    public BukkitScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public void sendMessage(CommandSender receiver, String message) {
        receiver.sendMessage(message);
    }

    /**
     * Checks if a plugin is installed on the server
     *
     * @param name the name of the plugin
     * @return true if the plugin is installed
     */
    @Override
    public boolean isPluginInstalled(String name) {
        // the plugin may be enabled after FastLogin, so isPluginEnabled() won't work here
        return Bukkit.getServer().getPluginManager().getPlugin(name) != null;
    }

    public void setInitialized(boolean hookFound) {
        if (backend instanceof ProxyAuthentication) {
            logger.info("BungeeCord setting detected. No auth plugin is required");
        } else if (!hookFound) {
            logger.warn("No auth plugin were found by this plugin "
                    + "(other plugins could hook into this after the initialization of this plugin)"
                    + "and BungeeCord is deactivated. "
                    + "Either one or both of the checks have to pass in order to use this plugin");
        }

        initialized = true;
    }

    public ProxyAuthentication getBungeeManager() {
        return (ProxyAuthentication) backend;
    }

    @Override
    public BedrockService<?> getBedrockService() {
        if (floodgateService != null) {
            return floodgateService;
        }

        return geyserService;
    }
}

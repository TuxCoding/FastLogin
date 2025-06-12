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
package com.github.games647.fastlogin.core.shared;

import com.github.games647.craftapi.resolver.MojangResolver;
import com.github.games647.craftapi.resolver.Options;
import com.github.games647.craftapi.resolver.http.RotatingProxySelector;
import com.github.games647.fastlogin.core.CommonUtil;
import com.github.games647.fastlogin.core.ProxyAgnosticMojangResolver;
import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;
import com.github.games647.fastlogin.core.antibot.RateLimiter;
import com.github.games647.fastlogin.core.antibot.TickingRateLimiter;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.hooks.DefaultPasswordGenerator;
import com.github.games647.fastlogin.core.hooks.PasswordGenerator;
import com.github.games647.fastlogin.core.storage.MySQLStorage;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.SQLiteStorage;
import com.google.common.base.Ticker;
import com.zaxxer.hikari.HikariConfig;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * @param <P> GameProfile class
 * @param <C> CommandSender
 * @param <T> Plugin class
 */
public class FastLoginCore<P extends C, C, T extends PlatformPlugin<C>> {

    private static final long MAX_EXPIRE_RATE = 1_000_000;

    private final Map<String, String> localeMessages = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> pendingLogin = CommonUtil.buildCache(
            Duration.ofMinutes(5), -1
    );

    private final Collection<UUID> pendingConfirms = new HashSet<>();
    private final T plugin;

    private MojangResolver resolver;

    private Configuration config;
    private SQLStorage storage;
    private AntiBotService antiBot;
    private PasswordGenerator<P> passwordGenerator = new DefaultPasswordGenerator<>();
    private AuthPlugin<P> authPlugin;

    public FastLoginCore(T plugin) {
        this.plugin = plugin;
    }

    public void load() {
        saveDefaultFile("messages.yml");
        saveDefaultFile("config.yml");

        try {
            config = loadFile("config.yml");
            Configuration messages = loadFile("messages.yml");

            messages.getKeys()
                    .stream()
                    .filter(key -> messages.get(key) != null)
                    .collect(toMap(identity(), messages::get))
                    .forEach((key, message) -> {
                        String colored = CommonUtil.translateColorCodes((String) message);
                        if (!colored.isEmpty()) {
                            localeMessages.put(key, colored.replace("/newline", "\n"));
                        }
                    });
        } catch (IOException ioEx) {
            plugin.getLog().error("Failed to load yaml files", ioEx);
            return;
        }

        Options resolverOptions = new Options();
        resolverOptions.setMaxNameRequests(config.getInt("mojang-request-limit", 600));

        Set<Proxy> proxies = config.getStringList("proxies")
                .stream()
                .map(proxy -> proxy.split(":"))
                .map(proxy -> new InetSocketAddress(proxy[0], Integer.parseInt(proxy[1])))
                .map(sa -> new Proxy(Type.HTTP, sa))
                .collect(toSet());
        resolverOptions.setProxySelector(new RotatingProxySelector(proxies));

//        TODO: Fix this?
//        Collection<InetAddress> addresses = new HashSet<>();
//        for (String localAddress : config.getStringList("ip-addresses")) {
//            try {
//                addresses.add(InetAddress.getByName(localAddress.replace('-', '.')));
//            } catch (UnknownHostException ex) {
//                plugin.getLog().error("IP-Address is unknown to us", ex);
//            }
//        }
//        resolver.setOutgoingAddresses(addresses);

        // Initialize the resolver based on the config parameter
        this.resolver = this.config.getBoolean("useProxyAgnosticResolver", false)
            ? new ProxyAgnosticMojangResolver(resolverOptions) : new MojangResolver(resolverOptions);

        antiBot = createAntiBotService(config.getSection("anti-bot"));

    }

    private AntiBotService createAntiBotService(Configuration botSection) {
        RateLimiter rateLimiter;
        if (botSection.getBoolean("enabled", true)) {
            int maxCon = botSection.getInt("connections", 200);
            long expireTime = botSection.getLong("expire", 5) * 60 * 1_000L;
            if (expireTime > MAX_EXPIRE_RATE) {
                expireTime = MAX_EXPIRE_RATE;
            }

            rateLimiter = new TickingRateLimiter(Ticker.systemTicker(), maxCon, expireTime);
        } else {
            // no-op rate limiter
            rateLimiter = () -> true;
        }

        Action action = Action.Ignore;
        switch (botSection.getString("action", "ignore")) {
            case "ignore":
                action = Action.Ignore;
                break;
            case "block":
                action = Action.Block;
                break;
            default:
                plugin.getLog().warn("Invalid anti bot action - defaulting to ignore");
        }

        return new AntiBotService(plugin.getLog(), rateLimiter, action);
    }

    private Configuration loadFile(String fileName) throws IOException {
        ConfigurationProvider configProvider = ConfigurationProvider.getProvider(YamlConfiguration.class);

        Configuration defaults;
        try (InputStream defaultStream = getClass().getClassLoader().getResourceAsStream(fileName)) {
            defaults = configProvider.load(defaultStream);
        }

        Path file = plugin.getPluginFolder().resolve(fileName);

        Configuration config;
        try (Reader reader = Files.newBufferedReader(file)) {
            config = configProvider.load(reader, defaults);
        }

        // explicitly add keys here, because Configuration.getKeys doesn't return the keys from the default config
        for (String key : defaults.getKeys()) {
            config.set(key, config.get(key));
        }

        return config;
    }

    public MojangResolver getResolver() {
        return resolver;
    }

    public SQLStorage getStorage() {
        return storage;
    }

    public T getPlugin() {
        return plugin;
    }

    public void sendLocaleMessage(String key, C receiver) {
        String message = localeMessages.get(key);
        if (message != null) {
            plugin.sendMultiLineMessage(receiver, message);
        }
    }

    public String getMessage(String key) {
        return localeMessages.get(key);
    }

    public boolean setupDatabase() {
        String type = config.getString("driver");

        HikariConfig databaseConfig = new HikariConfig();
        String database = config.getString("database");

        databaseConfig.setConnectionTimeout(config.getInt("timeout", 30) * 1_000L);
        databaseConfig.setMaxLifetime(config.getInt("lifetime", 30) * 1_000L);

        if (type.contains("sqlite")) {
            storage = new SQLiteStorage(plugin, database, databaseConfig);
        } else {
            String host = config.get("host", "");
            int port = config.get("port", 3306);
            boolean useSSL = config.get("useSSL", false);

            if (useSSL) {
                boolean publicKeyRetrieval = config.getBoolean("allowPublicKeyRetrieval", false);
                String rsaPublicKeyFile = config.getString("ServerRSAPublicKeyFile");
                String sslMode = config.getString("sslMode", "Required");

                databaseConfig.addDataSourceProperty("allowPublicKeyRetrieval", publicKeyRetrieval);
                databaseConfig.addDataSourceProperty("serverRSAPublicKeyFile", rsaPublicKeyFile);
                databaseConfig.addDataSourceProperty("sslMode", sslMode);
            }

            databaseConfig.setUsername(config.get("username", ""));
            databaseConfig.setPassword(config.getString("password"));
            storage = new MySQLStorage(plugin, type, host, port, database, databaseConfig, useSSL);
        }

        try {
            storage.createTables();
            return true;
        } catch (Exception ex) {
            plugin.getLog().warn("Failed to setup database. Disabling plugin...", ex);
            return false;
        }
    }

    public Configuration getConfig() {
        return config;
    }

    public PasswordGenerator<P> getPasswordGenerator() {
        return passwordGenerator;
    }

    @SuppressWarnings("unused")
    public void setPasswordGenerator(PasswordGenerator<P> passwordGenerator) {
        this.passwordGenerator = passwordGenerator;
    }

    public void addLoginAttempt(String ip, String username) {
        pendingLogin.put(ip + username, new Object());
    }

    public boolean hasFailedLogin(String ip, String username) {
        if (!config.get("secondAttemptCracked", false)) {
            return false;
        }

        return pendingLogin.remove(ip + username) != null;
    }

    public Collection<UUID> getPendingConfirms() {
        return pendingConfirms;
    }

    public AuthPlugin<P> getAuthPluginHook() {
        return authPlugin;
    }

    public AntiBotService getAntiBotService() {
        return antiBot;
    }

    public void setAuthPluginHook(AuthPlugin<P> authPlugin) {
        this.authPlugin = authPlugin;
    }

    public void saveDefaultFile(String fileName) {
        Path dataFolder = plugin.getPluginFolder();

        try {
            Files.createDirectories(dataFolder);

            Path configFile = dataFolder.resolve(fileName);
            if (Files.notExists(configFile)) {
                try (InputStream defaultStream = getClass().getClassLoader().getResourceAsStream(fileName)) {
                    Files.copy(Objects.requireNonNull(defaultStream), configFile);
                }
            }
        } catch (IOException ioExc) {
            plugin.getLog().error("Cannot create plugin folder {}", dataFolder, ioExc);
        }
    }

    public void close() {
        plugin.getLog().info("Safely shutting down scheduler. This could take up to one minute.");

        if (storage != null) {
            storage.close();
        }
    }
}

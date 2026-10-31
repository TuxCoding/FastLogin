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
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import fr.xephi.authme.api.v3.AuthMeApi;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class AuthMeHookTest {

    @Test
    void olderAuthMeKeepsStandardLogin() {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class, RETURNS_DEEP_STUBS);
        AuthMeApi api = mock(AuthMeApi.class);
        Player player = mock(Player.class);
        try (MockedStatic<AuthMeApi> singleton = mockStatic(AuthMeApi.class)) {
            singleton.when(AuthMeApi::getInstance).thenReturn(api);
            AuthMeHook hook = new AuthMeHook(plugin);
            assertTrue(hook.forceLogin(player));
            verify(api).forceLogin(player);
            when(api.isAuthenticated(player)).thenReturn(true);
            assertFalse(hook.forceLogin(player));
        }
    }

    @Test
    void modernAuthMeCanPrepareLoginBeforePlayerExists() throws ReflectiveOperationException {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class, RETURNS_DEEP_STUBS);
        when(plugin.getName()).thenReturn("FastLogin");
        AuthMeApi api = mock(AuthMeApi.class, withSettings().extraInterfaces(ProxyLoginApi.class));
        try (MockedStatic<AuthMeApi> singleton = mockStatic(AuthMeApi.class)) {
            singleton.when(AuthMeApi::getInstance).thenReturn(api);
            AuthMeHook hook = new AuthMeHook(plugin);
            hook.forceLoginBeforeJoin("PremiumPlayer");
            verify((ProxyLoginApi) api).forceLoginFromProxy("PremiumPlayer");

            Player player = authenticatedPlayer(plugin, api, false);
            assertFalse(hook.forceLogin(player));
            plugin.getSession(player.spigot().getRawAddress()).setPreJoinLoginQueued(true);
            assertTrue(hook.forceLogin(player));

            when(api.isAuthenticated(player)).thenReturn(false);
            when(plugin.getBungeeManager().isEnabled()).thenReturn(true);
            assertTrue(hook.forceLogin(player));
            verify((ProxyLoginApi) api, times(2)).forceLoginFromProxy("PremiumPlayer");
        }
    }

    @Test
    void authMeWithoutNewApiQueuesVerifiedLoginBeforeDialog() throws ReflectiveOperationException {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class, RETURNS_DEEP_STUBS);
        when(plugin.getName()).thenReturn("FastLogin");
        Plugin authme = mock(Plugin.class);
        when(plugin.getServer().getPluginManager().getPlugin("AuthMe")).thenReturn(authme);
        AuthMeApi api = mock(AuthMeApi.class);
        LegacyDialogListener dialogListener = new LegacyDialogListener();
        RegisteredListener registration = new RegisteredListener(dialogListener, (listener, event) -> { },
                EventPriority.HIGHEST, authme, false);
        HandlerList handlers = AsyncPlayerConnectionConfigureEvent.getHandlerList();
        handlers.register(registration);
        try (MockedStatic<AuthMeApi> singleton = mockStatic(AuthMeApi.class)) {
            singleton.when(AuthMeApi::getInstance).thenReturn(api);
            AuthMeHook hook = new AuthMeHook(plugin);
            verify(plugin.getServer().getPluginManager()).registerEvents(isA(AuthMePreJoinListener.class), same(plugin));

            hook.forceLoginBeforeJoin("PremiumPlayer");
            assertTrue(dialogListener.proxySessionManager.sessions.contains("premiumplayer"));
            assertFalse(dialogListener.proxySessionManager.sessions.contains("crackedplayer"));

            Player player = authenticatedPlayer(plugin, api, true);
            assertTrue(hook.forceLogin(player));
        } finally {
            handlers.unregister(registration);
        }
    }

    @Test
    void anotherPluginsSessionQueueCannotEnableAuthMeBypass() {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class, RETURNS_DEEP_STUBS);
        Plugin authme = mock(Plugin.class);
        when(plugin.getServer().getPluginManager().getPlugin("AuthMe")).thenReturn(authme);
        RegisteredListener registration = new RegisteredListener(new LegacyDialogListener(), (listener, event) -> { },
                EventPriority.HIGHEST, mock(Plugin.class), false);
        HandlerList handlers = AsyncPlayerConnectionConfigureEvent.getHandlerList();
        handlers.register(registration);
        try (MockedStatic<AuthMeApi> singleton = mockStatic(AuthMeApi.class)) {
            singleton.when(AuthMeApi::getInstance).thenReturn(mock(AuthMeApi.class));
            new AuthMeHook(plugin);
            verify(plugin.getServer().getPluginManager(), never())
                    .registerEvents(isA(AuthMePreJoinListener.class), same(plugin));
        } finally {
            handlers.unregister(registration);
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "authme.compatibilityJar", matches = ".+")
    void authMe2734SkipsDialogUsingItsRealSessionQueue() throws Exception {
        URL jar = Paths.get(System.getProperty("authme.compatibilityJar")).toUri().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar}, getClass().getClassLoader()) {
            @Override
            protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("fr.xephi.authme.")) {
                    return super.loadClass(name, resolve);
                }
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    type = findClass(name);
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }) {
            Class<?> listenerType = loader.loadClass("fr.xephi.authme.listener.PaperDialogFlowListener");
            Listener dialogListener = (Listener) listenerType.getConstructor().newInstance();
            Object sessions = loader.loadClass("fr.xephi.authme.data.ProxySessionManager").getConstructor().newInstance();
            Field sessionField = listenerType.getDeclaredField("proxySessionManager");
            sessionField.setAccessible(true);
            sessionField.set(dialogListener, sessions);
            Constructor<?> cacheConstructor = loader.loadClass("fr.xephi.authme.data.auth.PlayerCache")
                    .getDeclaredConstructor();
            cacheConstructor.setAccessible(true);
            Field cacheField = listenerType.getDeclaredField("playerCache");
            cacheField.setAccessible(true);
            cacheField.set(dialogListener, cacheConstructor.newInstance());

            FastLoginBukkit plugin = mock(FastLoginBukkit.class, RETURNS_DEEP_STUBS);
            when(plugin.getName()).thenReturn("FastLogin");
            Plugin authme = mock(Plugin.class);
            when(plugin.getServer().getPluginManager().getPlugin("AuthMe")).thenReturn(authme);
            RegisteredListener registration = new RegisteredListener(dialogListener, (listener, event) -> { },
                    EventPriority.HIGHEST, authme, false);
            HandlerList handlers = AsyncPlayerConnectionConfigureEvent.getHandlerList();
            handlers.register(registration);
            try (MockedStatic<AuthMeApi> singleton = mockStatic(AuthMeApi.class)) {
                singleton.when(AuthMeApi::getInstance).thenReturn(mock(AuthMeApi.class));
                AuthMeHook hook = new AuthMeHook(plugin);
                hook.forceLoginBeforeJoin("PremiumPlayer");
                Method shouldResume = sessions.getClass().getMethod("shouldResumeSession", String.class);
                assertTrue((boolean) shouldResume.invoke(sessions, "premiumplayer"));
                assertFalse((boolean) shouldResume.invoke(sessions, "crackedplayer"));

                Method skipDialog = listenerType.getDeclaredMethod("shouldSkipDialogs", String.class,
                        PlayerConfigurationConnection.class);
                skipDialog.setAccessible(true);
                assertTrue((boolean) skipDialog.invoke(dialogListener, "premiumplayer",
                        mock(PlayerConfigurationConnection.class)));
            } finally {
                handlers.unregister(registration);
            }
        }
    }

    private Player authenticatedPlayer(FastLoginBukkit plugin, AuthMeApi api, boolean queued) {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.getName()).thenReturn("PremiumPlayer");
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
        when(player.spigot().getRawAddress()).thenReturn(address);
        BukkitLoginSession session = new BukkitLoginSession("PremiumPlayer", true);
        session.setVerifiedPremium(true);
        session.setPreJoinLoginQueued(queued);
        when(plugin.getSession(address)).thenReturn(session);
        when(api.isAuthenticated(player)).thenReturn(true);
        return player;
    }

    public static class LegacyDialogListener implements Listener {

        private final LegacyProxySessions proxySessionManager = new LegacyProxySessions();
    }

    public static class LegacyProxySessions {

        private final Set<String> sessions = new HashSet<>();

        public void processProxySessionMessage(String name) {
            sessions.add(name.toLowerCase(Locale.ROOT));
        }
    }

    public interface ProxyLoginApi {

        void forceLoginFromProxy(String name);
    }
}

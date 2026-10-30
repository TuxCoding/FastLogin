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
import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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

            Player player = mock(Player.class);
            when(api.isAuthenticated(player)).thenReturn(true);
            assertTrue(hook.forceLogin(player));
        }
    }

    public interface ProxyLoginApi {

        void forceLoginFromProxy(String name);
    }
}

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

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.hook.AuthMeHook;
import com.github.games647.fastlogin.bukkit.hook.CrazyLoginHook;
import com.github.games647.fastlogin.bukkit.hook.LogItHook;
import com.github.games647.fastlogin.bukkit.hook.LoginSecurityHook;
import com.github.games647.fastlogin.bukkit.hook.UltraAuthHook;
import com.github.games647.fastlogin.bukkit.hook.XAuthHook;
import com.github.games647.fastlogin.bukkit.hook.PasskyHook;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

public class DelayedAuthHook implements Runnable {

    private final FastLoginBukkit plugin;

    public DelayedAuthHook(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        boolean hookFound = isHookFound();
        if (plugin.getBungeeManager().isEnabled()) {
            plugin.getLog().info("BungeeCord setting detected. No auth plugin is required");
        } else if (!hookFound) {
            plugin.getLog().warn("No auth plugin were found by this plugin "
                    + "(other plugins could hook into this after the initialization of this plugin)"
                    + "and BungeeCord is deactivated. "
                    + "Either one or both of the checks have to pass in order to use this plugin");
        }

        if (hookFound) {
            plugin.markInitialized();
        }
    }

    private boolean isHookFound() {
        return plugin.getCore().getAuthPluginHook() != null || registerHooks();
    }

    private boolean registerHooks() {
        AuthPlugin<Player> authPluginHook = getAuthHook();
        if (authPluginHook == null) {
            //run this check for exceptions (errors) and not found plugins
            plugin.getLog().warn("No support offline Auth plugin found. ");
            return false;
        }

        if (authPluginHook instanceof Listener) {
            Bukkit.getPluginManager().registerEvents((Listener) authPluginHook, plugin);
        }

        if (plugin.getCore().getAuthPluginHook() == null) {
            plugin.getLog().info("Hooking into auth plugin: {}", authPluginHook.getClass().getSimpleName());
            plugin.getCore().setAuthPluginHook(authPluginHook);
        }

        return true;
    }

    private AuthPlugin<Player> getAuthHook() {
        try {
            List<Class<? extends AuthPlugin<Player>>> hooks = Arrays.asList(AuthMeHook.class,
                    CrazyLoginHook.class, LogItHook.class, LoginSecurityHook.class, UltraAuthHook.class,
                    XAuthHook.class, PasskyHook.class);

            for (Class<? extends AuthPlugin<Player>> clazz : hooks) {
                String pluginName = clazz.getSimpleName();
                pluginName = pluginName.substring(0, pluginName.length() - "Hook".length());
                //uses only member classes which uses AuthPlugin interface (skip interfaces)
                if (Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
                    //check only for enabled plugins. A single plugin could be disabled by plugin managers
                    return newInstance(clazz);
                }
            }
        } catch (ReflectiveOperationException ex) {
            plugin.getLog().error("Couldn't load the auth hook class", ex);
        }

        return null;
    }

    protected AuthPlugin<Player> newInstance(Class<? extends AuthPlugin<Player>> clazz)
            throws ReflectiveOperationException {
        try {
            Constructor<? extends AuthPlugin<Player>> cons = clazz.getDeclaredConstructor(FastLoginBukkit.class);
            return cons.newInstance(plugin);
        } catch (NoSuchMethodException noMethodEx) {
            return clazz.getDeclaredConstructor().newInstance();
        }
    }
}

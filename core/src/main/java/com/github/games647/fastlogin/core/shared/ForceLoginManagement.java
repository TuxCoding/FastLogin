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

import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.shared.event.FastLoginAutoLoginEvent;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;

public abstract class ForceLoginManagement<P extends C, C, L extends LoginSession, T extends PlatformPlugin<C>>
        implements Runnable {

    protected final FastLoginCore<P, C, T> core;
    protected final P player;
    protected final L session;

    public ForceLoginManagement(FastLoginCore<P, C, T> core, P player, L session) {
        this.core = core;
        this.player = player;
        this.session = session;
    }

    @Override
    public void run() {
        if (!isOnline(player)) {
            core.getPlugin().getLog().info("Player {} disconnected", player);
            return;
        }

        if (session == null) {
            core.getPlugin().getLog().info("No valid session found for {}", player);
            return;
        }

        SQLStorage storage = core.getStorage();
        StoredProfile playerProfile = session.getProfile();
        try {
            if (isOnlineMode()) {
                //premium player
                AuthPlugin<P> authPlugin = core.getAuthPluginHook();
                if (authPlugin == null) {
                    // maybe only bungeecord plugin
                    onForceActionSuccess(session);
                } else {
                    boolean success = true;
                    String playerName = getName(player);
                    if (core.getConfig().get("autoLogin", true)) {
                        if (session.needsRegistration()
                                || (core.getConfig().get("auto-register-unknown", false)
                                && !authPlugin.isRegistered(playerName))) {
                            success = forceRegister(player);
                        } else if (!callFastLoginAutoLoginEvent(session, playerProfile).isCancelled()) {
                            success = forceLogin(player);
                        }
                    }

                    if (success) {
                        //update only on success to prevent corrupt data
                        if (playerProfile != null) {
                            if (session.getUuid() == null) {
                                // Set session ID from floodgatePlayer.getCorrectUniqueId()
                                session.setUuid(playerProfile.getId());
                            } else {
                                // Set player UUID to session UUID
                                playerProfile.setId(session.getUuid());
                            }
                            playerProfile.setOnlinemodePreferred(true);
                            storage.save(playerProfile);
                        }

                        onForceActionSuccess(session);
                    }
                }
            } else if (playerProfile != null) {
                //cracked player
                playerProfile.setId(null);
                playerProfile.setOnlinemodePreferred(false);
                storage.save(playerProfile);
            }
        } catch (Exception ex) {
            core.getPlugin().getLog().warn("ERROR ON FORCE LOGIN of {}", getName(player), ex);
        }
    }

    public boolean forceRegister(P player) {
        core.getPlugin().getLog().info("Register player {}", getName(player));

        String generatedPassword = core.getPasswordGenerator().getRandomPassword(player);
        boolean success = core.getAuthPluginHook().forceRegister(player, generatedPassword);

        String message = core.getMessage("auto-register");
        if (success && message != null) {
            message = message.replace("%password", generatedPassword);
            core.getPlugin().sendMessage(player, message);
        }

        return success;
    }

    public boolean forceLogin(P player) {
        core.getPlugin().getLog().info("Logging player {} in", getName(player));

        boolean success = core.getAuthPluginHook().forceLogin(player);
        if (success) {
            core.sendLocaleMessage("auto-login", player);
        }

        return success;
    }

    public abstract FastLoginAutoLoginEvent callFastLoginAutoLoginEvent(LoginSession session, StoredProfile profile);

    public abstract void onForceActionSuccess(LoginSession session);

    public abstract String getName(P player);

    public abstract boolean isOnline(P player);

    public abstract boolean isOnlineMode();
}

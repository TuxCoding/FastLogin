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

import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.resolver.RateLimitException;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public abstract class FloodgateManagement<P extends C, C, L extends LoginSession, T extends PlatformPlugin<C>>
        implements Runnable {

    protected final FastLoginCore<P, C, T> core;
    protected final P player;
    private final FloodgatePlayer floodgatePlayer;
    private final String username;

    //config.yml values that might be accessed by multiple methods
    protected final String autoLoginFloodgate;
    protected final String autoRegisterFloodgate;
    protected final String allowNameConflict;

    //variables initialized through run() and accesses by subclass
    protected boolean isRegistered;
    protected StoredProfile profile;
    protected boolean isLinked;

    public FloodgateManagement(FastLoginCore<P, C, T> core, P player, FloodgatePlayer floodgatePlayer) {
        this.core = core;
        this.player = player;
        this.floodgatePlayer = floodgatePlayer;
        this.username = getName(player);

        //load values from config.yml
        autoLoginFloodgate = core.getConfig().get("autoLoginFloodgate").toString().toLowerCase(Locale.ROOT);
        autoRegisterFloodgate = core.getConfig().get("autoRegisterFloodgate").toString().toLowerCase(Locale.ROOT);
        allowNameConflict = core.getConfig().get("allowFloodgateNameConflict").toString().toLowerCase(Locale.ROOT);
    }

    @Override
    public void run() {
        core.getPlugin().getLog().info("Player {} is connecting through Geyser Floodgate.", username);

        // check if the Bedrock player is linked to a Java account
        isLinked = floodgatePlayer.getLinkedPlayer() != null;

        //this happens on Bukkit if it's connected to Bungee
        //if that's the case, players will be logged in via plugin messages
        if (core.getStorage() == null) {
            return;
        }

        profile = core.getStorage().loadProfile(username);

        if (profile.isExistingPlayer()) {
            if (profile.isFloodgateMigrated()) {
                if (profile.getFloodgate() == FloodgateState.TRUE && isLinked) {
                    core.getPlugin().getLog()
                            .info("Player {} is already stored by FastLogin as a non-linked Bedrock Edition player",
                                    username);
                    return;
                } else if (profile.getFloodgate() == FloodgateState.FALSE && isLinked) {
                    profile.setFloodgate(FloodgateState.LINKED);
                    core.getPlugin().getLog().info(
                            "Player {} will be changed from a Java player to a linked Floodgate player",
                            username);
                }
            } else {
                if (isLinked) {
                    profile.setFloodgate(FloodgateState.LINKED);
                    core.getPlugin().getLog().info(
                            "Player {} will be migrated to the v2 database schema as a linked Floodgate user",
                            username);
                } else {
                    profile.setFloodgate(FloodgateState.TRUE);
                    core.getPlugin().getLog().info(
                            "Player {} will be migrated to the v2 database schema as a Floodgate user", username);
                }
            }
        } else {
            if (isLinked) {
                profile.setFloodgate(FloodgateState.LINKED);
            } else {
                profile.setFloodgate(FloodgateState.TRUE);
            }
        }

        AuthPlugin<P> authPlugin = core.getAuthPluginHook();

        try {
            //maybe Bungee without auth plugin
            if (authPlugin == null) {
                if (profile != null) {
                    isRegistered = profile.isOnlinemodePreferred();
                } else {
                    isRegistered = false;
                }
            } else {
                isRegistered = authPlugin.isRegistered(username);
            }
        } catch (Exception ex) {
            core.getPlugin().getLog().error(
                    "An error has occurred while checking if player {} is registered",
                    username, ex);
            return;
        }

        //decide if checks should be made for conflicting Java player names
        if (isNameCheckRequired()) {
            // check for conflicting Premium Java name
            Optional<Profile> premiumUUID;
            try {
                premiumUUID = core.getResolver().findProfile(username);
            } catch (IOException | RateLimitException e) {
                core.getPlugin().getLog().error(
                        "Could not check whether Floodgate Player {}'s name conflicts a premium Java account's name.",
                        username, e);
                return;
            }

            //stop execution if player's name is conflicting
            if (premiumUUID.isPresent()) {
                return;
            }
        }

        // defer auto registration, if it's not enabled in the config
        if (!isRegistered && !isAutoAuthAllowed(autoRegisterFloodgate)) {
            return;
        }

        // stop the auto login procedure, if the current connection's parameters don't match the one stored in our
        // database
        // ex. we stored a LINKED account, but the current connection is not linked
        if ((profile.getFloodgate() == FloodgateState.LINKED && !isLinked)
            || (profile.getFloodgate() == FloodgateState.TRUE && isLinked)) {
            return;
        }

        // Set the UUID for the player login from floodgate.
        if (isLinked && profile.isOnlinemodePreferred()) {
            profile.setId(floodgatePlayer.getCorrectUniqueId());
            core.getPlugin().getLog().info(
                "Player {} login as a Floodgate linked premium user (UUID: {})",
                username, floodgatePlayer.getCorrectUniqueId());
        }
        //start Bukkit/Bungee specific tasks
        startLogin();

    }

    /**
     * Decide if the player can be automatically registered or logged in.<br>
     * The config option 'non-conflicting' is ignored by this function, as name
     * conflicts are checked by a different part of the code.
     *
     * @param configValue the value of either 'autoLoginFloodgate' or
     *                    'autoRegisterFloodgate' from config.yml
     * @return true if the Player can be registered automatically
     */
    protected boolean isAutoAuthAllowed(String configValue) {
        return "true".equals(configValue)
                || "no-conflict".equals(configValue) // this was checked before
                || ("linked".equals(configValue) && isLinked);
    }

    /**
     * Decides whether checks for conflicting Java names should be made
     * @return ture if an API call to Mojang is needed
     */
    private boolean isNameCheckRequired() {
        //linked players have the same name as their Java profile
        //OR
        //if allowNameConflict is 'false' or 'linked' and the player had a conflicting
        //name, then they would have been kicked in FloodgateHook#checkNameConflict
        if (isLinked || !"true".equals(allowNameConflict)) {
            return false;
        }

        //autoRegisterFloodgate should only be checked if then player is not yet registered
        if (!isRegistered && "no-conflict".equals(autoRegisterFloodgate)) {
            return true;
        }

        return "no-conflict".equals(autoLoginFloodgate);
    }

    protected abstract void startLogin();
    protected abstract String getName(P player);
    protected abstract UUID getUUID(P player);
    protected abstract InetSocketAddress getAddress(P player);

}

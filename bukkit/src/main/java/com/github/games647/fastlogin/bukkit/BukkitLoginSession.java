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

import com.github.games647.craftapi.model.skin.SkinProperty;
import com.github.games647.fastlogin.bukkit.listener.protocollib.packet.ClientPublicKey;
import com.github.games647.fastlogin.core.shared.LoginSession;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Represents a client connecting to the server.
 * <p>
 * This session is invalid if the player disconnects or the login was successful
 */
public class BukkitLoginSession extends LoginSession {

    private static final byte[] EMPTY_ARRAY = {};

    private final byte[] verifyToken;

    private final ClientPublicKey clientPublicKey;

    private boolean verified;
    private Boolean preJoinAutoLoginAllowed;
    private boolean preJoinLoginQueued;
    private SkinProperty skinProperty;

    public BukkitLoginSession(String username, byte[] verifyToken, ClientPublicKey publicKey, boolean registered,
                              StoredProfile profile) {
        super(username, registered, profile);

        this.clientPublicKey = publicKey;
        this.verifyToken = verifyToken.clone();
    }

    //available for BungeeCord
    public BukkitLoginSession(String username, boolean registered) {
        this(username, EMPTY_ARRAY, null, registered, null);
    }

    //cracked player
    public BukkitLoginSession(String username, StoredProfile profile) {
        this(username, EMPTY_ARRAY, null, false, profile);
    }

    //ProtocolSupport
    public BukkitLoginSession(String username, boolean registered, StoredProfile profile) {
        this(username, EMPTY_ARRAY, null, registered, profile);
    }

    /**
     * Gets the verify-token the server sent to the client.
     * <p>
     * Empty if it's a BungeeCord connection
     *
     * @return verify token from the server
     */
    public synchronized byte[] getVerifyToken() {
        return verifyToken.clone();
    }

    @Nullable
    public ClientPublicKey getClientPublicKey() {
        return clientPublicKey;
    }

    /**
     * @return premium skin if available
     */
    public synchronized Optional<SkinProperty> getSkin() {
        return Optional.ofNullable(skinProperty);
    }

    /**
     * Sets the premium skin property which was retrieved by the session server
     * @param skinProperty premium skin
     */
    public synchronized void setSkinProperty(SkinProperty skinProperty) {
        this.skinProperty = skinProperty;
    }

    /**
     * Sets whether the player has a premium (paid account) account and valid session
     *
     * @param verified whether the player has valid session
     */
    public synchronized void setVerifiedPremium(boolean verified) {
        this.verified = verified;
    }

    /**
     * Get whether the player has a premium (paid account) account and valid session
     *
     * @return whether the player has a valid session
     */
    public synchronized boolean isVerifiedPremium() {
        return verified;
    }

    public synchronized Boolean getPreJoinAutoLoginAllowed() {
        return preJoinAutoLoginAllowed;
    }

    public synchronized void setPreJoinAutoLoginAllowed(boolean allowed) {
        this.preJoinAutoLoginAllowed = allowed;
    }

    public synchronized boolean isPreJoinLoginQueued() {
        return preJoinLoginQueued;
    }

    public synchronized void setPreJoinLoginQueued(boolean queued) {
        this.preJoinLoginQueued = queued;
    }
}

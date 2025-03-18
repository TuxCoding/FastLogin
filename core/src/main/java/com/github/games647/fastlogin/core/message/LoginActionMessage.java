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
package com.github.games647.fastlogin.core.message;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

import java.util.UUID;

public class LoginActionMessage implements ChannelMessage {

    public static final String FORCE_CHANNEL = "force";

    private Type type;

    private String playerName;
    private UUID proxyId;

    public LoginActionMessage(Type type, String playerName, UUID proxyId) {
        this.type = type;
        this.playerName = playerName;
        this.proxyId = proxyId;
    }

    public LoginActionMessage() {
        //reading mode
    }

    public Type getType() {
        return type;
    }

    public String getPlayerName() {
        return playerName;
    }

    public UUID getProxyId() {
        return proxyId;
    }

    @Override
    public void readFrom(ByteArrayDataInput input) {
        this.type = Type.values()[input.readByte()];

        this.playerName = input.readUTF();

        //bungeecord UUID
        long mostSignificantBits = input.readLong();
        long leastSignificantBits = input.readLong();
        this.proxyId = new UUID(mostSignificantBits, leastSignificantBits);
    }

    @Override
    public void writeTo(ByteArrayDataOutput output) {
        output.writeByte(type.ordinal());

        //Data is sent through a random player. We have to tell the Bukkit version of this plugin the target
        output.writeUTF(playerName);

        //proxy identifier to check if it's an acceptable proxy
        output.writeLong(proxyId.getMostSignificantBits());
        output.writeLong(proxyId.getLeastSignificantBits());
    }

    @Override
    public String getChannelName() {
        return FORCE_CHANNEL;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
            + "type='" + type + '\''
            + ", playerName='" + playerName + '\''
            + ", proxyId=" + proxyId
            + '}';
    }

    public enum Type {

        LOGIN,

        REGISTER,

        CRACKED
    }
}

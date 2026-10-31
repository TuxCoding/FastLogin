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

/**
 * A connection-specific challenge and its proxy-authenticated response, before PlayerJoinEvent.
 */
public class PreJoinMessage implements ChannelMessage {

    public static final String CHANNEL = "prejoin";

    private UUID requestId;
    private LoginActionMessage loginAction;

    public PreJoinMessage() {
        // Reading mode.
    }

    public PreJoinMessage(UUID requestId, LoginActionMessage loginAction) {
        this.requestId = requestId;
        this.loginAction = loginAction;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public LoginActionMessage getLoginAction() {
        return loginAction;
    }

    @Override
    public void readFrom(ByteArrayDataInput input) {
        requestId = new UUID(input.readLong(), input.readLong());
        loginAction = null;
        if (input.readBoolean()) {
            loginAction = new LoginActionMessage();
            loginAction.readFrom(input);
        }
    }

    @Override
    public void writeTo(ByteArrayDataOutput output) {
        output.writeLong(requestId.getMostSignificantBits());
        output.writeLong(requestId.getLeastSignificantBits());
        output.writeBoolean(loginAction != null);
        if (loginAction != null) {
            loginAction.writeTo(output);
        }
    }

    @Override
    public String getChannelName() {
        return CHANNEL;
    }
}

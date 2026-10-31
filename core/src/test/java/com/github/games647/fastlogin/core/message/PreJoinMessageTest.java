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

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PreJoinMessageTest {

    @Test
    void requestAndResponseCarryConnectionChallenge() {
        UUID requestId = UUID.randomUUID();
        PreJoinMessage request = roundTrip(new PreJoinMessage(requestId, null));
        assertEquals(requestId, request.getRequestId());
        assertNull(request.getLoginAction());

        UUID proxyId = UUID.randomUUID();
        PreJoinMessage response = roundTrip(new PreJoinMessage(requestId,
                new LoginActionMessage(LoginActionMessage.Type.REGISTER, "PremiumPlayer", proxyId)));
        assertEquals(requestId, response.getRequestId());
        assertEquals("PremiumPlayer", response.getLoginAction().getPlayerName());
        assertEquals(LoginActionMessage.Type.REGISTER, response.getLoginAction().getType());
        assertEquals(proxyId, response.getLoginAction().getProxyId());
    }

    private PreJoinMessage roundTrip(PreJoinMessage message) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        message.writeTo(output);
        PreJoinMessage decoded = new PreJoinMessage();
        decoded.readFrom(ByteStreams.newDataInput(output.toByteArray()));
        return decoded;
    }
}

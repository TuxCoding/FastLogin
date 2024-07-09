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
package com.github.games647.fastlogin.bukkit.listener.protocollib;

import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.annotations.JsonAdapter;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SignatureTestData {

    public static SignatureTestData fromResource(String resourceName) throws IOException {
        URL keyUrl = Resources.getResource(resourceName);
        String encodedSignature = Resources.toString(keyUrl, StandardCharsets.US_ASCII);

        return new Gson().fromJson(encodedSignature, SignatureTestData.class);
    }

    @JsonAdapter(Base64Adapter.class)
    private byte[] nonce;

    private SignatureData signature;

    public byte[] getNonce() {
        return nonce;
    }

    public SignatureData getSignature() {
        return signature;
    }

    public static class SignatureData {

        private long salt;

        @JsonAdapter(Base64Adapter.class)
        private byte[] signature;

        public long getSalt() {
            return salt;
        }

        public byte[] getSignature() {
            return signature;
        }
    }
}

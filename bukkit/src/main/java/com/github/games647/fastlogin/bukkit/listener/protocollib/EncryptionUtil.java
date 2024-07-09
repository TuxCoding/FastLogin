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

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.listener.protocollib.packet.ClientPublicKey;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.Resources;
import com.google.common.primitives.Longs;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Random;
import java.util.UUID;

/**
 * Encryption and decryption minecraft util for connection between servers
 * and paid Minecraft account clients.
 */
final class EncryptionUtil {

    public static final int VERIFY_TOKEN_LENGTH = 4;
    public static final String KEY_PAIR_ALGORITHM = "RSA";

    private static final int RSA_LENGTH = 2_048;

    private static final PublicKey MOJANG_SESSION_KEY;
    private static final int LINE_LENGTH = 76;
    private static final Encoder KEY_ENCODER = Base64.getMimeEncoder(
            LINE_LENGTH, "\n".getBytes(StandardCharsets.UTF_8)
    );
    private static final int MILLISECOND_SIZE = 8;
    private static final int UUID_SIZE = 2 * MILLISECOND_SIZE;

    static {
        try {
            MOJANG_SESSION_KEY = loadMojangSessionKey();
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new RuntimeException("Failed to load Mojang session key", ex);
        }
    }

    private EncryptionUtil() {
        throw new RuntimeException("No instantiation of utility classes allowed");
    }

    /**
     * Generate an RSA key pair
     *
     * @return The RSA key pair.
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_PAIR_ALGORITHM);

            keyPairGenerator.initialize(RSA_LENGTH);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException nosuchalgorithmexception) {
            // Should be existing in every vm
            throw new ExceptionInInitializerError(nosuchalgorithmexception);
        }
    }

    /**
     * Generate a random token. This is used to verify that we are communicating with the same player
     * in a login session.
     *
     * @param random random generator
     * @return a token with 4 bytes long
     */
    public static byte[] generateVerifyToken(Random random) {
        byte[] token = new byte[VERIFY_TOKEN_LENGTH];
        random.nextBytes(token);
        return token;
    }

    /**
     * Generate the server id based on client and server data.
     *
     * @param serverId     session for the current login attempt
     * @param sharedSecret shared secret between the client and the server
     * @param publicKey    public key of the server
     * @return the server id formatted as a hexadecimal string.
     */
    public static String getServerIdHashString(String serverId, SecretKey sharedSecret, PublicKey publicKey) {
        byte[] serverHash = getServerIdHash(serverId, publicKey, sharedSecret);
        return (new BigInteger(serverHash)).toString(16);
    }

    /**
     * Decrypts the content and extracts the key spec.
     *
     * @param privateKey private server key
     * @param sharedKey  the encrypted shared key
     * @return shared secret key
     */
    public static SecretKey decryptSharedKey(PrivateKey privateKey, byte[] sharedKey)
            throws NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException,
            BadPaddingException, InvalidKeyException {
        return new SecretKeySpec(decrypt(privateKey, sharedKey), "AES");
    }

    public static boolean verifyClientKey(ClientPublicKey clientKey, Instant verifyTimestamp, UUID premiumId)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        if (clientKey.isExpired(verifyTimestamp)) {
            return false;
        }

        Signature verifier = Signature.getInstance("SHA1withRSA");
        // key of the signer
        verifier.initVerify(MOJANG_SESSION_KEY);
        verifier.update(toSignable(clientKey, premiumId));
        return verifier.verify(clientKey.signature());
    }

    private static byte[] toSignable(ClientPublicKey clientPublicKey, UUID ownerPremiumId) {
        if (ownerPremiumId == null) {
            long expiry = clientPublicKey.expiry().toEpochMilli();
            String encoded = KEY_ENCODER.encodeToString(clientPublicKey.key().getEncoded());
            return (expiry + "-----BEGIN RSA PUBLIC KEY-----\n" + encoded + "\n-----END RSA PUBLIC KEY-----\n")
                    .getBytes(StandardCharsets.US_ASCII);
        }

        byte[] keyData = clientPublicKey.key().getEncoded();
        return ByteBuffer.allocate(keyData.length + UUID_SIZE + MILLISECOND_SIZE)
                .putLong(ownerPremiumId.getMostSignificantBits())
                .putLong(ownerPremiumId.getLeastSignificantBits())
                .putLong(clientPublicKey.expiry().toEpochMilli())
                .put(keyData)
                .array();
    }

    public static boolean verifyNonce(byte[] expected, PrivateKey decryptionKey, byte[] encryptedNonce)
            throws NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException,
            BadPaddingException, InvalidKeyException {
        byte[] decryptedNonce = decrypt(decryptionKey, encryptedNonce);
        return Arrays.equals(expected, decryptedNonce);
    }

    public static boolean verifySignedNonce(byte[] nonce, PublicKey clientKey, long signatureSalt, byte[] signature)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        // key of the signer
        verifier.initVerify(clientKey);

        verifier.update(nonce);
        verifier.update(Longs.toByteArray(signatureSalt));
        return verifier.verify(signature);
    }

    private static PublicKey loadMojangSessionKey()
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        URL keyUrl = FastLoginBukkit.class.getClassLoader().getResource("yggdrasil_session_pubkey.der");
        byte[] keyData = Resources.toByteArray(keyUrl);
        KeySpec keySpec = new X509EncodedKeySpec(keyData);

        return KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }

    private static byte[] decrypt(PrivateKey key, byte[] data)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance(key.getAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    private static byte[] getServerIdHash(String sessionId, PublicKey publicKey, SecretKey sharedSecret) {
        @SuppressWarnings("deprecation")
        Hasher hasher = Hashing.sha1().newHasher();

        hasher.putBytes(sessionId.getBytes(StandardCharsets.ISO_8859_1));
        hasher.putBytes(sharedSecret.getEncoded());
        hasher.putBytes(publicKey.getEncoded());

        return hasher.hash().asBytes();
    }
}

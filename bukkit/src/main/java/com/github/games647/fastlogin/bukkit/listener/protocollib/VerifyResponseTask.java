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

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.netty.channel.NettyChannelInjector;
import com.comphenix.protocol.injector.packet.PacketRegistry;
import com.comphenix.protocol.injector.temporary.TemporaryPlayerFactory;
import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.ConstructorAccessor;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.Converters;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedProfilePublicKey.WrappedProfileKeyData;
import com.github.games647.craftapi.model.auth.Verification;
import com.github.games647.craftapi.model.skin.SkinProperty;
import com.github.games647.craftapi.resolver.MojangResolver;
import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.InetUtils;
import com.github.games647.fastlogin.bukkit.listener.protocollib.packet.ClientPublicKey;
import org.bukkit.entity.Player;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static com.comphenix.protocol.PacketType.Login.Client.START;
import static com.comphenix.protocol.PacketType.Login.Server.DISCONNECT;

public class VerifyResponseTask implements Runnable {

    private static final String ENCRYPTION_CLASS_NAME = "MinecraftEncryption";
    private static final String ADDRESS_VERIFY_WARNING = "This indicates the use of reverse-proxy like HAProxy, "
            + "TCPShield, BungeeCord, Velocity, etc. "
            + "By default (configurable in the config) this plugin requests Mojang to verify the connecting IP "
            + "to this server with the one used to log into Minecraft to prevent MITM attacks. In "
            + "order to work this security feature, the actual client IP needs to be forwarding "
            + "(keyword IP forwarding). This process will also be useful for other server "
            + "features like IP banning, so that it doesn't ban the proxy IP.";

    private final FastLoginBukkit plugin;
    private final PacketEvent packetEvent;
    private final KeyPair serverKey;

    private final Player player;

    private final BukkitLoginSession session;

    private final byte[] sharedSecret;

    private static Method encryptMethod;
    private static Method encryptKeyMethod;

    private static Method cipherMethod;

    public VerifyResponseTask(FastLoginBukkit plugin, PacketEvent packetEvent,
                              Player player, BukkitLoginSession session,
                              byte[] sharedSecret, KeyPair keyPair) {
        this.plugin = plugin;
        this.packetEvent = packetEvent;
        this.player = player;
        this.session = session;
        this.sharedSecret = Arrays.copyOf(sharedSecret, sharedSecret.length);
        this.serverKey = keyPair;
    }

    @Override
    public void run() {
        try {
            verifyResponse(session);
        } finally {
            //this is a fake packet; it shouldn't be sent to the server
            synchronized (packetEvent.getAsyncMarker().getProcessingLock()) {
                packetEvent.setCancelled(true);
            }

            ProtocolLibrary.getProtocolManager().getAsynchronousManager().signalPacketTransmission(packetEvent);
        }
    }

    private void verifyResponse(BukkitLoginSession session) {
        PrivateKey privateKey = serverKey.getPrivate();

        SecretKey loginKey;
        try {
            loginKey = EncryptionUtil.decryptSharedKey(privateKey, sharedSecret);
        } catch (GeneralSecurityException securityEx) {
            disconnect("error-kick", "Cannot decrypt received contents", securityEx);
            return;
        }

        try {
            if (!enableEncryption(loginKey)) {
                return;
            }
        } catch (Exception ex) {
            disconnect("error-kick", "Cannot decrypt received contents", ex);
            return;
        }

        String serverId = EncryptionUtil.getServerIdHashString("", loginKey, serverKey.getPublic());

        String requestedUsername = session.getRequestUsername();
        InetSocketAddress socketAddress = player.getAddress();
        try {
            MojangResolver resolver = plugin.getCore().getResolver();
            InetAddress address = socketAddress.getAddress();
            Optional<Verification> response = resolver.hasJoined(requestedUsername, serverId, address);
            if (response.isPresent()) {
                encryptConnection(session, requestedUsername, response.get());
            } else {
                //user tried to fake an authentication
                disconnect(
                        "invalid-session",
                        "Session server rejected incoming connection for GameProfile {} ({}). Possible reasons are "
                                + "1) Client IP address contacting Mojang and server during server join were different "
                                + "(Do you use a reverse proxy? -> Enable IP forwarding, "
                                + "or disable the feature in the config). "
                                + "2) Player is offline, but tried to bypass the authentication "
                                + "3) Client uses an outdated username for connecting (Fix: Restart client)",
                        requestedUsername, address
                );

                if (InetUtils.isLocalAddress(address)) {
                    plugin.getLog().warn(
                            "The incoming request for player {} uses a local IP address",
                            requestedUsername
                    );
                } else {
                    plugin.getLog().warn("If you think this is an error, please verify that the incoming "
                            + "IP address {} is not associated with a server hosting company.", address);
                }

                plugin.getLog().warn(ADDRESS_VERIFY_WARNING);
            }
        } catch (IOException ioEx) {
            disconnect("error-kick", "Failed to connect to session server", ioEx);
        }
    }

    private void encryptConnection(BukkitLoginSession session, String requestedUsername, Verification verification) {
        plugin.getLog().info("Profile {} has a verified premium account", requestedUsername);
        String realUsername = verification.getName();
        if (realUsername == null) {
            disconnect("invalid-session", "Username field null for {}", requestedUsername);
            return;
        }

        SkinProperty[] properties = verification.getProperties();
        if (properties.length > 0) {
            session.setSkinProperty(properties[0]);
        }

        session.setVerifiedUsername(realUsername);
        session.setUuid(verification.getId());
        session.setVerifiedPremium(true);

        setPremiumUUID(session.getUuid());
        receiveFakeStartPacket(realUsername, session.getClientPublicKey(), session.getUuid());
    }

    private void setPremiumUUID(UUID premiumUUID) {
        if (plugin.getConfig().getBoolean("premiumUuid") && premiumUUID != null) {
            try {
                Object networkManager = getNetworkManager();
                //https://github.com/bergerkiller/CraftSource/blob/master/net.minecraft.server/NetworkManager.java#L69

                Class<?> managerClass = networkManager.getClass();
                FieldAccessor accessor = Accessors.getFieldAccessorOrNull(managerClass, "spoofedUUID", UUID.class);
                accessor.set(networkManager, premiumUUID);
            } catch (Exception exc) {
                plugin.getLog().error("Error setting premium uuid of {}", player, exc);
            }
        }
    }

    //try to get the networkManager from ProtocolLib
    private Object getNetworkManager() throws ClassNotFoundException {
        NettyChannelInjector injectorContainer = (NettyChannelInjector) Accessors.getMethodAccessorOrNull(
                TemporaryPlayerFactory.class, "getInjectorFromPlayer", Player.class
        ).invoke(null, player);

        FieldAccessor accessor = Accessors.getFieldAccessorOrNull(
                NettyChannelInjector.class, "networkManager", Object.class
        );
        return accessor.get(injectorContainer);
    }

    private boolean enableEncryption(SecretKey loginKey) throws IllegalArgumentException {
        plugin.getLog().info("Enabling onlinemode encryption for {}", player.getAddress());
        // Initialize method reflections
        if (encryptKeyMethod == null || encryptMethod == null) {
            Class<?> networkManagerClass = MinecraftReflection.getNetworkManagerClass();
            try {
                // Try to get the old (pre MC 1.16.4) encryption method
                encryptKeyMethod = FuzzyReflection.fromClass(networkManagerClass)
                        .getMethodByParameters("a", SecretKey.class);
            } catch (IllegalArgumentException exception) {
                // Get the new encryption method
                encryptMethod = FuzzyReflection.fromClass(networkManagerClass)
                        .getMethodByParameters("a", Cipher.class, Cipher.class);

                Class<?> encryptionClass = MinecraftReflection.getMinecraftClass(
                        "util." + ENCRYPTION_CLASS_NAME, ENCRYPTION_CLASS_NAME
                );

                // Get the needed Cipher helper method (used to generate ciphers from login key)
                cipherMethod = FuzzyReflection.fromClass(encryptionClass)
                        .getMethodByParameters("a", int.class, Key.class);
            }
        }

        try {
            Object networkManager = this.getNetworkManager();

            // If cipherMethod is null - use old encryption (pre MC 1.16.4), otherwise use the new cipher one
            if (encryptKeyMethod != null) {
                // Encrypt/decrypt packet flow, this behaviour is expected by the client
                encryptKeyMethod.invoke(networkManager, loginKey);
            } else {
                // Create ciphers from login key
                Object decryptionCipher = cipherMethod.invoke(null, Cipher.DECRYPT_MODE, loginKey);
                Object encryptionCipher = cipherMethod.invoke(null, Cipher.ENCRYPT_MODE, loginKey);

                // Encrypt/decrypt packet flow, this behaviour is expected by the client
                encryptMethod.invoke(networkManager, decryptionCipher, encryptionCipher);
            }
        } catch (Exception ex) {
            disconnect("error-kick", "Couldn't enable encryption", ex);
            return false;
        }

        return true;
    }

    private void disconnect(String reasonKey, String logMessage, Object... arguments) {
        plugin.getLog().error(logMessage, arguments);
        kickPlayer(plugin.getCore().getMessage(reasonKey));
    }

    private void kickPlayer(String reason) {
        PacketContainer kickPacket = new PacketContainer(DISCONNECT);
        kickPacket.getChatComponents().write(0, WrappedChatComponent.fromText(reason));
        //send kick packet at login state
        //the normal event.getPlayer.kickPlayer(String) method does only work at play state
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, kickPacket);
        //tell the server that we want to close the connection
        player.kickPlayer("Disconnect");
    }

    //fake a new login packet in order to let the server handle all the other stuff
    private void receiveFakeStartPacket(String username, ClientPublicKey clientKey, UUID uuid) {
        PacketContainer startPacket;
        if (new MinecraftVersion(1, 20, 2).atOrAbove()) {
            startPacket = new PacketContainer(START);
            startPacket.getStrings().write(0, username);
            startPacket.getUUIDs().write(0, uuid);
        } else if (new MinecraftVersion(1, 19, 3).atOrAbove()) {
            startPacket = new PacketContainer(START);
            startPacket.getStrings().write(0, username);
            startPacket.getOptionals(Converters.passthrough(UUID.class)).write(0, Optional.of(uuid));
        } else if (new MinecraftVersion(1, 19, 0).atOrAbove()) {
            startPacket = new PacketContainer(START);
            startPacket.getStrings().write(0, username);

            EquivalentConverter<WrappedProfileKeyData> converter = BukkitConverters.getWrappedPublicKeyDataConverter();
            Optional<WrappedProfileKeyData> wrappedKey = Optional.ofNullable(clientKey).map(key ->
                    new WrappedProfileKeyData(clientKey.expiry(), clientKey.key(), clientKey.signature())
            );

            startPacket.getOptionals(converter).write(0, wrappedKey);
        } else {
            //uuid is ignored by the packet definition
            WrappedGameProfile fakeProfile = new WrappedGameProfile(UUID.randomUUID(), username);

            Class<?> profileHandleType = fakeProfile.getHandleType();
            Class<?> packetHandleType = PacketRegistry.getPacketClassFromType(START);
            ConstructorAccessor startCons = Accessors.getConstructorAccessorOrNull(packetHandleType, profileHandleType);
            startPacket = new PacketContainer(START, startCons.invoke(fakeProfile.getHandle()));
        }

        //we don't want to handle our own packets so ignore filters
        ProtocolLibrary.getProtocolManager().receiveClientPacket(player, startPacket, false);
    }
}

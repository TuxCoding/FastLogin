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

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.netty.channel.NettyChannelInjector;
import com.comphenix.protocol.injector.temporary.TemporaryPlayerFactory;
import com.comphenix.protocol.reflect.FieldAccessException;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.Converters;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedProfilePublicKey.WrappedProfileKeyData;
import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.listener.protocollib.packet.ClientPublicKey;
import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;
import com.mojang.datafixers.util.Either;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.jetbrains.annotations.NotNull;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static com.comphenix.protocol.PacketType.Login.Client.ENCRYPTION_BEGIN;
import static com.comphenix.protocol.PacketType.Login.Client.START;

public class ProtocolLibListener extends PacketAdapter {

    private final FastLoginBukkit plugin;

    //just create a new once on plugin enable. This used for verify token generation
    private final SecureRandom random = new SecureRandom();
    private final KeyPair keyPair = EncryptionUtil.generateKeyPair();
    private final AntiBotService antiBotService;

    private final boolean verifyClientKeys;

    public ProtocolLibListener(FastLoginBukkit plugin, AntiBotService antiBotService, boolean verifyClientKeys) {
        //run async in order to not block the server, because we are making api calls to Mojang
        super(params()
                .plugin(plugin)
                .types(START, ENCRYPTION_BEGIN)
                .optionAsync());

        this.plugin = plugin;
        this.antiBotService = antiBotService;
        this.verifyClientKeys = verifyClientKeys;
    }

    public static void register(FastLoginBukkit plugin, AntiBotService antiBotService, boolean verifyClientKeys) {
        // they will be created with a static builder, because otherwise it will throw a NoClassDefFoundError
        // TODO: make synchronous processing, but do web or database requests async
        ProtocolLibrary.getProtocolManager()
                .getAsynchronousManager()
                .registerAsyncHandler(new ProtocolLibListener(plugin, antiBotService, verifyClientKeys))
                .start();
    }

    @Override
    public void onPacketReceiving(PacketEvent packetEvent) {
        if (packetEvent.isCancelled()
                || plugin.getCore().getAuthPluginHook() == null
                || !plugin.isServerFullyStarted()) {
            return;
        }

        Player sender = packetEvent.getPlayer();
        PacketType packetType = getOverriddenType(packetEvent.getPacketType());

        plugin.getLog().info("New incoming packet {} from {}", packetType, sender.getName());
        try {
            if (packetType == START) {
                PacketContainer packet = packetEvent.getPacket();

                InetSocketAddress address = sender.getAddress();
                String username = getUsername(packet);

                Action action = antiBotService.onIncomingConnection(address, username);
                switch (action) {
                    case Ignore:
                        // just ignore
                        return;
                    case Block:
                        String message = plugin.getCore().getMessage("kick-antibot");
                        sender.kickPlayer(message);
                        break;
                    case Continue:
                    default:
                        //player.getName() won't work at this state
                        onLoginStart(packetEvent, sender, username);
                        break;
                }
            } else if (packetType == ENCRYPTION_BEGIN) {
                onEncryptionBegin(packetEvent, sender);
            } else {
                plugin.getLog().warn("Unknown packet type received {}", packetType);
            }
        } catch (FieldAccessException fieldAccessEx) {
            plugin.getLog().error("Failed to parse packet {}", packetEvent.getPacketType(), fieldAccessEx);
        }
    }

    private @NotNull PacketType getOverriddenType(PacketType packetType) {
        if (packetType.isDynamic()) {
            String vanillaName = packetType.getPacketClass().getName();
            plugin.getLog().info("Overriding packet type for unregistered packet type to fix ProtocolLib bug");
            if (vanillaName.endsWith("ServerboundHelloPacket")) {
                return START;
            }

            if (vanillaName.endsWith("ServerboundKeyPacket")) {
                return ENCRYPTION_BEGIN;
            }
        }

        return packetType;
    }

    private void onEncryptionBegin(PacketEvent packetEvent, Player sender) {
        byte[] sharedSecret = packetEvent.getPacket().getByteArrays().read(0);

        BukkitLoginSession session = plugin.getSession(sender.getAddress());
        if (session == null) {
            plugin.getLog().warn("Profile {} tried to send encryption response at invalid state", sender.getAddress());
            sender.kickPlayer(plugin.getCore().getMessage("invalid-request"));
        } else {
            byte[] expectedVerifyToken = session.getVerifyToken();
            if (verifyNonce(sender, packetEvent.getPacket(), session.getClientPublicKey(), expectedVerifyToken)) {
                packetEvent.getAsyncMarker().incrementProcessingDelay();

                Runnable verifyTask = new VerifyResponseTask(
                        plugin, packetEvent, sender, session, sharedSecret, keyPair
                );
                plugin.getScheduler().runAsync(verifyTask);
            } else {
                sender.kickPlayer(plugin.getCore().getMessage("invalid-verify-token"));
            }
        }
    }

    private boolean verifyNonce(Player sender, PacketContainer packet,
                                ClientPublicKey clientPublicKey, byte[] expectedToken) {
        try {
            if (new MinecraftVersion(1, 19, 0).atOrAbove()
                    && !new MinecraftVersion(1, 19, 3).atOrAbove()) {
                Either<byte[], ?> either = packet.getSpecificModifier(Either.class).read(0);
                if (clientPublicKey == null) {
                    Optional<byte[]> left = either.left();
                    if (!left.isPresent()) {
                        plugin.getLog().error("No verify token sent if requested without player signed key {}", sender);
                        return false;
                    }

                    return EncryptionUtil.verifyNonce(expectedToken, keyPair.getPrivate(), left.get());
                } else {
                    Optional<?> optSignatureData = either.right();
                    if (!optSignatureData.isPresent()) {
                        plugin.getLog().error("No signature given to sent player signing key {}", sender);
                        return false;
                    }

                    Object signatureData = optSignatureData.get();
                    long salt = FuzzyReflection.getFieldValue(signatureData, Long.TYPE, true);
                    byte[] signature = FuzzyReflection.getFieldValue(signatureData, byte[].class, true);

                    PublicKey publicKey = clientPublicKey.key();
                    return EncryptionUtil.verifySignedNonce(expectedToken, publicKey, salt, signature);
                }
            } else {
                byte[] nonce = packet.getByteArrays().read(1);
                return EncryptionUtil.verifyNonce(expectedToken, keyPair.getPrivate(), nonce);
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | NoSuchPaddingException
                 | IllegalBlockSizeException | BadPaddingException signatureEx) {
            plugin.getLog().error("Invalid signature from player {}", sender, signatureEx);
            return false;
        }
    }

    private void onLoginStart(PacketEvent packetEvent, Player player, String username) {
        //this includes ip:port. Should be unique for an incoming login request with a timeout of 2 minutes
        String sessionKey = player.getAddress().toString();

        //remove old data every time on a new login in order to keep the session only for one person
        plugin.removeSession(player.getAddress());

        PacketContainer packet = packetEvent.getPacket();

        //floodgate event listeners may run after our event listener
        if (plugin.getFloodgateService() != null) {
            FloodgatePlayer floodgatePlayer = getFloodgatePlayer(packetEvent.getPlayer());
            if (floodgatePlayer != null) {
                //add the floodgate prefix for our internal checks
                //the correct username will be written to the packet by Floodgate
                username = floodgatePlayer.getCorrectUsername();
            }
        }

        Optional<ClientPublicKey> clientKey;
        if (new MinecraftVersion(1, 19, 3).atOrAbove()) {
            // public key is sent separate
            clientKey = Optional.empty();
        } else {
            Optional<Optional<WrappedProfileKeyData>> profileKey = packet.getOptionals(
                            BukkitConverters.getWrappedPublicKeyDataConverter()
                    ).optionRead(0);

            clientKey = profileKey.flatMap(Function.identity()).flatMap(data -> {
                Instant expires = data.getExpireTime();
                PublicKey key = data.getKey();
                byte[] signature = data.getSignature();
                return Optional.of(ClientPublicKey.of(expires, key, signature));
            });

            // start reading from index 1, because 0 is already used by the public key
            Optional<UUID> sessionUUID = packet.getOptionals(Converters.passthrough(UUID.class)).readSafely(1);
            if (verifyClientKeys && sessionUUID.isPresent() && clientKey.isPresent()
                    && verifyPublicKey(clientKey.get(), sessionUUID.get())) {
                // missing or incorrect
                // expired always not allowed
                player.kickPlayer(plugin.getCore().getMessage("invalid-public-key"));
                plugin.getLog().warn("Invalid public key from player {}", username);
                return;
            }
        }

        plugin.getLog().trace("GameProfile {} with {} connecting", sessionKey, username);

        packetEvent.getAsyncMarker().incrementProcessingDelay();
        Runnable nameCheckTask = new NameCheckTask(
                plugin, random, player, packetEvent, username, clientKey.orElse(null), keyPair.getPublic()
        );
        plugin.getScheduler().runAsync(nameCheckTask);
    }

    private boolean verifyPublicKey(ClientPublicKey clientKey, UUID sessionPremiumUUID) {
        try {
            return EncryptionUtil.verifyClientKey(clientKey, Instant.now(), sessionPremiumUUID);
        } catch (SignatureException | InvalidKeyException | NoSuchAlgorithmException ex) {
            return false;
        }
    }

    private String getUsername(PacketContainer packet) {
        WrappedGameProfile profile = packet.getGameProfiles().readSafely(0);
        if (profile == null) {
            return packet.getStrings().read(0);
        }

        //player.getName() won't work at this state
        return profile.getName();
    }

    private FloodgatePlayer getFloodgatePlayer(Player player) {
        Channel channel = getChannel(player);
        AttributeKey<FloodgatePlayer> floodgateAttribute = AttributeKey.valueOf("floodgate-player");
        return channel.attr(floodgateAttribute).get();
    }

    private static Channel getChannel(Player player) {
        NettyChannelInjector injector = (NettyChannelInjector) Accessors.getMethodAccessorOrNull(
                        TemporaryPlayerFactory.class, "getInjectorFromPlayer", Player.class
                ).invoke(null, player);
        return FuzzyReflection.getFieldValue(injector, Channel.class, true);
    }

}

package com.github.games647.fastlogin.bukkit.listener.protocollib;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.RateLimiter;

import java.security.KeyPair;
import java.security.SecureRandom;

import org.bukkit.entity.Player;

import static com.comphenix.protocol.PacketType.Login.Client.ENCRYPTION_BEGIN;
import static com.comphenix.protocol.PacketType.Login.Client.START;

public class ProtocolLibListener extends PacketAdapter {

    private final FastLoginBukkit plugin;

    //just create a new once on plugin enable. This used for verify token generation
    private final SecureRandom random = new SecureRandom();
    private final KeyPair keyPair = EncryptionUtil.generateKeyPair();
    private final RateLimiter rateLimiter;

    public ProtocolLibListener(FastLoginBukkit plugin, RateLimiter rateLimiter) {
        //run async in order to not block the server, because we are making api calls to Mojang
        super(params()
                .plugin(plugin)
                .types(START, ENCRYPTION_BEGIN)
                .optionAsync());

        this.plugin = plugin;
        this.rateLimiter = rateLimiter;
    }

    public static void register(FastLoginBukkit plugin, RateLimiter rateLimiter) {
        //they will be created with a static builder, because otherwise it will throw a NoClassDefFoundError
        ProtocolLibrary.getProtocolManager()
                .getAsynchronousManager()
                .registerAsyncHandler(new ProtocolLibListener(plugin, rateLimiter))
                .start();
    }

    @Override
    public void onPacketReceiving(PacketEvent packetEvent) {
        if (packetEvent.isCancelled()
                || plugin.getCore().getAuthPluginHook()== null
                || !plugin.isServerFullyStarted()) {
            return;
        }

        Player sender = packetEvent.getPlayer();
        if (isPacketLoginInStart(packetEvent.getPacket())) {
            if (!rateLimiter.tryAcquire()) {
                plugin.getLog().warn("Rate Limit hit - Ignoring player {}", sender);
                return;
            }

            onLogin(packetEvent, sender);
        } else {
            onEncryptionBegin(packetEvent, sender);
        }
    }

    private boolean isPacketLoginInStart(PacketContainer packet) {
        // Workaround for ProtocolLib issues on CatServer / Magma servers.
        return packet.getGameProfiles().size() > 0;
    }

    private void onEncryptionBegin(PacketEvent packetEvent, Player sender) {
        byte[] sharedSecret = packetEvent.getPacket().getByteArrays().read(0);

        packetEvent.getAsyncMarker().incrementProcessingDelay();
        Runnable verifyTask = new VerifyResponseTask(plugin, packetEvent, sender, sharedSecret, keyPair);
        plugin.getScheduler().runAsync(verifyTask);
    }

    private void onLogin(PacketEvent packetEvent, Player player) {
        //this includes ip:port. Should be unique for an incoming login request with a timeout of 2 minutes
        String sessionKey = player.getAddress().toString();

        //remove old data every time on a new login in order to keep the session only for one person
        plugin.removeSession(player.getAddress());

        //player.getName() won't work at this state
        PacketContainer packet = packetEvent.getPacket();

        String username = packet.getGameProfiles().read(0).getName();
        plugin.getLog().trace("GameProfile {} with {} connecting", sessionKey, username);

        packetEvent.getAsyncMarker().incrementProcessingDelay();
        Runnable nameCheckTask = new NameCheckTask(plugin, packetEvent, random, player, username, keyPair.getPublic());
        plugin.getScheduler().runAsync(nameCheckTask);
    }
}

package com.rounds.nms.impl;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Universal fake game connection (versions 1.20.5+): swallows keepalives/timeout
 * kicks instead of disconnecting the bot. Disconnect overloads are inherited from
 * the parent listener, whose exact shape differs between Minecraft releases; the
 * underlying NoopConnection discards every outgoing packet anyway.
 */
public class FakeConnection extends ServerGamePacketListenerImpl {

    public FakeConnection(MinecraftServer server, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        super(server, connection, player, cookie);
    }

    @Override
    public void tick() {
        // Prevent flying kick, keepalive timeout, and inactivity disconnects
        resetPosition();
    }

    @Override
    public void send(Packet<?> packet) {
        // No network send for fake connection
    }
}

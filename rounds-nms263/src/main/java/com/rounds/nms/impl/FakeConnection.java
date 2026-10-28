package com.rounds.nms.impl;

import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * MC 26.1.2 fake game connection: swallows keepalives/kicks instead of disconnecting the bot.
 * 26.1.x+ disconnect flow: net.minecraft.network.chat.Component,
 * net.minecraft.network.chat.Component + io.papermc.paper.connection.DisconnectionReason,
 * net.minecraft.network.DisconnectionDetails.
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

    @Override
    public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener) {
        if (listener != null) {
            try {
                listener.operationComplete(null);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void disconnect(net.minecraft.network.chat.Component message) {
        // Prevent spurious disconnects
    }

    @Override
    public void disconnect(net.minecraft.network.chat.Component message, io.papermc.paper.connection.DisconnectionReason reason) {
        // Prevent spurious disconnects
    }

    @Override
    public void disconnect(DisconnectionDetails details) {
        // Prevent spurious disconnects
    }
}

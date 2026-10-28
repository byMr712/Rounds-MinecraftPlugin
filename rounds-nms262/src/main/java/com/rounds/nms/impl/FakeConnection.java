package com.rounds.nms.impl;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.event.player.PlayerKickEvent;

/**
 * MC 26.2 fake game connection: swallows keepalives/ragequits instead of kicking the bot.
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
    public void send(Packet<?> packet, ChannelFutureListener listener) {
        if (listener != null) {
            try {
                listener.operationComplete(null);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void disconnect(net.kyori.adventure.text.Component message) {
        // Prevent spurious disconnects
    }

    @Override
    public void disconnect(net.kyori.adventure.text.Component message, PlayerKickEvent.Cause cause) {
        // Prevent spurious disconnects
    }

    @Override
    public void disconnect(net.minecraft.network.chat.Component message) {
        // Prevent spurious disconnects
    }

    @Override
    public void disconnect(net.minecraft.network.chat.Component message, PlayerKickEvent.Cause cause) {
        // Prevent spurious disconnects
    }
}

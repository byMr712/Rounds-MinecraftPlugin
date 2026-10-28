package com.rounds.nms.impl;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * MC 1.20.4 fake network pipeline: discards all outgoing packets, guarantees "connected" state.
 * channel/address are injected via reflection by field TYPE because the runtime field names
 * are obfuscated (n/o) and private; paperweight's Access Transformer widens them only at
 * compile time, so direct assignment would throw IllegalAccessError at runtime.
 */
public class NoopConnection extends Connection {

    public NoopConnection(PacketFlow flow) {
        super(flow);
        injectFakeChannel();
        injectProtocolAttributes();
    }

    /**
     * placeNewPlayer -> new ServerGamePacketListenerImpl -> Connection.setListener reads the
     * per-flow protocol attribute of the channel (Connection.ATTRIBUTE_*_PROTOCOL) and would
     * NPE on an un-initialized fake channel. Simulate the handshake end state by registering
     * the PLAY codec data for both flows, exactly what a real client racks up before entering PLAY.
     */
    private void injectProtocolAttributes() {
        try {
            this.channel.attr(Connection.ATTRIBUTE_SERVERBOUND_PROTOCOL)
                    .set(ConnectionProtocol.PLAY.codec(PacketFlow.SERVERBOUND));
            this.channel.attr(Connection.ATTRIBUTE_CLIENTBOUND_PROTOCOL)
                    .set(ConnectionProtocol.PLAY.codec(PacketFlow.CLIENTBOUND));
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                    "[RoundsPlugin] Failed to initialize fake protocol attributes", t);
        }
    }

    private void injectFakeChannel() {
        try {
            for (Class<?> c = getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    Class<?> type = f.getType();
                    if (type == Channel.class) {
                        f.setAccessible(true);
                        f.set(this, new EmbeddedChannel());
                    } else if (type == SocketAddress.class) {
                        f.setAccessible(true);
                        f.set(this, new InetSocketAddress("127.0.0.1", 25565));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void send(Packet<?> packet) {
        // Discard outgoing packets to fake player
    }

    @Override
    public void send(Packet<?> packet, PacketSendListener listener) {
        if (listener != null) {
            try {
                listener.onSuccess();
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void send(Packet<?> packet, PacketSendListener listener, boolean flush) {
        if (listener != null) {
            try {
                listener.onSuccess();
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void flushChannel() {
        // No-op
    }

    @Override
    public void tick() {
        // No-op
    }
}

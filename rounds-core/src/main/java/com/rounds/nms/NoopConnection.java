package com.rounds.nms;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.net.InetSocketAddress;

public class NoopConnection extends Connection {

    public NoopConnection(PacketFlow flow) {
        super(flow);
        this.channel = new EmbeddedChannel();
        this.address = new InetSocketAddress("127.0.0.1", 25565);
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
    public void send(Packet<?> packet, ChannelFutureListener listener) {
        if (listener != null) {
            try {
                listener.operationComplete(null);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
        if (listener != null) {
            try {
                listener.operationComplete(null);
            } catch (Exception ignored) {}
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

package com.rounds.nms.impl;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * MC 26.2 fake network pipeline: discards all outgoing packets, guarantees "connected" state.
 * channel/address are injected via reflection by field TYPE so the impl is robust against
 * access-modifier or field-name differences between runtime mappings.
 */
public class NoopConnection extends Connection {

    public NoopConnection(PacketFlow flow) {
        super(flow);
        injectFakeChannel();
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
    public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener) {
        if (listener != null) {
            try {
                listener.operationComplete(null);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener, boolean flush) {
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

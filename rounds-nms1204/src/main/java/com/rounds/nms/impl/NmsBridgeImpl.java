package com.rounds.nms.impl;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R3.CraftServer;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * MC 1.20.4 implementation of the NMS bridge.
 * All net.minecraft.* / org.bukkit.craftbukkit.v1_20_R3.* access is isolated here.
 * Compiled with paperweight-userdev (Mojang-mapped) and reobf-to-runtime (spigot v1_20_R3)
 * by the build, so the flow matches the 1.20.4 Paper/Purpur runtime.
 * NOTE: 1.20.4-era signatures differ from newer versions:
 *  - CommonListenerCookie.createInitial(GameProfile) has no boolean parameter
 *  - craftbukkit classes live under the versioned v1_20_R3 package
 */
public class NmsBridgeImpl implements com.rounds.nms.NmsBridge {

    private static MinecraftServer nmsServer() {
        return ((CraftServer) Bukkit.getServer()).getServer();
    }

    @Override
    public NmsPlayerHandleImpl createFakePlayer(World world, String botName, UUID botUuid, Location loc) {
        try {
            MinecraftServer nmsServer = nmsServer();
            ServerLevel nmsLevel = ((CraftWorld) world).getHandle();

            GameProfile profile = new GameProfile(botUuid, botName);
            ClientInformation clientInfo = ClientInformation.createDefault();

            ServerPlayer nmsPlayer = new ServerPlayer(nmsServer, nmsLevel, profile, clientInfo);
            nmsPlayer.setPos(loc.getX(), loc.getY(), loc.getZ());
            nmsPlayer.setRot(loc.getYaw(), loc.getPitch());

            // 1. Setup NoopConnection & place into server PlayerList
            NoopConnection noopConn = new NoopConnection(PacketFlow.SERVERBOUND);
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile);
            nmsServer.getPlayerList().placeNewPlayer(noopConn, nmsPlayer, cookie);

            // 2. Swap with FakeConnection to prevent timeout disconnects
            nmsPlayer.connection = new FakeConnection(nmsServer, noopConn, nmsPlayer, cookie);

            return new NmsPlayerHandleImpl(nmsPlayer);
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                    "[RoundsPlugin] createFakePlayer(" + botName + ") failed", t);
            return null;
        }
    }

    @Override
    public NmsPlayerHandleImpl getHandle(Player player) {
        try {
            if (player == null) return null;
            ServerPlayer sp = nmsServer().getPlayerList().getPlayer(player.getUniqueId());
            return (sp == null) ? null : new NmsPlayerHandleImpl(sp);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void ensureConnection(Player player) {
        try {
            if (player == null) return;
            ServerPlayer sp = nmsServer().getPlayerList().getPlayer(player.getUniqueId());
            if (sp != null && sp.connection == null) {
                NoopConnection noopConn = new NoopConnection(PacketFlow.SERVERBOUND);
                CommonListenerCookie cookie = CommonListenerCookie.createInitial(sp.getGameProfile());
                sp.connection = new FakeConnection(nmsServer(), noopConn, sp, cookie);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void despawnFakePlayer(com.rounds.nms.NmsPlayerHandle handle) {
        if (!(handle instanceof NmsPlayerHandleImpl impl)) return;
        ServerPlayer nmsPlayer = impl.nmsPlayer();
        if (nmsPlayer == null) return;

        MinecraftServer nmsServer = nmsServer();
        Player bukkitPlayer = nmsPlayer.getBukkitEntity();

        try {
            nmsPlayer.unRide();
            nmsPlayer.closeContainer();
            nmsPlayer.discard();
            if (bukkitPlayer != null && bukkitPlayer.getWorld() != null) {
                ServerLevel level = ((CraftWorld) bukkitPlayer.getWorld()).getHandle();
                if (level != null) {
                    level.removePlayerImmediately(nmsPlayer, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                }
            }
        } catch (Throwable ignored) {
        }

        if (nmsServer != null && nmsServer.getPlayerList() != null) {
            nmsServer.getPlayerList().remove(nmsPlayer);
        }

        if (nmsPlayer.connection != null) {
            try {
                nmsPlayer.connection.disconnect(net.minecraft.network.chat.Component.literal("Despawned"));
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void sendEntityFlags(Player spectator, Player target, byte flags) {
        try {
            ServerPlayer sp = nmsServer().getPlayerList().getPlayer(spectator.getUniqueId());
            if (sp == null || sp.connection == null) return;
            SynchedEntityData.DataValue<Byte> dataValue = new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, flags);
            ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(target.getEntityId(), List.of(dataValue));
            sp.connection.send(packet);
        } catch (Throwable ignored) {
        }
    }
}

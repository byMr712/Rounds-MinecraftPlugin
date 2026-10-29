package com.rounds.nms.impl;

import com.rounds.nms.NmsPlayerHandle;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.entity.Player;

/**
 * MC 26.2 implementation of the NMS player facade.
 */
public class NmsPlayerHandleImpl implements NmsPlayerHandle {

    private final ServerPlayer sp;

    public NmsPlayerHandleImpl(ServerPlayer sp) {
        this.sp = sp;
    }

    ServerPlayer nmsPlayer() {
        return sp;
    }

    @Override
    public Player getBukkitEntity() {
        return sp.getBukkitEntity();
    }

    @Override
    public void setRot(float yaw, float pitch) {
        sp.setRot(yaw, pitch);
    }

    @Override
    public void setYHeadRot(float yaw) {
        sp.setYHeadRot(yaw);
    }

    @Override
    public float getYHeadRot() {
        return sp.getYHeadRot();
    }

    @Override
    public float getYBodyRot() {
        return sp.yBodyRot;
    }

    @Override
    public void setYBodyRot(float value) {
        sp.setYBodyRot(value);
    }

    @Override
    public boolean isOnGround() {
        return sp.onGround();
    }

    @Override
    public boolean isHorizontalCollision() {
        return sp.horizontalCollision;
    }

    @Override
    public boolean isVerticalCollision() {
        return sp.verticalCollision;
    }

    @Override
    public boolean isVerticalCollisionBelow() {
        return sp.verticalCollisionBelow;
    }

    @Override
    public double getDeltaX() {
        return sp.getDeltaMovement().x;
    }

    @Override
    public double getDeltaY() {
        return sp.getDeltaMovement().y;
    }

    @Override
    public double getDeltaZ() {
        return sp.getDeltaMovement().z;
    }

    @Override
    public void clearDeltaMovement() {
        sp.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    @Override
    public void move(double dx, double dy, double dz) {
        sp.move(net.minecraft.world.entity.MoverType.SELF, new net.minecraft.world.phys.Vec3(dx, dy, dz));
    }
}

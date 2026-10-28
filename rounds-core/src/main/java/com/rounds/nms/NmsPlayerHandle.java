package com.rounds.nms;

import org.bukkit.entity.Player;

/**
 * Version-stable facade over a native NMS player entity (ServerPlayer / EntityPlayer).
 * Implemented per Minecraft version inside rounds-nms* modules.
 * All calls must be cheap; this interface sits in the per-tick bot movement path.
 */
public interface NmsPlayerHandle {

    Player getBukkitEntity();

    // --- Rotation ---
    void setRot(float yaw, float pitch) ;
    void setYHeadRot(float yaw);
    float getYHeadRot();
    float getYBodyRot();
    void setYBodyRot(float value);

    // --- Physics flags ---
    boolean isOnGround();
    boolean isHorizontalCollision();
    boolean isVerticalCollision();
    boolean isVerticalCollisionBelow();

    // --- Movement ---
    double getDeltaX();
    double getDeltaY();
    double getDeltaZ();
    void clearDeltaMovement();
    void move(double dx, double dy, double dz);
}

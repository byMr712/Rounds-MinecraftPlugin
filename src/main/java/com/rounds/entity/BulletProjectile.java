package com.rounds.entity;

import org.bukkit.entity.Arrow;

import java.util.UUID;

public class BulletProjectile {

    private final Arrow arrow;
    private final UUID ownerId;

    public BulletProjectile(Arrow arrow, UUID ownerId) {
        this.arrow = arrow;
        this.ownerId = ownerId;
    }

    public Arrow getArrow() { return arrow; }
    public UUID getOwnerId() { return ownerId; }
}

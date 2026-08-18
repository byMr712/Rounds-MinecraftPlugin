package com.rounds.entity;

import com.rounds.player.PlayerData;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.ItemDisplay;

import java.util.UUID;

public class BulletProjectile {

    private final Arrow arrow;
    private final UUID ownerId;
    private final ItemDisplay display;

    public BulletProjectile(Arrow arrow, UUID ownerId, ItemDisplay display) {
        this.arrow = arrow;
        this.ownerId = ownerId;
        this.display = display;
    }

    public Arrow getArrow() { return arrow; }
    public UUID getOwnerId() { return ownerId; }
    public ItemDisplay getDisplay() { return display; }
}

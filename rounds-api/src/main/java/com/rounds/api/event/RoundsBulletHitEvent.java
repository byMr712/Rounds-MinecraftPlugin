package com.rounds.api.event;

import com.rounds.api.entity.RoundsBullet;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.Optional;

public class RoundsBulletHitEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    public enum HitType {
        ENTITY,
        BLOCK
    }

    private final RoundsBullet bullet;
    private final HitType hitType;
    private final Entity hitEntity;
    private final Block hitBlock;
    private final Location hitLocation;
    private double damage;
    private Vector knockback;
    private boolean cancelled = false;

    public RoundsBulletHitEvent(RoundsBullet bullet, Entity entity, Location loc, double damage) {
        this.bullet = Objects.requireNonNull(bullet, "Bullet cannot be null");
        this.hitType = HitType.ENTITY;
        this.hitEntity = entity;
        this.hitBlock = null;
        this.hitLocation = Objects.requireNonNull(loc, "Hit location cannot be null");
        this.damage = damage;
    }

    public RoundsBulletHitEvent(RoundsBullet bullet, Block block, Location loc) {
        this.bullet = Objects.requireNonNull(bullet, "Bullet cannot be null");
        this.hitType = HitType.BLOCK;
        this.hitEntity = null;
        this.hitBlock = block;
        this.hitLocation = Objects.requireNonNull(loc, "Hit location cannot be null");
        this.damage = 0.0;
    }

    public RoundsBullet getBullet() { return bullet; }
    public HitType getHitType() { return hitType; }

    public Optional<Entity> getHitEntity() { return Optional.ofNullable(hitEntity); }
    public Optional<Block> getHitBlock() { return Optional.ofNullable(hitBlock); }
    public Location getHitLocation() { return hitLocation.clone(); }

    public double getDamage() { return damage; }
    public void setDamage(double damage) { this.damage = Math.max(0.0, damage); }

    public Vector getKnockback() { return knockback != null ? knockback.clone() : new Vector(); }
    public void setKnockback(Vector knockback) { this.knockback = knockback; }

    @Override
    public boolean isCancelled() { return cancelled; }
    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

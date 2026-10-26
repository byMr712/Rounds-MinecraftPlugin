package com.rounds.api.event;

import com.rounds.api.data.CustomDataContainer;
import com.rounds.api.entity.RoundsBullet;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class RoundsWeaponShootEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final RoundsBullet primaryBullet;
    private final List<RoundsBullet> initialBullets;
    private final List<Consumer<RoundsBullet>> bulletConfigurators = new ArrayList<>();
    private final CustomDataContainer customData;
    private int bulletCount;
    private double spread;
    private double speedMultiplier = 1.0;
    private boolean cancelled = false;

    public RoundsWeaponShootEvent(Player player, RoundsBullet primary, List<RoundsBullet> all, CustomDataContainer data, int count, double spread) {
        this.player = Objects.requireNonNull(player, "Player cannot be null");
        this.primaryBullet = Objects.requireNonNull(primary, "Primary bullet cannot be null");
        this.initialBullets = (all == null || all.isEmpty()) ? List.of(primary) : all;
        this.customData = Objects.requireNonNull(data, "CustomData cannot be null");
        this.bulletCount = count;
        this.spread = spread;
    }

    public Player getPlayer() { return player; }
    public RoundsBullet getPrimaryBullet() { return primaryBullet; }
    public List<RoundsBullet> getBullets() { return Collections.unmodifiableList(initialBullets); }
    public CustomDataContainer getCustomData() { return customData; }

    public void configureBullets(Consumer<RoundsBullet> action) {
        Objects.requireNonNull(action, "Action не может быть null");
        bulletConfigurators.add(action);
        for (RoundsBullet b : initialBullets) {
            action.accept(b);
        }
    }

    public List<Consumer<RoundsBullet>> getBulletConfigurators() {
        return Collections.unmodifiableList(bulletConfigurators);
    }

    public boolean addBulletTag(String tag) {
        Objects.requireNonNull(tag, "Tag не может быть null");
        configureBullets(b -> b.addTag(tag));
        boolean anyAdded = false;
        for (RoundsBullet b : initialBullets) {
            if (b.hasTag(tag)) anyAdded = true;
        }
        return anyAdded;
    }

    public int getBulletCount() { return bulletCount; }
    public void setBulletCount(int bulletCount) { this.bulletCount = Math.max(1, bulletCount); }

    public double getSpread() { return spread; }
    public void setSpread(double spread) { this.spread = Math.max(0.0, spread); }

    public double getSpeedMultiplier() { return speedMultiplier; }
    public void setSpeedMultiplier(double speedMultiplier) { this.speedMultiplier = Math.max(0.1, speedMultiplier); }

    @Override
    public boolean isCancelled() { return cancelled; }
    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

package com.rounds.api.event;

import com.rounds.api.entity.RoundsBullet;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.Optional;

public class RoundsShieldBlockEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Player attacker;
    private final RoundsBullet bullet;
    private double damage;
    private boolean shieldBypassed = false;
    private boolean parry = false;
    private boolean cancelled = false;

    public RoundsShieldBlockEvent(Player player, Player attacker, RoundsBullet bullet, double damage) {
        this.player = Objects.requireNonNull(player, "Player cannot be null");
        this.attacker = attacker;
        this.bullet = bullet;
        this.damage = Math.max(0.0, damage);
    }

    public Player getPlayer() { return player; }
    public Optional<Player> getAttacker() { return Optional.ofNullable(attacker); }
    public Optional<RoundsBullet> getBullet() { return Optional.ofNullable(bullet); }

    public double getDamage() { return damage; }
    public void setDamage(double damage) { this.damage = Math.max(0.0, damage); }

    public boolean isShieldBypassed() { return shieldBypassed; }
    public void setShieldBypassed(boolean shieldBypassed) { this.shieldBypassed = shieldBypassed; }

    public boolean isParry() { return parry; }
    public void setParry(boolean parry) { this.parry = parry; }

    @Override
    public boolean isCancelled() { return cancelled; }
    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

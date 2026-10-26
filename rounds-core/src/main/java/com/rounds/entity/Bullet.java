package com.rounds.entity;

import com.rounds.api.entity.RoundsBullet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public final class Bullet implements RoundsBullet {
    private final UUID bulletId = UUID.randomUUID();
    private final Set<String> tags = new LinkedHashSet<>();

    UUID ownerId;
    final double damage;
    final int maxBounce;
    final double scale;
    double homing;
    final double tgBounce;
    final double drill;
    final double sneaky;
    final Location spawnLoc;
    Location loc;
    Vector velocity;
    LivingEntity homingTarget;
    int bounceCount;
    int ticksLived;

    // Сколько логических пуль представляет этот репрезентатив (залпы > капа).
    int stack = 1;
    // Полный радиус попадания от осевой линии цели (растёт со стаками крупной пули).
    double hitRadius = RoundsEntities.HIT_RADIUS;
    // Id залпа: пули одной когорты делят общий запрос сущностей на тик.
    long cohortId;
    boolean alive = true;

    // Транзиентные данные фазы полёта: заполняются в предшаге, читаются в резолве.
    double sX, sY, sZ, eX, eY, eZ;
    RayTraceResult wallHit;
    final Vector rayDir = new Vector();

    Bullet(UUID ownerId, double damage, int maxBounce, double scale,
           double homing, double tgBounce, double drill, double sneaky,
           Location spawnLoc, Location loc, Vector velocity) {
        this.ownerId = ownerId;
        this.damage = damage;
        this.maxBounce = maxBounce;
        this.scale = scale;
        this.homing = homing;
        this.tgBounce = tgBounce;
        this.drill = drill;
        this.sneaky = sneaky;
        this.spawnLoc = spawnLoc;
        this.loc = loc;
        this.velocity = velocity;
    }

    @Override
    public UUID getBulletId() {
        return bulletId;
    }

    @Override
    public UUID getShooterId() {
        return ownerId;
    }

    @Override
    public Optional<Player> getShooter() {
        if (ownerId == null) return Optional.empty();
        return Optional.ofNullable(Bukkit.getPlayer(ownerId));
    }

    @Override
    public Location getCurrentLocation() {
        return loc != null ? loc.clone() : spawnLoc.clone();
    }

    @Override
    public Vector getVelocity() {
        return velocity != null ? velocity.clone() : new Vector();
    }

    @Override
    public void setVelocity(Vector velocity) {
        if (velocity != null) {
            this.velocity = velocity.clone();
        }
    }

    @Override
    public void addTag(String tag) {
        if (tag == null) return;
        if (tags.size() >= MAX_TAGS) return;
        if (tag.length() > MAX_TAG_LENGTH) {
            tag = tag.substring(0, MAX_TAG_LENGTH);
        }
        tags.add(tag);
    }

    @Override
    public boolean hasTag(String tag) {
        return tag != null && tags.contains(tag);
    }

    @Override
    public void removeTag(String tag) {
        if (tag != null) {
            tags.remove(tag);
        }
    }

    @Override
    public Set<String> getTags() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    }

    @Override
    public boolean isBouncing() {
        return bounceCount > 0;
    }

    @Override
    public void setBouncing(boolean bouncing) {
        if (!bouncing) {
            bounceCount = maxBounce;
        } else if (bounceCount >= maxBounce) {
            bounceCount = 0;
        }
    }

    @Override
    public int getRemainingBounces() {
        return Math.max(0, maxBounce - bounceCount);
    }

    @Override
    public void setRemainingBounces(int bounces) {
        bounceCount = Math.max(0, maxBounce - bounces);
    }
}

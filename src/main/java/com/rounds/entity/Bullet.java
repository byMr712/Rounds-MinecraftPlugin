package com.rounds.entity;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;

final class Bullet {
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
}

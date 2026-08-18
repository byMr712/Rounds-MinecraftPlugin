package com.rounds.entity;

import com.rounds.RoundsKeys;
import com.rounds.RoundsPlugin;
import com.rounds.item.GunItem;
import com.rounds.player.PlayerData;
import com.rounds.teams.TeamManager.GameTeam;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class RoundsEntities implements Listener {

    private static RoundsPlugin plugin;
    private static final Map<UUID, Integer> bounceCounters = new HashMap<>();

    public static void register(RoundsPlugin pl) {
        plugin = pl;
    }

    public static BulletProjectile spawnBullet(Player shooter, Location loc, Vector velocity, PlayerData data) {
        double scale = data.bigBullet > 0 ? 2.0 : 1.0;

        Arrow arrow = shooter.getWorld().spawn(loc, Arrow.class, a -> {
            a.setShooter(shooter);
            a.setVelocity(velocity);
            a.setDamage(0);
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            a.setLifetimeTicks(100);
            a.setSilent(true);

            PersistentDataContainer pdc = a.getPersistentDataContainer();
            pdc.set(RoundsKeys.IS_BULLET, PersistentDataType.BYTE, (byte) 1);
            pdc.set(RoundsKeys.BULLET_OWNER, PersistentDataType.STRING, shooter.getUniqueId().toString());
            pdc.set(RoundsKeys.BULLET_DAMAGE, PersistentDataType.DOUBLE, data.getEffectiveDamage());
            pdc.set(RoundsKeys.BULLET_BOUNCE, PersistentDataType.INTEGER, (int) data.bouncePl);
            pdc.set(RoundsKeys.BULLET_SCALE, PersistentDataType.DOUBLE, scale);
            pdc.set(RoundsKeys.BULLET_HOMING, PersistentDataType.DOUBLE, data.homing);
            if (data.drill > 0) {
                pdc.set(RoundsKeys.BULLET_DRILL, PersistentDataType.DOUBLE, data.drill);
            }
        });

        ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.ARROW));
            d.setViewRange(0f);
            d.getPersistentDataContainer().set(RoundsKeys.IS_BULLET, PersistentDataType.BYTE, (byte) 1);
        });

        int lifetime = arrow.getLifetimeTicks();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (display.isValid()) display.remove();
            }
        }.runTaskLater(plugin, lifetime + 5);

        if (data.homing > 0) {
            startHomingTask(arrow, display);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (arrow.isDead() || !arrow.isValid()) {
                        display.remove();
                        cancel();
                        return;
                    }
                    display.teleport(arrow.getLocation());
                    display.setVelocity(arrow.getVelocity());
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }

        return new BulletProjectile(arrow, shooter.getUniqueId(), display);
    }

    public static void bounceBullet(Arrow oldArrow, Vector reflectedVelocity) {
        PersistentDataContainer pdc = oldArrow.getPersistentDataContainer();
        if (!pdc.has(RoundsKeys.IS_BULLET, PersistentDataType.BYTE)) return;

        UUID ownerId = UUID.fromString(pdc.getOrDefault(RoundsKeys.BULLET_OWNER, PersistentDataType.STRING, ""));
        double damage = pdc.getOrDefault(RoundsKeys.BULLET_DAMAGE, PersistentDataType.DOUBLE, 1.0);
        int maxBounce = pdc.getOrDefault(RoundsKeys.BULLET_BOUNCE, PersistentDataType.INTEGER, 0);
        double scale = pdc.getOrDefault(RoundsKeys.BULLET_SCALE, PersistentDataType.DOUBLE, 1.0);
        double homing = pdc.getOrDefault(RoundsKeys.BULLET_HOMING, PersistentDataType.DOUBLE, 0.0);
        Double drill = pdc.get(RoundsKeys.BULLET_DRILL, PersistentDataType.DOUBLE);

        int oldBounce = bounceCounters.getOrDefault(oldArrow.getUniqueId(), 0);
        bounceCounters.remove(oldArrow.getUniqueId());

        double speed = reflectedVelocity.length() * 0.6;
        Vector newVel = reflectedVelocity.lengthSquared() > 0.001
            ? reflectedVelocity.normalize().multiply(speed)
            : reflectedVelocity;

        Location newLoc = oldArrow.getLocation().add(newVel.clone().normalize().multiply(0.5));
        Player shooter = oldArrow.getShooter() instanceof Player p ? p : null;
        org.bukkit.projectiles.ProjectileSource originalShooter = oldArrow.getShooter();

        removeDisplayNear(oldArrow.getLocation());
        oldArrow.remove();

        Arrow newArrow = newLoc.getWorld().spawn(newLoc, Arrow.class, a -> {
            a.setShooter(shooter != null ? shooter : originalShooter);
            a.setVelocity(newVel);
            a.setDamage(0);
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            a.setLifetimeTicks(100);
            a.setSilent(true);

            PersistentDataContainer newPdc = a.getPersistentDataContainer();
            newPdc.set(RoundsKeys.IS_BULLET, PersistentDataType.BYTE, (byte) 1);
            newPdc.set(RoundsKeys.BULLET_OWNER, PersistentDataType.STRING, ownerId.toString());
            newPdc.set(RoundsKeys.BULLET_DAMAGE, PersistentDataType.DOUBLE, damage);
            newPdc.set(RoundsKeys.BULLET_BOUNCE, PersistentDataType.INTEGER, maxBounce);
            newPdc.set(RoundsKeys.BULLET_SCALE, PersistentDataType.DOUBLE, scale);
            newPdc.set(RoundsKeys.BULLET_HOMING, PersistentDataType.DOUBLE, homing);
            if (drill != null && drill > 0) {
                newPdc.set(RoundsKeys.BULLET_DRILL, PersistentDataType.DOUBLE, drill);
            }
        });

        bounceCounters.put(newArrow.getUniqueId(), oldBounce + 1);

        ItemDisplay display = newLoc.getWorld().spawn(newLoc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.ARROW));
            d.setViewRange(0f);
            d.getPersistentDataContainer().set(RoundsKeys.IS_BULLET, PersistentDataType.BYTE, (byte) 1);
        });

        int lifetime = newArrow.getLifetimeTicks();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (display.isValid()) display.remove();
            }
        }.runTaskLater(plugin, lifetime + 5);

        if (homing > 0) {
            startHomingTask(newArrow, display);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (newArrow.isDead() || !newArrow.isValid()) {
                        display.remove();
                        cancel();
                        return;
                    }
                    display.teleport(newArrow.getLocation());
                    display.setVelocity(newArrow.getVelocity());
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }
    }

    private static void startHomingTask(Arrow arrow, ItemDisplay display) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (arrow.isDead() || !arrow.isValid()) {
                    display.remove();
                    cancel();
                    return;
                }

                display.teleport(arrow.getLocation());
                display.setVelocity(arrow.getVelocity());

                PersistentDataContainer pdc = arrow.getPersistentDataContainer();
                UUID ownerId = UUID.fromString(pdc.getOrDefault(RoundsKeys.BULLET_OWNER, PersistentDataType.STRING, ""));
                Double homingVal = pdc.get(RoundsKeys.BULLET_HOMING, PersistentDataType.DOUBLE);
                double homingStrength = homingVal != null ? homingVal : 0.0;
                if (homingStrength <= 0) {
                    return;
                }

                LivingEntity nearest = findNearestEnemy(arrow, ownerId);
                if (nearest != null) {
                    Vector toTarget = nearest.getEyeLocation().toVector().subtract(arrow.getLocation().toVector());
                    double dist = toTarget.length();
                    if (dist > 0 && dist < 20) {
                        Vector current = arrow.getVelocity();
                        Vector guided = current.clone().add(toTarget.normalize().multiply(homingStrength * 0.3));
                        double speed = current.length();
                        if (guided.length() > 0) {
                            arrow.setVelocity(guided.normalize().multiply(speed));
                        }
                    }
                }

                display.teleport(arrow.getLocation());
                display.setVelocity(arrow.getVelocity());
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static LivingEntity findNearestEnemy(Arrow arrow, UUID ownerId) {
        double closest = Double.MAX_VALUE;
        LivingEntity nearest = null;
        GameTeam ownerTeam = plugin.getTeamManager().getPlayerTeam(ownerId);
        for (Entity entity : arrow.getNearbyEntities(30, 30, 30)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity.getUniqueId().equals(ownerId)) continue;
            if (entity instanceof org.bukkit.entity.ArmorStand) continue;
            if (entity instanceof org.bukkit.entity.Player p) {
                GameTeam targetTeam = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
                if (ownerTeam != null && targetTeam != null && ownerTeam == targetTeam) continue;
            }
            double dist = living.getLocation().distanceSquared(arrow.getLocation());
            if (dist < closest) {
                closest = dist;
                nearest = living;
            }
        }
        return nearest;
    }

    public static void spawnBomb(Location loc, UUID owner) {
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 1);
        for (var entity : loc.getNearbyLivingEntities(5.0)) {
            if (entity.getUniqueId().equals(owner)) continue;
            double dist = entity.getLocation().distance(loc);
            if (dist > 5.0) continue;
            double dmg = Math.max(0, 8.0 * (1.0 - dist / 5.0));
            if (dmg > 0) {
                entity.damage(dmg);
            }
        }
    }

    public static void spawnHealRing(Location loc, UUID owner) {
        loc.getWorld().spawn(loc, AreaEffectCloud.class, aec -> {
            aec.setRadius(3.0f);
            aec.setRadiusOnUse(0);
            aec.setRadiusPerTick(0);
            aec.setDuration(200);
            aec.addCustomEffect(new org.bukkit.potion.PotionEffect(
                PotionEffectType.REGENERATION, 100, 1), true);
            aec.getPersistentDataContainer().set(RoundsKeys.IS_HEAL_RING, PersistentDataType.BYTE, (byte) 1);
        });
    }

    public static void spawnToxicRing(Location loc, UUID owner) {
        loc.getWorld().spawn(loc, AreaEffectCloud.class, aec -> {
            aec.setRadius(3.0f);
            aec.setRadiusOnUse(0);
            aec.setRadiusPerTick(0);
            aec.setDuration(200);
            aec.addCustomEffect(new org.bukkit.potion.PotionEffect(
                PotionEffectType.POISON, 100, 2), true);
            aec.getPersistentDataContainer().set(RoundsKeys.IS_TOXIC_RING, PersistentDataType.BYTE, (byte) 1);
        });
    }

    public static void spawnBombShield(Location loc, UUID owner) {
        loc.getWorld().createExplosion(loc, 2.0f, false, false);
        for (var entity : loc.getNearbyLivingEntities(2.0)) {
            if (entity.getUniqueId().equals(owner)) continue;
            double dist = entity.getLocation().distance(loc);
            double dmg = Math.max(0, 5.0 * (1.0 - dist / 2.0));
            if (dmg > 0) entity.damage(dmg);
        }
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        if (!pdc.has(RoundsKeys.IS_BULLET, PersistentDataType.BYTE)) return;

        UUID ownerId = UUID.fromString(pdc.getOrDefault(RoundsKeys.BULLET_OWNER, PersistentDataType.STRING, ""));
        double damage = pdc.getOrDefault(RoundsKeys.BULLET_DAMAGE, PersistentDataType.DOUBLE, 1.0);
        int maxBounce = pdc.getOrDefault(RoundsKeys.BULLET_BOUNCE, PersistentDataType.INTEGER, 0);

        Entity hitEntity = event.getHitEntity();

        if (hitEntity != null && GunItem.isShield(hitEntity)) {
            UUID shieldOwner = GunItem.getShieldOwner(hitEntity);
            if (shieldOwner != null && shieldOwner.equals(ownerId)) {
                event.setCancelled(true);
                Vector vel = arrow.getVelocity();
                if (vel.lengthSquared() > 0.01) {
                    Location newLoc = arrow.getLocation().add(vel.clone().normalize().multiply(1.5));
                    arrow.teleport(newLoc);
                    arrow.setVelocity(vel);
                }
                return;
            }
            if (shieldOwner != null && !shieldOwner.equals(ownerId)) {
                pdc.set(RoundsKeys.BULLET_OWNER, PersistentDataType.STRING, shieldOwner.toString());

                Player shieldPlayer = plugin.getServer().getPlayer(shieldOwner);
                if (shieldPlayer != null) {
                    Vector toShooter = arrow.getLocation().toVector()
                        .subtract(shieldPlayer.getLocation().toVector()).normalize().multiply(3.0);
                    arrow.setVelocity(toShooter);
                    arrow.setShooter(shieldPlayer);
                } else {
                    arrow.setVelocity(arrow.getVelocity().multiply(-1));
                }

                arrow.setPierceLevel((byte) 0);
                bounceCounters.remove(arrow.getUniqueId());

                if (shieldPlayer != null) {
                    shieldPlayer.getWorld().playSound(shieldPlayer.getLocation(),
                        Sound.BLOCK_ANVIL_USE, 0.6f, 2.0f);
                }

                hitEntity.remove();
                return;
            }
        }

        if (hitEntity != null && hitEntity instanceof LivingEntity living && !living.getUniqueId().equals(ownerId)) {
            PlayerData data = plugin.getPlayerDataManager().getData(ownerId);
            double finalDamage = damage * 2.0;
            if (data != null) {
                int bounceCount = bounceCounters.getOrDefault(arrow.getUniqueId(), 0);
                if (bounceCount > 0 && data.damagePerBounce > 0) {
                    finalDamage += bounceCount * data.damagePerBounce;
                }
                if (data.trusterLvl > 0) {
                    Vector knockback = arrow.getVelocity().normalize().multiply(data.trusterLvl * 0.5);
                    living.setVelocity(living.getVelocity().add(knockback));
                }
                if (data.overpower > 0) {
                    finalDamage *= (1.0 + data.overpower * 0.2);
                }
            }
            living.damage(finalDamage);
            living.getWorld().playSound(living.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);

            if (data != null) {
                if (data.stun > 0 && living instanceof Player stunTarget) {
                    stunTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.BLINDNESS, 30, 0));
                    stunTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.SLOW, 30, 2));
                    stunTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.CONFUSION, 40, 0));
                }
                if (data.splash > 0 && arrow.getShooter() instanceof Player splashShooter) {
                    double splashRadius = 2.0 + data.splash;
                    for (Entity entity : living.getNearbyEntities(splashRadius, splashRadius, splashRadius)) {
                        if (entity instanceof LivingEntity splashTarget && !splashTarget.getUniqueId().equals(ownerId)) {
                            double splashDmg = finalDamage * 0.5;
                            splashTarget.damage(splashDmg);
                        }
                    }
                    living.getWorld().playSound(living.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f);
                }
                if (data.shockwave > 0) {
                    for (Entity entity : living.getNearbyEntities(5.0, 5.0, 5.0)) {
                        if (entity instanceof LivingEntity shockTarget && !shockTarget.getUniqueId().equals(ownerId)) {
                            Vector push = shockTarget.getLocation().toVector()
                                .subtract(living.getLocation().toVector()).normalize().multiply(data.shockwave * 0.8);
                            push.setY(0.5);
                            shockTarget.setVelocity(shockTarget.getVelocity().add(push));
                        }
                    }
                }
                if (data.radiance > 0 && living instanceof Player radTarget) {
                    radTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.GLOWING, 100, 0));
                }
                if (data.poison > 0) {
                    living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.POISON, 60, (int) Math.max(data.poisonLvl, 1)));
                }
                if (data.cold > 0) {
                    living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.SLOW, 100, (int) Math.max(data.coldLvl, 1)));
                }
                if (data.parazit > 0) {
                    living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.WITHER, 40, (int) Math.max(data.parazitLvl, 1)));
                }
                if (data.leech > 0 && arrow.getShooter() instanceof Player shooter) {
                    double heal = data.leech * 0.5;
                    shooter.setHealth(Math.min(shooter.getHealth() + heal, shooter.getMaxHealth()));
                }
                if (data.speedBoost > 0 && arrow.getShooter() instanceof Player speedShooter) {
                    speedShooter.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.SPEED, 100, 0));
                }
                if (data.hpBoostOnHit > 0 && arrow.getShooter() instanceof Player hpShooter) {
                    PlayerData shooterData = plugin.getPlayerDataManager().getData(hpShooter);
                    double baseMaxHP = hpShooter.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    double boost = baseMaxHP * data.hpBoostOnHit;
                    hpShooter.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(baseMaxHP + boost);
                    hpShooter.setHealth(Math.min(hpShooter.getHealth() + boost, baseMaxHP + boost));
                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                        hpShooter.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(baseMaxHP);
                        hpShooter.setHealth(Math.min(hpShooter.getHealth(), baseMaxHP));
                    }, 40L);
                }
                if (data.silence > 0 && living instanceof Player silenceTarget) {
                    GunItem.silencePlayer(silenceTarget.getUniqueId());
                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                        GunItem.unsilencePlayer(silenceTarget.getUniqueId());
                    }, (long) (data.silence * 40));
                }
                if (data.ammoPerHit > 0 && arrow.getShooter() instanceof Player hitShooter) {
                    data.ammo = Math.min(data.ammo + data.ammoPerHit, data.maxAmmo);
                }
                if (data.refresh > 0 && arrow.getShooter() instanceof Player refreshShooter) {
                    GunItem.resetBlockCooldown(refreshShooter.getUniqueId());
                }
                if (data.bombBullet > 0) {
                    spawnBomb(living.getLocation(), ownerId);
                }
            }

            arrow.remove();
            bounceCounters.remove(arrow.getUniqueId());
            removeDisplayNear(arrow.getLocation());
            return;
        }

        if (event.getHitBlock() != null) {
            int currentBounce = bounceCounters.getOrDefault(arrow.getUniqueId(), 0);
            if (maxBounce > 0 && currentBounce < maxBounce) {
                event.setCancelled(true);
                Vector vel = arrow.getVelocity();
                org.bukkit.block.BlockFace face = event.getHitBlockFace();
                if (face != null && vel.lengthSquared() > 0.01) {
                    Vector normal = face.getDirection();
                    Vector reflected = vel.subtract(normal.multiply(2 * vel.dot(normal)));
                    bounceBullet(arrow, reflected);
                }
                return;
            }
            Double drillVal = pdc.get(RoundsKeys.BULLET_DRILL, PersistentDataType.DOUBLE);
            if (drillVal != null && drillVal > 0) {
                event.setCancelled(true);
                Vector vel = arrow.getVelocity();
                if (vel.lengthSquared() > 0.01) {
                    Location newLoc = arrow.getLocation().add(vel.clone().normalize().multiply(2.0));
                    arrow.teleport(newLoc);
                    arrow.setVelocity(vel);
                }
                return;
            }
            arrow.remove();
            bounceCounters.remove(arrow.getUniqueId());
            removeDisplayNear(arrow.getLocation());
            return;
        }
    }

    private static Vector reflectVelocity(Vector velocity, org.bukkit.block.BlockFace face) {
        Vector normal = face.getDirection();
        return velocity.subtract(normal.multiply(2 * velocity.dot(normal)));
    }

    private static void removeDisplayNear(Location loc) {
        for (var entity : loc.getNearbyEntities(1, 1, 1)) {
            if (entity instanceof ItemDisplay display) {
                PersistentDataContainer pdc = display.getPersistentDataContainer();
                if (pdc.has(RoundsKeys.IS_BULLET, PersistentDataType.BYTE)) {
                    display.remove();
                }
            }
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (arrow.getPersistentDataContainer().has(RoundsKeys.IS_BULLET, PersistentDataType.BYTE)) return;
        if (arrow.getShooter() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Arrow arrow) {
            PersistentDataContainer pdc = arrow.getPersistentDataContainer();
            if (pdc.has(RoundsKeys.IS_BULLET, PersistentDataType.BYTE)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player) {
            event.setCancelled(true);
            if (GunItem.isGun(attacker.getInventory().getItemInMainHand())) {
                GunItem.getInstance().doShoot(attacker);
            }
        }
        if (event.getDamager() instanceof Player attacker && GunItem.isShield(event.getEntity())) {
            event.setCancelled(true);
            if (GunItem.isGun(attacker.getInventory().getItemInMainHand())) {
                GunItem.getInstance().doShoot(attacker);
            }
        }
    }
}

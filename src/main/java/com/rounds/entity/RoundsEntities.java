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
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import org.bukkit.Location;

public class RoundsEntities implements Listener {

    private static RoundsPlugin plugin;
    private static final Map<UUID, Integer> bounceCounters = new HashMap<>();
    private static final Map<UUID, Vector> lastBulletVelocity = new HashMap<>();
    private static final Map<UUID, Double> hpBoostPending = new HashMap<>();
    private static final Map<UUID, Integer> hpBoostTasks = new HashMap<>();
    private static final List<CloudFx> activeClouds = new ArrayList<>();
    private static int cloudTickId = -1;
    private static final Particle.DustOptions TOXIC_DUST =
            new Particle.DustOptions(Color.fromRGB(0, 200, 0), 1.5f);

    private static class CloudFx {
        final Location center;
        final double radius;
        final int duration;
        int elapsed = 0;

        CloudFx(Location center, double radius, int duration) {
            this.center = center;
            this.radius = radius;
            this.duration = duration;
        }
    }

    private static void startCloudTicker() {
        if (cloudTickId != -1) return;
        cloudTickId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Iterator<CloudFx> it = activeClouds.iterator();
            while (it.hasNext()) {
                CloudFx fx = it.next();
                if (fx.elapsed >= fx.duration) {
                    it.remove();
                    continue;
                }
                for (int i = 0; i < 4; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double r = Math.random() * fx.radius;
                    double x = fx.center.getX() + Math.cos(angle) * r;
                    double z = fx.center.getZ() + Math.sin(angle) * r;
                    Location p = new Location(fx.center.getWorld(), x,
                            fx.center.getY() + Math.random() * 0.5, z);
                    p.getWorld().spawnParticle(Particle.REDSTONE, p, 1, 0.1, 0.1, 0.1, TOXIC_DUST);
                }
                fx.elapsed += 2;
            }
            if (activeClouds.isEmpty()) {
                Bukkit.getScheduler().cancelTask(cloudTickId);
                cloudTickId = -1;
            }
        }, 0L, 2L);
    }

    private static final Map<UUID, BulletData> activeBullets = new HashMap<>();
    private static int centralizedTickId = -1;
    private static final double HIT_RADIUS = 1.0;
    private static final double HIT_RADIUS_BIG = 1.8;
    private static final int MAX_CHECKPOINTS = 5;

    public static void register(RoundsPlugin pl) {
        plugin = pl;
        startCentralizedTick();
    }

    // ==================== Cached bullet data ====================

    public static class BulletData {
        UUID ownerId;
        final double damage;
        final int maxBounce;
        final double scale;
        double homing;
        final double tgBounce;
        final double drill;
        final double sneaky;
        final String spawnLoc;
        Vector lastVelocity;
        Location prevLoc;
        LivingEntity homingTarget;

        BulletData(UUID ownerId, double damage, int maxBounce, double scale,
                   double homing, double tgBounce, double drill, double sneaky,
                   String spawnLoc, Vector velocity, Location prevLoc) {
            this.ownerId = ownerId;
            this.damage = damage;
            this.maxBounce = maxBounce;
            this.scale = scale;
            this.homing = homing;
            this.tgBounce = tgBounce;
            this.drill = drill;
            this.sneaky = sneaky;
            this.spawnLoc = spawnLoc;
            this.lastVelocity = velocity;
            this.prevLoc = prevLoc;
        }
    }

    // ==================== Centralized tick ====================

    private static void startCentralizedTick() {
        if (centralizedTickId != -1) return;
        centralizedTickId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, RoundsEntities::processAllBullets, 0L, 1L);
    }

    public static void stopCentralizedTick() {
        if (centralizedTickId != -1) {
            Bukkit.getScheduler().cancelTask(centralizedTickId);
            centralizedTickId = -1;
        }
    }

    private static void processAllBullets() {
        if (activeBullets.isEmpty()) return;
        // Обработка пули может менять activeBullets (попадание, отражение щитом,
        // рикошет) — итерируемся по снимку ключей, иначе ConcurrentModificationException.
        for (UUID arrowId : activeBullets.keySet().toArray(new UUID[0])) {
            BulletData data = activeBullets.get(arrowId);
            if (data == null) continue; // пуля уже обработана в этом тике

            Entity entity = Bukkit.getEntity(arrowId);
            if (entity == null || !(entity instanceof Arrow arrow) || !arrow.isValid() || arrow.isDead()) {
                activeBullets.remove(arrowId);
                lastBulletVelocity.remove(arrowId);
                continue;
            }

            processBulletTick(arrow, data);
        }
    }

    private static void processBulletTick(Arrow arrow, BulletData data) {
        Vector bulletVel = arrow.getVelocity();
        if (isFinite(bulletVel)) {
            data.lastVelocity = bulletVel;
            lastBulletVelocity.put(arrow.getUniqueId(), bulletVel);
        }

        if (data.homing > 0) {
            // Цель ищем не каждый тик: кэшируем и обновляем раз в 10 тиков или при потере.
            boolean targetInvalid = data.homingTarget == null || !data.homingTarget.isValid()
                    || data.homingTarget.isDead()
                    || arrow.getLocation().distance(data.homingTarget.getLocation()) > 20.0
                    || (data.homingTarget instanceof Player ht
                        && (!ht.isOnline() || ht.getGameMode() == GameMode.SPECTATOR));
            if (targetInvalid || arrow.getTicksLived() % 10 == 0) {
                data.homingTarget = findNearestEnemy(arrow, data.ownerId);
            }
            LivingEntity nearest = data.homingTarget;
            if (nearest != null) {
                Vector toTarget = nearest.getEyeLocation().toVector().subtract(arrow.getLocation().toVector());
                double dist = toTarget.length();
                if (dist < 1.5) {
                    double finalDamage = data.damage * 2.0;
                    PlayerData shooterData = plugin.getPlayerDataManager().getData(data.ownerId);
                    if (shooterData != null && shooterData.overpower > 0) {
                        finalDamage *= (1.0 + shooterData.overpower * 0.2);
                    }
                    finalDamage = applyLegendaryDamageMultipliers(shooterData, data.ownerId, nearest, finalDamage);
                    if (tryDodge(nearest)) {
                        removeBullet(arrow);
                        return;
                    }
                    nearest.setNoDamageTicks(0);
                    if (shouldExecute(shooterData, nearest)) {
                        executeTarget(nearest);
                    } else {
                        nearest.damage(finalDamage);
                    }
                    nearest.getWorld().playSound(nearest.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
                    applyPostHitEffects(data.ownerId, shooterData, nearest, finalDamage);
                    if (shooterData != null && shooterData.leech > 0) {
                        Player shooterPlayer = Bukkit.getPlayer(data.ownerId);
                        if (shooterPlayer != null && shooterPlayer.isOnline() && shooterPlayer.isValid()) {
                            double heal = Math.max(Math.ceil(finalDamage * shooterData.leech), 1);
                            shooterPlayer.setHealth(Math.min(shooterPlayer.getHealth() + heal, shooterPlayer.getMaxHealth()));
                        }
                    }
                    arrow.remove();
                    activeBullets.remove(arrow.getUniqueId());
                    lastBulletVelocity.remove(arrow.getUniqueId());
                    bounceCounters.remove(arrow.getUniqueId());
                    return;
                }
                if (dist > 0 && dist < 20) {
                    Vector current = arrow.getVelocity();
                    Vector guided = current.clone().add(toTarget.normalize().multiply(data.homing * 0.3));
                    double speed = current.length();
                    if (guided.length() > 0) {
                        arrow.setVelocity(guided.normalize().multiply(speed));
                    }
                }
            }
        }

        Location aLoc = arrow.getLocation();
        Vector traveled = aLoc.toVector().subtract(data.prevLoc.toVector());
        double dist = traveled.length();

        double hitRadius = data.scale > 1.0 ? HIT_RADIUS_BIG : HIT_RADIUS;

        // Один запрос по охватывающему bbox всего отрезка полёта вместо чекпоинт-цикла.
        Vector segStart = data.prevLoc.toVector();
        Vector segEnd = aLoc.toVector();
        org.bukkit.util.BoundingBox sweepBox = org.bukkit.util.BoundingBox.of(data.prevLoc, aLoc)
                .expand(hitRadius + 0.5);
        List<Entity> candidates = new ArrayList<>();
        for (Entity entity : aLoc.getWorld().getNearbyEntities(sweepBox)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity.getUniqueId().equals(data.ownerId)) continue;
            if (entity instanceof org.bukkit.entity.ArmorStand) continue;
            candidates.add(living);
        }
        // Ближайший к стрелку по траектории хит обрабатывается первым.
        candidates.sort(Comparator.comparingDouble(e ->
                projectionT(segStart, segEnd, e.getLocation().toVector())));

        boolean reflected = false;
        for (Entity entity : candidates) {
            LivingEntity living = (LivingEntity) entity;
            Vector hitPos = closestPointOnSegment(segStart, segEnd, living.getLocation().toVector());
            double segmentDist = hitPos.distance(living.getLocation().toVector()) - 0.5;
            if (segmentDist > hitRadius) continue;

            if (living instanceof Player p) {
                if (!plugin.getGameManager().isTargetable(p)) continue;
                GameTeam ownerTeam = plugin.getTeamManager().getPlayerTeam(data.ownerId);
                GameTeam targetTeam = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
                if (ownerTeam != null && targetTeam != null && ownerTeam == targetTeam) continue;
                if (GunItem.isShieldActive(p.getUniqueId())) {
                    reflectBulletFromPlayer(arrow, p, arrow.getPersistentDataContainer());
                    BulletData reflectedData = activeBullets.get(arrow.getUniqueId());
                    if (reflectedData != null) {
                        reflectedData.ownerId = p.getUniqueId();
                    }
                    reflected = true;
                    break;
                }
            }
            if (!arrow.isValid()) return;
            if (!arrow.getPersistentDataContainer().has(RoundsKeys.IS_BULLET, PersistentDataType.BYTE)) return;

            double finalDmg = data.damage * 2.0;
            PlayerData shooterData = plugin.getPlayerDataManager().getData(data.ownerId);
            if (shooterData != null) {
                if (!data.spawnLoc.isEmpty() && shooterData.grow > 0) {
                    try {
                        String[] parts = data.spawnLoc.split(",");
                        Location sLoc = new Location(
                            Bukkit.getWorld(parts[0]),
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3]));
                        double d = sLoc.toVector().distance(hitPos);
                        double growMult = 1.0 + Math.min(d * 0.1 * shooterData.grow, 2.0);
                        finalDmg *= growMult;
                    } catch (Exception ignored) {}
                }
                    int bounces = bounceCounters.getOrDefault(arrow.getUniqueId(), 0);
                    if (bounces > 0 && shooterData.damagePerBounce > 0) {
                        finalDmg *= (1.0 + bounces * shooterData.damagePerBounce);
                    }
                    if (shooterData.overpower > 0) {
                        finalDmg *= (1.0 + shooterData.overpower * 0.2);
                    }
                }
                finalDmg = applyLegendaryDamageMultipliers(shooterData, data.ownerId, living, finalDmg);
                if (tryDodge(living)) {
                    removeBullet(arrow);
                    return;
                }
                living.setNoDamageTicks(0);
                if (shouldExecute(shooterData, living)) {
                    executeTarget(living);
                } else {
                    living.damage(finalDmg);
                }
                living.getWorld().playSound(living.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
                applyPostHitEffects(data.ownerId, shooterData, living, finalDmg);
                if (shooterData != null) {
                    if (shooterData.trusterLvl > 0) {
                        safeKnockback(living, arrow.getVelocity(), shooterData.trusterLvl * 3.0);
                    }
                    if (shooterData.stun > 0 && living instanceof Player stunTarget) {
                        stunTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            PotionEffectType.BLINDNESS, 40, 0));
                        stunTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            PotionEffectType.SLOW, 40, 2));
                        stunTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            PotionEffectType.CONFUSION, 40, 0));
                    }
                    if (shooterData.toxicCloud > 0) {
                        spawnToxicCloud(living.getLocation(), data.ownerId, shooterData);
                    }
                    if (shooterData.bombBullet > 0) {
                        spawnBomb(living.getLocation(), data.ownerId, shooterData.getEffectiveDamage() * 0.3);
                    }
                    if (shooterData.poison > 0) {
                        living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            PotionEffectType.POISON, 60, (int) Math.max(shooterData.poisonLvl, 1)));
                    }
                    if (shooterData.leech > 0) {
                        Player shooterPlayer = Bukkit.getPlayer(data.ownerId);
                        if (shooterPlayer != null && shooterPlayer.isOnline() && shooterPlayer.isValid()) {
                            double heal = Math.max(Math.ceil(finalDmg * shooterData.leech), 1);
                            shooterPlayer.setHealth(Math.min(shooterPlayer.getHealth() + heal, shooterPlayer.getMaxHealth()));
                        }
                    }
                }
                arrow.remove();
                activeBullets.remove(arrow.getUniqueId());
                bounceCounters.remove(arrow.getUniqueId());
                lastBulletVelocity.remove(arrow.getUniqueId());
                return;
        }
        data.prevLoc = aLoc.clone();
    }

    private static double projectionT(Vector a, Vector b, Vector p) {
        Vector ab = b.clone().subtract(a);
        double lenSq = ab.lengthSquared();
        if (lenSq < 1.0E-8) return 0.0;
        return p.clone().subtract(a).dot(ab) / lenSq;
    }

    private static Vector closestPointOnSegment(Vector a, Vector b, Vector p) {
        Vector ab = b.clone().subtract(a);
        double lenSq = ab.lengthSquared();
        if (lenSq < 1.0E-8) return a.clone();
        double t = Math.max(0.0, Math.min(1.0, p.clone().subtract(a).dot(ab) / lenSq));
        return a.clone().add(ab.multiply(t));
    }

    // ==================== Utilities ====================

    private static boolean isFinite(Vector v) {
        return Double.isFinite(v.getX()) && Double.isFinite(v.getY()) && Double.isFinite(v.getZ());
    }

    private static void safeKnockback(LivingEntity target, Vector direction, double strength) {
        if (!isFinite(direction) || direction.lengthSquared() <= 0.001) return;
        Vector kb = direction.normalize().multiply(strength);
        Vector newVel = target.getVelocity().add(kb);
        if (isFinite(newVel)) {
            target.setVelocity(newVel);
        }
    }

    // ==================== Legendary combat effects ====================

    private static boolean tryDodge(LivingEntity target) {
        if (!(target instanceof Player p)) return false;
        PlayerData d = plugin.getPlayerDataManager().getData(p.getUniqueId());
        if (d == null || d.evasion <= 0) return false;
        if (Math.random() >= d.evasion) return false;
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1.5f);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.05);
        return true;
    }

    private static double applyLegendaryDamageMultipliers(PlayerData shooter, UUID shooterId, LivingEntity target, double damage) {
        double dmg = damage;
        if (shooter != null) {
            if (shooter.bloodFurry > 0 && com.rounds.listener.LegendaryEffects.isRaging(shooterId)) {
                dmg *= (1.0 + shooter.bloodFurry);
            }
            if (shooter.berserk > 0) {
                Player sp = Bukkit.getPlayer(shooterId);
                if (sp != null && sp.isOnline() && sp.isValid() && sp.getMaxHealth() > 0) {
                    double ratio = sp.getHealth() / sp.getMaxHealth();
                    double missing = Math.max(0.0, Math.min((1.0 - ratio) / 0.8, 1.0));
                    dmg *= (1.0 + shooter.berserk * missing);
                }
            }
            if (shooter.snowball > 0 && shooter.snowballWins > 0) {
                dmg *= (1.0 + 0.1 * shooter.snowballWins);
            }
            if ((shooter.executioner > 0 || shooter.chikibamboni > 0) && target instanceof Player tp && tp.getMaxHealth() > 0) {
                double ratio = tp.getHealth() / tp.getMaxHealth();
                if (shooter.executioner > 0 && ratio < 0.2) {
                    dmg *= 2.0;
                }
            }
        }
        return dmg;
    }

    private static boolean shouldExecute(PlayerData shooter, LivingEntity target) {
        if (shooter == null) return false;
        if (!(target instanceof Player tp)) return false;
        if (tp.getMaxHealth() <= 0) return false;
        double ratio = tp.getHealth() / tp.getMaxHealth();
        if (shooter.chikibamboni > 0 && ratio < 0.15) return true;
        if (shooter.executioner > 0 && ratio < 0.2 && Math.random() < 0.01) return true;
        return false;
    }

    private static void executeTarget(LivingEntity target) {
        target.setNoDamageTicks(0);
        target.damage(Math.max(target.getHealth(), 1) * 100.0);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.8f);
    }

    private static void applyPostHitEffects(UUID ownerId, PlayerData shooter, LivingEntity target, double damageDealt) {
        if (target instanceof Player tp) {
            PlayerData td = plugin.getPlayerDataManager().getData(tp.getUniqueId());
            if (td.spikes > 0) {
                Player attacker = Bukkit.getPlayer(ownerId);
                if (attacker != null && attacker.isOnline() && attacker.isValid()) {
                    attacker.setNoDamageTicks(0);
                    attacker.damage(Math.max(damageDealt * td.spikes, 0.5));
                }
            }
            if (td.frostArmor > 0) {
                Player attacker = Bukkit.getPlayer(ownerId);
                if (attacker != null && attacker.isOnline() && attacker.isValid()) {
                    attacker.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.SLOW, 60, 0));
                }
            }
        }
        if (shooter != null && shooter.stormCaller > 0 && Math.random() < shooter.stormCaller) {
            target.getWorld().strikeLightningEffect(target.getLocation());
            target.setNoDamageTicks(0);
            target.damage(5.0);
        }
    }

    private static void removeBullet(Arrow arrow) {
        arrow.remove();
        activeBullets.remove(arrow.getUniqueId());
        bounceCounters.remove(arrow.getUniqueId());
        lastBulletVelocity.remove(arrow.getUniqueId());
    }

    public static void clearAllState() {
        // Отложенные восстановления HP: отменяем задачи и применяем откат сразу.
        for (int taskId : hpBoostTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        hpBoostTasks.clear();
        for (Map.Entry<UUID, Double> entry : hpBoostPending.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) continue;
            var attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) {
                double restored = Math.max(1.0, attr.getBaseValue() - entry.getValue());
                attr.setBaseValue(restored);
                p.setHealth(Math.min(p.getHealth(), restored));
            }
        }
        hpBoostPending.clear();

        bounceCounters.clear();
        lastBulletVelocity.clear();
        activeBullets.clear();
        activeClouds.clear();
        if (cloudTickId != -1) {
            Bukkit.getScheduler().cancelTask(cloudTickId);
            cloudTickId = -1;
        }
        removeMarkedEntities();
    }

    private static void removeMarkedEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClasses(Arrow.class, AreaEffectCloud.class)) {
                PersistentDataContainer pdc = entity.getPersistentDataContainer();
                boolean isBulletArrow = entity instanceof Arrow
                        && pdc.has(RoundsKeys.IS_BULLET, PersistentDataType.BYTE);
                boolean isEffectCloud = entity instanceof AreaEffectCloud
                        && (pdc.has(RoundsKeys.IS_HEAL_RING, PersistentDataType.BYTE)
                            || pdc.has(RoundsKeys.IS_TOXIC_RING, PersistentDataType.BYTE));
                if (isBulletArrow || isEffectCloud) {
                    entity.remove();
                }
            }
        }
    }

    // ==================== Spawn bullet ====================

    public static BulletProjectile spawnBullet(Player shooter, Location loc, Vector velocity, PlayerData data) {
        double scale = data.bigBullet > 0 ? 2.0 : 1.0;
        Vector dir = (velocity.lengthSquared() > 0.001 && isFinite(velocity))
            ? velocity.clone().normalize()
            : new Vector(0, 0, 1);
        Location spawnLoc = loc.clone().add(dir.multiply(0.5));

        Arrow arrow = shooter.getWorld().spawn(spawnLoc, Arrow.class, a -> {
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
            pdc.set(RoundsKeys.BULLET_SPAWN_LOC, PersistentDataType.STRING,
                loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ());
            if (data.tgBounce > 0) {
                pdc.set(RoundsKeys.BULLET_TG_BOUNCE, PersistentDataType.DOUBLE, data.tgBounce);
            }
            if (data.drill > 0) {
                pdc.set(RoundsKeys.BULLET_DRILL, PersistentDataType.DOUBLE, data.drill);
            }
            if (data.sneaky > 0) {
                pdc.set(RoundsKeys.BULLET_SNEAKY, PersistentDataType.DOUBLE, data.sneaky);
            }
        });

        lastBulletVelocity.put(arrow.getUniqueId(), velocity);

        String spawnLocStr = loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ();
        activeBullets.put(arrow.getUniqueId(), new BulletData(
            shooter.getUniqueId(),
            data.getEffectiveDamage(),
            (int) data.bouncePl,
            scale,
            data.homing,
            data.tgBounce,
            data.drill,
            data.sneaky,
            spawnLocStr,
            velocity,
            spawnLoc.clone()
        ));

        return new BulletProjectile(arrow, shooter.getUniqueId());
    }

    // ==================== Reflect ====================

    private static void reflectBulletFromPlayer(Arrow arrow, Player shieldPlayer, PersistentDataContainer pdc) {
        Player originalShooter = arrow.getShooter() instanceof Player p ? p : null;
        Vector toTarget;
        if (originalShooter != null && originalShooter.isOnline()) {
            toTarget = originalShooter.getLocation().toVector().subtract(arrow.getLocation().toVector());
            if (toTarget.lengthSquared() > 0.01) {
                toTarget = toTarget.normalize().multiply(3.0);
            } else {
                toTarget = shieldPlayer.getLocation().getDirection().multiply(3.0);
            }
        } else {
            toTarget = shieldPlayer.getLocation().getDirection().multiply(3.0);
        }
        pdc.set(RoundsKeys.BULLET_OWNER, PersistentDataType.STRING, shieldPlayer.getUniqueId().toString());
        arrow.setVelocity(toTarget);
        arrow.setShooter(shieldPlayer);
        arrow.setPierceLevel((byte) 0);
        bounceCounters.remove(arrow.getUniqueId());
        shieldPlayer.getWorld().playSound(shieldPlayer.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 2.0f);
    }

    // ==================== Bounce ====================

    public static void bounceBullet(Arrow oldArrow, Vector reflectedVelocity) {
        PersistentDataContainer pdc = oldArrow.getPersistentDataContainer();
        if (!pdc.has(RoundsKeys.IS_BULLET, PersistentDataType.BYTE)) return;

        String ownerStr = pdc.getOrDefault(RoundsKeys.BULLET_OWNER, PersistentDataType.STRING, "");
        if (ownerStr.isEmpty()) return;
        UUID ownerId;
        try { ownerId = UUID.fromString(ownerStr); } catch (IllegalArgumentException e) { return; }
        double damage = pdc.getOrDefault(RoundsKeys.BULLET_DAMAGE, PersistentDataType.DOUBLE, 1.0);
        int maxBounce = pdc.getOrDefault(RoundsKeys.BULLET_BOUNCE, PersistentDataType.INTEGER, 0);
        double scale = pdc.getOrDefault(RoundsKeys.BULLET_SCALE, PersistentDataType.DOUBLE, 1.0);
        double homing = pdc.getOrDefault(RoundsKeys.BULLET_HOMING, PersistentDataType.DOUBLE, 0.0);
        Double drill = pdc.get(RoundsKeys.BULLET_DRILL, PersistentDataType.DOUBLE);
        double tgBounce = pdc.getOrDefault(RoundsKeys.BULLET_TG_BOUNCE, PersistentDataType.DOUBLE, 0.0);
        double sneaky = pdc.getOrDefault(RoundsKeys.BULLET_SNEAKY, PersistentDataType.DOUBLE, 0.0);
        String spawnLocStr = pdc.getOrDefault(RoundsKeys.BULLET_SPAWN_LOC, PersistentDataType.STRING, "");

        int oldBounce = bounceCounters.getOrDefault(oldArrow.getUniqueId(), 0);
        bounceCounters.remove(oldArrow.getUniqueId());

        double speed = reflectedVelocity.length() * 0.6;
        Vector newVel = reflectedVelocity.lengthSquared() > 0.001
            ? reflectedVelocity.normalize().multiply(speed)
            : reflectedVelocity;

        Location newLoc = newVel.lengthSquared() > 0.001
            ? oldArrow.getLocation().add(newVel.clone().normalize().multiply(0.5))
            : oldArrow.getLocation().clone();
        Player shooter = oldArrow.getShooter() instanceof Player p ? p : null;
        org.bukkit.projectiles.ProjectileSource originalShooter = oldArrow.getShooter();

        activeBullets.remove(oldArrow.getUniqueId());
        lastBulletVelocity.remove(oldArrow.getUniqueId());
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
            if (tgBounce > 0) {
                newPdc.set(RoundsKeys.BULLET_TG_BOUNCE, PersistentDataType.DOUBLE, tgBounce);
            }
            if (!spawnLocStr.isEmpty()) {
                newPdc.set(RoundsKeys.BULLET_SPAWN_LOC, PersistentDataType.STRING, spawnLocStr);
            }
            if (drill != null && drill > 0) {
                newPdc.set(RoundsKeys.BULLET_DRILL, PersistentDataType.DOUBLE, drill);
            }
            if (sneaky > 0) {
                newPdc.set(RoundsKeys.BULLET_SNEAKY, PersistentDataType.DOUBLE, sneaky);
            }
        });

        bounceCounters.put(newArrow.getUniqueId(), oldBounce + 1);

        double effectiveHoming = 0;
        if (tgBounce > 0) {
            LivingEntity nearest = findNearestEnemy(newArrow, ownerId);
            if (nearest != null) {
                Vector toTarget = nearest.getEyeLocation().toVector().subtract(newArrow.getLocation().toVector());
                if (toTarget.length() > 0) {
                    double speed2 = newVel.length();
                    newArrow.setVelocity(toTarget.normalize().multiply(speed2));
                }
            }
        } else if (homing > 0) {
            LivingEntity nearest = findNearestEnemy(newArrow, ownerId);
            if (nearest != null) {
                Vector toTarget = nearest.getEyeLocation().toVector().subtract(newArrow.getLocation().toVector());
                if (toTarget.length() > 0) {
                    double speed2 = newVel.length();
                    newArrow.setVelocity(toTarget.normalize().multiply(speed2));
                }
            }
        }

        activeBullets.put(newArrow.getUniqueId(), new BulletData(
            ownerId,
            damage,
            maxBounce,
            scale,
            effectiveHoming,
            tgBounce,
            drill != null ? drill : 0.0,
            sneaky,
            spawnLocStr,
            newVel,
            newLoc.clone()
        ));
    }

    // ==================== Find nearest enemy ====================

    private static LivingEntity findNearestEnemy(Arrow arrow, UUID ownerId) {
        double closest = Double.MAX_VALUE;
        LivingEntity nearest = null;
        GameTeam ownerTeam = plugin.getTeamManager().getPlayerTeam(ownerId);
        for (Entity entity : arrow.getNearbyEntities(30, 30, 30)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity.getUniqueId().equals(ownerId)) continue;
            if (entity instanceof org.bukkit.entity.ArmorStand) continue;
            if (entity instanceof org.bukkit.entity.Player p) {
                if (!plugin.getGameManager().isTargetable(p)) continue;
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

    // ==================== Spawn effects ====================

    public static void spawnBomb(Location loc, UUID owner) {
        spawnBomb(loc, owner, 8.0);
    }

    public static void spawnBomb(Location loc, UUID owner, double bombDamage) {
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 1);
        for (var entity : loc.getNearbyLivingEntities(5.0)) {
            if (entity.getUniqueId().equals(owner)) continue;
            if (entity instanceof Player p && !plugin.getGameManager().isTargetable(p)) continue;
            double dmg = Math.max(0, bombDamage);
            if (dmg > 0) {
                entity.setNoDamageTicks(0);
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

    public static void spawnToxicCloud(Location loc, UUID owner, PlayerData data) {
        double radius = 2.5;
        int duration = 100;
        int amplifier = (int) Math.max(data.poisonLvl, 1);

        AreaEffectCloud cloud = loc.getWorld().spawn(loc, AreaEffectCloud.class, aec -> {
            aec.setRadius((float) radius);
            aec.setRadiusOnUse(0);
            aec.setRadiusPerTick(0);
            aec.setDuration(duration);
            aec.setWaitTime(0);
            aec.setReapplicationDelay(20);
            aec.addCustomEffect(new org.bukkit.potion.PotionEffect(
                PotionEffectType.POISON, 40, amplifier), true);
            aec.getPersistentDataContainer().set(RoundsKeys.IS_TOXIC_RING, PersistentDataType.BYTE, (byte) 1);
            aec.getPersistentDataContainer().set(RoundsKeys.BULLET_OWNER, PersistentDataType.STRING, owner.toString());
        });

        Location particleCenter = loc.clone().add(0, 0.5, 0);
        activeClouds.add(new CloudFx(particleCenter, radius, duration));
        startCloudTicker();

        loc.getWorld().playSound(loc, Sound.BLOCK_HONEY_BLOCK_PLACE, 0.8f, 0.6f);
        loc.getWorld().spawnParticle(Particle.SMOKE_LARGE, loc.clone().add(0, 0.5, 0),
            30, 1.0, 0.5, 1.0, 0.02);
        loc.getWorld().spawnParticle(Particle.REDSTONE, loc.clone().add(0, 0.5, 0),
            20, 1.0, 0.5, 1.0, new Particle.DustOptions(Color.fromRGB(0, 200, 0), 1.5f));
    }

    public static void spawnBombShield(Location loc, UUID owner) {
        loc.getWorld().createExplosion(loc, 2.0f, false, false);
        for (var entity : loc.getNearbyLivingEntities(2.0)) {
            if (entity.getUniqueId().equals(owner)) continue;
            if (entity instanceof Player sp && !plugin.getGameManager().isTargetable(sp)) continue;
            double dist = entity.getLocation().distance(loc);
            double dmg = Math.max(0, 5.0 * (1.0 - dist / 2.0));
            if (dmg > 0) { entity.setNoDamageTicks(0); entity.damage(dmg); }
        }
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);
    }

    // ==================== Event handlers ====================

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (arrow.isDead() || !arrow.isValid()) return;

        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        if (!pdc.has(RoundsKeys.IS_BULLET, PersistentDataType.BYTE)) return;

        UUID ownerId;
        double damage;
        int maxBounce;
        BulletData cachedData = activeBullets.get(arrow.getUniqueId());
        if (cachedData != null) {
            ownerId = cachedData.ownerId;
            damage = cachedData.damage;
            maxBounce = cachedData.maxBounce;
        } else {
            String ownerStr = pdc.getOrDefault(RoundsKeys.BULLET_OWNER, PersistentDataType.STRING, "");
            try { ownerId = UUID.fromString(ownerStr); } catch (IllegalArgumentException e) { return; }
            damage = pdc.getOrDefault(RoundsKeys.BULLET_DAMAGE, PersistentDataType.DOUBLE, 1.0);
            maxBounce = pdc.getOrDefault(RoundsKeys.BULLET_BOUNCE, PersistentDataType.INTEGER, 0);
        }

        Entity hitEntity = event.getHitEntity();

        if (hitEntity != null && hitEntity instanceof LivingEntity living && !living.getUniqueId().equals(ownerId)) {
            if (living instanceof Player blockedPlayer && GunItem.isShieldActive(blockedPlayer.getUniqueId())) {
                event.setCancelled(true);
                reflectBulletFromPlayer(arrow, blockedPlayer, pdc);
                if (cachedData != null) {
                    cachedData.ownerId = blockedPlayer.getUniqueId();
                }
                return;
            }
            if (living instanceof Player hitPlayer && !plugin.getGameManager().isTargetable(hitPlayer)) return;
            PlayerData data = plugin.getPlayerDataManager().getData(ownerId);
            double finalDamage = damage * 2.0;
            if (data != null) {
                String spawnLocStr = cachedData != null ? cachedData.spawnLoc : pdc.getOrDefault(RoundsKeys.BULLET_SPAWN_LOC, PersistentDataType.STRING, "");
                if (spawnLocStr != null && !spawnLocStr.isEmpty() && data.grow > 0) {
                    try {
                        String[] parts = spawnLocStr.split(",");
                        Location spawnLoc = new Location(
                            Bukkit.getWorld(parts[0]),
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3]));
                        double dist = arrow.getLocation().distance(spawnLoc);
                        double growMult = 1.0 + Math.min(dist * 0.1 * data.grow, 2.0);
                        finalDamage *= growMult;
                    } catch (Exception ignored) {}
                }
                int bounceCount = bounceCounters.getOrDefault(arrow.getUniqueId(), 0);
                if (bounceCount > 0 && data.damagePerBounce > 0) {
                    finalDamage *= (1.0 + bounceCount * data.damagePerBounce);
                }
                if (data.trusterLvl > 0) {
                    safeKnockback(living, arrow.getVelocity(), data.trusterLvl * 3.0);
                }
                if (data.overpower > 0) {
                    finalDamage *= (1.0 + data.overpower * 0.2);
                }
            }
            finalDamage = applyLegendaryDamageMultipliers(data, ownerId, living, finalDamage);
            if (tryDodge(living)) {
                arrow.remove();
                activeBullets.remove(arrow.getUniqueId());
                bounceCounters.remove(arrow.getUniqueId());
                lastBulletVelocity.remove(arrow.getUniqueId());
                return;
            }
            living.setNoDamageTicks(0);
            if (shouldExecute(data, living)) {
                executeTarget(living);
            } else {
                living.damage(finalDamage);
            }
            living.getWorld().playSound(living.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
            applyPostHitEffects(ownerId, data, living, finalDamage);

            if (data != null) {
                if (data.stun > 0 && living instanceof Player stunTarget) {
                    stunTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.BLINDNESS, 40, 0));
                    stunTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.SLOW, 40, 2));
                    stunTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.CONFUSION, 40, 0));
                }
                if (data.splash > 0 && arrow.getShooter() instanceof Player splashShooter) {
                    double splashRadius = 2.0 + data.splash;
                    for (Entity entity : living.getNearbyEntities(splashRadius, splashRadius, splashRadius)) {
                        if (entity instanceof LivingEntity splashTarget && !splashTarget.getUniqueId().equals(ownerId)) {
                            if (splashTarget instanceof Player sp && !plugin.getGameManager().isTargetable(sp)) continue;
                            double splashDmg = finalDamage * 0.5;
                            splashTarget.setNoDamageTicks(0);
                            splashTarget.damage(splashDmg);
                        }
                    }
                    living.getWorld().playSound(living.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f);
                }
                if (data.shockwave > 0) {
                    for (Entity entity : living.getNearbyEntities(5.0, 5.0, 5.0)) {
                        if (entity instanceof LivingEntity shockTarget && !shockTarget.getUniqueId().equals(ownerId)) {
                            if (shockTarget instanceof Player sp && !plugin.getGameManager().isTargetable(sp)) continue;
                            Vector toTarget = shockTarget.getLocation().toVector()
                                .subtract(living.getLocation().toVector());
                            if (isFinite(toTarget) && toTarget.lengthSquared() > 0.001) {
                                Vector push = toTarget.normalize().multiply(data.shockwave * 0.8);
                                push.setY(0.5);
                                Vector newVel = shockTarget.getVelocity().add(push);
                                if (isFinite(newVel)) shockTarget.setVelocity(newVel);
                            }
                        }
                    }
                }
                if (data.radiance > 0 && living instanceof Player radTarget) {
                    radTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.GLOWING, 100, 0));
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
                    double heal = Math.max(Math.ceil(finalDamage * data.leech), 1);
                    shooter.setHealth(Math.min(shooter.getHealth() + heal, shooter.getMaxHealth()));
                }
                if (data.speedBoost > 0 && arrow.getShooter() instanceof Player speedShooter) {
                    speedShooter.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.SPEED, 100, 0));
                }
                if (data.hpBoostOnHit > 0 && arrow.getShooter() instanceof Player hpShooter) {
                    UUID shooterUUID = hpShooter.getUniqueId();
                    if (!hpBoostPending.containsKey(shooterUUID)) {
                        var hpAttr = hpShooter.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                        if (hpAttr != null) {
                            double baseMaxHP = hpAttr.getValue();
                            double boost = baseMaxHP * data.hpBoostOnHit;
                            double newMax = Math.min(baseMaxHP + boost, PlayerData.MAX_HEALTH);
                            double actualBoost = Math.max(0, newMax - baseMaxHP);
                            if (actualBoost > 0) {
                                // Храним применённый буст, а не исходный максимум:
                                // восстановление вычитает буст из текущего значения,
                                // чтобы не затирать бонусы от карт здоровья.
                                hpBoostPending.put(shooterUUID, actualBoost);
                                hpAttr.setBaseValue(newMax);
                                hpShooter.setHealth(Math.min(hpShooter.getHealth() + actualBoost, newMax));
                                int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                                    hpBoostPending.remove(shooterUUID);
                                    hpBoostTasks.remove(shooterUUID);
                                    var restoreAttr = hpShooter.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                                    if (restoreAttr != null) {
                                        double restored = Math.max(1.0, restoreAttr.getBaseValue() - actualBoost);
                                        restoreAttr.setBaseValue(restored);
                                        hpShooter.setHealth(Math.min(hpShooter.getHealth(), restored));
                                    }
                                }, 40L);
                                hpBoostTasks.put(shooterUUID, taskId);
                            }
                        }
                    }
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
                    spawnBomb(living.getLocation(), ownerId, data.getEffectiveDamage() * 0.3);
                }
                if (data.toxicCloud > 0) {
                    spawnToxicCloud(living.getLocation(), ownerId, data);
                }
                if (data.poison > 0) {
                    living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.POISON, 60, (int) Math.max(data.poisonLvl, 1)));
                }
            }

            arrow.remove();
            activeBullets.remove(arrow.getUniqueId());
            bounceCounters.remove(arrow.getUniqueId());
            lastBulletVelocity.remove(arrow.getUniqueId());
            return;
        }

        if (event.getHitBlock() != null) {
            Double sneakyVal = cachedData != null ? cachedData.sneaky : pdc.get(RoundsKeys.BULLET_SNEAKY, PersistentDataType.DOUBLE);
            if (sneakyVal != null && sneakyVal > 0) {
                org.bukkit.block.BlockFace face = event.getHitBlockFace();
                if (face != null) {
                    event.setCancelled(true);
                    Vector vel = lastBulletVelocity.getOrDefault(arrow.getUniqueId(), arrow.getVelocity());
                    if (vel.lengthSquared() < 0.01) {
                        vel = arrow.getLocation().getDirection().multiply(3.0);
                    }
                    Vector normal = face.getDirection();
                    Vector along = vel.clone().subtract(normal.multiply(vel.dot(normal)));
                    if (along.lengthSquared() < 0.001) {
                        Vector look = arrow.getLocation().getDirection();
                        along = look.clone().subtract(normal.multiply(look.dot(normal)));
                    }
                    if (along.lengthSquared() > 0.001) {
                        Location slideLoc = arrow.getLocation().add(normal.clone().multiply(0.5));
                        arrow.teleport(slideLoc);
                        arrow.setVelocity(along.normalize().multiply(vel.length()));
                    }
                }
                lastBulletVelocity.remove(arrow.getUniqueId());
                if (cachedData != null) {
                    cachedData.prevLoc = arrow.getLocation().clone();
                }
                return;
            }

            int currentBounce = bounceCounters.getOrDefault(arrow.getUniqueId(), 0);
            Double drillVal = cachedData != null ? cachedData.drill : pdc.get(RoundsKeys.BULLET_DRILL, PersistentDataType.DOUBLE);
            if (drillVal != null && drillVal > 0) {
                event.setCancelled(true);
                Vector vel = arrow.getVelocity();
                if (vel.lengthSquared() > 0.01) {
                    Location newLoc = arrow.getLocation().add(vel.clone().normalize().multiply(2.0));
                    arrow.teleport(newLoc);
                    arrow.setVelocity(vel);
                }
                lastBulletVelocity.remove(arrow.getUniqueId());
                if (cachedData != null) {
                    cachedData.prevLoc = arrow.getLocation().clone();
                }
                return;
            }
            int effectiveMaxBounce = cachedData != null ? cachedData.maxBounce : maxBounce;
            if (effectiveMaxBounce > 0 && currentBounce < effectiveMaxBounce) {
                event.setCancelled(true);
                Vector vel = arrow.getVelocity();
                org.bukkit.block.BlockFace face = event.getHitBlockFace();
                if (face != null && vel.lengthSquared() > 0.01) {
                    Vector normal = face.getDirection();
                    Vector reflected = vel.subtract(normal.multiply(2 * vel.dot(normal)));
                    PlayerData bounceData = plugin.getPlayerDataManager().getData(ownerId);
                    if (bounceData != null) {
                        if (bounceData.toxicCloud > 0) {
                            spawnToxicCloud(arrow.getLocation(), ownerId, bounceData);
                        }
                        if (bounceData.bombBullet > 0) {
                            spawnBomb(arrow.getLocation(), ownerId, bounceData.getEffectiveDamage() * 0.3);
                        }
                    }
                    bounceBullet(arrow, reflected);
                }
                return;
            }
            arrow.remove();
            activeBullets.remove(arrow.getUniqueId());
            bounceCounters.remove(arrow.getUniqueId());
            lastBulletVelocity.remove(arrow.getUniqueId());
            PlayerData blockData = plugin.getPlayerDataManager().getData(ownerId);
            if (blockData != null && blockData.toxicCloud > 0) {
                spawnToxicCloud(arrow.getLocation(), ownerId, blockData);
            }
            if (blockData != null && blockData.bombBullet > 0) {
                spawnBomb(arrow.getLocation(), ownerId, blockData.getEffectiveDamage() * 0.3);
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) return;
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity.getPersistentDataContainer().has(RoundsKeys.IS_BULLET, PersistentDataType.BYTE)) {
                entity.remove();
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
        if (event.getDamager() instanceof Player attacker) {
            event.setCancelled(true);
            if (!plugin.getGameManager().isParticipant(attacker.getUniqueId())) return;
            if (!GunItem.consumeShotThisTick(attacker.getUniqueId()) && GunItem.isGun(attacker.getInventory().getItemInMainHand())) {
                GunItem.getInstance().doShoot(attacker);
            }
        }
    }
}

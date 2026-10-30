package com.rounds.entity;

import com.rounds.RoundsKeys;
import com.rounds.RoundsPlugin;
import com.rounds.item.GunItem;
import com.rounds.player.PlayerData;
import com.rounds.teams.TeamManager.GameTeam;
import com.rounds.util.Messages;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Arrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RoundsEntities implements Listener {

    private static RoundsPlugin plugin;
    private static final Map<UUID, Double> hpBoostPending = new HashMap<>();
    private static final Map<UUID, Integer> hpBoostTasks = new HashMap<>();
    private static final List<CloudFx> activeClouds = new ArrayList<>();
    private static int cloudTickId = -1;
    private static final Particle.DustOptions TOXIC_DUST =
            new Particle.DustOptions(Color.fromRGB(0, 200, 0), 1.5f);
    private static final Particle.DustOptions TRACER_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 190, 60), 0.95f);
    private static final Map<UUID, org.bukkit.scheduler.BukkitTask> vignetteTasks = new ConcurrentHashMap<>();

    private static boolean isApplyingRoundsDamage = false;

    public static boolean isApplyingRoundsDamage() {
        return isApplyingRoundsDamage;
    }

    public static void applyDirectDamage(LivingEntity target, double damage, Entity damager) {
        if (target == null || target.isDead() || damage <= 0.0) return;
        target.setNoDamageTicks(0);
        boolean prev = isApplyingRoundsDamage;
        isApplyingRoundsDamage = true;
        try {
            if (damager != null) {
                target.damage(damage, damager);
            } else {
                target.damage(damage);
            }
        } finally {
            isApplyingRoundsDamage = prev;
        }
    }

    public static void safeSetHealth(LivingEntity entity, double health) {
        if (entity == null || entity.isDead()) return;
        if (entity.isInvulnerable() && health < entity.getHealth()) return;
        if (entity instanceof Player p && plugin.getNmsBridge() != null) {
            plugin.getNmsBridge().ensureConnection(p);
        }
        try {
            entity.setHealth(Math.min(Math.max(0.0, health), entity.getMaxHealth()));
        } catch (Throwable ignored) {}
    }

    public static int getBulletCount() {
        return activeBullets.size();
    }

    public static void clearPlayer(UUID uuid) {
        if (uuid == null) return;
        hpBoostPending.remove(uuid);
        Integer taskId = hpBoostTasks.remove(uuid);
        if (taskId != null) {
            try { Bukkit.getScheduler().cancelTask(taskId); } catch (Throwable ignored) {}
        }
        org.bukkit.scheduler.BukkitTask task = vignetteTasks.remove(uuid);
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignored) {}
        }
    }

    public static void applyHitRedEffects(LivingEntity victim, Location attackerLoc, Location hitPos) {
        if (victim == null || !victim.isValid()) return;
        Location vEye = victim.getEyeLocation();
        Vector toAttacker = (attackerLoc != null)
                ? attackerLoc.toVector().subtract(vEye.toVector())
                : victim.getLocation().getDirection().multiply(-1);
        float damageYaw = (float) (Math.toDegrees(Math.atan2(toAttacker.getZ(), toAttacker.getX())) - 90.0f);

        // 1. Red hurt animation on victim model (flashes model bright red & tilts away)
        try {
            victim.playHurtAnimation(damageYaw);
        } catch (Throwable ignored) {}

        // 2. Screen red vignette feedback for real player HUD
        if (victim instanceof Player vp) {
            showDamageVignette(vp);
        }
    }

    public static void showDamageVignette(Player player) {
        if (player == null || !player.isOnline()) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (plugin.getBotManager() != null && plugin.getBotManager().getBot(player.getName()) != null) return;

        try {
            org.bukkit.WorldBorder border = Bukkit.createWorldBorder();
            border.setCenter(player.getLocation());
            border.setSize(200.0);
            border.setWarningDistance(160);
            player.setWorldBorder(border);

            UUID uid = player.getUniqueId();
            org.bukkit.scheduler.BukkitTask old = vignetteTasks.remove(uid);
            if (old != null) old.cancel();

            org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                vignetteTasks.remove(uid);
                if (player.isOnline()) {
                    player.setWorldBorder(null);
                }
            }, 8L);
            vignetteTasks.put(uid, task);
        } catch (Throwable ignored) {}
    }

    public static void clearAllDamageVignettes() {
        for (Map.Entry<UUID, org.bukkit.scheduler.BukkitTask> entry : vignetteTasks.entrySet()) {
            entry.getValue().cancel();
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                try { p.setWorldBorder(null); } catch (Throwable ignored) {}
            }
        }
        vignetteTasks.clear();
    }
    // Цвет крупной пули; размер пыли считается от scale: 0.8 × scale (при scale=2.0 → 1.6f).
    private static final Color TRACER_COLOR_BIG = Color.fromRGB(255, 120, 30);

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

    private static final List<Bullet> activeBullets = new ArrayList<>();
    private static int centralizedTickId = -1;
    // Полный радиус попадания от осевой линии цели: 0.3 базово, ×BIG_BULLET_GROWTH за стак крупной пули.
    static final double HIT_RADIUS = 0.3;
    // Рост крупной пули за стак карты (+70%): и визуальный размер, и радиус попадания.
    static final double BIG_BULLET_GROWTH = 1.7;
    private static final int BULLET_LIFETIME_TICKS = 200;
    private static final double TRACER_STEP = 0.9;
    private static final int TRACER_MAX_POINTS = 4;
    // Переиспользуемый bbox для sweep'а и LOD трейсёра при массовом спавне пуль.
    private static final BoundingBox SWEEP_BOX = new BoundingBox(-1, -1, -1, 1, 1, 1);
    private static final int LOD_FULL_MAX_BULLETS = 250;
    private static final int LOD_MED_MAX_BULLETS = 1200;
    private static long bulletTickCounter = 0L;

    // Когорты залпов: переиспользуемые бакеты и общий список кандидатов на тик.
    private static long cohortCounter = 0L;
    private static final Map<Long, List<Bullet>> cohortBuckets = new HashMap<>();
    private static final ArrayDeque<List<Bullet>> bucketPool = new ArrayDeque<>();
    private static final List<LivingEntity> cohortCandidates = new ArrayList<>();
    private static boolean isProcessingBullets = false;
    private static boolean clearStateRequested = false;

    public static long nextCohortId() {
        return ++cohortCounter;
    }

    public static void register(RoundsPlugin pl) {
        plugin = pl;
        startCentralizedTick();
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
        if (activeBullets.isEmpty()) { cohortBuckets.clear(); return; }
        bulletTickCounter++;

        // Группируем пули по залпам-когортам (бакеты переиспользуются из пула).
        cohortBuckets.clear();
        for (Bullet b : activeBullets) {
            List<Bullet> bucket = cohortBuckets.get(b.cohortId);
            if (bucket == null) {
                bucket = bucketPool.isEmpty() ? new ArrayList<>() : bucketPool.pop();
                cohortBuckets.put(b.cohortId, bucket);
            }
            bucket.add(b);
        }

        isProcessingBullets = true;
        clearStateRequested = false;
        try {
            List<List<Bullet>> buckets = new ArrayList<>(cohortBuckets.values());
            for (int i = 0; i < buckets.size(); i++) {
                if (clearStateRequested) break;
                List<Bullet> bucket = buckets.get(i);
                if (!bucket.isEmpty()) processCohort(bucket);
            }
        } finally {
            isProcessingBullets = false;
            for (List<Bullet> bucket : cohortBuckets.values()) {
                bucket.clear();
                bucketPool.push(bucket);
            }
            cohortBuckets.clear();
            if (clearStateRequested) {
                activeBullets.clear();
                clearStateRequested = false;
            } else {
                activeBullets.removeIf(b -> !b.alive);
            }
        }
    }

    private static void processCohort(List<Bullet> bucket) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        double maxRadius = 0.0;
        World world = null;

        // Фаза 1: предшаг всех пуль когорты (физика + рейкаст стен), копим union-bbox.
        for (Bullet b : bucket) {
            bulletPreStep(b);
            if (!b.alive) continue;
            if (world == null) world = b.loc.getWorld();
            minX = Math.min(minX, Math.min(b.sX, b.eX));
            minY = Math.min(minY, Math.min(b.sY, b.eY));
            minZ = Math.min(minZ, Math.min(b.sZ, b.eZ));
            maxX = Math.max(maxX, Math.max(b.sX, b.eX));
            maxY = Math.max(maxY, Math.max(b.sY, b.eY));
            maxZ = Math.max(maxZ, Math.max(b.sZ, b.eZ));
            maxRadius = Math.max(maxRadius, b.hitRadius);
        }
        if (world == null) return;

        // Фаза 2: ОДИН запрос сущностей на всю когорту вместо запроса на каждую пулю.
        cohortCandidates.clear();
        double pad = maxRadius + 0.5;
        SWEEP_BOX.resize(minX - pad, minY - pad, minZ - pad, maxX + pad, maxY + pad, maxZ + pad);
        for (Entity entity : world.getNearbyEntities(SWEEP_BOX)) {
            if (entity instanceof LivingEntity living && !(entity instanceof ArmorStand)) {
                cohortCandidates.add(living);
            }
        }

        // Фаза 3: каждая пуля проверяет свой сегмент против общего небольшого списка.
        for (Bullet b : bucket) {
            if (b.alive) bulletResolve(b, world);
        }
        cohortCandidates.clear();
    }

    private static void bulletPreStep(Bullet b) {
        if (++b.ticksLived > BULLET_LIFETIME_TICKS) { b.alive = false; return; }
        World world = b.loc.getWorld();
        if (world == null) { b.alive = false; return; }

        if (b.homing > 0) {
            // Цель ищем не каждый тик: кэшируем и обновляем раз в 10 тиков или при потере.
            LivingEntity cached = b.homingTarget;
            boolean targetInvalid = cached == null || !cached.isValid()
                    || cached.isDead()
                    || b.loc.distanceSquared(cached.getLocation()) > 400.0
                    || (cached instanceof Player ht
                        && (!plugin.getGameManager().isTargetable(ht)
                            || isTeammate(b.ownerId, ht.getUniqueId())));
            if (targetInvalid || b.ticksLived % 10 == 0) {
                b.homingTarget = findNearestEnemy(b.loc, b.ownerId);
            }
            LivingEntity nearest = b.homingTarget;
            if (nearest != null) {
                if (nearest instanceof Player p) {
                    if (!plugin.getGameManager().isTargetable(p) || isTeammate(b.ownerId, p.getUniqueId())) {
                        b.homingTarget = null;
                        nearest = null;
                    }
                }
            }
            if (nearest != null) {
                Location eye = nearest.getEyeLocation();
                double tx = eye.getX() - b.loc.getX();
                double ty = eye.getY() - b.loc.getY();
                double tz = eye.getZ() - b.loc.getZ();
                double dist = Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (dist < 1.5) {
                    double finalDamage = b.damage * 2.0 * b.stack;
                    PlayerData shooterData = plugin.getPlayerDataManager().getData(b.ownerId);
                    if (shooterData != null && shooterData.overpower > 0) {
                        finalDamage *= (1.0 + shooterData.overpower * 0.2);
                    }
                    finalDamage = applyLegendaryDamageMultipliers(shooterData, b.ownerId, nearest, finalDamage);
                    if (tryDodge(nearest)) {
                        b.alive = false;
                        return;
                    }

                    Location homingHitLoc = new Location(world, b.loc.getX(), b.loc.getY(), b.loc.getZ());
                    com.rounds.api.event.RoundsBulletHitEvent hitEvent =
                        new com.rounds.api.event.RoundsBulletHitEvent(b, nearest, homingHitLoc, finalDamage);
                    Bukkit.getPluginManager().callEvent(hitEvent);
                    if (plugin != null && plugin.getRoundsService() != null) {
                        for (com.rounds.api.card.CustomCard cc : plugin.getRoundsService().getRegisteredCards()) {
                            try { cc.onBulletHit(hitEvent); } catch (Throwable ignored) {}
                        }
                    }
                    if (hitEvent.isCancelled()) {
                        impactParticles(world, b.loc.getX(), b.loc.getY(), b.loc.getZ(), b.scale);
                        b.alive = false;
                        return;
                    }
                    finalDamage = hitEvent.getDamage();
                    Vector extraKnockback = hitEvent.getKnockback();
                    if (extraKnockback != null && extraKnockback.lengthSquared() > 0.001) {
                        nearest.setVelocity(nearest.getVelocity().add(extraKnockback));
                    }

                    if (nearest.isInvulnerable()) {
                        impactParticles(world, b.loc.getX(), b.loc.getY(), b.loc.getZ(), b.scale);
                        b.alive = false;
                        return;
                    }
                    nearest.setNoDamageTicks(0);
                    if (shouldExecute(shooterData, nearest)) {
                        Player shooter = Bukkit.getPlayer(b.ownerId);
                        executeTarget(nearest, (shooter != null && shooter.isOnline()) ? shooter : null);
                    } else {
                        if (nearest instanceof Player victim) {
                            PlayerData victimData = plugin.getPlayerDataManager().getData(victim.getUniqueId());
                            if (victimData != null && victimData.bulletTime > 0
                                    && !com.rounds.listener.LegendaryEffects.isBulletTimeUsedThisRound(victim.getUniqueId())
                                    && (nearest.getHealth() <= finalDamage || (nearest.getHealth() - finalDamage) <= victimData.getMaxHealth() * 0.20)) {
                                victim.setHealth(Math.max(victimData.getMaxHealth() * 0.20, 2.0));
                                com.rounds.listener.LegendaryEffects.triggerBulletTime(victim, victimData);
                                impactParticles(world, b.loc.getX(), b.loc.getY(), b.loc.getZ(), b.scale);
                                b.alive = false;
                                return;
                            }
                        }
                        Player shooter = Bukkit.getPlayer(b.ownerId);
                        double hpBefore = nearest.getHealth();
                        applyDirectDamage(nearest, finalDamage, (shooter != null && shooter.isOnline()) ? shooter : null);
                        if (shooter != null && shooter.isOnline() && nearest instanceof Player victim) {
                            com.rounds.stats.MatchStatsManager.recordDamageDealt(shooter.getUniqueId(), victim.getUniqueId(), finalDamage);
                            com.rounds.stats.MatchStatsManager.recordLastDamager(victim.getUniqueId(), shooter.getUniqueId());
                        }
                        if (!nearest.isInvulnerable() && nearest.getHealth() >= hpBefore && finalDamage > 0.0 && !nearest.isDead()) {
                            if (nearest instanceof Player p && com.rounds.listener.LegendaryEffects.hasDecay(p.getUniqueId())) {
                                // Handled by LegendaryEffects.onDamage: damage converted to damage-over-time
                            } else {
                                safeSetHealth(nearest, hpBefore - finalDamage);
                            }
                        }
                    }
                    if (nearest instanceof Player victim && plugin.getBotManager() != null) {
                        var victimBot = plugin.getBotManager().getBot(victim.getName());
                        if (victimBot != null) victimBot.onAttacked(b.loc, b.ownerId);
                    }
                    nearest.getWorld().playSound(nearest.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.RECORDS, 0.7f, 1f);
                    Player shooter = Bukkit.getPlayer(b.ownerId);
                    applyHitRedEffects(nearest, (shooter != null ? shooter.getEyeLocation() : b.loc), nearest.getEyeLocation());
                    applyPostHitEffects(b.ownerId, shooterData, nearest, finalDamage, b.stack);
                    applyDirectHitEffects(b, shooterData, nearest, finalDamage);
                    impactParticles(world, b.loc.getX(), b.loc.getY(), b.loc.getZ(), b.scale);
                    b.alive = false;
                    return;
                }
                if (dist > 0 && dist < 20) {
                    Vector hvel = b.velocity;
                    double speed = hvel.length();
                    if (speed > 0) {
                        double speedRatio = speed / 3.0;
                        double speedResilience = 1.0 + 0.5 * Math.max(0.0, speedRatio - 1.0);
                        double k = b.homing * 0.3 * speedResilience / dist;
                        double gx = hvel.getX() + tx * k;
                        double gy = hvel.getY() + ty * k;
                        double gz = hvel.getZ() + tz * k;
                        double gl = Math.sqrt(gx * gx + gy * gy + gz * gz);
                        if (gl > 0) {
                            double s = speed / gl;
                            hvel.setX(gx * s);
                            hvel.setY(gy * s);
                            hvel.setZ(gz * s);
                        }
                    }
                }
            }
        }

        // Физика стрелы 1:1: drag 0.99/тик + гравитация 0.05/тик.
        Vector vel = b.velocity;
        boolean slowMo = com.rounds.listener.LegendaryEffects.isBulletTimeActiveFor(b.ownerId);
        double stepScale = slowMo ? 0.15 : 1.0;
        if (slowMo) {
            vel.multiply(0.9985);
            vel.setY(vel.getY() - 0.05 * 0.15);
        } else {
            vel.multiply(0.99);
            vel.setY(vel.getY() - 0.05);
        }
        double vx = vel.getX() * stepScale;
        double vy = vel.getY() * stepScale;
        double vz = vel.getZ() * stepScale;
        if (!Double.isFinite(vx) || !Double.isFinite(vy) || !Double.isFinite(vz)) {
            b.alive = false;
            return;
        }

        b.sX = b.loc.getX();
        b.sY = b.loc.getY();
        b.sZ = b.loc.getZ();
        b.eX = b.sX + vx;
        b.eY = b.sY + vy;
        b.eZ = b.sZ + vz;

        b.wallHit = null;
        if (b.drill <= 0) {
            double lenSq = vx * vx + vy * vy + vz * vz;
            if (lenSq > 1.0E-6) {
                b.rayDir.setX(vx);
                b.rayDir.setY(vy);
                b.rayDir.setZ(vz);
                b.wallHit = world.rayTraceBlocks(b.loc, b.rayDir, Math.sqrt(lenSq), FluidCollisionMode.NEVER, false);
            }
        }
    }

    private static void bulletResolve(Bullet b, World world) {
        double sx = b.sX, sy = b.sY, sz = b.sZ;
        Vector vel = b.velocity;
        double vx = b.eX - b.sX, vy = b.eY - b.sY, vz = b.eZ - b.sZ;
        double segLenSq = vx * vx + vy * vy + vz * vz;
        double scale = b.scale;

        // Свип по общему списку кандидатов когорты: один проход, аргмин по проекции t.
        LivingEntity hitEnt = null;
        boolean hitShield = false;
        double bestT = Double.MAX_VALUE;
        double hitPX = 0, hitPY = 0, hitPZ = 0;

        if (segLenSq > 0) {
            double hitRadius = b.hitRadius;
            for (LivingEntity living : cohortCandidates) {
                if (living.getUniqueId().equals(b.ownerId)) continue;
                Location eloc = living.getLocation();
                double cx = eloc.getX(), cy = eloc.getY(), cz = eloc.getZ();
                double t = ((cx - sx) * vx + (cy - sy) * vy + (cz - sz) * vz) / segLenSq;
                t = t < 0 ? 0 : (t > 1 ? 1 : t);
                double px = sx + vx * t, py = sy + vy * t, pz = sz + vz * t;
                // Капсульный хитбокс: зажимаем точку прохода по реальной высоте сущности.
                // Иначе выстрелы в макушку (выше ~1.5 от ног) не засчитывались.
                double topY = cy + living.getHeight();
                double qy = py < cy ? cy : Math.min(py, topY);
                double ddx = px - cx, ddy = py - qy, ddz = pz - cz;
                double maxHitDist = hitRadius + 0.5;
                if ((ddx * ddx + ddy * ddy + ddz * ddz) > maxHitDist * maxHitDist) continue;

                boolean shield = false;
                if (living instanceof Player p) {
                    if (!plugin.getGameManager().isTargetable(p)) continue;
                    if (isTeammate(b.ownerId, p.getUniqueId())) continue;
                    shield = GunItem.isShieldActive(p.getUniqueId());
                }
                if (t < bestT) {
                    bestT = t;
                    hitEnt = living;
                    hitShield = shield;
                    hitPX = px; hitPY = py; hitPZ = pz;
                }
            }
        }

        if (hitEnt != null) {
            if (hitShield) {
                Player shieldPlayer = (Player) hitEnt;
                Player originalShooter = Bukkit.getPlayer(b.ownerId);
                double incomingDamage = b.damage * 2.0 * b.stack;
                com.rounds.api.event.RoundsShieldBlockEvent blockEvent =
                    new com.rounds.api.event.RoundsShieldBlockEvent(shieldPlayer, originalShooter, b, incomingDamage);
                Bukkit.getPluginManager().callEvent(blockEvent);
                if (plugin != null && plugin.getRoundsService() != null) {
                    for (com.rounds.api.card.CustomCard cc : plugin.getRoundsService().getRegisteredCards()) {
                        try { cc.onShieldBlock(blockEvent); } catch (Throwable ignored) {}
                    }
                }
                if (blockEvent.isCancelled()) {
                    impactParticles(world, hitPX, hitPY, hitPZ, scale);
                    b.alive = false;
                    return;
                }
                if (blockEvent.isShieldBypassed()) {
                    hitShield = false;
                } else {
                    if (blockEvent.isParry()) {
                        GunItem.resetBlockCooldown(shieldPlayer.getUniqueId());
                        shieldPlayer.getWorld().playSound(shieldPlayer.getLocation(), Sound.BLOCK_ANVIL_USE, SoundCategory.RECORDS, 0.7f, 2.0f);
                        if (originalShooter != null && originalShooter.isOnline()) {
                            double parryDmg = blockEvent.getDamage() * 0.5;
                            applyDirectDamage(originalShooter, parryDmg, shieldPlayer);
                        }
                    }
                    reflectBulletFromPlayer(b, shieldPlayer);
                    spawnTracer(world, sx, sy, sz, b.loc.getX(), b.loc.getY(), b.loc.getZ(), scale);
                    return;
                }
            }

            double finalDmg = b.damage * 2.0 * b.stack;
            PlayerData shooterData = plugin.getPlayerDataManager().getData(b.ownerId);
            if (shooterData != null) {
                if (shooterData.grow > 0) {
                    double gdx = hitPX - b.spawnLoc.getX();
                    double gdy = hitPY - b.spawnLoc.getY();
                    double gdz = hitPZ - b.spawnLoc.getZ();
                    finalDmg *= 1.0 + Math.min(Math.sqrt(gdx * gdx + gdy * gdy + gdz * gdz)
                            * 0.1 * shooterData.grow, 2.0);
                }
                if (b.bounceCount > 0 && shooterData.damagePerBounce > 0) {
                    finalDmg *= (1.0 + b.bounceCount * shooterData.damagePerBounce);
                }
                if (shooterData.overpower > 0) {
                    finalDmg *= (1.0 + shooterData.overpower * 0.2);
                }
            }
            finalDmg = applyLegendaryDamageMultipliers(shooterData, b.ownerId, hitEnt, finalDmg);
            if (tryDodge(hitEnt)) {
                impactParticles(world, hitPX, hitPY, hitPZ, scale);
                b.alive = false;
                return;
            }

            Location rayHitLoc = new Location(world, hitPX, hitPY, hitPZ);
            com.rounds.api.event.RoundsBulletHitEvent hitEvent =
                new com.rounds.api.event.RoundsBulletHitEvent(b, hitEnt, rayHitLoc, finalDmg);
            Bukkit.getPluginManager().callEvent(hitEvent);
            if (plugin != null && plugin.getRoundsService() != null) {
                for (com.rounds.api.card.CustomCard cc : plugin.getRoundsService().getRegisteredCards()) {
                    try { cc.onBulletHit(hitEvent); } catch (Throwable ignored) {}
                }
            }
            if (hitEvent.isCancelled()) {
                impactParticles(world, hitPX, hitPY, hitPZ, scale);
                b.alive = false;
                return;
            }
            finalDmg = hitEvent.getDamage();
            Vector extraKnockback = hitEvent.getKnockback();
            if (extraKnockback != null && extraKnockback.lengthSquared() > 0.001) {
                hitEnt.setVelocity(hitEnt.getVelocity().add(extraKnockback));
            }

            if (hitEnt.isInvulnerable()) {
                impactParticles(world, hitPX, hitPY, hitPZ, scale);
                b.alive = false;
                return;
            }
            hitEnt.setNoDamageTicks(0);
            if (shouldExecute(shooterData, hitEnt)) {
                Player shooter = Bukkit.getPlayer(b.ownerId);
                executeTarget(hitEnt, (shooter != null && shooter.isOnline()) ? shooter : null);
            } else {
                if (hitEnt instanceof Player victim) {
                    PlayerData victimData = plugin.getPlayerDataManager().getData(victim.getUniqueId());
                    if (victimData != null && victimData.bulletTime > 0
                            && !com.rounds.listener.LegendaryEffects.isBulletTimeUsedThisRound(victim.getUniqueId())
                            && (hitEnt.getHealth() <= finalDmg || (hitEnt.getHealth() - finalDmg) <= victimData.getMaxHealth() * 0.20)) {
                        victim.setHealth(Math.max(victimData.getMaxHealth() * 0.20, 2.0));
                        com.rounds.listener.LegendaryEffects.triggerBulletTime(victim, victimData);
                        impactParticles(world, hitPX, hitPY, hitPZ, scale);
                        b.alive = false;
                        return;
                    }
                }
                Player shooter = Bukkit.getPlayer(b.ownerId);
                double hpBefore = hitEnt.getHealth();
                applyDirectDamage(hitEnt, finalDmg, (shooter != null && shooter.isOnline()) ? shooter : null);
                if (shooter != null && shooter.isOnline() && hitEnt instanceof Player victim) {
                    com.rounds.stats.MatchStatsManager.recordDamageDealt(shooter.getUniqueId(), victim.getUniqueId(), finalDmg);
                    com.rounds.stats.MatchStatsManager.recordLastDamager(victim.getUniqueId(), shooter.getUniqueId());
                }
                if (!hitEnt.isInvulnerable() && hitEnt.getHealth() >= hpBefore && finalDmg > 0.0 && !hitEnt.isDead()) {
                    if (hitEnt instanceof Player victim && com.rounds.listener.LegendaryEffects.hasDecay(victim.getUniqueId())) {
                        // Handled by LegendaryEffects.onDamage: damage converted to damage-over-time
                    } else {
                        safeSetHealth(hitEnt, hpBefore - finalDmg);
                    }
                }
            }
            if (hitEnt instanceof Player victim && plugin.getBotManager() != null) {
                var victimBot = plugin.getBotManager().getBot(victim.getName());
                if (victimBot != null) victimBot.onAttacked(b.loc, b.ownerId);
            }
            hitEnt.getWorld().playSound(hitEnt.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.RECORDS, 0.7f, 1f);
            Player shooter = Bukkit.getPlayer(b.ownerId);
            applyHitRedEffects(hitEnt, (shooter != null ? shooter.getEyeLocation() : b.spawnLoc), new Location(world, hitPX, hitPY, hitPZ));
            applyPostHitEffects(b.ownerId, shooterData, hitEnt, finalDmg, b.stack);
            applyDirectHitEffects(b, shooterData, hitEnt, finalDmg);
            impactParticles(world, hitPX, hitPY, hitPZ, scale);
            b.alive = false;
            return;
        }

        RayTraceResult blockHit = b.wallHit;
        if (blockHit != null && blockHit.getHitBlock() != null) {
            Vector hp = blockHit.getHitPosition();
            double hx = hp.getX(), hy = hp.getY(), hz = hp.getZ();

            Location blockHitLoc = new Location(world, hx, hy, hz);
            com.rounds.api.event.RoundsBulletHitEvent blockHitEvent =
                new com.rounds.api.event.RoundsBulletHitEvent(b, blockHit.getHitBlock(), blockHitLoc);
            Bukkit.getPluginManager().callEvent(blockHitEvent);
            if (plugin != null && plugin.getRoundsService() != null) {
                for (com.rounds.api.card.CustomCard cc : plugin.getRoundsService().getRegisteredCards()) {
                    try { cc.onBulletHit(blockHitEvent); } catch (Throwable ignored) {}
                }
            }
            if (blockHitEvent.isCancelled()) {
                impactParticles(world, hx, hy, hz, scale);
                b.alive = false;
                return;
            }

            BlockFace face = blockHit.getHitBlockFace();
            double nxc, nyc, nzc;
            if (face != null) {
                Vector nd = face.getDirection();
                nxc = nd.getX(); nyc = nd.getY(); nzc = nd.getZ();
            } else {
                double inv = 1.0 / Math.sqrt(segLenSq);
                nxc = -vx * inv; nyc = -vy * inv; nzc = -vz * inv;
            }

            if (b.sneaky > 0) {
                double dot = vx * nxc + vy * nyc + vz * nzc;
                double ax = vx - dot * nxc, ay = vy - dot * nyc, az = vz - dot * nzc;
                double al2 = ax * ax + ay * ay + az * az;
                if (al2 < 0.001) {
                    impactParticles(world, hx, hy, hz, scale);
                    b.alive = false;
                    return;
                }
                double k = Math.sqrt(segLenSq) / Math.sqrt(al2);
                vel.setX(ax * k); vel.setY(ay * k); vel.setZ(az * k);
                b.loc.setX(hx + nxc * 0.05);
                b.loc.setY(hy + nyc * 0.05);
                b.loc.setZ(hz + nzc * 0.05);
                spawnTracer(world, sx, sy, sz, b.loc.getX(), b.loc.getY(), b.loc.getZ(), scale);
                return;
            }

            PlayerData bd = plugin.getPlayerDataManager().getData(b.ownerId);
            if (bd != null) {
                Location hitLoc = new Location(world, hx, hy, hz);
                if (bd.toxicCloud > 0) spawnToxicCloud(hitLoc, b.ownerId, bd);
                if (bd.bombBullet > 0) spawnBomb(hitLoc, b.ownerId, bd.getEffectiveDamage() * 0.3);
                if (bd.stormCaller > 0 && Math.random() < bd.stormCaller) {
                    spawnStormLightning(world, hitLoc, b.ownerId, b.stack);
                }
            }

            if (b.bounceCount < b.maxBounce) {
                double dot = vx * nxc + vy * nyc + vz * nzc;
                double rvx = vx - 2 * dot * nxc;
                double rvy = vy - 2 * dot * nyc;
                double rvz = vz - 2 * dot * nzc;
                double rl2 = rvx * rvx + rvy * rvy + rvz * rvz;
                if (!Double.isFinite(rl2) || rl2 < 2.5e-3) {
                    impactParticles(world, hx, hy, hz, scale);
                    b.alive = false;
                    return;
                }
                double rl = Math.sqrt(rl2);
                double speed = rl * 0.6;
                double k = speed / rl;
                vel.setX(rvx * k); vel.setY(rvy * k); vel.setZ(rvz * k);
                b.bounceCount++;
                double off = 0.05 / rl;
                b.loc.setX(hx + rvx * off);
                b.loc.setY(hy + rvy * off);
                b.loc.setZ(hz + rvz * off);
                if (b.tgBounce > 0 || b.homing > 0) {
                    LivingEntity nearest = findNearestEnemy(b.loc, b.ownerId);
                    if (nearest != null) {
                        Location eye = nearest.getEyeLocation();
                        double ttx = eye.getX() - b.loc.getX();
                        double tty = eye.getY() - b.loc.getY();
                        double ttz = eye.getZ() - b.loc.getZ();
                        double tl2 = ttx * ttx + tty * tty + ttz * ttz;
                        if (tl2 > 0) {
                            double s = speed / Math.sqrt(tl2);
                            vel.setX(ttx * s); vel.setY(tty * s); vel.setZ(ttz * s);
                        }
                    }
                }
                spawnTracer(world, sx, sy, sz, b.loc.getX(), b.loc.getY(), b.loc.getZ(), scale);
                return;
            }

            impactParticles(world, hx, hy, hz, scale);
            b.alive = false;
            return;
        }

        // Свободный полёт: пишем координаты прямо в существующую Location — без клонов.
        b.loc.setX(b.eX);
        b.loc.setY(b.eY);
        b.loc.setZ(b.eZ);
        spawnTracer(world, sx, sy, sz, b.eX, b.eY, b.eZ, scale);
    }

    private static void spawnTracer(World world, double fx, double fy, double fz,
                                    double tx, double ty, double tz, double scale) {
        int totalBullets = activeBullets.size();
        // LOD: при сотнях/тысячах пуль режем плотность трейсёра, иначе пакеты частиц
        // сами по себе создают гору мусора и трафик.
        if (totalBullets > LOD_MED_MAX_BULLETS && (bulletTickCounter & 1L) != 0L) return;
        double dx = tx - fx, dy = ty - fy, dz = tz - fz;
        double lenSq = dx * dx + dy * dy + dz * dz;
        if (!Double.isFinite(lenSq) || lenSq < 0.01) return;
        if (totalBullets <= LOD_FULL_MAX_BULLETS) {
            world.spawnParticle(Particle.END_ROD, tx, ty, tz, 1, 0, 0, 0, 0);
        }
        int maxPoints = totalBullets <= LOD_FULL_MAX_BULLETS ? TRACER_MAX_POINTS
                : totalBullets <= LOD_MED_MAX_BULLETS ? 4 : 2;
        int points = Math.min((int) Math.ceil(Math.sqrt(lenSq) / TRACER_STEP), maxPoints);
        boolean big = scale > 1.0;
        Particle.DustOptions dust = big
                ? new Particle.DustOptions(TRACER_COLOR_BIG, (float) (0.8 * scale))
                : TRACER_DUST;
        int count = big ? 2 : 1;
        double ix = dx / points, iy = dy / points, iz = dz / points;
        double px = fx, py = fy, pz = fz;
        for (int i = 0; i < points; i++) {
            px += ix; py += iy; pz += iz;
            world.spawnParticle(Particle.REDSTONE, px, py, pz, count, 0.02, 0.02, 0.02, dust);
        }
    }

    private static void impactParticles(World world, double x, double y, double z, double scale) {
        if (world == null) return;
        // Облако удара разрастается вместе с пулей: разброс 0.15 × scale/2,
        // количество частиц не растёт, чтобы не плодить пакеты.
        float spread = (float) (0.15 * Math.max(1.0, scale * 0.5));
        world.spawnParticle(Particle.CRIT, x, y, z, scale > 1.0 ? 8 : 5, spread, spread, spread, 0.08);
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
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, SoundCategory.RECORDS, 0.7f, 1.5f);
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
                dmg *= (1.0 + 0.1 * shooter.snowballWins * Math.max(shooter.snowball, 1.0));
            }
            if ((shooter.executioner > 0 || shooter.chikibamboni > 0) && target instanceof Player tp && tp.getMaxHealth() > 0) {
                double ratio = tp.getHealth() / tp.getMaxHealth();
                if (shooter.executioner > 0 && ratio < 0.2) {
                    dmg *= (1.0 + shooter.executioner);
                }
            }
        }
        return dmg;
    }

    private static boolean shouldExecute(PlayerData shooter, LivingEntity target) {
        if (shooter == null) return false;
        if (!(target instanceof Player tp)) return false;
        if (tp.getMaxHealth() <= 0) return false;
        double currentRatio = tp.getHealth() / tp.getMaxHealth();
        if (shooter.chikibamboni > 0 && currentRatio < Math.min(0.15 * shooter.chikibamboni, 0.5)) {
            return true;
        }
        if (shooter.executioner > 0 && currentRatio < 0.2 && Math.random() < 0.01 * shooter.executioner) {
            return true;
        }
        return false;
    }

    private static void executeTarget(LivingEntity target, Player shooter) {
        target.setNoDamageTicks(0);
        if (target instanceof Player victim) {
            com.rounds.listener.LegendaryEffects.clearDecay(victim.getUniqueId());
            com.rounds.listener.LegendaryEffects.isExecuting.add(victim.getUniqueId());
            if (shooter != null) {
                com.rounds.stats.MatchStatsManager.recordLastDamager(victim.getUniqueId(), shooter.getUniqueId());
            }
        }
        try {
            double lethal = Math.max(target.getHealth(), 1.0) * 100.0;
            if (shooter != null && shooter.isOnline()) {
                applyDirectDamage(target, lethal, shooter);
            } else {
                applyDirectDamage(target, lethal, null);
            }
            if (!target.isDead() && target.getHealth() > 0.0) {
                target.setHealth(0.0);
            }
        } finally {
            if (target instanceof Player victim) {
                com.rounds.listener.LegendaryEffects.isExecuting.remove(victim.getUniqueId());
            }
        }
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.RECORDS, 0.7f, 1.8f);
    }

    // Единый блок эффектов при прямом попадании: объединяет бывшие событийный
    // (onProjectileHit) и sweep-пайплайны, чтобы ни одна карта не терялась.
    private static void applyDirectHitEffects(Bullet b, PlayerData data, LivingEntity living, double finalDamage) {
        if (data == null) return;
        UUID ownerId = b.ownerId;
        Player shooterPlayer = Bukkit.getPlayer(ownerId);

        if (data.trusterLvl > 0) {
            safeKnockback(living, b.velocity, data.trusterLvl * 3.0 * b.stack);
        }
        if (data.stun > 0 && living instanceof Player stunTarget) {
            stunTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                PotionEffectType.BLINDNESS, 40, 0));
            stunTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                PotionEffectType.SLOW, 40, 2));
            stunTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                PotionEffectType.CONFUSION, 40, 0));
        }
        if (data.splash > 0) {
            double splashRadius = 2.0 + data.splash;
            for (Entity entity : living.getNearbyEntities(splashRadius, splashRadius, splashRadius)) {
                if (entity instanceof LivingEntity splashTarget && !splashTarget.getUniqueId().equals(ownerId)) {
                    if (splashTarget instanceof Player sp) {
                        if (!plugin.getGameManager().isTargetable(sp)) continue;
                        if (isTeammate(ownerId, sp.getUniqueId())) continue;
                    }
                    splashTarget.setNoDamageTicks(0);
                    splashTarget.damage(finalDamage * 0.5);
                }
            }
            living.getWorld().playSound(living.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.RECORDS, 0.7f, 2.0f);
        }
        if (data.shockwave > 0) {
            for (Entity entity : living.getNearbyEntities(5.0, 5.0, 5.0)) {
                if (entity instanceof LivingEntity shockTarget && !shockTarget.getUniqueId().equals(ownerId)) {
                    if (shockTarget instanceof Player sp) {
                        if (!plugin.getGameManager().isTargetable(sp)) continue;
                        if (isTeammate(ownerId, sp.getUniqueId())) continue;
                    }
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
        if (data.radiance > 0 && living instanceof Player radTarget && shooterPlayer != null && shooterPlayer.isOnline()) {
            com.rounds.nms.NmsBridge bridge = plugin.getNmsBridge();
            if (bridge != null) {
                byte flags = plugin.getGameManager().getEntitySharedFlags(radTarget, true);
                bridge.sendEntityFlags(shooterPlayer, radTarget, flags);
                UUID radTargetId = radTarget.getUniqueId();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (shooterPlayer.isOnline()) {
                        Player t = Bukkit.getPlayer(radTargetId);
                        if (t != null && t.isOnline()) {
                            byte clearFlags = plugin.getGameManager().getEntitySharedFlags(t, t.hasPotionEffect(PotionEffectType.GLOWING));
                            bridge.sendEntityFlags(shooterPlayer, t, clearFlags);
                        }
                    }
                }, 100L);
            } else {
                radTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    PotionEffectType.GLOWING, 100, 0));
            }
        }
        if (data.cold > 0) {
            living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                PotionEffectType.SLOW, 100, (int) Math.max(data.coldLvl, 1)));
        }
        if (data.parazit > 0) {
            living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                PotionEffectType.WITHER, 40, (int) Math.max(data.parazitLvl, 1)));
        }
        if (data.speedBoost > 0 && shooterPlayer != null && shooterPlayer.isOnline()) {
            shooterPlayer.addPotionEffect(new org.bukkit.potion.PotionEffect(
                PotionEffectType.SPEED, 100, 0));
        }
        if (data.hpBoostOnHit > 0 && shooterPlayer != null && shooterPlayer.isOnline()) {
            UUID shooterUUID = shooterPlayer.getUniqueId();
            if (!hpBoostPending.containsKey(shooterUUID)) {
                var hpAttr = shooterPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (hpAttr != null) {
                    double baseMaxHP = Math.min(hpAttr.getValue(), PlayerData.MAX_HEALTH);
                    double boost = baseMaxHP * data.hpBoostOnHit * 0.1;
                    double newMax = Math.min(baseMaxHP + boost, PlayerData.MAX_HEALTH);
                    double actualBoost = Math.max(0, newMax - baseMaxHP);
                    if (actualBoost > 0) {
                        // Храним применённый буст: восстановление вычитает его из
                        // текущего значения, чтобы не затирать бонусы карт здоровья.
                        hpBoostPending.put(shooterUUID, actualBoost);
                        hpAttr.setBaseValue(newMax);
                        double targetHealth = Math.min(shooterPlayer.getHealth() + actualBoost, hpAttr.getValue());
                        shooterPlayer.setHealth(Math.min(targetHealth, PlayerData.MAX_HEALTH));
                        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                            hpBoostPending.remove(shooterUUID);
                            hpBoostTasks.remove(shooterUUID);
                            var restoreAttr = shooterPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                            if (restoreAttr != null) {
                                double restored = Math.max(1.0, Math.min(restoreAttr.getBaseValue() - actualBoost, PlayerData.MAX_HEALTH));
                                restoreAttr.setBaseValue(restored);
                                shooterPlayer.setHealth(Math.min(shooterPlayer.getHealth(), restoreAttr.getValue()));
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
        if (data.ammoPerHit > 0) {
            // Стек репрезентатива: каждая «виртуальная» пуля залпа возвращает патроны.
            double before = data.ammo;
            data.ammo = Math.min(data.ammo + data.ammoPerHit * b.stack, data.maxAmmo);
            if (data.ammo > before && shooterPlayer != null && shooterPlayer.isOnline()) {
                if (data.ammo >= data.maxAmmo && GunItem.isReloading(ownerId)) {
                    GunItem.cancelReload(ownerId);
                }
                if (!GunItem.isReloading(ownerId)) {
                    shooterPlayer.sendActionBar(ChatColor.GRAY + Messages.get("gun.ammo-display", (int) data.ammo, (int) data.maxAmmo));
                }
                shooterPlayer.playSound(shooterPlayer.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.RECORDS, 0.7f, 1.5f);
            }
        }
        data.executeTriggers(com.rounds.cards.CardTrigger.ON_BULLET_HIT, shooterPlayer, living, b.damage);
        if (data.refresh > 0 && shooterPlayer != null && shooterPlayer.isOnline()) {
            GunItem.resetBlockCooldown(shooterPlayer.getUniqueId());
        }
        if (data.toxicCloud > 0) {
            spawnToxicCloud(living.getLocation(), ownerId, data);
        }
        if (data.bombBullet > 0) {
            spawnBomb(living.getLocation(), ownerId, data.getEffectiveDamage() * 0.3 * b.stack);
        }
        if (data.poison > 0) {
            living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                PotionEffectType.POISON, 60, (int) Math.max(data.poisonLvl, 1)));
            applyPoisonDamage(living);
        }
        if (data.leech > 0 && shooterPlayer != null && shooterPlayer.isOnline() && shooterPlayer.isValid()) {
            double heal = Math.max(Math.ceil(finalDamage * data.leech), 1);
            shooterPlayer.setHealth(Math.min(shooterPlayer.getHealth() + heal, shooterPlayer.getMaxHealth()));
        }
    }

    private static void applyPoisonDamage(LivingEntity target) {
        var attr = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHp = attr != null ? attr.getValue() : target.getMaxHealth();
        for (int i = 1; i <= 4; i++) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                if (!target.isValid() || target.isDead()) return;
                target.setNoDamageTicks(0);
                target.damage(maxHp * 0.025);
            }, 15L * i);
        }
    }

    private static void applyPostHitEffects(UUID ownerId, PlayerData shooter, LivingEntity target,
                                            double damageDealt, int stack) {
        if (target instanceof Player tp) {
            PlayerData td = plugin.getPlayerDataManager().getData(tp.getUniqueId());
            if (td != null) {
                if (td.spikes > 0) {
                    Player attacker = Bukkit.getPlayer(ownerId);
                    if (attacker != null && attacker.isOnline() && attacker.isValid()) {
                        attacker.setNoDamageTicks(0);
                        // damageDealt уже умножен на стек: шипы отражают пропорционально.
                        attacker.damage(Math.max(damageDealt * td.spikes, 0.5));
                    }
                }
                if (td.frostArmor > 0) {
                    Player attacker = Bukkit.getPlayer(ownerId);
                    if (attacker != null && attacker.isOnline() && attacker.isValid()) {
                        attacker.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            PotionEffectType.SLOW, (int) (60 * Math.max(td.frostArmor, 1.0)), 0));
                    }
                }
                com.rounds.listener.LegendaryEffects.onDirectDamage(tp, td, damageDealt, ownerId);
            }
        }
        if (shooter != null && shooter.stormCaller > 0 && Math.random() < shooter.stormCaller) {
            spawnStormLightning(target.getWorld(), target.getLocation(), ownerId, stack);
        }
    }

    public static void clearAllState() {
        clearAllDamageVignettes();
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
                double restored = Math.max(1.0, Math.min(attr.getBaseValue() - entry.getValue(), PlayerData.MAX_HEALTH));
                attr.setBaseValue(restored);
                p.setHealth(Math.min(p.getHealth(), attr.getValue()));
            }
        }
        hpBoostPending.clear();

        activeClouds.clear();
        if (cloudTickId != -1) {
            Bukkit.getScheduler().cancelTask(cloudTickId);
            cloudTickId = -1;
        }
        removeMarkedEntities();

        if (isProcessingBullets) {
            clearStateRequested = true;
            return;
        }

        activeBullets.clear();
        cohortBuckets.clear();
        bucketPool.clear();
        cohortCandidates.clear();
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

    public static void spawnBullet(Player shooter, Location loc, Vector velocity, PlayerData data,
                                   long cohortId, int stack) {
        spawnBullet(shooter, loc, velocity, data, cohortId, stack, 1.0);
    }

    public static Bullet createBullet(Player shooter, Location loc, Vector velocity, PlayerData data,
                                      long cohortId, int stack, double damageMultiplier) {
        double scale = data.bigBullet > 0 ? 2.0 * Math.pow(BIG_BULLET_GROWTH, data.bigBullet - 1) : 1.0;
        Bullet bullet = new Bullet(
            shooter.getUniqueId(),
            data.getEffectiveDamage() * Math.max(damageMultiplier, 0.1),
            (int) data.bouncePl,
            scale,
            Math.max(data.homing, GunItem.getBonusHoming(shooter.getUniqueId())),
            data.tgBounce,
            data.drill,
            data.sneaky,
            loc.clone(),
            loc.clone(),
            velocity.clone()
        );
        bullet.cohortId = cohortId;
        bullet.stack = Math.max(stack, 1);
        bullet.hitRadius = HIT_RADIUS * Math.pow(BIG_BULLET_GROWTH, data.bigBullet);
        return bullet;
    }

    public static void addBullet(Bullet bullet) {
        if (bullet != null) {
            activeBullets.add(bullet);
        }
    }

    public static void spawnBullet(Player shooter, Location loc, Vector velocity, PlayerData data,
                                   long cohortId, int stack, double damageMultiplier) {
        Bullet bullet = createBullet(shooter, loc, velocity, data, cohortId, stack, damageMultiplier);
        addBullet(bullet);
    }

    // ==================== Reflect ====================

    private static void reflectBulletFromPlayer(Bullet b, Player shieldPlayer) {
        Player originalShooter = Bukkit.getPlayer(b.ownerId);
        Vector toTarget;
        if (originalShooter != null && originalShooter.isOnline()) {
            toTarget = originalShooter.getLocation().toVector().subtract(b.loc.toVector());
            if (toTarget.lengthSquared() > 0.01) {
                toTarget = toTarget.normalize().multiply(3.0);
            } else {
                toTarget = shieldPlayer.getLocation().getDirection().multiply(3.0);
            }
        } else {
            toTarget = shieldPlayer.getLocation().getDirection().multiply(3.0);
        }
        b.ownerId = shieldPlayer.getUniqueId();
        b.velocity = toTarget;
        b.bounceCount = 0;
        b.homingTarget = null;
        b.loc = b.loc.clone().add(toTarget.clone().normalize().multiply(0.5));
        shieldPlayer.getWorld().playSound(shieldPlayer.getLocation(), Sound.BLOCK_ANVIL_USE, SoundCategory.RECORDS, 0.7f, 2.0f);
    }

    // ==================== Teammate helper ====================

    public static boolean isTeammate(UUID u1, UUID u2) {
        if (u1 == null || u2 == null) return false;
        if (u1.equals(u2)) return true;
        if (plugin == null || plugin.getTeamManager() == null) return false;

        GameTeam t1 = plugin.getTeamManager().getPlayerTeam(u1);
        if (t1 == null && plugin.getBotManager() != null) {
            Player p1 = Bukkit.getPlayer(u1);
            if (p1 != null) {
                com.rounds.bot.FakeBot b1 = plugin.getBotManager().getBot(p1.getName());
                if (b1 != null) t1 = b1.getAssignedTeam();
            }
        }

        GameTeam t2 = plugin.getTeamManager().getPlayerTeam(u2);
        if (t2 == null && plugin.getBotManager() != null) {
            Player p2 = Bukkit.getPlayer(u2);
            if (p2 != null) {
                com.rounds.bot.FakeBot b2 = plugin.getBotManager().getBot(p2.getName());
                if (b2 != null) t2 = b2.getAssignedTeam();
            }
        }

        if (t1 != null && t2 != null) {
            return t1 == t2;
        }

        return false;
    }

    // ==================== Find nearest enemy ====================

    private static LivingEntity findNearestEnemy(Location center, UUID ownerId) {
        if (center == null || center.getWorld() == null) return null;
        double closest = 900.0; // 30 blocks maximum range squared (30 * 30)
        LivingEntity nearest = null;
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getUniqueId().equals(ownerId)) continue;
            if (!plugin.getGameManager().isTargetable(p)) continue;
            if (isTeammate(ownerId, p.getUniqueId())) continue;
            double dist = p.getLocation().distanceSquared(center);
            if (dist < closest) {
                closest = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    // ==================== Spawn effects ====================

    public static void spawnBomb(Location loc, UUID owner) {
        spawnBomb(loc, owner, 8.0);
    }

    public static void spawnBomb(Location loc, UUID owner, double bombDamage) {
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.RECORDS, 0.7f, 1.2f);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 1);
        for (var entity : loc.getNearbyLivingEntities(5.0)) {
            if (entity.getUniqueId().equals(owner)) continue;
            if (entity instanceof Player p) {
                if (!plugin.getGameManager().isTargetable(p)) continue;
                if (isTeammate(owner, p.getUniqueId())) continue;
            }
            double dmg = Math.max(0, bombDamage);
            if (dmg > 0) {
                entity.setNoDamageTicks(0);
                entity.damage(dmg);
            }
        }
    }

    public static void spawnStormLightning(World world, Location loc, UUID ownerId, int stack) {
        if (world == null || loc == null) return;
        LightningStrike strike = world.strikeLightning(loc);
        Player attacker = ownerId != null ? Bukkit.getPlayer(ownerId) : null;
        if (attacker != null) {
            try { strike.setCausingPlayer(attacker); } catch (Throwable ignored) {}
        }
        world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 5.0f, 1.0f);
        world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.WEATHER, 2.0f, 1.0f);
        try { world.spawnParticle(Particle.FLASH, loc.clone().add(0, 1, 0), 2); } catch (Throwable ignored) {}
        try { world.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 20, 0.5, 1.0, 0.5, 0.1); } catch (Throwable ignored) {}

        double splashDamage = 5.0 * stack;
        for (var living : loc.getNearbyLivingEntities(3.0)) {
            if (living.isDead() || !living.isValid() || (living instanceof ArmorStand)) continue;
            if (living instanceof Player p) {
                if (ownerId != null && (p.getUniqueId().equals(ownerId) || isTeammate(ownerId, p.getUniqueId()))) {
                    continue;
                }
                if (!plugin.getGameManager().isTargetable(p)) continue;
                com.rounds.stats.MatchStatsManager.recordLastDamager(p.getUniqueId(), ownerId);
            }
            applyDirectDamage(living, splashDamage, (attacker != null && attacker.isOnline()) ? attacker : null);
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

        loc.getWorld().playSound(loc, Sound.BLOCK_HONEY_BLOCK_PLACE, SoundCategory.RECORDS, 0.7f, 0.6f);
        loc.getWorld().spawnParticle(Particle.SMOKE_LARGE, loc.clone().add(0, 0.5, 0),
            30, 1.0, 0.5, 1.0, 0.02);
        loc.getWorld().spawnParticle(Particle.REDSTONE, loc.clone().add(0, 0.5, 0),
            20, 1.0, 0.5, 1.0, new Particle.DustOptions(Color.fromRGB(0, 200, 0), 1.5f));
    }

    public static void spawnBombShield(Location loc, UUID owner) {
        loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 1, 0, 0, 0, 0);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 3, 0.5, 0.5, 0.5, 0.05);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.RECORDS, 0.7f, 1.2f);
        for (var entity : loc.getNearbyLivingEntities(2.0)) {
            if (entity.getUniqueId().equals(owner)) continue;
            if (entity instanceof Player sp) {
                if (!plugin.getGameManager().isTargetable(sp)) continue;
                if (isTeammate(owner, sp.getUniqueId())) continue;
            }
            double dist = entity.getLocation().distance(loc);
            double dmg = Math.max(0, 5.0 * (1.0 - dist / 2.0));
            if (dmg > 0) { entity.setNoDamageTicks(0); entity.damage(dmg); }
        }
    }

    // ==================== Event handlers ====================

    @EventHandler
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        var pdc = event.getEntity().getPersistentDataContainer();
        if (!pdc.has(RoundsKeys.IS_TOXIC_RING, PersistentDataType.BYTE)) return;
        String ownerStr = pdc.get(RoundsKeys.BULLET_OWNER, PersistentDataType.STRING);
        if (ownerStr == null) return;
        UUID ownerId;
        try {
            ownerId = UUID.fromString(ownerStr);
        } catch (IllegalArgumentException e) {
            return;
        }
        event.getAffectedEntities().removeIf(e -> {
            if (e.getUniqueId().equals(ownerId)) return true;
            if (e instanceof Player p) {
                if (p.getGameMode() == GameMode.SPECTATOR) return true;
                if (isTeammate(ownerId, p.getUniqueId())) return true;
            }
            return false;
        });
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Arrow)) return;
        if (event.getEntity().getShooter() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (isApplyingRoundsDamage) {
            return;
        }
        if (event.getDamager() instanceof Arrow arrow) {
            PersistentDataContainer pdc = arrow.getPersistentDataContainer();
            if (pdc.has(RoundsKeys.IS_BULLET, PersistentDataType.BYTE)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getDamager() instanceof Player attacker) {
            if (com.rounds.listener.LegendaryEffects.isDecayTicking(event.getEntity().getUniqueId())) {
                return;
            }
            event.setCancelled(true);
            event.setDamage(0.0);

            if (attacker.getGameMode() == GameMode.CREATIVE || attacker.getGameMode() == GameMode.SPECTATOR) return;
            if (!plugin.getGameManager().isParticipant(attacker.getUniqueId())) return;
            if (plugin.getGameManager().getState() != com.rounds.game.GameManager.GameState.PLAYING) return;

            if (event.getEntity() instanceof Player victim) {
                if (isTeammate(attacker.getUniqueId(), victim.getUniqueId())) {
                    return;
                }
            }

            if (GunItem.isGun(attacker.getInventory().getItemInMainHand())) {
                if (!GunItem.consumeShotThisTick(attacker.getUniqueId())) {
                    GunItem.getInstance().doShoot(attacker);
                }
            }
        }
    }
}

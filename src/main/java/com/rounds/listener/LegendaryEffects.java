package com.rounds.listener;

import com.rounds.RoundsPlugin;
import com.rounds.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LegendaryEffects implements Listener {

    public static class PlayerSnapshot {
        public final long time;
        public final Location loc;
        public final double hp;

        public PlayerSnapshot(long time, Location loc, double hp) {
            this.time = time;
            this.loc = loc;
            this.hp = hp;
        }
    }

    private final RoundsPlugin plugin;
    private static final Map<UUID, Long> rageUntil = new HashMap<>();
    private static final Map<UUID, Integer> secondWindUses = new HashMap<>();
    private static final Map<UUID, Double> fallPeakY = new HashMap<>();
    private static final Map<UUID, Long> skyfallCooldowns = new HashMap<>();
    private static final long SKYFALL_COOLDOWN_MS = 2000L;

    private static final Map<UUID, Deque<PlayerSnapshot>> playerHistory = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> recallCooldowns = new ConcurrentHashMap<>();
    private static final long RECALL_COOLDOWN_MS = 15000L;

    private static final Set<UUID> bulletTimeUsedThisRound = new HashSet<>();
    private static long bulletTimeUntil = 0L;
    private static UUID bulletTimeOwner = null;

    private static final Map<UUID, Long> lastRazorwire = new ConcurrentHashMap<>();

    public LegendaryEffects(RoundsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.isValid() || p.isDead()) continue;
                UUID uid = p.getUniqueId();
                Deque<PlayerSnapshot> deque = playerHistory.computeIfAbsent(uid, k -> new ArrayDeque<>());
                deque.addLast(new PlayerSnapshot(now, p.getLocation().clone(), p.getHealth()));
                while (!deque.isEmpty() && now - deque.peekFirst().time > 4000L) {
                    deque.removeFirst();
                }
            }
        }, 4L, 4L);
    }

    public static boolean isRaging(UUID uuid) {
        Long until = rageUntil.get(uuid);
        return until != null && System.currentTimeMillis() < until;
    }

    public static void clearRoundState() {
        secondWindUses.clear();
        skyfallCooldowns.clear();
        fallPeakY.clear();
        bulletTimeUsedThisRound.clear();
        bulletTimeUntil = 0L;
        bulletTimeOwner = null;
        lastRazorwire.clear();
    }

    public static void resetAll() {
        rageUntil.clear();
        secondWindUses.clear();
        fallPeakY.clear();
        skyfallCooldowns.clear();
        playerHistory.clear();
        recallCooldowns.clear();
        bulletTimeUsedThisRound.clear();
        bulletTimeUntil = 0L;
        bulletTimeOwner = null;
        lastRazorwire.clear();
    }

    public static void clearPlayer(UUID uuid) {
        if (uuid == null) return;
        rageUntil.remove(uuid);
        secondWindUses.remove(uuid);
        fallPeakY.remove(uuid);
        skyfallCooldowns.remove(uuid);
        playerHistory.remove(uuid);
        recallCooldowns.remove(uuid);
        bulletTimeUsedThisRound.remove(uuid);
        lastRazorwire.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Record damage received for match stats
        com.rounds.stats.MatchStatsManager.recordDamageReceived(player.getUniqueId(), event.getFinalDamage());

        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());

        if (data.bloodFurry > 0) {
            rageUntil.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
        }

        if (data.spiteRazorwire > 0 && event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            triggerRazorwire(player, data);
        }

        if (data.bulletTime > 0
                && !bulletTimeUsedThisRound.contains(player.getUniqueId())
                && (player.getHealth() - event.getFinalDamage()) <= data.getMaxHealth() * 0.20) {
            triggerBulletTime(player, data);
        }

        int maxUses = (int) Math.round(data.secondWind);
        int used = secondWindUses.getOrDefault(player.getUniqueId(), 0);
        if (maxUses > 0
                && used < maxUses
                && plugin.getGameManager().isParticipant(player.getUniqueId())
                && event.getFinalDamage() >= player.getHealth()) {
            secondWindUses.put(player.getUniqueId(), used + 1);
            event.setCancelled(true);
            player.setHealth(1.0);
            player.setFallDistance(0);
            player.setFireTicks(0);
            player.setInvulnerable(true);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1.2f);
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                if (player.isOnline() && player.isValid()) {
                    player.setInvulnerable(false);
                }
            }, 40L);
        }

        if (data.skyfall > 0
                && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            player.setFallDistance(0);
            fallPeakY.put(player.getUniqueId(), player.getLocation().getY());
            checkAndTriggerSkyfall(player, data);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getData(uuid);
        if (data.skyfall <= 0) {
            fallPeakY.remove(uuid);
            return;
        }
        Location to = event.getTo() != null ? event.getTo() : player.getLocation();
        if (player.isFlying()
                || player.isInsideVehicle()
                || to.getBlock().isLiquid()) {
            fallPeakY.remove(uuid);
            return;
        }

        double y = to.getY();
        double fromY = event.getFrom().getY();

        Double peak = fallPeakY.get(uuid);
        if (peak == null || y > peak) {
            fallPeakY.put(uuid, y);
            peak = y;
        }

        double fallDist = peak - y;
        boolean onSolid = isLandingOnSolid(to);
        boolean onEntity = isLandingOnEntity(player, to);
        boolean downwardStopped = (fromY > y && player.getVelocity().getY() >= -0.05);

        if (fallDist >= 2.5) {
            boolean landed = player.isOnGround() || onSolid || onEntity || downwardStopped;
            if (landed) {
                fallPeakY.put(uuid, y);
                player.setFallDistance(0f);
                checkAndTriggerSkyfall(player, data);
            }
        } else if (player.isOnGround() || onSolid || onEntity) {
            fallPeakY.put(uuid, y);
        }
    }

    private boolean isLandingOnSolid(Location to) {
        if (to == null || to.getWorld() == null) return false;
        double x = to.getX();
        double y = to.getY();
        double z = to.getZ();
        double[] dx = {0, -0.28, 0.28, 0, 0};
        double[] dz = {0, 0, 0, -0.28, 0.28};
        for (int i = 0; i < 5; i++) {
            Location check = new Location(to.getWorld(), x + dx[i], y - 0.1, z + dz[i]);
            org.bukkit.block.Block b = check.getBlock();
            if (b.getType().isSolid()) {
                return true;
            }
        }
        return false;
    }

    private boolean isLandingOnEntity(Player player, Location to) {
        if (to == null || to.getWorld() == null) return false;
        double px = to.getX();
        double py = to.getY();
        double pz = to.getZ();
        for (LivingEntity e : to.getWorld().getNearbyLivingEntities(to, 1.2, 2.2, 1.2)) {
            if (e.getUniqueId().equals(player.getUniqueId())) continue;
            if (e instanceof Player tp && tp.getGameMode() == GameMode.SPECTATOR) continue;
            double ey = e.getLocation().getY();
            if (py >= ey && py - ey <= 2.2) {
                double distXZ = Math.hypot(px - e.getLocation().getX(), pz - e.getLocation().getZ());
                if (distXZ <= 1.0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkAndTriggerSkyfall(Player player, PlayerData data) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = skyfallCooldowns.get(uuid);
        if (last != null && now - last < SKYFALL_COOLDOWN_MS) {
            return;
        }
        skyfallCooldowns.put(uuid, now);
        triggerSkyfall(player, data);
    }

    private void triggerSkyfall(Player player, PlayerData data) {
        Location loc = player.getLocation();
        loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 1, 0, 0, 0, 0);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 4, 0.5, 0.5, 0.5, 0.05);
        double dmgBase = data.getEffectiveDamage();
        double mult = dmgBase <= 20.0 ? 2.0 : 1.5;
        double maxDmg = Math.max(dmgBase * mult * Math.max(data.skyfall, 1), 1.0);
        com.rounds.teams.TeamManager.GameTeam myTeam = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        for (LivingEntity entity : loc.getNearbyLivingEntities(4.0)) {
            if (entity.getUniqueId().equals(player.getUniqueId())) continue;
            if (entity instanceof Player tp) {
                if (tp.getGameMode() == GameMode.SPECTATOR || tp.getGameMode() == GameMode.CREATIVE) continue;
                if (!plugin.getGameManager().isTargetable(tp)) continue;
                com.rounds.teams.TeamManager.GameTeam targetTeam = plugin.getTeamManager().getPlayerTeam(tp.getUniqueId());
                if (myTeam != null && targetTeam != null && myTeam == targetTeam) continue;
            }
            try {
                double dist = Math.min(entity.getLocation().distance(loc), entity.getLocation().clone().add(0, 0.9, 0).distance(loc));
                if (dist <= 4.0 && maxDmg > 0) {
                    double hpBefore = entity.getHealth();
                    entity.setNoDamageTicks(0);
                    entity.damage(maxDmg);
                    if (entity.getHealth() >= hpBefore && maxDmg > 0.0 && !entity.isDead()) {
                        com.rounds.entity.RoundsEntities.safeSetHealth(entity, hpBefore - maxDmg);
                    }
                    if (entity instanceof Player victim) {
                        com.rounds.entity.RoundsEntities.applyHitRedEffects(victim, loc, victim.getLocation());
                        if (plugin.getBotManager() != null) {
                            var victimBot = plugin.getBotManager().getBot(victim.getName());
                            if (victimBot != null) victimBot.onAttacked(loc, player.getUniqueId());
                        }
                    }
                }
            } catch (IllegalArgumentException ignored) {}
        }
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
    }

    // ==================== Bullet Time ====================

    public static boolean isBulletTimeActiveFor(UUID bulletOwner) {
        if (System.currentTimeMillis() >= bulletTimeUntil) return false;
        if (bulletTimeOwner == null) return false;
        return !bulletTimeOwner.equals(bulletOwner);
    }

    private void triggerBulletTime(Player owner, PlayerData data) {
        bulletTimeUsedThisRound.add(owner.getUniqueId());
        bulletTimeOwner = owner.getUniqueId();
        bulletTimeUntil = System.currentTimeMillis() + 5000L;

        Location loc = owner.getLocation();
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.5f);
        loc.getWorld().playSound(loc, Sound.BLOCK_BELL_RESONATE, 1.2f, 0.6f);

        owner.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SPEED, 100, 1, false, false, true));
        owner.sendActionBar(org.bukkit.ChatColor.GOLD + com.rounds.util.Messages.get("card.bullet-time-active"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getUniqueId().equals(owner.getUniqueId()) || !p.isValid() || p.isDead()) continue;
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOW, 100, 5, false, false, true));
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.DARKNESS, 100, 0, false, false, false));
            p.sendTitle(org.bukkit.ChatColor.DARK_GRAY + "[ " + org.bukkit.ChatColor.GRAY + com.rounds.util.Messages.get("card.bullet-time-slowed") + org.bukkit.ChatColor.DARK_GRAY + " ]", "", 5, 40, 10);
        }
    }

    // ==================== Temporal Recall ====================

    public static boolean tryTemporalRecall(Player player, PlayerData data) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = recallCooldowns.get(uuid);
        if (last != null && now - last < RECALL_COOLDOWN_MS) {
            long remaining = (RECALL_COOLDOWN_MS - (now - last)) / 1000 + 1;
            player.sendActionBar(org.bukkit.ChatColor.YELLOW + com.rounds.util.Messages.get("card.recall-cooldown", remaining));
            return false;
        }

        Deque<PlayerSnapshot> deque = playerHistory.get(uuid);
        if (deque == null || deque.isEmpty()) return false;

        long targetTime = now - 3000L;
        PlayerSnapshot best = null;
        long minDiff = Long.MAX_VALUE;
        for (PlayerSnapshot s : deque) {
            long diff = Math.abs(s.time - targetTime);
            if (diff < minDiff) {
                minDiff = diff;
                best = s;
            }
        }
        if (best == null) return false;

        recallCooldowns.put(uuid, now);
        Location from = player.getLocation();
        if (from.getWorld() != null) {
            from.getWorld().spawnParticle(Particle.PORTAL, from, 40, 0.5, 1.0, 0.5, 0.5);
            from.getWorld().spawnParticle(Particle.FLASH, from, 1);
            from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);
        }

        player.teleport(best.loc);

        if (best.loc.getWorld() != null) {
            best.loc.getWorld().spawnParticle(Particle.PORTAL, best.loc, 40, 0.5, 1.0, 0.5, 0.5);
            best.loc.getWorld().spawnParticle(Particle.FLASH, best.loc, 1);
            best.loc.getWorld().playSound(best.loc, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.0f, 1.2f);
        }

        if (best.hp > player.getHealth()) {
            player.setHealth(Math.min(best.hp, data.getMaxHealth()));
        }

        data.ammo = data.maxAmmo;
        com.rounds.item.GunItem.cancelReload(uuid);
        player.sendActionBar(org.bukkit.ChatColor.AQUA + com.rounds.util.Messages.get("card.recall-success"));
        return true;
    }

    // ==================== Spite Razorwire ====================

    private void triggerRazorwire(Player player, PlayerData data) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastRazorwire.get(uuid);
        if (last != null && now - last < 250L) return;
        lastRazorwire.put(uuid, now);

        int shards = (int) Math.max(data.razorwireShards, 4);
        double radius = data.razorwireRadius > 0 ? data.razorwireRadius : 5.0;
        double dmgMult = data.razorwireDamage > 0 ? data.razorwireDamage : 0.25;
        double shardDamage = Math.max(data.getEffectiveDamage() * dmgMult, 0.5);
        boolean homing = data.razorwireHoming > 0;
        double lifesteal = data.razorwireLifesteal;

        Location center = player.getEyeLocation();
        org.bukkit.World world = center.getWorld();
        if (world == null) return;

        world.playSound(center, Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.5f);
        world.playSound(center, Sound.ITEM_TRIDENT_THROW, 0.7f, 1.8f);

        List<LivingEntity> enemies = new ArrayList<>();
        com.rounds.teams.TeamManager.GameTeam myTeam = plugin.getTeamManager().getPlayerTeam(uuid);
        for (org.bukkit.entity.Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof LivingEntity living && !e.getUniqueId().equals(uuid) && !e.isDead() && e.isValid()) {
                if (living instanceof Player other) {
                    if (other.getGameMode() == GameMode.SPECTATOR || other.getGameMode() == GameMode.CREATIVE) continue;
                    if (!plugin.getGameManager().isTargetable(other)) continue;
                    com.rounds.teams.TeamManager.GameTeam otherTeam = plugin.getTeamManager().getPlayerTeam(other.getUniqueId());
                    if (myTeam != null && otherTeam != null && myTeam == otherTeam) continue;
                }
                enemies.add(living);
            }
        }

        double totalDealt = 0.0;
        if (!enemies.isEmpty()) {
            int targetsCount = enemies.size();
            for (int i = 0; i < shards; i++) {
                LivingEntity target = enemies.get(i % targetsCount);
                Location tEye = target.getEyeLocation();
                org.bukkit.util.Vector dir = tEye.toVector().subtract(center.toVector()).normalize();

                if (!homing) {
                    var ray = world.rayTraceBlocks(center, dir, center.distance(tEye));
                    if (ray != null && ray.getHitBlock() != null) continue;
                }

                spawnShardBeam(world, center, tEye);

                double hpBefore = target.getHealth();
                target.setNoDamageTicks(0);
                target.damage(shardDamage, player);
                if (target.getHealth() >= hpBefore && shardDamage > 0.0 && !target.isDead()) {
                    com.rounds.entity.RoundsEntities.safeSetHealth(target, hpBefore - shardDamage);
                }
                totalDealt += shardDamage;
                com.rounds.stats.MatchStatsManager.recordDamageDealt(uuid, target.getUniqueId(), shardDamage);
            }
        } else {
            for (int i = 0; i < shards; i++) {
                double angle = 2 * Math.PI * i / shards;
                org.bukkit.util.Vector v = new org.bukkit.util.Vector(Math.cos(angle), 0.2, Math.sin(angle)).normalize().multiply(radius * 0.7);
                world.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(v), 1);
                world.spawnParticle(Particle.CRIT, center.clone().add(v), 2, 0.1, 0.1, 0.1, 0.05);
            }
        }

        if (totalDealt > 0 && lifesteal > 0) {
            double heal = totalDealt * lifesteal;
            player.setHealth(Math.min(player.getHealth() + heal, data.getMaxHealth()));
            world.spawnParticle(Particle.HEART, player.getEyeLocation(), 2, 0.3, 0.3, 0.3, 0.05);
        }
    }

    private void spawnShardBeam(org.bukkit.World world, Location from, Location to) {
        org.bukkit.util.Vector step = to.toVector().subtract(from.toVector());
        double dist = step.length();
        if (dist <= 0) return;
        step.normalize().multiply(0.5);
        Location cur = from.clone();
        for (double d = 0; d < dist; d += 0.5) {
            cur.add(step);
            world.spawnParticle(Particle.CRIT, cur, 1, 0, 0, 0, 0);
        }
    }
}

package com.rounds.listener;

import com.rounds.RoundsPlugin;
import com.rounds.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

    private static LegendaryEffects instance;
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

    private static final Map<UUID, List<DecaySlice>> decaySlices = new ConcurrentHashMap<>();
    private static final Set<UUID> isApplyingDecay = ConcurrentHashMap.newKeySet();

    public static void clearDecay(UUID uuid) {
        decaySlices.remove(uuid);
        isApplyingDecay.remove(uuid);
    }

    public static boolean isDecayTicking(UUID uuid) {
        return uuid != null && isApplyingDecay.contains(uuid);
    }

    public static boolean hasDecay(UUID uuid) {
        if (uuid == null || instance == null || instance.plugin == null) return false;
        PlayerData data = instance.plugin.getPlayerDataManager().getPlayerDataIfPresent(uuid);
        return data != null && data.decay > 0;
    }

    public static void addDecayDamage(Player player, double totalDamage, UUID attackerId) {
        if (player == null || !player.isOnline() || player.isDead() || totalDamage <= 0.0) return;
        if (instance == null || instance.plugin == null) return;
        PlayerData data = instance.plugin.getPlayerDataManager().getPlayerDataIfPresent(player.getUniqueId());
        if (data == null || data.decay <= 0) return;

        player.setNoDamageTicks(0);
        try { player.playHurtAnimation(0.0f); } catch (Throwable ignored) {}
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.RECORDS, 0.7f, 1.2f);

        if (attackerId != null) {
            com.rounds.stats.MatchStatsManager.recordLastDamager(player.getUniqueId(), attackerId);
        }

        if (data.bloodFurry > 0) {
            rageUntil.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
        }

        int totalSeconds = Math.max(1, (int) Math.round(data.decay));
        DecaySlice slice = new DecaySlice(totalDamage, totalSeconds, attackerId);
        decaySlices.computeIfAbsent(player.getUniqueId(), k -> Collections.synchronizedList(new ArrayList<>())).add(slice);
    }

    public LegendaryEffects(RoundsPlugin plugin) {
        this.plugin = plugin;
        instance = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickDecay, 1L, 1L);
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
        decaySlices.clear();
        isApplyingDecay.clear();
        secondWindUses.clear();
        skyfallCooldowns.clear();
        fallPeakY.clear();
        bulletTimeUsedThisRound.clear();
        bulletTimeUntil = 0L;
        bulletTimeOwner = null;
        lastRazorwire.clear();
    }

    public static void resetAll() {
        decaySlices.clear();
        isApplyingDecay.clear();
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

    public static Long getRecallCooldown(UUID uuid) {
        return recallCooldowns.get(uuid);
    }

    public static boolean isBulletTimeUsedThisRound(UUID uuid) {
        return bulletTimeUsedThisRound.contains(uuid);
    }

    public static void resetBulletTime(UUID uuid) {
        bulletTimeUsedThisRound.remove(uuid);
    }

    public static void onDirectDamage(Player victim, PlayerData victimData, double damageDealt, UUID attackerId) {
        if (victim == null || victimData == null || victim.isDead()) return;
        UUID uuid = victim.getUniqueId();
        com.rounds.stats.MatchStatsManager.recordDamageReceived(uuid, damageDealt);

        if (victimData.bloodFurry > 0) {
            rageUntil.put(uuid, System.currentTimeMillis() + 3000L);
        }

        if (victimData.spiteRazorwire > 0) {
            try {
                triggerRazorwire(victim, victimData);
            } catch (Throwable razorError) {
                // Must never abort the surrounding damage pipeline
            }
        }

        if (victimData.bulletTime > 0
                && !bulletTimeUsedThisRound.contains(uuid)
                && (victim.getHealth() <= victimData.getMaxHealth() * 0.20 || victim.getHealth() <= 0.5)) {
            victim.setHealth(Math.max(victimData.getMaxHealth() * 0.20, 2.0));
            triggerBulletTime(victim, victimData);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        boolean isDecayTick = isApplyingDecay.contains(player.getUniqueId());

        if (player.isInvulnerable()) {
            event.setCancelled(true);
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
        if (data == null) return;

        if (!isDecayTick && data.decay > 0 && event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            double incomingDmg = event.getDamage();
            if (incomingDmg > 0.0) {
                event.setCancelled(true);
                UUID attackerId = null;
                if (event instanceof EntityDamageByEntityEvent edbe) {
                    attackerId = edbe.getDamager().getUniqueId();
                } else {
                    attackerId = com.rounds.stats.MatchStatsManager.getLastDamager(player.getUniqueId());
                }
                addDecayDamage(player, incomingDmg, attackerId);
            }
            return;
        }

        // Check fatal or low-health Bullet Time save
        if (data.bulletTime > 0
                && !bulletTimeUsedThisRound.contains(player.getUniqueId())
                && (event.getFinalDamage() >= player.getHealth()
                || event.getDamage() >= player.getHealth()
                || (player.getHealth() - event.getFinalDamage()) <= data.getMaxHealth() * 0.20
                || (player.getHealth() - event.getDamage()) <= data.getMaxHealth() * 0.20)) {
            event.setCancelled(true);
            player.setHealth(Math.max(data.getMaxHealth() * 0.20, 2.0));
            clearDecay(player.getUniqueId());
            triggerBulletTime(player, data);
            return;
        }

        int maxUses = (int) Math.round(data.secondWind);
        int used = secondWindUses.getOrDefault(player.getUniqueId(), 0);
        if (maxUses > 0
                && used < maxUses
                && plugin.getGameManager().isParticipant(player.getUniqueId())
                && event.getFinalDamage() >= player.getHealth()) {
            secondWindUses.put(player.getUniqueId(), used + 1);
            clearDecay(player.getUniqueId());
            event.setCancelled(true);
            player.setHealth(1.0);
            player.setFallDistance(0);
            player.setFireTicks(0);
            player.setInvulnerable(true);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, SoundCategory.RECORDS, 0.7f, 1.2f);
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                if (player.isOnline() && player.isValid()) {
                    player.setInvulnerable(false);
                }
            }, 40L);
            return;
        }

        if (event.isCancelled()) return;

        // Record damage received for match stats
        com.rounds.stats.MatchStatsManager.recordDamageReceived(player.getUniqueId(), event.getFinalDamage());

        if (data.bloodFurry > 0) {
            rageUntil.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
        }

        if (data.skyfall > 0
                && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            player.setFallDistance(0);
            fallPeakY.put(player.getUniqueId(), player.getLocation().getY());
            checkAndTriggerSkyfall(player, data);
        }

        if (!event.isCancelled() && event.getFinalDamage() > 0) {
            org.bukkit.entity.Entity attacker = (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent edbe) ? edbe.getDamager() : null;
            data.executeTriggers(com.rounds.cards.CardTrigger.ON_DAMAGE_TAKEN, player, attacker, event.getFinalDamage());
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
                    if (!entity.isInvulnerable() && entity.getHealth() >= hpBefore && maxDmg > 0.0 && !entity.isDead()) {
                        com.rounds.entity.RoundsEntities.safeSetHealth(entity, hpBefore - maxDmg);
                    }
                    com.rounds.stats.MatchStatsManager.recordDamageDealt(player.getUniqueId(), entity.getUniqueId(), maxDmg);
                    com.rounds.stats.MatchStatsManager.recordLastDamager(entity.getUniqueId(), player.getUniqueId());
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
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.RECORDS, 0.7f, 0.8f);
    }

    // ==================== Bullet Time ====================

    public static boolean isBulletTimeActiveFor(UUID entityId) {
        if (System.currentTimeMillis() >= bulletTimeUntil) return false;
        if (bulletTimeOwner == null) return false;
        if (bulletTimeOwner.equals(entityId)) return false;
        if (instance != null && instance.plugin != null) {
            com.rounds.teams.TeamManager.GameTeam myTeam = instance.plugin.getTeamManager().getPlayerTeam(bulletTimeOwner);
            com.rounds.teams.TeamManager.GameTeam otherTeam = instance.plugin.getTeamManager().getPlayerTeam(entityId);
            if (myTeam != null && otherTeam != null && myTeam == otherTeam) return false;
        }
        return true;
    }

    public static void triggerBulletTime(Player owner, PlayerData data) {
        if (owner == null || !owner.isOnline() || owner.isDead()) return;
        if (bulletTimeUsedThisRound.contains(owner.getUniqueId())) return;
        bulletTimeUsedThisRound.add(owner.getUniqueId());
        bulletTimeOwner = owner.getUniqueId();
        bulletTimeUntil = System.currentTimeMillis() + 5000L;

        // Invulnerability for 1.0s (20 ticks)
        owner.setInvulnerable(true);
        owner.setNoDamageTicks(20);
        if (instance != null && instance.plugin != null) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(instance.plugin, () -> {
                // Reset unconditionally while online (even mid-death/respawn): a lingering
                // invulnerable flag would let the 1-second blessing "stack" indefinitely.
                if (owner.isOnline()) {
                    owner.setInvulnerable(false);
                }
            }, 20L);
        }

        Location loc = owner.getLocation();
        if (loc.getWorld() != null) {
            loc.getWorld().playSound(loc, Sound.ITEM_TOTEM_USE, SoundCategory.RECORDS, 0.7f, 1.2f);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.RECORDS, 0.7f, 0.5f);
            loc.getWorld().playSound(loc, Sound.BLOCK_BELL_RESONATE, SoundCategory.RECORDS, 0.7f, 0.6f);
            try {
                loc.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, loc.clone().add(0, 1.0, 0), 25, 0.3, 0.5, 0.3, 0.15);
                loc.getWorld().spawnParticle(Particle.TOTEM, loc.clone().add(0, 1.0, 0), 20, 0.3, 0.5, 0.3, 0.2);
            } catch (Throwable ignored) {}
        }

        // Owner: +50% movement speed (Speed III) for 5 seconds (100 ticks)
        owner.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SPEED, 100, 2, false, false, true));
        owner.sendTitle(ChatColor.GOLD + "§lЗАМЕДЛЕНИЕ ВРЕМЕНИ",
                ChatColor.YELLOW + "Бессмертие 1 сек! Враги и пули замедлены на 5 сек!", 5, 40, 10);
        owner.sendActionBar(ChatColor.GOLD + com.rounds.util.Messages.get("card.bullet-time-active"));

        com.rounds.teams.TeamManager.GameTeam myTeam = (instance != null && instance.plugin != null)
                ? instance.plugin.getTeamManager().getPlayerTeam(owner.getUniqueId()) : null;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getUniqueId().equals(owner.getUniqueId()) || !p.isValid() || p.isDead()) continue;
            if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
            if (myTeam != null && instance != null && instance.plugin != null) {
                com.rounds.teams.TeamManager.GameTeam otherTeam = instance.plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
                if (otherTeam != null && myTeam == otherTeam) continue;
            }
            // SLOW VII (amplifier 6) вместо SLOW VI
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOW, 100, 6, false, false, true));
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.DARKNESS, 100, 0, false, false, false));
            p.sendTitle(ChatColor.DARK_GRAY + "[ " + ChatColor.RED + "§lВРЕМЯ ЗАМЕДЛЕНО" + ChatColor.DARK_GRAY + " ]",
                    ChatColor.GRAY + com.rounds.util.Messages.get("card.bullet-time-slowed"), 5, 40, 10);
            p.playSound(p.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, SoundCategory.RECORDS, 0.7f, 0.5f);
        }

        if (instance != null && instance.plugin != null) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(instance.plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.isOnline() && p.isValid()) {
                        p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW);
                        p.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS);
                    }
                }
            }, 100L);
        }
    }

    // ==================== Temporal Recall ====================

    public static boolean tryTemporalRecall(Player player, PlayerData data) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = recallCooldowns.get(uuid);
        if (last != null && now - last < RECALL_COOLDOWN_MS) {
            return false;
        }

        Deque<PlayerSnapshot> deque = playerHistory.get(uuid);
        Location from = player.getLocation();
        PlayerSnapshot best = null;
        if (deque != null && !deque.isEmpty()) {
            long targetTime = now - 3000L;
            long minDiff = Long.MAX_VALUE;
            for (PlayerSnapshot s : deque) {
                long diff = Math.abs(s.time - targetTime);
                if (diff < minDiff) {
                    minDiff = diff;
                    best = s;
                }
            }
        }
        if (best == null) {
            best = new PlayerSnapshot(now, from.clone(), player.getHealth());
        }

        recallCooldowns.put(uuid, now);

        if (from.getWorld() != null) {
            try {
                from.getWorld().spawnParticle(Particle.PORTAL, from, 50, 0.5, 1.0, 0.5, 0.5);
                from.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, from.clone().add(0, 1.0, 0), 15, 0.2, 0.4, 0.2, 0.1);
            } catch (Throwable ignored) {}
            from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.RECORDS, 0.7f, 1.4f);
        }

        player.teleport(best.loc);

        if (best.loc.getWorld() != null) {
            try {
                best.loc.getWorld().spawnParticle(Particle.PORTAL, best.loc, 50, 0.5, 1.0, 0.5, 0.5);
                best.loc.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, best.loc.clone().add(0, 1.0, 0), 15, 0.2, 0.4, 0.2, 0.1);
            } catch (Throwable ignored) {}
            best.loc.getWorld().playSound(best.loc, Sound.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.RECORDS, 0.7f, 1.2f);
            best.loc.getWorld().playSound(best.loc, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.RECORDS, 0.7f, 1.8f);

            if (from.getWorld() != null && from.getWorld().equals(best.loc.getWorld())) {
                org.bukkit.util.Vector line = best.loc.toVector().subtract(from.toVector());
                double dist = line.length();
                if (dist > 0.5) {
                    line.normalize().multiply(0.4);
                    Location cur = from.clone().add(0, 1.0, 0);
                    for (double d = 0; d < dist; d += 0.4) {
                        cur.add(line);
                        best.loc.getWorld().spawnParticle(Particle.PORTAL, cur, 2, 0.1, 0.1, 0.1, 0.05);
                    }
                }
            }
        }

        double restoredHp = 0;
        if (best.hp > player.getHealth()) {
            restoredHp = Math.min(best.hp, data.getMaxHealth()) - player.getHealth();
            player.setHealth(Math.min(best.hp, data.getMaxHealth()));
            player.getWorld().spawnParticle(Particle.HEART, player.getEyeLocation(), 5, 0.3, 0.3, 0.3, 0.05);
        }

        data.ammo = data.maxAmmo;
        com.rounds.item.GunItem.cancelReload(uuid);

        player.sendTitle(ChatColor.AQUA + "§l«ОТКАТ ВО ВРЕМЕНИ»",
                ChatColor.WHITE + com.rounds.util.Messages.get("card.recall-success"), 5, 30, 10);

        if (restoredHp > 0.05) {
            player.sendActionBar(ChatColor.AQUA + com.rounds.util.Messages.get("card.recall-success")
                    + " §a(+" + String.format(java.util.Locale.US, "%.1f", restoredHp) + " HP)");
        } else {
            player.sendActionBar(ChatColor.AQUA + com.rounds.util.Messages.get("card.recall-success")
                    + " §e(" + (int) data.ammo + "/" + (int) data.maxAmmo + ")");
        }
        return true;
    }

    // ==================== Spite Razorwire ====================

    public static void triggerRazorwire(Player player, PlayerData data) {
        if (player == null || data == null) return;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastRazorwire.get(uuid);
        if (last != null && now - last < 200L) return;
        lastRazorwire.put(uuid, now);

        int shards = (int) Math.max(data.razorwireShards, 4);
        double radius = data.razorwireRadius > 0 ? data.razorwireRadius : 5.0;
        double dmgMult = data.razorwireDamage > 0 ? data.razorwireDamage : 0.25;
        double shardDamage = Math.max(data.getEffectiveDamage() * dmgMult, 0.5);
        boolean homing = data.razorwireHoming > 0;
        double lifesteal = data.razorwireLifesteal;

        Location center = player.getEyeLocation();
        org.bukkit.World world = center.getWorld();
        if (world == null || !isFiniteLocation(center)) return;

        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.RECORDS, 0.7f, 1.2f);
        world.playSound(center, Sound.ITEM_TRIDENT_THROW, SoundCategory.RECORDS, 0.7f, 1.8f);
        world.playSound(center, Sound.ENTITY_IRON_GOLEM_HURT, SoundCategory.RECORDS, 0.7f, 1.5f);

        List<LivingEntity> enemies = new ArrayList<>();
        com.rounds.teams.TeamManager.GameTeam myTeam = (instance != null && instance.plugin != null)
                ? instance.plugin.getTeamManager().getPlayerTeam(uuid) : null;

        for (org.bukkit.entity.Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof LivingEntity living && !e.getUniqueId().equals(uuid) && !e.isDead() && e.isValid()) {
                if (living instanceof Player other) {
                    if (other.getGameMode() == GameMode.SPECTATOR || other.getGameMode() == GameMode.CREATIVE) continue;
                    if (myTeam != null && instance != null && instance.plugin != null) {
                        com.rounds.teams.TeamManager.GameTeam otherTeam = instance.plugin.getTeamManager().getPlayerTeam(other.getUniqueId());
                        if (otherTeam != null && myTeam == otherTeam) continue;
                    }
                }
                enemies.add(living);
            }
        }

        double totalDealt = 0.0;
        if (!enemies.isEmpty()) {
            int targetsCount = enemies.size();
            for (int i = 0; i < shards; i++) {
                try {
                    LivingEntity target = enemies.get(i % targetsCount);
                    Location tEye = target.getEyeLocation();
                    // Non-finite entity positions (dead/despawned targets at the moment of the hit,
                    // transient respawn states) previously produced "x not finite" rayTraceBlocks
                    // exceptions that ABORTED the whole damage pipeline mid-event.
                    if (!isFiniteLocation(tEye)) continue;
                    org.bukkit.util.Vector delta = tEye.toVector().subtract(center.toVector());
                    double len = delta.length();
                    if (!Double.isFinite(len) || len < 1e-6) continue;
                    org.bukkit.util.Vector dir = delta.multiply(1.0 / len);

                    if (!homing) {
                        var ray = world.rayTraceBlocks(center, dir, len);
                        if (ray != null && ray.getHitBlock() != null) continue;
                    }

                    spawnShardBeam(world, center, tEye);

                    double hpBefore = target.getHealth();
                    target.setNoDamageTicks(0);
                    target.damage(shardDamage, player);
                    if (!target.isInvulnerable() && target.getHealth() >= hpBefore && shardDamage > 0.0 && !target.isDead()) {
                        com.rounds.entity.RoundsEntities.safeSetHealth(target, hpBefore - shardDamage);
                    }
                    target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, tEye, 3, 0.2, 0.2, 0.2, 0.05);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.RECORDS, 0.7f, 1.2f);
                    totalDealt += shardDamage;
                    com.rounds.stats.MatchStatsManager.recordDamageDealt(uuid, target.getUniqueId(), shardDamage);
                    com.rounds.stats.MatchStatsManager.recordLastDamager(target.getUniqueId(), uuid);
                } catch (Throwable shardError) {
                    // A single broken shard must never abort the razor volley or the surrounding damage event
                }
            }
        } else {
            // Burst around player when no enemies in range
            for (int i = 0; i < shards; i++) {
                double angle = 2 * Math.PI * i / shards;
                org.bukkit.util.Vector v = new org.bukkit.util.Vector(Math.cos(angle), 0.2, Math.sin(angle)).normalize().multiply(radius * 0.7);
                world.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(v), 1);
                world.spawnParticle(Particle.CRIT, center.clone().add(v), 4, 0.15, 0.15, 0.15, 0.05);
            }
        }

        if (totalDealt > 0 && lifesteal > 0) {
            double heal = totalDealt * lifesteal;
            player.setHealth(Math.min(player.getHealth() + heal, data.getMaxHealth()));
            world.spawnParticle(Particle.HEART, player.getEyeLocation(), 3, 0.3, 0.3, 0.3, 0.05);
        }
    }

    private static void spawnShardBeam(org.bukkit.World world, Location from, Location to) {
        org.bukkit.util.Vector step = to.toVector().subtract(from.toVector());
        double dist = step.length();
        if (dist <= 0) return;
        step.normalize().multiply(0.4);
        Location cur = from.clone();
        for (double d = 0; d < dist; d += 0.4) {
            cur.add(step);
            world.spawnParticle(Particle.CRIT, cur, 2, 0.05, 0.05, 0.05, 0.02);
        }
        world.spawnParticle(Particle.SWEEP_ATTACK, to, 1, 0, 0, 0, 0);
    }

    /** Rejects NaN/Infinity positions and missing worlds so raytraces can never throw "not finite". */
    private void tickDecay() {
        if (decaySlices.isEmpty() || Bukkit.getServer() == null) return;
        for (Map.Entry<UUID, List<DecaySlice>> entry : decaySlices.entrySet()) {
            UUID uuid = entry.getKey();
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline() || p.isDead()) {
                decaySlices.remove(uuid);
                continue;
            }
            List<DecaySlice> slices = entry.getValue();
            if (slices == null || slices.isEmpty()) {
                decaySlices.remove(uuid);
                continue;
            }

            double totalTickDmg = 0.0;
            UUID lastAttacker = null;
            synchronized (slices) {
                Iterator<DecaySlice> it = slices.iterator();
                while (it.hasNext()) {
                    DecaySlice s = it.next();
                    s.ticksUntilNextSecond--;
                    if (s.ticksUntilNextSecond <= 0) {
                        totalTickDmg += s.damagePerSecond;
                        if (s.attackerId != null) lastAttacker = s.attackerId;
                        s.secondsRemaining--;
                        if (s.secondsRemaining > 0) {
                            s.ticksUntilNextSecond = 20; // 1 second interval
                        } else {
                            it.remove();
                        }
                    }
                }
                if (slices.isEmpty()) {
                    decaySlices.remove(uuid);
                }
            }

            if (totalTickDmg > 0.0 && p.isOnline() && !p.isDead()) {
                Location loc = p.getLocation().add(0, 1.0, 0);
                loc.getWorld().spawnParticle(Particle.REDSTONE, loc, 6, 0.3, 0.4, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(45, 140, 35), 0.9f));

                isApplyingDecay.add(uuid);
                try {
                    p.setNoDamageTicks(0);
                    if (lastAttacker != null) {
                        com.rounds.stats.MatchStatsManager.recordLastDamager(p.getUniqueId(), lastAttacker);
                    }

                    double curHp = p.getHealth();
                    double newHp = curHp - totalTickDmg;

                    try { p.playHurtAnimation(0.0f); } catch (Throwable ignored) {}
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.RECORDS, 0.5f, 1.3f);
                    com.rounds.entity.RoundsEntities.applyHitRedEffects(p, p.getLocation().clone().add(0, -1, 0), p.getLocation());

                    if (newHp <= 0.0) {
                        Player attacker = (lastAttacker != null) ? Bukkit.getPlayer(lastAttacker) : null;
                        if (attacker != null && attacker.isOnline()) {
                            p.damage(1_000_000.0, attacker);
                        } else {
                            p.damage(1_000_000.0);
                        }
                    } else {
                        com.rounds.entity.RoundsEntities.safeSetHealth(p, newHp);
                    }
                } catch (Throwable ignored) {
                } finally {
                    isApplyingDecay.remove(uuid);
                }
            }
        }
    }

    private static boolean isFiniteLocation(Location l) {
        return l != null && l.getWorld() != null
                && Double.isFinite(l.getX()) && Double.isFinite(l.getY()) && Double.isFinite(l.getZ());
    }
}

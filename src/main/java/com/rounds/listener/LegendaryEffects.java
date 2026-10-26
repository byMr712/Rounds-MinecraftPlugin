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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LegendaryEffects implements Listener {

    private final RoundsPlugin plugin;
    private static final Map<UUID, Long> rageUntil = new HashMap<>();
    private static final Map<UUID, Integer> secondWindUses = new HashMap<>();
    private static final Map<UUID, Double> fallPeakY = new HashMap<>();
    private static final Map<UUID, Long> skyfallCooldowns = new HashMap<>();
    private static final long SKYFALL_COOLDOWN_MS = 2000L;

    public LegendaryEffects(RoundsPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean isRaging(UUID uuid) {
        Long until = rageUntil.get(uuid);
        return until != null && System.currentTimeMillis() < until;
    }

    public static void clearRoundState() {
        secondWindUses.clear();
        skyfallCooldowns.clear();
        fallPeakY.clear();
    }

    public static void resetAll() {
        rageUntil.clear();
        secondWindUses.clear();
        fallPeakY.clear();
        skyfallCooldowns.clear();
    }

    public static void clearPlayer(UUID uuid) {
        if (uuid == null) return;
        rageUntil.remove(uuid);
        secondWindUses.remove(uuid);
        fallPeakY.remove(uuid);
        skyfallCooldowns.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());

        if (data.bloodFurry > 0) {
            rageUntil.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
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
}

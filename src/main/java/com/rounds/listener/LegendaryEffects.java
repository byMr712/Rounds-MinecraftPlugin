package com.rounds.listener;

import com.rounds.RoundsPlugin;
import com.rounds.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
    private static final Set<UUID> secondWindUsed = new HashSet<>();
    private static final Map<UUID, Double> fallPeakY = new HashMap<>();

    public LegendaryEffects(RoundsPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean isRaging(UUID uuid) {
        Long until = rageUntil.get(uuid);
        return until != null && System.currentTimeMillis() < until;
    }

    public static void clearRoundState() {
        secondWindUsed.clear();
    }

    public static void resetAll() {
        rageUntil.clear();
        secondWindUsed.clear();
        fallPeakY.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());

        if (data.bloodFurry > 0) {
            rageUntil.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
        }

        if (data.secondWind > 0
                && !secondWindUsed.contains(player.getUniqueId())
                && plugin.getGameManager().isParticipant(player.getUniqueId())
                && event.getFinalDamage() >= player.getHealth()) {
            secondWindUsed.add(player.getUniqueId());
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
            fallPeakY.remove(player.getUniqueId());
            triggerSkyfall(player, data);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
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
        if (player.isOnGround()) {
            Double peak = fallPeakY.remove(uuid);
            if (peak != null && peak - y >= 3.0) {
                triggerSkyfall(player, data);
            }
            return;
        }
        fallPeakY.merge(uuid, y, Math::max);
    }

    private void triggerSkyfall(Player player, PlayerData data) {
        Location loc = player.getLocation();
        loc.getWorld().createExplosion(loc, 2.0f, false, false);
        double dmgBase = data.getEffectiveDamage();
        double mult = dmgBase <= 20.0 ? 2.0 : 1.5;
        double maxDmg = Math.max(dmgBase * mult * Math.max(data.skyfall, 1), 1.0);
        for (LivingEntity entity : loc.getNearbyLivingEntities(4.0)) {
            if (entity.getUniqueId().equals(player.getUniqueId())) continue;
            if (entity instanceof Player tp && tp.getGameMode() == GameMode.SPECTATOR) continue;
            try {
                double dist = entity.getLocation().distance(loc);
                double dmg = Math.max(0, maxDmg * (1.0 - dist / 4.0));
                if (dmg > 0) {
                    entity.setNoDamageTicks(0);
                    entity.damage(dmg);
                }
            } catch (IllegalArgumentException ignored) {}
        }
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
    }
}

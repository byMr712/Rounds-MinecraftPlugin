package com.rounds.listener;

import com.rounds.RoundsPlugin;
import com.rounds.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LegendaryEffects implements Listener {

    private final RoundsPlugin plugin;
    private static final Map<UUID, Long> rageUntil = new HashMap<>();
    private static final Set<UUID> secondWindUsed = new HashSet<>();

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
                && event.getCause() == EntityDamageEvent.DamageCause.FALL
                && player.getFallDistance() >= 3.0f) {
            event.setCancelled(true);
            player.setFallDistance(0);
            Location loc = player.getLocation();
            loc.getWorld().createExplosion(loc, 2.0f, false, false);
            for (LivingEntity entity : loc.getNearbyLivingEntities(4.0)) {
                if (entity.getUniqueId().equals(player.getUniqueId())) continue;
                if (entity instanceof Player tp && !plugin.getGameManager().isTargetable(tp)) continue;
                try {
                    double dist = entity.getLocation().distance(loc);
                    double dmg = Math.max(0, 8.0 * (1.0 - dist / 4.0));
                    if (dmg > 0) {
                        entity.setNoDamageTicks(0);
                        entity.damage(dmg);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
        }
    }
}

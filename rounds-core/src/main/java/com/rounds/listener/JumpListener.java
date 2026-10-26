package com.rounds.listener;

import com.rounds.RoundsPlugin;
import com.rounds.player.PlayerData;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class JumpListener implements Listener {

    private final RoundsPlugin plugin;

    public JumpListener(RoundsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
        if (data.jumpHeight <= 0) return;
        double multiplier = Math.sqrt(Math.max(0.0, 1.0 + data.jumpHeight));
        if (!Double.isFinite(multiplier)) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) return;
                Vector v = player.getVelocity();
                if (v.getY() > 0 && Double.isFinite(v.getY())) {
                    double newY = v.getY() * multiplier;
                    if (Double.isFinite(newY)) {
                        v.setY(newY);
                        player.setVelocity(v);
                    }
                }
            }
        }.runTaskLater(plugin, 1L);
    }
}

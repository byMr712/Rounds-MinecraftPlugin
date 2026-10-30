package com.rounds.addon;

import com.rounds.api.RoundsAPI;
import com.rounds.api.card.CardBuilder;
import com.rounds.api.card.CardRarity;
import com.rounds.api.card.CardRole;
import com.rounds.api.event.RoundsBulletHitEvent;
import com.rounds.api.event.RoundsRoundEndEvent;
import com.rounds.api.event.RoundsRoundStartEvent;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class SampleAddon extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        if (!RoundsAPI.isAvailable()) {
            getLogger().warning("Rounds API is not available! Disabling SampleAddon...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register custom card via CardBuilder
        CardBuilder.create(this, "frost_shock")
                .name("ru", "Ледяной Шок")
                .name("en", "Frost Shock")
                .description("ru", "Пули наносят +3 урона и замораживают цель")
                .description("en", "Bullets deal +3 damage and freeze target")
                .rarity(CardRarity.RARE)
                .material(Material.PACKED_ICE)
                .role(CardRole.OFFENSIVE)
                .onBulletHit(event -> {
                    event.setDamage(event.getDamage() + 3.0);
                    if (event.getHitEntity().orElse(null) instanceof LivingEntity target) {
                        target.setFreezeTicks(100);
                    }
                    var loc = event.getHitLocation();
                    loc.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 20, 0.4, 0.4, 0.4, 0.05);
                    loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, SoundCategory.RECORDS, 1.0f, 1.5f);
                })
                .onRoundStart(context -> {
                    context.getPlayer().sendMessage("§b[Frost Shock] Ваши ледяные заряды готовы к бою!");
                })
                .register();

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("[SampleAddon] Frost Shock card registered and Rounds events subscribed!");
    }

    @Override
    public void onDisable() {
        if (RoundsAPI.isAvailable()) {
            RoundsAPI.unregisterAllCards(this);
        }
        getLogger().info("[SampleAddon] SampleAddon disabled and cards unregistered.");
    }

    @EventHandler
    public void onRoundStart(RoundsRoundStartEvent event) {
        getLogger().info("[SampleAddon Event] Round #" + event.getRoundNumber() + " started with " + event.getParticipants().size() + " participants.");
    }

    @EventHandler
    public void onRoundEnd(RoundsRoundEndEvent event) {
        getLogger().info("[SampleAddon Event] Round #" + event.getRoundNumber() + " ended. Winner: " + event.getWinningTeam().orElse("DRAW"));
    }

    @EventHandler
    public void onBulletHit(RoundsBulletHitEvent event) {
        getLogger().info("[SampleAddon Event] Bullet hit " + event.getHitType() + " at " + event.getHitLocation());
    }
}

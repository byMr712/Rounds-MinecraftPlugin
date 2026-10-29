package com.example.thunder;

import com.rounds.api.bot.BotArchetype;
import com.rounds.api.bot.BotView;
import com.rounds.api.card.CardContext;
import com.rounds.api.card.CardRarity;
import com.rounds.api.card.CardRole;
import com.rounds.api.card.CustomCard;
import com.rounds.api.event.RoundsBulletHitEvent;
import com.rounds.api.event.RoundsWeaponShootEvent;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class LightningStrikeCard extends CustomCard {

    private static final String BULLET_TAG = "thunder_addon:charged";
    private static final String SHOTS_KEY = "thunder_addon:shots_count";

    public LightningStrikeCard(Plugin plugin) {
        super(plugin, "lightning_strike", CardRarity.EPIC, Material.LIGHTNING_ROD, CardRole.OFFENSIVE);
        setCustomModelData(1005);
    }

    @Override
    public String getName(String langCode) {
        return "ru".equalsIgnoreCase(langCode) ? "&eГромовержец" : "&eLightning Strike";
    }

    @Override
    public String getDescription(String langCode) {
        return "ru".equalsIgnoreCase(langCode)
                ? "Каждый 3-й выстрел заряжается электричеством, нанося +5 урона при попадании."
                : "Every 3rd shot is electrified, dealing +5 bonus damage on hit.";
    }

    @Override
    public void onRoundStart(CardContext context) {
        context.getData().set(SHOTS_KEY, 0);
    }

    @Override
    public void onShoot(RoundsWeaponShootEvent event) {
        var data = event.getCustomData();
        int currentShots = data.getInt(SHOTS_KEY, 0) + 1;
        data.set(SHOTS_KEY, currentShots);

        if (currentShots % 3 == 0) {
            event.addBulletTag(BULLET_TAG);
            Player player = event.getPlayer();
            player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, SoundCategory.RECORDS, 0.7f, 1.8f);
        }
    }

    @Override
    public void onBulletHit(RoundsBulletHitEvent event) {
        if (event.getHitType() != RoundsBulletHitEvent.HitType.ENTITY) return;
        if (!event.getBullet().hasTag(BULLET_TAG)) return;

        event.setDamage(event.getDamage() + 5.0);

        var loc = event.getHitLocation();
        loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 25, 0.4, 0.4, 0.4, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.RECORDS, 0.7f, 1.5f);
    }

    @Override
    public double evaluateForBot(BotView bot) {
        if (bot == null) return 1.0;
        if (bot.getArchetype() == BotArchetype.AGGRESSIVE_FLANKER 
                || bot.getArchetype() == BotArchetype.TACTICAL_SNIPER) {
            return 1.6;
        }
        return 1.1;
    }
}

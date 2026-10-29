package com.rounds.api.impl;

import com.rounds.RoundsPlugin;
import com.rounds.api.card.CardContext;
import com.rounds.api.card.CardRarity;
import com.rounds.api.card.CardRole;
import com.rounds.api.card.CustomCard;
import com.rounds.api.event.RoundsBulletHitEvent;
import com.rounds.player.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;

public class YamlCustomCardLoader {

    private final RoundsPlugin plugin;

    public YamlCustomCardLoader(RoundsPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadCustomCards() {
        File customDir = new File(plugin.getDataFolder(), "cards/custom");
        if (!customDir.exists()) {
            customDir.mkdirs();
            return;
        }

        loadFromDirectory(customDir);
    }

    private void loadFromDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                loadFromDirectory(file);
                continue;
            }
            if (!file.getName().endsWith(".yml")) continue;

            try {
                loadCardFile(file);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load custom YAML card from " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private void loadCardFile(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // 1. Resolve ID: id field has priority, fallback to filename without .yml
        String resolvedId = config.getString("id");
        if (resolvedId == null || resolvedId.isBlank()) {
            String fname = file.getName();
            resolvedId = fname.substring(0, fname.length() - 4);
        }
        String cleanId = "custom/" + resolvedId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");

        // Localized names and descriptions
        Map<String, String> names = loadLocalizedMap(config, "name");
        Map<String, String> descriptions = loadLocalizedMap(config, "description");

        Material material = parseMaterial(config.getString("material", "PAPER"));
        int customModelData = config.getInt("custom-model-data", 0);
        CardRarity rarity = parseRarity(config.getString("rarity", "COMMON"));
        CardRole role = parseRole(config.getString("role", "OFFENSIVE"));

        // Effects
        Map<String, Double> effects = new HashMap<>();
        ConfigurationSection effSec = config.getConfigurationSection("effects");
        if (effSec != null) {
            for (String k : effSec.getKeys(false)) {
                effects.put(k.toLowerCase(), effSec.getDouble(k));
            }
        }

        // Triggers: on-hit
        HitTrigger hitTrigger = null;
        ConfigurationSection trigSec = config.getConfigurationSection("triggers.on-hit");
        if (trigSec != null) {
            hitTrigger = parseHitTrigger(trigSec);
        }

        final HitTrigger finalHitTrigger = hitTrigger;
        final Map<String, Double> finalEffects = effects;

        CustomCard card = new CustomCard(plugin, cleanId, rarity, material, role) {
            @Override
            public String getName(String langCode) {
                String code = langCode != null ? langCode.toLowerCase() : "en";
                String val = names.get(code);
                if (val != null) return val;
                val = names.get("en");
                return val != null ? val : getKey().getKey();
            }

            @Override
            public String getDescription(String langCode) {
                String code = langCode != null ? langCode.toLowerCase() : "en";
                String val = descriptions.get(code);
                if (val != null) return val;
                val = descriptions.get("en");
                return val != null ? val : "";
            }

            @Override
            public void onApply(CardContext context) {
                if (context.getPlayer() == null) return;
                PlayerData data = plugin.getPlayerDataManager().getData(context.getPlayer());
                if (data == null) return;

                // Section 9: additive / multiplicative effects
                if (finalEffects.containsKey("damage")) {
                    data.dmg = PlayerData.round2(Math.max(data.dmg + finalEffects.get("damage"), 0.5));
                }
                if (finalEffects.containsKey("bullets")) {
                    data.bullets = PlayerData.round2(Math.max(data.bullets + finalEffects.get("bullets"), 1));
                }
                if (finalEffects.containsKey("ammo")) {
                    double addAmmo = finalEffects.get("ammo");
                    data.ammo = Math.max(data.ammo + addAmmo, 1);
                    data.maxAmmo = Math.max(data.maxAmmo + addAmmo, 1);
                }
                if (finalEffects.containsKey("hp")) {
                    data.hp = PlayerData.round2(Math.max(data.hp + finalEffects.get("hp"), 2));
                }
                if (finalEffects.containsKey("bullet-speed")) {
                    data.bulletSpeed = PlayerData.round2(Math.max(data.bulletSpeed + finalEffects.get("bullet-speed"), 0.5));
                }
                if (finalEffects.containsKey("attack-speed")) {
                    data.atkSpeed = PlayerData.round2(Math.max(data.atkSpeed + finalEffects.get("attack-speed"), -0.5));
                }
                if (finalEffects.containsKey("reload-speed")) {
                    data.atksReload = Math.max(0, data.atksReload * (1.0 - finalEffects.get("reload-speed")));
                }
            }

            @Override
            public void onBulletHit(RoundsBulletHitEvent event) {
                if (finalHitTrigger == null) return;
                Location loc = event.getHitLocation();

                // Sound
                if (finalHitTrigger.sound != null && loc.getWorld() != null) {
                    loc.getWorld().playSound(loc, finalHitTrigger.sound, SoundCategory.RECORDS, finalHitTrigger.volume, finalHitTrigger.pitch);
                }

                // Particle
                if (finalHitTrigger.particle != null && loc.getWorld() != null) {
                    loc.getWorld().spawnParticle(
                        finalHitTrigger.particle,
                        loc,
                        finalHitTrigger.particleCount,
                        finalHitTrigger.offsetX,
                        finalHitTrigger.offsetY,
                        finalHitTrigger.offsetZ,
                        0.05
                    );
                }

                // Entity triggers
                Optional<Entity> optEnt = event.getHitEntity();
                if (optEnt.isPresent()) {
                    Entity ent = optEnt.get();
                    if (finalHitTrigger.igniteTicks > 0) {
                        ent.setFireTicks(Math.max(ent.getFireTicks(), finalHitTrigger.igniteTicks));
                    }
                    if (finalHitTrigger.knockbackStrength > 0) {
                        Vector currentKnockback = event.getKnockback();
                        Vector bulletDir = event.getBullet().getVelocity().clone().normalize();
                        Vector extra = bulletDir.multiply(finalHitTrigger.knockbackStrength);
                        event.setKnockback(currentKnockback.add(extra));
                    }
                }
            }
        };

        card.setCustomModelData(customModelData);

        // Register card via core service
        if (plugin.getRoundsService() != null) {
            plugin.getRoundsService().registerCard(card);
        }
    }

    private static class HitTrigger {
        Sound sound;
        float volume = 1.0f;
        float pitch = 1.0f;
        Particle particle;
        int particleCount = 10;
        double offsetX = 0.2, offsetY = 0.2, offsetZ = 0.2;
        int igniteTicks = 0;
        double knockbackStrength = 0.0;
    }

    private HitTrigger parseHitTrigger(ConfigurationSection sec) {
        HitTrigger ht = new HitTrigger();

        ConfigurationSection soundSec = sec.getConfigurationSection("sound");
        if (soundSec != null) {
            String type = soundSec.getString("type");
            if (type != null) {
                try { ht.sound = Sound.valueOf(type.toUpperCase()); } catch (IllegalArgumentException ignored) {}
            }
            ht.volume = (float) soundSec.getDouble("volume", 0.7);
            ht.pitch = (float) soundSec.getDouble("pitch", 1.0);
        }

        ConfigurationSection partSec = sec.getConfigurationSection("particle");
        if (partSec != null) {
            String type = partSec.getString("type");
            if (type != null) {
                try { ht.particle = Particle.valueOf(type.toUpperCase()); } catch (IllegalArgumentException ignored) {}
            }
            ht.particleCount = partSec.getInt("count", 10);
            List<?> offset = partSec.getList("offset");
            if (offset != null && offset.size() >= 3) {
                try {
                    ht.offsetX = Double.parseDouble(offset.get(0).toString());
                    ht.offsetY = Double.parseDouble(offset.get(1).toString());
                    ht.offsetZ = Double.parseDouble(offset.get(2).toString());
                } catch (NumberFormatException ignored) {}
            }
        }

        ht.igniteTicks = sec.getInt("ignite-ticks", 0);
        ht.knockbackStrength = sec.getDouble("knockback-strength", 0.0);
        return ht;
    }

    private Map<String, String> loadLocalizedMap(YamlConfiguration config, String path) {
        Map<String, String> map = new HashMap<>();
        ConfigurationSection sec = config.getConfigurationSection(path);
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                String val = sec.getString(k);
                if (val != null) map.put(k.toLowerCase(), val);
            }
        } else {
            String def = config.getString(path);
            if (def != null) {
                map.put("en", def);
                map.put("ru", def);
            }
        }
        return map;
    }

    private Material parseMaterial(String str) {
        try { return Material.valueOf(str.toUpperCase()); } catch (Exception e) { return Material.PAPER; }
    }

    private CardRarity parseRarity(String str) {
        try { return CardRarity.valueOf(str.toUpperCase()); } catch (Exception e) { return CardRarity.COMMON; }
    }

    private CardRole parseRole(String str) {
        try { return CardRole.valueOf(str.toUpperCase()); } catch (Exception e) { return CardRole.OFFENSIVE; }
    }
}

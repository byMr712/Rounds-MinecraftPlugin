package com.rounds.cards;

import com.rounds.DefaultStats;
import com.rounds.player.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HeavyShotTest {

    @Test
    void testHeavyShotYamlStructure() {
        InputStream is = getClass().getResourceAsStream("/cards/original/heavy-shot.yml");
        assertNotNull(is, "heavy-shot.yml must exist in resources");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
        assertEquals(128, yaml.getInt("id"));
        assertTrue(yaml.getBoolean("enabled"));

        List<Map<?, ?>> variations = yaml.getMapList("variations");
        assertEquals(4, variations.size());

        String[] rarities = {"COMMON", "UNCOMMON", "RARE", "EPIC"};
        double[] bulletSpeeds = {-0.1, -0.3, -0.5, -0.7};

        for (int i = 0; i < 4; i++) {
            Map<?, ?> v = variations.get(i);
            assertEquals(rarities[i], v.get("rarity"));
            Map<?, ?> effects = (Map<?, ?>) v.get("effects");
            assertNotNull(effects);
            assertEquals(bulletSpeeds[i], ((Number) effects.get("bullet_speed")).doubleValue(), 0.001);
            assertEquals(0.2, ((Number) effects.get("attack_speed")).doubleValue(), 0.001);
        }
    }

    @Test
    void testBulletSpeedMinimumClamp() {
        PlayerData data = new PlayerData();
        assertEquals(DefaultStats.get().bulletSpeed, data.bulletSpeed, 0.001);

        Card cardCommon = new Card(1280, 128, Collections.emptyMap(), Collections.emptyMap(),
                null, CardRegistry.Rarity.COMMON, true, Map.of("bullet_speed", -0.1, "attack_speed", 0.2),
                Collections.emptyList(), null, Collections.emptyList(), null);
        cardCommon.apply(null, data, false);
        assertEquals(0.9, data.bulletSpeed, 0.001);
        assertEquals(0.2, data.atkSpeed, 0.001);

        Card cardEpic = new Card(1283, 128, Collections.emptyMap(), Collections.emptyMap(),
                null, CardRegistry.Rarity.EPIC, true, Map.of("bullet_speed", -0.7, "attack_speed", 0.2),
                Collections.emptyList(), null, Collections.emptyList(), null);
        cardEpic.apply(null, data, false);
        // 0.9 - 0.7 = 0.2, but minimum is DefaultStats (1.0) * 0.3 = 0.3
        assertEquals(0.3, data.bulletSpeed, 0.001);

        // Apply again to ensure it never goes lower than 0.3
        cardEpic.apply(null, data, false);
        assertEquals(0.3, data.bulletSpeed, 0.001);
    }

    @Test
    void testAmmoMinimumClamp() {
        PlayerData data = new PlayerData();
        assertEquals(DefaultStats.get().maxAmmo, data.maxAmmo, 0.001);

        Card cardMinusAmmo = new Card(999, 999, Collections.emptyMap(), Collections.emptyMap(),
                null, CardRegistry.Rarity.RARE, true, Map.of("ammo", -10.0),
                Collections.emptyList(), null, Collections.emptyList(), null);

        cardMinusAmmo.apply(null, data, false);
        assertEquals(1.0, data.maxAmmo, 0.001);
        assertEquals(1.0, data.ammo, 0.001);

        // Apply multiple times
        cardMinusAmmo.apply(null, data, false);
        assertEquals(1.0, data.maxAmmo, 0.001);
        assertEquals(1.0, data.ammo, 0.001);
    }

    @Test
    void testPoisonAndParasiteRenamedToYad() {
        InputStream isPoison = getClass().getResourceAsStream("/cards/original/poison.yml");
        assertNotNull(isPoison);
        YamlConfiguration yamlPoison = YamlConfiguration.loadConfiguration(new InputStreamReader(isPoison, StandardCharsets.UTF_8));
        String descPoison = yamlPoison.getString("description.ru");
        assertNotNull(descPoison);
        assertTrue(descPoison.contains("ЯД"), "poison.yml must contain ЯД in Russian description: " + descPoison);

        InputStream isParasite = getClass().getResourceAsStream("/cards/original/parasite.yml");
        assertNotNull(isParasite);
        YamlConfiguration yamlParasite = YamlConfiguration.loadConfiguration(new InputStreamReader(isParasite, StandardCharsets.UTF_8));
        String descParasite = yamlParasite.getString("description.ru");
        assertNotNull(descParasite);
        assertTrue(descParasite.contains("ЯД"), "parasite.yml must contain ЯД in Russian description: " + descParasite);
    }
}

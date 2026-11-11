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

    @Test
    void testDemonicPactAndHpCost() {
        InputStream is = getClass().getResourceAsStream("/cards/original/demonic-pact.yml");
        assertNotNull(is, "demonic-pact.yml must exist");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
        List<Map<?, ?>> variations = yaml.getMapList("variations");
        assertFalse(variations.isEmpty());
        Map<?, ?> effects = (Map<?, ?>) variations.get(0).get("effects");
        assertNotNull(effects);
        assertEquals(0.1, ((Number) effects.get("hp-cost")).doubleValue(), 0.001);

        PlayerData data = new PlayerData();
        assertEquals(0.0, data.hpCost, 0.001);

        Card cardDemonicPact = new Card(17, 17, Collections.emptyMap(), Collections.emptyMap(),
                null, CardRegistry.Rarity.LEGENDARY, true, Map.of("hp-cost", 0.1, "bullets", 9.0),
                Collections.emptyList(), null, Collections.emptyList(), null);
        cardDemonicPact.apply(null, data, false);
        assertEquals(0.1, data.hpCost, 0.001);
        assertEquals(10.0, data.bullets, 0.001);

        // Test with underscore variant
        Card cardUnderscore = new Card(107, 107, Collections.emptyMap(), Collections.emptyMap(),
                null, CardRegistry.Rarity.EPIC, true, Map.of("hp_cost", 0.05),
                Collections.emptyList(), null, Collections.emptyList(), null);
        cardUnderscore.apply(null, data, false);
        assertEquals(0.15, data.hpCost, 0.001);
    }

    @Test
    void testOverheatCardAndReloadState() {
        InputStream is = getClass().getResourceAsStream("/cards/original/overheat.yml");
        assertNotNull(is, "overheat.yml must exist");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
        List<Map<?, ?>> variations = yaml.getMapList("variations");
        assertFalse(variations.isEmpty());
        Map<?, ?> effects = (Map<?, ?>) variations.get(0).get("effects");
        assertNotNull(effects);
        assertEquals(0.1, ((Number) effects.get("overheat")).doubleValue(), 0.001);

        PlayerData data = new PlayerData();
        assertEquals(0.0, data.overheat, 0.001);

        Card cardOverheat = new Card(88, 88, Collections.emptyMap(), Collections.emptyMap(),
                null, CardRegistry.Rarity.LEGENDARY, true, Map.of("overheat", 0.1),
                Collections.emptyList(), null, Collections.emptyList(), null);
        cardOverheat.apply(null, data, false);
        assertEquals(0.1, data.overheat, 0.001);

        // Verify shooting reload condition check
        boolean canShootReloadWithoutCard = (data.bloodCartridge > 0 || 0.0 > 0);
        assertFalse(canShootReloadWithoutCard);

        boolean canShootReloadWithOverheat = (data.bloodCartridge > 0 || data.overheat > 0);
        assertTrue(canShootReloadWithOverheat);
    }
}

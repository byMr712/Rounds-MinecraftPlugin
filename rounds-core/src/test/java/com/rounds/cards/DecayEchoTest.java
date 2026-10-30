package com.rounds.cards;

import com.rounds.listener.DecaySlice;
import com.rounds.player.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DecayEchoTest {

    @Test
    void testDecaySliceCalculation() {
        // Jump block damage: 20% of 20 HP = 4.0 damage
        double totalDamage = 4.0;
        int[] durations = {3, 4, 5, 6};

        for (int seconds : durations) {
            DecaySlice slice = new DecaySlice(totalDamage, seconds, null);
            assertEquals(seconds, slice.secondsRemaining);
            assertEquals(20, slice.ticksUntilNextSecond);
            assertEquals(totalDamage / seconds, slice.damagePerSecond, 0.0001);

            double accumulatedDamage = 0.0;
            for (int s = 0; s < seconds; s++) {
                accumulatedDamage += slice.damagePerSecond;
            }
            assertEquals(totalDamage, accumulatedDamage, 0.0001,
                    "Total damage distributed over " + seconds + "s must equal original incoming damage");
        }
    }

    @Test
    void testPlayerDataStats() {
        PlayerData data = new PlayerData();
        assertEquals(0.0, data.decay, 0.001);
        assertEquals(0.0, data.echo, 0.001);

        data.decay = 5.0;
        data.echo = 1.0;
        assertEquals(5.0, data.decay, 0.001);
        assertEquals(1.0, data.echo, 0.001);

        data.resetStats();
        assertEquals(0.0, data.decay, 0.001);
        assertEquals(0.0, data.echo, 0.001);
    }

    @Test
    void testDecayYamlStructure() {
        InputStream is = getClass().getResourceAsStream("/cards/original/decay.yml");
        assertNotNull(is, "decay.yml must exist in resources");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));

        assertEquals(15, yaml.getInt("id"));
        assertTrue(yaml.getBoolean("enabled"));

        List<Map<?, ?>> variations = yaml.getMapList("variations");
        assertEquals(4, variations.size(), "Decay must have 4 variations (COMMON, UNCOMMON, RARE, EPIC)");

        int[] expectedDecay = {3, 4, 5, 6};
        String[] expectedRarity = {"COMMON", "UNCOMMON", "RARE", "EPIC"};

        for (int i = 0; i < 4; i++) {
            Map<?, ?> v = variations.get(i);
            assertEquals(expectedRarity[i], v.get("rarity"));
            assertEquals(String.valueOf(expectedDecay[i]), String.valueOf(v.get("value")));

            Map<?, ?> effects = (Map<?, ?>) v.get("effects");
            assertNotNull(effects);
            assertEquals(expectedDecay[i], ((Number) effects.get("decay")).intValue());
            assertNull(effects.get("hp"), "Decay must not add extra HP");
        }
    }

    @Test
    void testEchoYamlStructure() {
        InputStream is = getClass().getResourceAsStream("/cards/original/echo.yml");
        assertNotNull(is, "echo.yml must exist in resources");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));

        assertEquals(127, yaml.getInt("id"));
        assertTrue(yaml.getBoolean("enabled"));
        assertEquals("ECHO_SHARD", yaml.getString("material"));

        List<Map<?, ?>> variations = yaml.getMapList("variations");
        assertEquals(1, variations.size());
        Map<?, ?> v = variations.get(0);
        assertEquals("RARE", v.get("rarity"));

        Map<?, ?> effects = (Map<?, ?>) v.get("effects");
        assertNotNull(effects);
        assertEquals(1, ((Number) effects.get("echo")).intValue());
    }

    @Test
    void testDemonicPactYaml() {
        InputStream is = getClass().getResourceAsStream("/cards/original/demonic-pact.yml");
        assertNotNull(is);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
        assertEquals(17, yaml.getInt("id"));
        List<Map<?, ?>> variations = yaml.getMapList("variations");
        assertEquals(1, variations.size());
        Map<?, ?> effects = (Map<?, ?>) variations.get(0).get("effects");
        assertNotNull(effects);
        assertEquals(9, ((Number) effects.get("bullets")).intValue());
        assertEquals(0.1, ((Number) effects.get("hp-cost")).doubleValue(), 0.001);
    }

    @Test
    void testThrusterYaml() {
        InputStream is = getClass().getResourceAsStream("/cards/original/thruster.yml");
        assertNotNull(is);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
        assertEquals(63, yaml.getInt("id"));
        List<Map<?, ?>> variations = yaml.getMapList("variations");
        Map<?, ?> effects = (Map<?, ?>) variations.get(0).get("effects");
        assertNotNull(effects);
        assertEquals(1, ((Number) effects.get("truster")).intValue());
    }

    @Test
    void testApplyDirectDamageFlagLifecycle() {
        assertFalse(com.rounds.entity.RoundsEntities.isApplyingRoundsDamage());

        org.bukkit.entity.LivingEntity mockTarget = org.mockito.Mockito.mock(org.bukkit.entity.LivingEntity.class);
        org.bukkit.entity.Player mockDamager = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.mockito.Mockito.when(mockTarget.isDead()).thenReturn(false);

        org.mockito.Mockito.doAnswer(invocation -> {
            assertTrue(com.rounds.entity.RoundsEntities.isApplyingRoundsDamage(),
                    "isApplyingRoundsDamage must be true while target.damage() is running");
            return null;
        }).when(mockTarget).damage(org.mockito.Mockito.anyDouble(), org.mockito.Mockito.any(org.bukkit.entity.Entity.class));

        com.rounds.entity.RoundsEntities.applyDirectDamage(mockTarget, 10.0, mockDamager);

        assertFalse(com.rounds.entity.RoundsEntities.isApplyingRoundsDamage(),
                "isApplyingRoundsDamage must be restored to false after damage call finishes");
        org.mockito.Mockito.verify(mockTarget).setNoDamageTicks(0);
        org.mockito.Mockito.verify(mockTarget).damage(10.0, mockDamager);
    }

    @Test
    void testChikibamboniYamlAndExecutionLifecycle() {
        InputStream is = getClass().getResourceAsStream("/cards/original/chikibamboni.yml");
        assertNotNull(is, "chikibamboni.yml must exist in resources");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
        assertEquals(91, yaml.getInt("id"));
        assertTrue(yaml.getBoolean("enabled"));

        List<Map<?, ?>> variations = yaml.getMapList("variations");
        assertEquals(1, variations.size());
        Map<?, ?> v = variations.get(0);
        assertEquals("LEGENDARY", v.get("rarity"));
        Map<?, ?> effects = (Map<?, ?>) v.get("effects");
        assertNotNull(effects);
        assertEquals(1, ((Number) effects.get("chikibamboni")).intValue());

        PlayerData pd = new PlayerData();
        assertEquals(0.0, pd.chikibamboni, 0.001);
        pd.chikibamboni = 1.0;
        assertEquals(1.0, pd.chikibamboni, 0.001);
        pd.resetStats();
        assertEquals(0.0, pd.chikibamboni, 0.001);

        java.util.UUID testId = java.util.UUID.randomUUID();
        assertFalse(com.rounds.listener.LegendaryEffects.isExecuting.contains(testId));
        com.rounds.listener.LegendaryEffects.isExecuting.add(testId);
        assertTrue(com.rounds.listener.LegendaryEffects.isExecuting.contains(testId));
        com.rounds.listener.LegendaryEffects.clearRoundState();
        assertFalse(com.rounds.listener.LegendaryEffects.isExecuting.contains(testId));
    }
}

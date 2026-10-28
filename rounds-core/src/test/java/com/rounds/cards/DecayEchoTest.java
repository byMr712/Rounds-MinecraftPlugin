package com.rounds.cards;

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
            assertEquals(0.5, ((Number) effects.get("hp")).doubleValue(), 0.001);
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
}

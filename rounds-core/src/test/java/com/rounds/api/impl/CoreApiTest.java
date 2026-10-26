package com.rounds.api.impl;

import com.rounds.api.data.CustomDataContainer;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class CoreApiTest {

    @Test
    void testIdMappingRegistry(@TempDir File tempDir) {
        File regFile = new File(tempDir, "id_registry.yml");
        Logger logger = Logger.getLogger("TestLogger");

        IdMappingRegistry registry = new IdMappingRegistry(regFile, logger);

        NamespacedKey card1 = new NamespacedKey("test_addon", "card_one");
        NamespacedKey card2 = new NamespacedKey("test_addon", "card_two");

        int id1 = registry.getOrAssignId(card1);
        int id2 = registry.getOrAssignId(card2);

        // Section 3.4: Custom IDs start from 100_000
        assertTrue(id1 >= IdMappingRegistry.CUSTOM_MIN_ID);
        assertTrue(id2 > id1);

        assertEquals(id1, registry.getOrAssignId(card1));
        assertEquals(card1, registry.getKey(id1).orElse(null));

        // Test retiring
        registry.retire(card1);
        assertFalse(registry.getKey(id1).isPresent());
        assertFalse(registry.getId(card1).isPresent());

        // Reload from file to ensure persistence
        IdMappingRegistry reloaded = new IdMappingRegistry(regFile, logger);
        assertFalse(reloaded.getKey(id1).isPresent());
        assertEquals(card2, reloaded.getKey(id2).orElse(null));
    }

    @Test
    void testCustomDataContainerSecurityAndTypes() {
        CustomDataContainer container = new CustomDataContainerImpl();

        // Security check: "rounds:" prefix is restricted
        assertThrows(SecurityException.class, () -> container.set("rounds:illegal_key", "val"));
        assertThrows(NullPointerException.class, () -> container.set(null, "val"));
        assertThrows(NullPointerException.class, () -> container.set("addon:key", null));

        // Valid data
        container.set("addon:score", 42);
        container.set("addon:speed", 1.75);
        container.set("addon:active", true);
        container.set("addon:name", "Test");

        assertEquals(42, container.getInt("addon:score", 0));
        assertEquals(1.75, container.getDouble("addon:speed", 0.0), 0.001);
        assertTrue(container.getBoolean("addon:active", false));
        assertEquals("Test", container.getString("addon:name", ""));

        // Tags
        assertFalse(container.hasTag("charged"));
        container.addTag("charged");
        assertTrue(container.hasTag("charged"));
        container.removeTag("charged");
        assertFalse(container.hasTag("charged"));
    }
}

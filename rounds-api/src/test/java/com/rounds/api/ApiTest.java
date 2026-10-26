package com.rounds.api;

import com.rounds.api.card.CardBuilder;
import com.rounds.api.card.CardRarity;
import com.rounds.api.card.CardRole;
import com.rounds.api.card.CustomCard;
import com.rounds.api.event.RoundsBulletHitEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiTest {

    @Test
    void testApiVersionCompatibility() {
        ApiVersion v1_0_0 = new ApiVersion(1, 0, 0);
        ApiVersion v1_1_0 = new ApiVersion(1, 1, 0);
        ApiVersion v1_0_1 = new ApiVersion(1, 0, 1);
        ApiVersion v2_0_0 = new ApiVersion(2, 0, 0);

        // Same version is compatible
        assertTrue(v1_0_0.isCompatibleWith(v1_0_0));

        // Newer minor/patch is compatible with older
        assertTrue(v1_1_0.isCompatibleWith(v1_0_0));
        assertTrue(v1_0_1.isCompatibleWith(v1_0_0));

        // Older is not compatible with newer
        assertFalse(v1_0_0.isCompatibleWith(v1_1_0));
        assertFalse(v1_0_0.isCompatibleWith(v1_0_1));

        // Different major is not compatible
        assertFalse(v2_0_0.isCompatibleWith(v1_0_0));
        assertFalse(v1_0_0.isCompatibleWith(v2_0_0));
    }

    @Test
    void testBulletHitEventNullChecks() {
        // Recommendation 1: Objects.requireNonNull in both constructors
        assertThrows(NullPointerException.class, () ->
            new RoundsBulletHitEvent(null, (org.bukkit.entity.Entity) null, null, 10.0)
        );

        assertThrows(NullPointerException.class, () ->
            new RoundsBulletHitEvent(null, (org.bukkit.block.Block) null, null)
        );
    }

    @Test
    void testCardBuilderCreation() {
        Plugin mockPlugin = mock(Plugin.class);
        when(mockPlugin.getName()).thenReturn("TestPlugin");

        CustomCard card = CardBuilder.create(mockPlugin, "test_card")
            .name("en", "Test Card")
            .name("ru", "Тестовая Карта")
            .description("en", "Test description")
            .rarity(CardRarity.EPIC)
            .material(Material.DIAMOND)
            .role(CardRole.SNIPER)
            .customModelData(1234)
            .build();

        assertNotNull(card);
        assertEquals("test_card", card.getKey().getKey());
        assertEquals("Test Card", card.getName("en"));
        assertEquals("Тестовая Карта", card.getName("ru"));
        assertEquals("Test description", card.getDescription("en"));
        assertEquals(CardRarity.EPIC, card.getRarity());
        assertEquals(Material.DIAMOND, card.getMaterial());
        assertEquals(CardRole.SNIPER, card.getRole());
        assertEquals(1234, card.getCustomModelData());
    }
}

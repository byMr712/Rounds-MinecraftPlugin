package com.rounds.cards;

import com.rounds.DefaultStats;
import com.rounds.player.PlayerData;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CardDuplicationTest {

    @Test
    void testCardDuplicationAndRemoval() {
        PlayerData data = new PlayerData();
        data.resetStats();
        double baseDmg = DefaultStats.get().dmg;
        assertEquals(0, data.getOwnedCards().size());
        assertEquals(baseDmg, data.dmg, 0.01);

        Map<String, Double> effects = new HashMap<>();
        effects.put("damage", 0.5);

        Card card = new Card(101, 101,
                Collections.singletonMap("en", "Test Card"),
                Collections.singletonMap("en", "Test Desc"),
                Material.PAPER,
                CardRegistry.Rarity.RARE,
                true,
                effects,
                Collections.emptyList(),
                null);

        // First application
        card.apply(null, data);
        data.addCard(card.getId());

        assertEquals(1, data.getOwnedCards().size());
        assertEquals(1, data.getCardCount(card.getId()));
        assertEquals(baseDmg * 1.5, data.dmg, 0.01);

        // Second application (duplication)
        card.apply(null, data);
        data.addCard(card.getId());

        assertEquals(2, data.getOwnedCards().size());
        assertEquals(2, data.getCardCount(card.getId()));
        assertEquals(baseDmg * 1.5 * 1.5, data.dmg, 0.01);

        // Remove one copy by index
        assertTrue(data.removeCardAtIndex(0));
        assertEquals(1, data.getOwnedCards().size());
        assertEquals(1, data.getCardCount(card.getId()));

        // Recalculate stats with remaining 1 copy
        data.resetStats();
        for (int id : data.getOwnedCards()) {
            if (id == card.getId()) {
                card.apply(null, data, false);
            }
        }
        assertEquals(baseDmg * 1.5, data.dmg, 0.01);
    }
}

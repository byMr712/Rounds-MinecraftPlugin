package com.rounds.cards;

import com.rounds.player.PlayerData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class CardTriggerActionTest {

    private PlayerData data;

    @BeforeEach
    public void setup() {
        data = new PlayerData();
        data.resetStats();
    }

    @Test
    public void testCardTriggerActionParsing() {
        Map<String, Object> map = new HashMap<>();
        map.put("on", "ON_BULLET_HIT");
        map.put("action", "RESTORE_AMMO");
        map.put("value", 3);

        CardTriggerAction action = CardTriggerAction.fromMap(map);
        assertNotNull(action);
        assertEquals(CardTrigger.ON_BULLET_HIT, action.trigger());
        assertEquals(CardActionType.RESTORE_AMMO, action.action());
        assertEquals(3.0, action.value());
        assertEquals("", action.param());
    }

    @Test
    public void testTriggerExecutionRestoreAmmo() {
        data.ammo = 1;
        data.maxAmmo = 10;

        CardTriggerAction action = new CardTriggerAction(
                CardTrigger.ON_BULLET_HIT,
                CardActionType.RESTORE_AMMO,
                2.0,
                ""
        );

        Card card = new Card(
                999, 999,
                Map.of("en", "Ammo Thief"),
                Map.of("en", "Restore 2 ammo on hit"),
                org.bukkit.Material.PAPER,
                CardRegistry.Rarity.RARE,
                true,
                Collections.emptyMap(),
                Collections.emptyList(),
                null,
                List.of(action)
        );

        card.apply(null, data, false);
        assertEquals(1, data.getTriggerActions().size());

        // Execute ON_BULLET_HIT trigger
        data.executeTriggers(CardTrigger.ON_BULLET_HIT, null, null, 1.0);
        assertEquals(3.0, data.ammo);

        // Execute ON_SHOOT trigger (should not match)
        data.executeTriggers(CardTrigger.ON_SHOOT, null, null, 1.0);
        assertEquals(3.0, data.ammo);
    }

    @Test
    public void testResetClearsTriggers() {
        CardTriggerAction action = new CardTriggerAction(
                CardTrigger.ON_SHOOT,
                CardActionType.RESTORE_AMMO,
                1.0,
                ""
        );
        data.addTriggerActions(List.of(action));
        assertEquals(1, data.getTriggerActions().size());

        data.resetStats();
        assertTrue(data.getTriggerActions().isEmpty());
    }
}

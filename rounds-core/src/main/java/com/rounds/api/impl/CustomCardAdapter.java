package com.rounds.api.impl;

import com.rounds.RoundsPlugin;
import com.rounds.api.card.CardContext;
import com.rounds.api.card.CustomCard;
import com.rounds.cards.Card;
import com.rounds.cards.CardRegistry;
import com.rounds.player.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.Map;

public class CustomCardAdapter extends Card {

    private final CustomCard customCard;
    private final RoundsPlugin plugin;

    public CustomCardAdapter(int id, CustomCard customCard, RoundsPlugin plugin, Map<String, Double> effects) {
        super(
            id,
            id,
            Collections.emptyMap(),
            Collections.emptyMap(),
            customCard.getMaterial() != null ? customCard.getMaterial() : Material.PAPER,
            mapRarity(customCard.getRarity()),
            true,
            effects != null ? effects : Collections.emptyMap(),
            Collections.emptyList(),
            null
        );
        this.customCard = customCard;
        this.plugin = plugin;
    }

    private static CardRegistry.Rarity mapRarity(com.rounds.api.card.CardRarity apiRarity) {
        if (apiRarity == null) return CardRegistry.Rarity.COMMON;
        try {
            return CardRegistry.Rarity.valueOf(apiRarity.name());
        } catch (IllegalArgumentException e) {
            return CardRegistry.Rarity.COMMON;
        }
    }

    public CustomCard getCustomCard() {
        return customCard;
    }

    @Override
    public String getName(String lang) {
        String name = customCard.getName(lang);
        if (name == null || name.isBlank()) {
            name = customCard.getName("en");
        }
        return name != null ? name : customCard.getKey().getKey();
    }

    @Override
    public String getDescription(String lang) {
        String desc = customCard.getDescription(lang);
        if (desc == null) {
            desc = customCard.getDescription("en");
        }
        return desc != null ? desc : "";
    }

    @Override
    public ItemStack createItemStack(String lang, boolean withSelectHint) {
        ItemStack item = super.createItemStack(lang, withSelectHint);
        if (customCard.getCustomModelData() > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(customCard.getCustomModelData());
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    @Override
    public void apply(Player player, PlayerData data, boolean runCommands) {
        super.apply(player, data, runCommands);
        if (player != null && plugin != null) {
            var dataContainer = plugin.getCustomDataManager().getOrCreate(player.getUniqueId());
            CardContext context = new CardContextImpl(plugin, player, dataContainer);
            try {
                customCard.onApply(context);
            } catch (Throwable t) {
                plugin.getLogger().warning("Error in onApply for card " + customCard.getKey() + ": " + t.getMessage());
            }
        }
    }

    public void onRemove(Player player) {
        if (player != null && plugin != null) {
            var dataContainer = plugin.getCustomDataManager().getOrCreate(player.getUniqueId());
            CardContext context = new CardContextImpl(plugin, player, dataContainer);
            try {
                customCard.onRemove(context);
            } catch (Throwable t) {
                plugin.getLogger().warning("Error in onRemove for card " + customCard.getKey() + ": " + t.getMessage());
            }
        }
    }
}

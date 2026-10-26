package com.rounds.api.impl;

import com.rounds.RoundsPlugin;
import com.rounds.api.card.CardRarity;
import com.rounds.api.card.CardRole;
import com.rounds.api.card.CustomCard;
import com.rounds.cards.Card;
import com.rounds.cards.CardRegistry;

public class BuiltinCardWrapper extends CustomCard {

    private final Card card;

    public BuiltinCardWrapper(RoundsPlugin plugin, Card card, String slug) {
        super(
            plugin,
            slug != null ? slug : "card_" + card.getId(),
            mapRarity(card.getRarity()),
            card.getMaterial(),
            CardRole.OFFENSIVE
        );
        this.card = card;
    }

    private static CardRarity mapRarity(CardRegistry.Rarity rarity) {
        if (rarity == null) return CardRarity.COMMON;
        try {
            return CardRarity.valueOf(rarity.name());
        } catch (IllegalArgumentException e) {
            return CardRarity.COMMON;
        }
    }

    public Card getCoreCard() {
        return card;
    }

    @Override
    public String getName(String langCode) {
        return card.getName(langCode);
    }

    @Override
    public String getDescription(String langCode) {
        return card.getDescription(langCode);
    }
}

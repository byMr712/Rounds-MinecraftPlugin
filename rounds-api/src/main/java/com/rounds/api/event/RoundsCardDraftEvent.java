package com.rounds.api.event;

import com.rounds.api.RoundsAPI;
import com.rounds.api.card.CustomCard;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoundsCardDraftEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final List<CustomCard> cards;
    private boolean cancelled = false;

    public RoundsCardDraftEvent(Player player, List<CustomCard> cards) {
        this.player = player;
        this.cards = new ArrayList<>(cards);
    }

    public Player getPlayer() { return player; }

    public List<CustomCard> getOfferedCards() {
        return Collections.unmodifiableList(cards);
    }

    public boolean addOfferedCard(CustomCard card) {
        if (card == null) return false;
        if (!RoundsAPI.getCard(card.getKey()).isPresent()) {
            throw new IllegalArgumentException("Нельзя предложить незарегистрированную карту: " + card.getKey());
        }
        return cards.add(card);
    }

    public boolean removeOfferedCard(NamespacedKey key) {
        return cards.removeIf(c -> c.getKey().equals(key));
    }

    public boolean replaceOfferedCard(int index, CustomCard newCard) {
        if (index < 0 || index >= cards.size() || newCard == null) return false;
        if (!RoundsAPI.getCard(newCard.getKey()).isPresent()) {
            throw new IllegalArgumentException("Нельзя предложить незарегистрированную карту: " + newCard.getKey());
        }
        cards.set(index, newCard);
        return true;
    }

    @Override
    public boolean isCancelled() { return cancelled; }
    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

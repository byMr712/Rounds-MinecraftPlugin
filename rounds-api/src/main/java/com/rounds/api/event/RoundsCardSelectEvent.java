package com.rounds.api.event;

import com.rounds.api.card.CustomCard;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.Optional;

public class RoundsCardSelectEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final CustomCard selectedCard;
    private final int cardIndex;
    private CustomCard replacementCard;
    private boolean cancelled = false;

    public RoundsCardSelectEvent(Player player, CustomCard selectedCard, int cardIndex) {
        this.player = Objects.requireNonNull(player, "Player cannot be null");
        this.selectedCard = selectedCard;
        this.cardIndex = cardIndex;
    }

    public Player getPlayer() { return player; }
    public CustomCard getSelectedCard() { return selectedCard; }
    public int getCardIndex() { return cardIndex; }

    public Optional<CustomCard> getReplacementCard() {
        return Optional.ofNullable(replacementCard);
    }

    public void setReplacementCard(CustomCard replacementCard) {
        this.replacementCard = replacementCard;
    }

    @Override
    public boolean isCancelled() { return cancelled; }
    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

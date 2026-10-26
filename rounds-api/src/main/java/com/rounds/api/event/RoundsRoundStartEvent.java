package com.rounds.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RoundsRoundStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final int roundNumber;
    private final List<Player> participants;

    public RoundsRoundStartEvent(int roundNumber, Collection<? extends Player> participants) {
        this.roundNumber = roundNumber;
        this.participants = participants != null ? List.copyOf(participants) : List.of();
    }

    public int getRoundNumber() { return roundNumber; }
    public List<Player> getParticipants() { return Collections.unmodifiableList(participants); }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

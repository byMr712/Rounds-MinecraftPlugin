package com.rounds.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RoundsRoundEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final int roundNumber;
    private final String winningTeam;
    private final List<Player> winners;
    private final Map<String, Integer> teamScores;

    public RoundsRoundEndEvent(int roundNumber, String winningTeam, Collection<? extends Player> winners, Map<String, Integer> teamScores) {
        this.roundNumber = roundNumber;
        this.winningTeam = winningTeam;
        this.winners = winners != null ? List.copyOf(winners) : List.of();
        this.teamScores = teamScores != null ? Map.copyOf(teamScores) : Map.of();
    }

    public int getRoundNumber() { return roundNumber; }
    public Optional<String> getWinningTeam() { return Optional.ofNullable(winningTeam); }
    public List<Player> getWinners() { return Collections.unmodifiableList(winners); }
    public Map<String, Integer> getTeamScores() { return Collections.unmodifiableMap(teamScores); }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

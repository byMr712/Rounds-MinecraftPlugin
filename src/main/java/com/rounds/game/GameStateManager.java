package com.rounds.game;

import com.rounds.RoundsPlugin;
import com.rounds.game.GameManager.GameState;
import com.rounds.teams.TeamManager.GameTeam;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GameStateManager {

    private final RoundsPlugin plugin;
    private final File file;

    public GameStateManager(RoundsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "game-state.yml");
    }

    public void save(GameManager gm) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("state", gm.getState().name());
        yml.set("current-round", gm.getCurrentRound());
        yml.set("rounds-to-win", gm.getRounds());

        GameTeam loser = gm.getLastLoser();
        yml.set("last-loser", loser != null ? loser.name() : null);

        List<String> deadStrs = new ArrayList<>();
        for (UUID uuid : gm.getDeadPlayers()) {
            deadStrs.add(uuid.toString());
        }
        yml.set("dead-players", deadStrs);

        for (GameTeam gt : GameTeam.values()) {
            yml.set("team-wins." + gt.name(), plugin.getTeamManager().getWins(gt));
        }

        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save game state: " + e.getMessage());
        }
    }

    public SavedState load() {
        if (!file.exists()) return null;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        String stateStr = yml.getString("state");
        if (stateStr == null) return null;

        GameState state;
        try {
            state = GameState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (state == GameState.WAITING) return null;

        double currentRound = yml.getDouble("current-round", 0);
        double roundsToWin = yml.getDouble("rounds-to-win", 5);

        String loserStr = yml.getString("last-loser");
        GameTeam lastLoser = null;
        if (loserStr != null) {
            try {
                lastLoser = GameTeam.valueOf(loserStr);
            } catch (IllegalArgumentException ignored) {}
        }

        Set<UUID> deadPlayers = new HashSet<>();
        List<String> deadStrs = yml.getStringList("dead-players");
        for (String s : deadStrs) {
            try {
                deadPlayers.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {}
        }

        Map<GameTeam, Integer> wins = new EnumMap<>(GameTeam.class);
        for (GameTeam gt : GameTeam.values()) {
            wins.put(gt, yml.getInt("team-wins." + gt.name(), 0));
        }

        return new SavedState(state, currentRound, roundsToWin, lastLoser, deadPlayers, wins);
    }

    public void clear() {
        if (file.exists()) file.delete();
    }

    public static class SavedState {
        public final GameState state;
        public final double currentRound;
        public final double roundsToWin;
        public final GameTeam lastLoser;
        public final Set<UUID> deadPlayers;
        public final Map<GameTeam, Integer> wins;

        public SavedState(GameState state, double currentRound, double roundsToWin,
                          GameTeam lastLoser, Set<UUID> deadPlayers, Map<GameTeam, Integer> wins) {
            this.state = state;
            this.currentRound = currentRound;
            this.roundsToWin = roundsToWin;
            this.lastLoser = lastLoser;
            this.deadPlayers = deadPlayers;
            this.wins = wins;
        }
    }
}

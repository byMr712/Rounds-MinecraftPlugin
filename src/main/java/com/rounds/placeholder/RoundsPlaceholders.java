package com.rounds.placeholder;

import com.rounds.RoundsPlugin;
import com.rounds.game.GameManager;
import com.rounds.teams.TeamManager;
import com.rounds.teams.TeamManager.GameTeam;
import com.rounds.util.Messages;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RoundsPlaceholders extends PlaceholderExpansion {

    private final RoundsPlugin plugin;

    public RoundsPlaceholders(RoundsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "rounds";
    }

    @Override
    public @NotNull String getAuthor() {
        return "RoundsPlugin";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        GameManager gm = plugin.getGameManager();
        TeamManager tm = plugin.getTeamManager();
        GameTeam team = tm.getPlayerTeam(player.getUniqueId());

        // %rounds_round% - current round
        if (params.equalsIgnoreCase("round")) {
            return String.valueOf((int) gm.getCurrentRound());
        }

        // %rounds_rounds_to_win% - rounds needed to win
        if (params.equalsIgnoreCase("rounds_to_win")) {
            return String.valueOf((int) gm.getRounds());
        }

        // %rounds_round_display% - "3/5"
        if (params.equalsIgnoreCase("round_display")) {
            return (int) gm.getCurrentRound() + "/" + (int) gm.getRounds();
        }

        // %rounds_state% - game state
        if (params.equalsIgnoreCase("state")) {
            return gm.getState().name();
        }

        // %rounds_team% - player's team name
        if (params.equalsIgnoreCase("team")) {
            if (team == null) return Messages.raw("team.none");
            return Messages.raw("team." + team.name().toLowerCase());
        }

        // %rounds_team_color% - player's team color code
        if (params.equalsIgnoreCase("team_color")) {
            if (team == null) return "";
            return team.getColor().toString();
        }

        // %rounds_team_adjective% - adjective form ("синий", "красный" etc.)
        if (params.equalsIgnoreCase("team_adjective")) {
            if (team == null) return "";
            return Messages.raw("team.adj-" + team.name().toLowerCase());
        }

        // %rounds_blue_wins% - blue team wins
        if (params.equalsIgnoreCase("blue_wins")) {
            return String.valueOf((int) tm.getWins(GameTeam.BLUE));
        }

        // %rounds_red_wins% - red team wins
        if (params.equalsIgnoreCase("red_wins")) {
            return String.valueOf((int) tm.getWins(GameTeam.RED));
        }

        // %rounds_yellow_wins% - yellow team wins
        if (params.equalsIgnoreCase("yellow_wins")) {
            return String.valueOf((int) tm.getWins(GameTeam.YELLOW));
        }

        // %rounds_green_wins% - green team wins
        if (params.equalsIgnoreCase("green_wins")) {
            return String.valueOf((int) tm.getWins(GameTeam.GREEN));
        }

        // %rounds_team_wins% - player's team wins
        if (params.equalsIgnoreCase("team_wins")) {
            if (team == null) return "0";
            return String.valueOf((int) tm.getWins(team));
        }

        // %rounds_blue_name% / %rounds_red_name% etc
        if (params.equalsIgnoreCase("blue_name")) return Messages.raw("team.blue");
        if (params.equalsIgnoreCase("red_name")) return Messages.raw("team.red");
        if (params.equalsIgnoreCase("yellow_name")) return Messages.raw("team.yellow");
        if (params.equalsIgnoreCase("green_name")) return Messages.raw("team.green");

        return null;
    }
}

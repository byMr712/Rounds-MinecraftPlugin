package com.rounds.teams;

import com.rounds.RoundsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class TeamManager {

    public enum GameTeam {
        BLUE(ChatColor.BLUE, "Blue", "blockjoinblue"),
        RED(ChatColor.RED, "Red", "blockjoinred"),
        YELLOW(ChatColor.YELLOW, "Yellow", "blockjoinyellow"),
        GREEN(ChatColor.GREEN, "Green", "blockjoingreen");

        private final ChatColor color;
        private final String name;
        private final String blockName;

        GameTeam(ChatColor color, String name, String blockName) {
            this.color = color;
            this.name = name;
            this.blockName = blockName;
        }

        public ChatColor getColor() { return color; }
        public String getName() { return name; }
        public String getBlockName() { return blockName; }
    }

    private static final String TEAM_PREFIX = "rounds_";
    private final Scoreboard scoreboard;
    private final Map<GameTeam, Team> teams = new EnumMap<>(GameTeam.class);
    private final Map<UUID, GameTeam> playerTeams = new HashMap<>();
    private final Map<GameTeam, Integer> wins = new EnumMap<>(GameTeam.class);
    private final Map<GameTeam, Integer> playerCounts = new EnumMap<>(GameTeam.class);

    public TeamManager(RoundsPlugin plugin) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        scoreboard = sm.getMainScoreboard();

        for (GameTeam gt : GameTeam.values()) {
            String teamName = TEAM_PREFIX + gt.name().toLowerCase();
            Team existing = scoreboard.getTeam(teamName);
            if (existing != null) {
                existing.unregister();
            }
            Team t = scoreboard.registerNewTeam(teamName);
            t.setDisplayName(gt.getColor() + gt.getName());
            t.setColor(gt.getColor());
            t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM);
            t.setAllowFriendlyFire(false);
            teams.put(gt, t);
            wins.put(gt, 0);
            playerCounts.put(gt, 0);
        }
    }

    private void ensureTeamsRegistered() {
        for (GameTeam gt : GameTeam.values()) {
            String teamName = TEAM_PREFIX + gt.name().toLowerCase();
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
                team.setDisplayName(gt.getColor() + gt.getName());
                team.setColor(gt.getColor());
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM);
                team.setAllowFriendlyFire(false);
            }
            teams.put(gt, team);
        }
    }

    public boolean joinTeam(UUID uuid, GameTeam team) {
        ensureTeamsRegistered();
        GameTeam old = playerTeams.get(uuid);
        if (old != null) {
            Team oldTeam = teams.get(old);
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null && oldTeam != null) {
                oldTeam.removeEntry(name);
            }
        }
        playerTeams.put(uuid, team);
        Team t = teams.get(team);
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        if (name != null && t != null) {
            t.addEntry(name);
        }
        recalculateCounts();
        return true;
    }

    public void leaveTeam(UUID uuid) {
        ensureTeamsRegistered();
        GameTeam old = playerTeams.remove(uuid);
        if (old != null) {
            Team t = teams.get(old);
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null && t != null) {
                t.removeEntry(name);
            }
        }
        recalculateCounts();
    }

    public void recalculateCounts() {
        playerCounts.clear();
        for (GameTeam gt : GameTeam.values()) {
            playerCounts.put(gt, 0);
        }
        for (Map.Entry<UUID, GameTeam> entry : playerTeams.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                playerCounts.merge(entry.getValue(), 1, Integer::sum);
            }
        }
    }

    public GameTeam getPlayerTeam(UUID uuid) {
        return playerTeams.get(uuid);
    }

    public void addWin(GameTeam team) {
        wins.merge(team, 1, Integer::sum);
    }

    public void resetWins() {
        wins.replaceAll((k, v) -> 0);
    }

    public int getWins(GameTeam team) {
        return wins.getOrDefault(team, 0);
    }

    public int getPlayerCount(GameTeam team) {
        int count = 0;
        for (Map.Entry<UUID, GameTeam> entry : playerTeams.entrySet()) {
            if (entry.getValue() == team) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    count++;
                }
            }
        }
        return count;
    }

    public Set<UUID> getTeamPlayers(GameTeam team) {
        Set<UUID> result = new HashSet<>();
        for (Map.Entry<UUID, GameTeam> entry : playerTeams.entrySet()) {
            if (entry.getValue() == team) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }

    public int getTotalReadyPlayers() {
        int count = 0;
        for (UUID uuid : playerTeams.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                count++;
            }
        }
        return count;
    }

    public void clearAll() {
        playerTeams.clear();
        ensureTeamsRegistered();
        for (GameTeam gt : GameTeam.values()) {
            Team team = teams.get(gt);
            if (team != null) {
                for (String entry : new ArrayList<>(team.getEntries())) {
                    team.removeEntry(entry);
                }
            }
            playerCounts.put(gt, 0);
        }
    }

    public Scoreboard getScoreboard() { return scoreboard; }
    public Team getTeam(GameTeam gt) { return teams.get(gt); }

    public static GameTeam fromBlockName(String blockName) {
        for (GameTeam gt : GameTeam.values()) {
            if (gt.getBlockName().equalsIgnoreCase(blockName)) return gt;
        }
        return null;
    }

    public static GameTeam fromColor(ChatColor color) {
        for (GameTeam gt : GameTeam.values()) {
            if (gt.getColor() == color) return gt;
        }
        return null;
    }

    public static GameTeam getAliveOpponent(GameTeam self, TeamManager tm) {
        GameTeam best = null;
        int bestCount = 0;
        for (GameTeam gt : GameTeam.values()) {
            if (gt == self) continue;
            int count = tm.getPlayerCount(gt);
            if (count > bestCount) {
                bestCount = count;
                best = gt;
            }
        }
        return best != null ? best : self;
    }
}

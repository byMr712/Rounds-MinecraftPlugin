package com.rounds.api.impl;

import com.rounds.RoundsPlugin;
import com.rounds.api.card.CardContext;
import com.rounds.api.data.CustomDataContainer;
import com.rounds.teams.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class CardContextImpl implements CardContext {

    private final RoundsPlugin plugin;
    private final Player player;
    private final CustomDataContainer customData;

    public CardContextImpl(RoundsPlugin plugin, Player player, CustomDataContainer customData) {
        this.plugin = plugin;
        this.player = player;
        this.customData = customData;
    }

    @Override
    public int getRoundNumber() {
        if (!isMatchRunning()) return 0;
        return (int) plugin.getGameManager().getCurrentRound();
    }

    @Override
    public boolean isMatchRunning() {
        return plugin != null && plugin.getGameManager() != null && plugin.getGameManager().isGameStarted();
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public Collection<Player> getTeammates() {
        if (!isMatchRunning() || player == null) {
            return Collections.emptyList();
        }
        TeamManager tm = plugin.getTeamManager();
        if (tm == null) return Collections.emptyList();
        TeamManager.GameTeam myTeam = tm.getPlayerTeam(player.getUniqueId());
        if (myTeam == null) return Collections.emptyList();

        List<Player> teammates = new ArrayList<>();
        for (UUID u : tm.getTeamPlayers(myTeam)) {
            if (u.equals(player.getUniqueId())) continue;
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) {
                teammates.add(p);
            }
        }
        return Collections.unmodifiableList(teammates);
    }

    @Override
    public Collection<Player> getEnemies() {
        if (!isMatchRunning() || player == null) {
            return Collections.emptyList();
        }
        TeamManager tm = plugin.getTeamManager();
        if (tm == null) return Collections.emptyList();
        TeamManager.GameTeam myTeam = tm.getPlayerTeam(player.getUniqueId());

        List<Player> enemies = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getUniqueId().equals(player.getUniqueId())) continue;
            TeamManager.GameTeam otherTeam = tm.getPlayerTeam(p.getUniqueId());
            if (otherTeam != null && otherTeam != myTeam) {
                enemies.add(p);
            }
        }
        return Collections.unmodifiableList(enemies);
    }

    @Override
    public CustomDataContainer getData() {
        return customData;
    }
}

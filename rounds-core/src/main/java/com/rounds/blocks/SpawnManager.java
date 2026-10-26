package com.rounds.blocks;

import com.rounds.teams.TeamManager.GameTeam;
import org.bukkit.Location;

import java.util.*;

public class SpawnManager {

    /**
     * Assign each team a unique spawn from available spawns.
     * If spawns < teams, remaining teams get a random spawn (with repeat).
     * Returns a map team → spawn location (+1Y applied).
     */
    public static Map<GameTeam, Location> assignSpawns(
            Collection<GameTeam> teams, List<Location> spawns) {

        Map<GameTeam, Location> result = new LinkedHashMap<>();
        if (spawns.isEmpty()) return result;

        List<Location> available = new ArrayList<>(spawns);
        Collections.shuffle(available);

        int idx = 0;
        for (GameTeam team : teams) {
            Location spawn = available.get(idx % available.size());
            result.put(team, spawn.clone().add(0, 1, 0));
            idx++;
        }
        return result;
    }
}

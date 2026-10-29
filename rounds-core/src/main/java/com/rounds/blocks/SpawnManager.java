package com.rounds.blocks;

import com.rounds.teams.TeamManager.GameTeam;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.*;

public class SpawnManager {

    /**
     * Orients the location to face towards the target horizontally (pitch = 0.0f).
     */
    public static void faceLocation(Location from, Location target) {
        if (from == null || target == null) return;
        double targetX = target.getBlockX() + 0.5;
        double targetZ = target.getBlockZ() + 0.5;
        double dx = targetX - from.getX();
        double dz = targetZ - from.getZ();

        if (dx * dx + dz * dz > 1e-4) {
            Vector dir = new Vector(dx, 0.0, dz);
            from.setDirection(dir);
            from.setPitch(0.0f);
        }
    }

    /**
     * Assign each team a unique spawn from available spawns.
     * Centers player on the spawn block (+0.5 X/Z, +1.0 Y) and rotates them
     * to face towards the center of the map (pitch = 0.0f).
     */
    public static Map<GameTeam, Location> assignSpawns(
            Collection<GameTeam> teams, List<Location> spawns, Location center) {

        Map<GameTeam, Location> result = new LinkedHashMap<>();
        if (spawns.isEmpty()) return result;

        List<Location> available = new ArrayList<>(spawns);
        Collections.shuffle(available);

        Location targetCenter = center;
        if (targetCenter == null) {
            double avgX = 0;
            double avgZ = 0;
            for (Location s : spawns) {
                avgX += s.getBlockX() + 0.5;
                avgZ += s.getBlockZ() + 0.5;
            }
            avgX /= spawns.size();
            avgZ /= spawns.size();
            targetCenter = new Location(spawns.get(0).getWorld(), avgX, spawns.get(0).getY(), avgZ);
        }

        int idx = 0;
        for (GameTeam team : teams) {
            Location spawn = available.get(idx % available.size());
            double x = spawn.getBlockX() + 0.5;
            double y = spawn.getBlockY() + 1.0;
            double z = spawn.getBlockZ() + 0.5;
            Location loc = new Location(spawn.getWorld(), x, y, z);
            faceLocation(loc, targetCenter);
            result.put(team, loc);
            idx++;
        }
        return result;
    }

    /**
     * Backward-compatible overload without explicit center.
     * Calculates the geometric center of available spawns automatically.
     */
    public static Map<GameTeam, Location> assignSpawns(
            Collection<GameTeam> teams, List<Location> spawns) {
        return assignSpawns(teams, spawns, null);
    }
}

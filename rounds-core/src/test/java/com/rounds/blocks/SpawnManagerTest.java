package com.rounds.blocks;

import com.rounds.teams.TeamManager.GameTeam;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SpawnManagerTest {

    @Test
    public void testFaceLocationDirections() {
        Location center = new Location(null, 0, 64, 0);

        // Spawn to the South (positive Z) -> should face North (towards negative Z, yaw ~180)
        Location southSpawn = new Location(null, 0.5, 64, 10.5);
        SpawnManager.faceLocation(southSpawn, center);
        assertEquals(0.0f, southSpawn.getPitch(), 0.01f);
        assertEquals(180.0f, southSpawn.getYaw(), 0.5f);

        // Spawn to the North (negative Z) -> should face South (towards positive Z, yaw ~0)
        Location northSpawn = new Location(null, 0.5, 64, -10.5);
        SpawnManager.faceLocation(northSpawn, center);
        assertEquals(0.0f, northSpawn.getPitch(), 0.01f);
        assertEquals(0.0f, northSpawn.getYaw(), 0.5f);

        // Spawn to the East (positive X) -> should face West (towards negative X, yaw ~90)
        Location eastSpawn = new Location(null, 10.5, 64, 0.5);
        SpawnManager.faceLocation(eastSpawn, center);
        assertEquals(0.0f, eastSpawn.getPitch(), 0.01f);
        assertEquals(90.0f, eastSpawn.getYaw(), 0.5f);

        // Spawn to the West (negative X) -> should face East (towards positive X, yaw ~270)
        Location westSpawn = new Location(null, -10.5, 64, 0.5);
        SpawnManager.faceLocation(westSpawn, center);
        assertEquals(0.0f, westSpawn.getPitch(), 0.01f);
        assertEquals(270.0f, westSpawn.getYaw(), 0.5f);
    }

    @Test
    public void testAssignSpawnsFacingCenter() {
        Location center = new Location(null, 0, 64, 0);
        List<Location> rawSpawns = List.of(
                new Location(null, 10, 64, 0),
                new Location(null, -10, 64, 0)
        );

        Map<GameTeam, Location> result = SpawnManager.assignSpawns(
                Set.of(GameTeam.RED, GameTeam.BLUE), rawSpawns, center);

        assertEquals(2, result.size());
        for (Location loc : result.values()) {
            assertEquals(65.0, loc.getY(), 0.001); // +1.0 Y offset
            assertEquals(0.0f, loc.getPitch(), 0.01f);
            // Must face center
            double dx = 0.5 - loc.getX();
            double dz = 0.5 - loc.getZ();
            assertTrue(dx != 0 || dz != 0);
        }
    }
}

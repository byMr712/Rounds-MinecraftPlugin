package com.rounds.blocks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BlockStorage {

    private static final int[] MAP_SIZES = {50, 100};

    private Location lobbyBlock;
    private final List<MapBlock> mapBlocks = new ArrayList<>();
    private final List<Location> spawnBlocks = new ArrayList<>();

    private final File file;

    public BlockStorage(File worldFolder) {
        this.file = new File(worldFolder, "rounds-map-blocks.yml");
        load();
    }

    // --- Lobby ---

    public Location getLobbyBlock() { return lobbyBlock; }

    public void setLobbyBlock(Location loc) {
        lobbyBlock = loc;
        save();
    }

    // --- Map blocks ---

    public List<MapBlock> getMapBlocks() { return Collections.unmodifiableList(mapBlocks); }

    public void addMapBlock(Location center, int size) {
        mapBlocks.add(new MapBlock(center, size));
        save();
    }

    public boolean removeMapBlock(Location loc) {
        boolean removed = mapBlocks.removeIf(m -> m.centerEquals(loc));
        if (removed) save();
        return removed;
    }

    // --- Spawn blocks ---

    public List<Location> getSpawnBlocks() { return Collections.unmodifiableList(spawnBlocks); }

    public void addSpawnBlock(Location loc) {
        spawnBlocks.add(loc.clone());
        save();
    }

    public boolean removeSpawnBlock(Location loc) {
        boolean removed = spawnBlocks.removeIf(s -> s.getWorld() == loc.getWorld()
                && s.getBlockX() == loc.getBlockX() && s.getBlockY() == loc.getBlockY() && s.getBlockZ() == loc.getBlockZ());
        if (removed) save();
        return removed;
    }

    /**
     * Find which map block zone a location falls into.
     * Zone = [centerX - size/2 .. centerX + size/2] on X and Z, Y ignored.
     */
    public MapBlock findMapBlock(Location loc) {
        if (loc.getWorld() == null) return null;
        for (MapBlock mb : mapBlocks) {
            if (mb.center.getWorld() != loc.getWorld()) continue;
            int half = mb.size / 2;
            if (Math.abs(loc.getBlockX() - mb.center.getBlockX()) <= half
                    && Math.abs(loc.getBlockZ() - mb.center.getBlockZ()) <= half) {
                return mb;
            }
        }
        return null;
    }

    /**
     * Get all spawn blocks that fall within a map block zone.
     */
    public List<Location> getSpawnBlocksInZone(MapBlock zone) {
        List<Location> result = new ArrayList<>();
        int half = zone.size / 2;
        for (Location s : spawnBlocks) {
            if (s.getWorld() != zone.center.getWorld()) continue;
            if (Math.abs(s.getBlockX() - zone.center.getBlockX()) <= half
                    && Math.abs(s.getBlockZ() - zone.center.getBlockZ()) <= half) {
                result.add(s);
            }
        }
        return result;
    }

    // --- Persistence ---

    public void save() {
        YamlConfiguration config = new YamlConfiguration();

        if (lobbyBlock != null && lobbyBlock.getWorld() != null) {
            config.set("lobby.world", lobbyBlock.getWorld().getName());
            config.set("lobby.x", lobbyBlock.getBlockX());
            config.set("lobby.y", lobbyBlock.getBlockY());
            config.set("lobby.z", lobbyBlock.getBlockZ());
        }

        for (int i = 0; i < mapBlocks.size(); i++) {
            MapBlock mb = mapBlocks.get(i);
            String path = "map." + i;
            if (mb.center.getWorld() == null) continue;
            config.set(path + ".world", mb.center.getWorld().getName());
            config.set(path + ".x", mb.center.getBlockX());
            config.set(path + ".y", mb.center.getBlockY());
            config.set(path + ".z", mb.center.getBlockZ());
            config.set(path + ".size", mb.size);
        }

        for (int i = 0; i < spawnBlocks.size(); i++) {
            Location s = spawnBlocks.get(i);
            String path = "spawn." + i;
            if (s.getWorld() == null) continue;
            config.set(path + ".world", s.getWorld().getName());
            config.set(path + ".x", s.getBlockX());
            config.set(path + ".y", s.getBlockY());
            config.set(path + ".z", s.getBlockZ());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        if (config.contains("lobby")) {
            lobbyBlock = loadLoc(config, "lobby");
        }

        var mapSection = config.getConfigurationSection("map");
        if (mapSection != null) {
            for (String key : mapSection.getKeys(false)) {
                Location loc = loadLoc(config, "map." + key);
                int size = config.getInt("map." + key + ".size", 50);
                if (loc != null) mapBlocks.add(new MapBlock(loc, size));
            }
        }

        var spawnSection = config.getConfigurationSection("spawn");
        if (spawnSection != null) {
            for (String key : spawnSection.getKeys(false)) {
                Location loc = loadLoc(config, "spawn." + key);
                if (loc != null) spawnBlocks.add(loc);
            }
        }
    }

    private Location loadLoc(YamlConfiguration config, String path) {
        String worldName = config.getString(path + ".world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        int x = config.getInt(path + ".x");
        int y = config.getInt(path + ".y");
        int z = config.getInt(path + ".z");
        return new Location(world, x, y, z);
    }

    // --- MapBlock data class ---

    public static class MapBlock {
        public final Location center;
        public final int size;

        public MapBlock(Location center, int size) {
            this.center = center.clone();
            this.size = size;
        }

        public boolean centerEquals(Location loc) {
            return center.getWorld() == loc.getWorld()
                    && center.getBlockX() == loc.getBlockX()
                    && center.getBlockY() == loc.getBlockY()
                    && center.getBlockZ() == loc.getBlockZ();
        }
    }
}

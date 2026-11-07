package com.rounds.blocks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BlockStorage {

    /** Default side length of the map area around the center block. */
    public static final int DEFAULT_MAP_SIZE = 50;
    /** Hard limit for a zone defined by 4 border blocks. */
    public static final int MAX_ZONE_SIZE = 300;
    /** Reject degenerate corner layouts smaller than this. */
    public static final int MIN_ZONE_SIZE = 10;
    /** Border blocks are searched inside this half-size around the map center. */
    private static final int BORDER_SEARCH_RADIUS = MAX_ZONE_SIZE / 2;

    private Location lobbyBlock;
    private final List<MapBlock> mapBlocks = new ArrayList<>();
    private final List<Location> spawnBlocks = new ArrayList<>();
    private final List<Location> borderBlocks = new ArrayList<>();

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

    // --- Map border blocks ---

    public List<Location> getBorderBlocks() { return Collections.unmodifiableList(borderBlocks); }

    public void addBorderBlock(Location loc) {
        borderBlocks.add(loc.clone());
        save();
    }

    public boolean removeBorderBlock(Location loc) {
        boolean removed = borderBlocks.removeIf(b -> b.getWorld() == loc.getWorld()
                && b.getBlockX() == loc.getBlockX()
                && b.getBlockY() == loc.getBlockY()
                && b.getBlockZ() == loc.getBlockZ());
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
            if (defaultBounds(mb).contains(loc)) {
                return mb;
            }
        }
        return null;
    }

    /**
     * Get all spawn blocks that fall within a resolved zone.
     */
    public List<Location> getSpawnBlocksInZone(ZoneBounds bounds) {
        List<Location> result = new ArrayList<>();
        if (bounds == null) return result;
        for (Location s : spawnBlocks) {
            if (bounds.contains(s)) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * Backward-compatible overload using the map's default square.
     */
    public List<Location> getSpawnBlocksInZone(MapBlock zone) {
        return getSpawnBlocksInZone(defaultBounds(zone));
    }

    // --- Zone bounds ---

    /** Square bounds derived from the map center block and its default size. */
    public static ZoneBounds defaultBounds(MapBlock mb) {
        int size = Math.max(MIN_ZONE_SIZE, Math.min(MAX_ZONE_SIZE, mb.size));
        return around(mb.center, size);
    }

    /** Square bounds of the given side length around a center. */
    public static ZoneBounds around(Location center, int size) {
        int clamped = Math.max(MIN_ZONE_SIZE, Math.min(MAX_ZONE_SIZE, size));
        int half = clamped / 2;
        return new ZoneBounds(center.getWorld(),
                center.getBlockX() - half, center.getBlockX() + half,
                center.getBlockZ() - half, center.getBlockZ() + half);
    }

    /**
     * Resolves the round's playable area. If exactly four border blocks are associated with this
     * map (one per quadrant, within +/-150 blocks of the center), their bounding rectangle wins.
     * Otherwise the default square from the center block is used and a warning key is returned.
     */
    public ZoneResolution resolveZoneBounds(MapBlock current, List<MapBlock> allMaps) {
        ZoneBounds fallback = defaultBounds(current);
        if (current.center.getWorld() == null) {
            return new ZoneResolution(fallback, null, 0);
        }

        int cx = current.center.getBlockX();
        int cz = current.center.getBlockZ();

        List<Location> candidates = new ArrayList<>();
        for (Location b : borderBlocks) {
            if (b.getWorld() != current.center.getWorld()) continue;
            if (Math.abs(b.getBlockX() - cx) > BORDER_SEARCH_RADIUS) continue;
            if (Math.abs(b.getBlockZ() - cz) > BORDER_SEARCH_RADIUS) continue;
            MapBlock owner = nearestMapCenter(b, allMaps);
            if (owner == null || !owner.centerEquals(current.center)) {
                continue; // another map owns this border block
            }
            candidates.add(b);
        }

        if (candidates.isEmpty()) {
            // No border blocks configured for this map: the default square is the intended setup.
            return new ZoneResolution(fallback, null, 0);
        }
        if (candidates.size() != 4) {
            return new ZoneResolution(fallback, "block.map-border-reason.not-four", candidates.size());
        }

        boolean[] quadrants = new boolean[4];
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Location b : candidates) {
            int qx = b.getBlockX() <= cx ? 0 : 1;
            int qz = b.getBlockZ() <= cz ? 0 : 1;
            quadrants[qz * 2 + qx] = true;
            minX = Math.min(minX, b.getBlockX());
            maxX = Math.max(maxX, b.getBlockX());
            minZ = Math.min(minZ, b.getBlockZ());
            maxZ = Math.max(maxZ, b.getBlockZ());
        }
        if (!(quadrants[0] && quadrants[1] && quadrants[2] && quadrants[3])) {
            return new ZoneResolution(fallback, "block.map-border-reason.quadrants", 0);
        }

        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        if (width < MIN_ZONE_SIZE || depth < MIN_ZONE_SIZE) {
            return new ZoneResolution(fallback, "block.map-border-reason.too-small", MIN_ZONE_SIZE);
        }
        // Bounds are inclusive, so a "300x300" setup with corners at +/-150 is 301 blocks wide.
        if (width > MAX_ZONE_SIZE + 1 || depth > MAX_ZONE_SIZE + 1) {
            return new ZoneResolution(fallback, "block.map-border-reason.too-large", MAX_ZONE_SIZE);
        }
        if (cx < minX || cx > maxX || cz < minZ || cz > maxZ) {
            return new ZoneResolution(fallback, "block.map-border-reason.center-outside", 0);
        }

        return new ZoneResolution(new ZoneBounds(current.center.getWorld(), minX, maxX, minZ, maxZ), null, 0);
    }

    private static MapBlock nearestMapCenter(Location border, List<MapBlock> maps) {
        MapBlock best = null;
        double bestDist = Double.MAX_VALUE;
        for (MapBlock mb : maps) {
            if (mb.center.getWorld() != border.getWorld()) continue;
            double dx = mb.center.getBlockX() - border.getBlockX();
            double dz = mb.center.getBlockZ() - border.getBlockZ();
            double dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = mb;
            }
        }
        return best;
    }

    public static final class ZoneBounds {
        public final World world;
        public final int minX, maxX, minZ, maxZ;

        public ZoneBounds(World world, int minX, int maxX, int minZ, int maxZ) {
            this.world = world;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        public boolean contains(Location loc) {
            return loc != null && loc.getWorld() != null && loc.getWorld().equals(world)
                    && loc.getBlockX() >= minX && loc.getBlockX() <= maxX
                    && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
        }

        public int width() { return maxX - minX + 1; }

        public int depth() { return maxZ - minZ + 1; }

        public Location center() {
            return new Location(world, (minX + maxX) / 2.0 + 0.5, 0, (minZ + maxZ) / 2.0 + 0.5);
        }
    }

    /** Result of resolving border blocks: effective bounds plus an optional warning key. */
    public static final class ZoneResolution {
        public final ZoneBounds bounds;
        public final String warningKey;
        public final int warningNumber;

        public ZoneResolution(ZoneBounds bounds, String warningKey, int warningNumber) {
            this.bounds = bounds;
            this.warningKey = warningKey;
            this.warningNumber = warningNumber;
        }
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

        for (int i = 0; i < borderBlocks.size(); i++) {
            Location b = borderBlocks.get(i);
            String path = "border." + i;
            if (b.getWorld() == null) continue;
            config.set(path + ".world", b.getWorld().getName());
            config.set(path + ".x", b.getBlockX());
            config.set(path + ".y", b.getBlockY());
            config.set(path + ".z", b.getBlockZ());
        }

        try {
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            config.save(tmp);
            try {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicEx) {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() {
        mapBlocks.clear();
        spawnBlocks.clear();
        borderBlocks.clear();
        lobbyBlock = null;
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

        var borderSection = config.getConfigurationSection("border");
        if (borderSection != null) {
            for (String key : borderSection.getKeys(false)) {
                Location loc = loadLoc(config, "border." + key);
                if (loc != null) borderBlocks.add(loc);
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

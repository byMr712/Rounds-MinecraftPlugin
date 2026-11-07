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
     * Resolves the round's playable area from the border blocks around this map center.
     *
     * The maps on the original arena are packed close together, so a "nearest center" rule is
     * unreliable: a corner of one arena can be closer to the center of a neighboring arena.
     * Instead, all border blocks within the search radius are considered, and the smallest
     * rectangle that contains this center with one corner per quadrant wins. Foreign corners
     * only make a rectangle larger, so they never override the intended four corners.
     */
    public ZoneResolution resolveZoneBounds(MapBlock current) {
        ZoneBounds fallback = defaultBounds(current);
        if (current.center.getWorld() == null) {
            return new ZoneResolution(fallback, null, 0);
        }

        int cx = current.center.getBlockX();
        int cz = current.center.getBlockZ();
        World world = current.center.getWorld();

        List<Location> candidates = new ArrayList<>();
        for (Location b : borderBlocks) {
            if (b.getWorld() != world) continue;
            if (Math.abs(b.getBlockX() - cx) > BORDER_SEARCH_RADIUS) continue;
            if (Math.abs(b.getBlockZ() - cz) > BORDER_SEARCH_RADIUS) continue;
            candidates.add(b);
        }

        if (candidates.isEmpty()) {
            // No border blocks configured for this map: the default square is the intended setup.
            return new ZoneResolution(fallback, null, 0,
                    borderDebugLines(current.center, candidates, null, null, null));
        }
        if (candidates.size() < 4) {
            return new ZoneResolution(fallback, "block.map-border-reason.not-four", candidates.size(),
                    borderDebugLines(current.center, candidates, null, "block.map-border-reason.not-four", null));
        }

        ZoneBounds best = null;
        Location[] bestCorners = null;
        long bestArea = Long.MAX_VALUE;
        long bestCenterOffset = Long.MAX_VALUE;
        boolean sawTooSmall = false;
        boolean sawTooLarge = false;
        int n = candidates.size();

        for (int a = 0; a < n - 3; a++) {
            Location pa = candidates.get(a);
            for (int b = a + 1; b < n - 2; b++) {
                Location pb = candidates.get(b);
                for (int c = b + 1; c < n - 1; c++) {
                    Location pc = candidates.get(c);
                    for (int d = c + 1; d < n; d++) {
                        Location pd = candidates.get(d);

                        int minX = Math.min(Math.min(pa.getBlockX(), pb.getBlockX()),
                                Math.min(pc.getBlockX(), pd.getBlockX()));
                        int maxX = Math.max(Math.max(pa.getBlockX(), pb.getBlockX()),
                                Math.max(pc.getBlockX(), pd.getBlockX()));
                        int minZ = Math.min(Math.min(pa.getBlockZ(), pb.getBlockZ()),
                                Math.min(pc.getBlockZ(), pd.getBlockZ()));
                        int maxZ = Math.max(Math.max(pa.getBlockZ(), pb.getBlockZ()),
                                Math.max(pc.getBlockZ(), pd.getBlockZ()));

                        int width = maxX - minX + 1;
                        int depth = maxZ - minZ + 1;
                        if (cx < minX || cx > maxX || cz < minZ || cz > maxZ) continue;

                        int mask = quadrantMask(pa, cx, cz)
                                | quadrantMask(pb, cx, cz)
                                | quadrantMask(pc, cx, cz)
                                | quadrantMask(pd, cx, cz);
                        if (mask != 0b1111) continue;

                        if (width < MIN_ZONE_SIZE || depth < MIN_ZONE_SIZE) {
                            sawTooSmall = true;
                            continue;
                        }
                        // Bounds are inclusive, so a "300x300" setup with corners at +/-150 is 301 wide.
                        if (width > MAX_ZONE_SIZE + 1 || depth > MAX_ZONE_SIZE + 1) {
                            sawTooLarge = true;
                            continue;
                        }

                        long area = (long) width * depth;
                        long ox = (long) minX + maxX - 2L * cx;
                        long oz = (long) minZ + maxZ - 2L * cz;
                        long centerOffset = ox * ox + oz * oz;
                        if (area < bestArea || (area == bestArea && centerOffset < bestCenterOffset)) {
                            bestArea = area;
                            bestCenterOffset = centerOffset;
                            best = new ZoneBounds(world, minX, maxX, minZ, maxZ);
                            bestCorners = new Location[]{pa, pb, pc, pd};
                        }
                    }
                }
            }
        }

        if (best == null) {
            if (sawTooSmall) {
                return new ZoneResolution(fallback, "block.map-border-reason.too-small", MIN_ZONE_SIZE,
                        borderDebugLines(current.center, candidates, null, "block.map-border-reason.too-small", null));
            }
            if (sawTooLarge) {
                return new ZoneResolution(fallback, "block.map-border-reason.too-large", MAX_ZONE_SIZE,
                        borderDebugLines(current.center, candidates, null, "block.map-border-reason.too-large", null));
            }
            return new ZoneResolution(fallback, "block.map-border-reason.quadrants", candidates.size(),
                    borderDebugLines(current.center, candidates, null, "block.map-border-reason.quadrants", null));
        }
        return new ZoneResolution(best, null, 0,
                borderDebugLines(current.center, candidates, best, null, bestCorners));
    }

    private static List<String> borderDebugLines(Location center, List<Location> candidates,
                                                 ZoneBounds chosen, String warningKey, Location[] used) {
        List<String> lines = new ArrayList<>();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        String chosenText = chosen == null
                ? "none"
                : "[" + chosen.minX + ".." + chosen.maxX + "; " + chosen.minZ + ".." + chosen.maxZ + "]";
        lines.add("BORDER center=(" + cx + "," + center.getBlockY() + "," + cz + ")"
                + " radius=" + BORDER_SEARCH_RADIUS
                + " found=" + candidates.size()
                + " chosen=" + chosenText
                + " warning=" + (warningKey == null ? "none" : warningKey));

        int[] quadrantCounts = new int[4];
        for (Location b : candidates) {
            int qx = b.getBlockX() <= cx ? 0 : 1;
            int qz = b.getBlockZ() <= cz ? 0 : 1;
            int qi = qz * 2 + qx;
            quadrantCounts[qi]++;
            double dist = Math.sqrt((double) (b.getBlockX() - cx) * (b.getBlockX() - cx)
                    + (double) (b.getBlockZ() - cz) * (b.getBlockZ() - cz));
            boolean isUsed = false;
            if (used != null) {
                for (Location u : used) {
                    if (sameBlock(u, b)) {
                        isUsed = true;
                        break;
                    }
                }
            }
            lines.add("BORDER cand=(" + b.getBlockX() + "," + b.getBlockY() + "," + b.getBlockZ() + ")"
                    + " q=" + quadrantName(qi)
                    + " dist=" + String.format(Locale.US, "%.1f", dist)
                    + " used=" + isUsed);
        }
        lines.add("BORDER quadrants NW=" + quadrantCounts[0] + " NE=" + quadrantCounts[1]
                + " SW=" + quadrantCounts[2] + " SE=" + quadrantCounts[3]);

        if (chosen == null) {
            StringBuilder missing = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                if (quadrantCounts[i] == 0) {
                    if (missing.length() > 0) missing.append(',');
                    missing.append(quadrantName(i));
                }
            }
            if (missing.length() > 0) {
                lines.add("BORDER missing=" + missing);
            } else {
                lines.add("BORDER no-valid-rectangle");
            }
        }
        return lines;
    }

    private static boolean sameBlock(Location a, Location b) {
        return a != null && b != null
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private static String quadrantName(int index) {
        switch (index) {
            case 0: return "NW";
            case 1: return "NE";
            case 2: return "SW";
            default: return "SE";
        }
    }

    private static int quadrantMask(Location corner, int cx, int cz) {
        int qx = corner.getBlockX() <= cx ? 0 : 1;
        int qz = corner.getBlockZ() <= cz ? 0 : 1;
        return 1 << (qz * 2 + qx);
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
        public final List<String> debugLines;

        public ZoneResolution(ZoneBounds bounds, String warningKey, int warningNumber) {
            this(bounds, warningKey, warningNumber, java.util.Collections.emptyList());
        }

        public ZoneResolution(ZoneBounds bounds, String warningKey, int warningNumber, List<String> debugLines) {
            this.bounds = bounds;
            this.warningKey = warningKey;
            this.warningNumber = warningNumber;
            this.debugLines = debugLines != null
                    ? java.util.Collections.unmodifiableList(debugLines)
                    : java.util.Collections.emptyList();
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

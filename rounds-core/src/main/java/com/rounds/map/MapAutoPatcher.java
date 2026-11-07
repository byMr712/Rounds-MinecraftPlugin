package com.rounds.map;

import com.rounds.RoundsPlugin;
import com.rounds.debug.DebugLog;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CommandBlock;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

public final class MapAutoPatcher {

    public static final String PATCH_DONE_MSG = "Detected original Rounds map by ifrit. Applying automatic arena patch by Mr712... Done!";

    /** Location of a placed Map Border Block that must survive the patch. */
    private record BorderPos(int x, int y, int z) {}

    private MapAutoPatcher() {}

    /**
     * Checks if the given world is an unpatched original map by ifrit and patches it.
     *
     * @param plugin       plugin instance
     * @param world        target world (defaults to primary world if null)
     * @param force        if true, bypasses marker check and already-patched check
     * @param notifySender optional sender to receive chat output (null on server start)
     * @return true if patched, false if skipped
     */
    public static boolean checkAndPatch(RoundsPlugin plugin, World world, boolean force, CommandSender notifySender) {
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) return false;

        // If not force, check if already patched
        if (!force && isAlreadyPatched(world)) {
            applyMapTextDisplays(world);
            if (notifySender != null) {
                notifySender.sendMessage(ChatColor.YELLOW + "[Rounds] Map is already patched.");
            }
            return false;
        }

        // Marker check (at least 2 of 4 markers must match unless force=true)
        if (!force) {
            int markers = countIfritMarkers(world);
            if (markers < 2) {
                if (notifySender != null) {
                    notifySender.sendMessage(ChatColor.RED + "[Rounds] Map markers do not match ifrit's map (" + markers + "/4 matched). Use force if intended.");
                }
                return false;
            }
        }

        try {
            applyPatch(plugin, world);

            if (notifySender == null) {
                // Startup patch: print the success line after the plugin's "enabled!" message
                // instead of in the middle of onEnable().
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> plugin.getLogger().info(PATCH_DONE_MSG), 1L);
            } else {
                plugin.getLogger().info(PATCH_DONE_MSG);
                notifySender.sendMessage(ChatColor.GREEN + PATCH_DONE_MSG);
            }

            DebugLog.log("MAP", PATCH_DONE_MSG);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[Rounds] Failed to apply map auto-patch: " + e.getMessage());
            e.printStackTrace();
            if (notifySender != null) {
                notifySender.sendMessage(ChatColor.RED + "[Rounds] Auto-patch failed: " + e.getMessage());
            }
            return false;
        }
    }

    public static boolean isAlreadyPatched(World world) {
        File worldDir = world.getWorldFolder();
        File mapBlocksFile = new File(worldDir, "rounds-map-blocks.yml");
        if (!mapBlocksFile.exists()) {
            File overworldDim = new File(worldDir, "dimensions/minecraft/overworld/rounds-map-blocks.yml");
            if (overworldDim.exists()) {
                mapBlocksFile = overworldDim;
            }
        }
        if (!mapBlocksFile.exists() || mapBlocksFile.length() < 100) {
            return false;
        }

        // Check if lobby start button at (1, -50, 6) is present
        Material btnMat = world.getBlockAt(1, -50, 6).getType();
        if (!btnMat.name().endsWith("_BUTTON")) {
            return false;
        }

        // Check if command block at (1, -52, 6) already runs /rdebug start
        Block b = world.getBlockAt(1, -52, 6);
        if (b.getState() instanceof CommandBlock cb) {
            String cmd = cb.getCommand();
            return cmd != null && cmd.toLowerCase(Locale.ROOT).contains("/rdebug start");
        }
        return false;
    }

    public static int countIfritMarkers(World world) {
        int count = 0;

        // Marker 1: Command block at (1, -52, 6) contains "start_g" or "/rdebug start"
        Block b1 = world.getBlockAt(1, -52, 6);
        if (b1.getState() instanceof CommandBlock cb1) {
            String cmd = cb1.getCommand();
            if (cmd != null && (cmd.contains("start_g") || cmd.toLowerCase(Locale.ROOT).contains("/rdebug start"))) {
                count++;
            }
        }

        // Marker 2: Command block at (68, -62, -64) contains "levitation"
        Block b2 = world.getBlockAt(68, -62, -64);
        if (b2.getState() instanceof CommandBlock cb2) {
            String cmd = cb2.getCommand();
            if (cmd != null && cmd.toLowerCase(Locale.ROOT).contains("levitation")) {
                count++;
            }
        }

        // Marker 3: World spawn at (1, -50, 16)
        Location spawn = world.getSpawnLocation();
        if (spawn.getBlockX() == 1 && spawn.getBlockY() == -50 && spawn.getBlockZ() == 16) {
            count++;
        }

        // Marker 4: Glazed terracotta at original team spawns (e.g. -29, -61, -5 or -73, -61, 67 or -73, -61, 33)
        Block s1 = world.getBlockAt(-29, -61, -5);
        Block s2 = world.getBlockAt(-73, -61, 67);
        Block s3 = world.getBlockAt(-73, -61, 33);
        if (isGlazedTerracotta(s1.getType()) || isGlazedTerracotta(s2.getType()) || isGlazedTerracotta(s3.getType())) {
            count++;
        }

        return count;
    }

    private static boolean isGlazedTerracotta(Material mat) {
        return mat != null && mat.name().endsWith("_GLAZED_TERRACOTTA");
    }

    private static List<BorderPos> readBorderPositions(File file) {
        List<BorderPos> result = new ArrayList<>();
        if (file == null || !file.exists()) return result;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            var section = cfg.getConfigurationSection("border");
            if (section == null) return result;
            for (String key : section.getKeys(false)) {
                String path = "border." + key;
                result.add(new BorderPos(
                        cfg.getInt(path + ".x"),
                        cfg.getInt(path + ".y"),
                        cfg.getInt(path + ".z")));
            }
        } catch (Exception ignored) {
            // A broken/old config must not abort the whole map patch.
        }
        return result;
    }

    private static void writeBorderPositions(File file, List<BorderPos> positions, String worldName) {
        if (file == null || !file.exists() || positions.isEmpty()) return;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            cfg.set("border", null);
            int i = 0;
            for (BorderPos pos : positions) {
                String path = "border." + i++;
                cfg.set(path + ".world", worldName);
                cfg.set(path + ".x", pos.x());
                cfg.set(path + ".y", pos.y());
                cfg.set(path + ".z", pos.z());
            }
            cfg.save(file);
        } catch (IOException e) {
            // Non-fatal: the blocks are already restored in the world.
            e.printStackTrace();
        }
    }

    private static void applyPatch(RoundsPlugin plugin, World world) throws IOException {
        File worldDir = world.getWorldFolder();
        File dimOverworld = new File(worldDir, "dimensions/minecraft/overworld");
        File rootMapBlocks = new File(worldDir, "rounds-map-blocks.yml");
        File dimMapBlocks = new File(dimOverworld, "rounds-map-blocks.yml");

        // Collect placed Map Border Blocks before the bundled config overwrites the YAML file.
        // The patcher may also overwrite their blocks, so they are restored after the block pass.
        List<BorderPos> borderPositions = readBorderPositions(rootMapBlocks);
        for (BorderPos pos : readBorderPositions(dimMapBlocks)) {
            if (!borderPositions.contains(pos)) borderPositions.add(pos);
        }

        // 1. Copy rounds-map-blocks.yml and rounds-blocks.yml to world directory (and dimensions subfolder if present)
        copyResourceToWorld(plugin, "maps/ifrit/rounds-map-blocks.yml", worldDir, "rounds-map-blocks.yml");
        copyResourceToWorld(plugin, "maps/ifrit/rounds-blocks.yml", worldDir, "rounds-blocks.yml");

        if (dimOverworld.exists() && dimOverworld.isDirectory()) {
            copyResourceToWorld(plugin, "maps/ifrit/rounds-map-blocks.yml", dimOverworld, "rounds-map-blocks.yml");
            copyResourceToWorld(plugin, "maps/ifrit/rounds-blocks.yml", dimOverworld, "rounds-blocks.yml");
        }

        // 2. Unpack binary delta patch and apply Mr712's block modifications (wool, concrete, obsidian markers, etc.)
        InputStream resStream = plugin.getResource("maps/ifrit/world-patch.bin.gz");
        if (resStream == null) {
            throw new FileNotFoundException("world-patch.bin.gz resource not found");
        }

        try (DataInputStream in = new DataInputStream(new GZIPInputStream(resStream))) {
            int totalRecords = in.readInt();
            int paletteSize = in.readUnsignedByte();

            Material[] palette = new Material[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                int len = in.readUnsignedByte();
                byte[] nameBytes = new byte[len];
                in.readFully(nameBytes);
                String name = new String(nameBytes, StandardCharsets.UTF_8);
                if (name.startsWith("minecraft:")) {
                    name = name.substring("minecraft:".length());
                }
                Material mat = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
                if (mat == null) {
                    if ("SHORT_GRASS".equalsIgnoreCase(name)) mat = Material.matchMaterial("GRASS");
                    else if ("GRASS".equalsIgnoreCase(name)) mat = Material.matchMaterial("SHORT_GRASS");
                }
                palette[i] = (mat != null) ? mat : Material.AIR;
            }

            for (int i = 0; i < totalRecords; i++) {
                short x = in.readShort();
                short y = in.readShort();
                short z = in.readShort();
                int matId = in.readUnsignedByte();

                Material mat = (matId < palette.length) ? palette[matId] : Material.AIR;

                int cx = x >> 4;
                int cz = z >> 4;
                if (!world.isChunkLoaded(cx, cz)) {
                    world.loadChunk(cx, cz);
                }
                world.getBlockAt(x, y, z).setType(mat, false);
            }
        }

        // Preserve user-placed borders over the bundled defaults, if any were recorded
        // before the patch (for example when the map is force-patched again).
        if (!borderPositions.isEmpty()) {
            writeBorderPositions(rootMapBlocks, borderPositions, world.getName());
            if (dimOverworld.exists() && dimOverworld.isDirectory()) {
                writeBorderPositions(dimMapBlocks, borderPositions, world.getName());
            }
        }

        // The final config is the source of truth: bundled defaults or preserved user borders.
        List<BorderPos> finalBorders = readBorderPositions(rootMapBlocks);
        if (finalBorders.isEmpty() && dimOverworld.exists() && dimOverworld.isDirectory()) {
            finalBorders = readBorderPositions(dimMapBlocks);
        }

        // Restore the physical Map Border Blocks; the block patch may have overwritten them.
        for (BorderPos pos : finalBorders) {
            int cx = pos.x() >> 4;
            int cz = pos.z() >> 4;
            if (!world.isChunkLoaded(cx, cz)) {
                world.loadChunk(cx, cz);
            }
            world.getBlockAt(pos.x(), pos.y(), pos.z()).setType(Material.LAPIS_BLOCK, false);
        }

        // Keep both copies of the config in sync so BlockStorage resolves the enlarged bounds.
        if (!finalBorders.isEmpty() && dimOverworld.exists() && dimOverworld.isDirectory()) {
            writeBorderPositions(dimMapBlocks, finalBorders, world.getName());
        }

        // 3. Command blocks adjustment
        // Lobby start button at (1, -50, 6) on top of the floor block (1, -51, 6)
        Block bButton = world.getBlockAt(1, -50, 6);
        bButton.setType(Material.STONE_BUTTON, false);
        if (bButton.getBlockData() instanceof org.bukkit.block.data.type.Switch sw) {
            sw.setAttachedFace(org.bukkit.block.data.type.Switch.AttachedFace.FLOOR);
            sw.setFacing(org.bukkit.block.BlockFace.NORTH);
            bButton.setBlockData(sw, false);
        }

        // Lobby start command block at (1, -52, 6) -> /rdebug start
        Block bStart = world.getBlockAt(1, -52, 6);
        if (bStart.getType() != Material.COMMAND_BLOCK) {
            bStart.setType(Material.COMMAND_BLOCK, false);
        }
        if (bStart.getState() instanceof CommandBlock cb) {
            cb.setCommand("/rdebug start");
            cb.update(true, false);
        }

        // Clear original levitation command blocks in Zone 5
        world.getBlockAt(68, -62, -64).setType(Material.AIR, false);
        world.getBlockAt(68, -62, -63).setType(Material.AIR, false);

        // Clear decorative particle command block in lobby
        world.getBlockAt(1, -53, 6).setType(Material.AIR, false);

        // 4. Edit TextDisplay entities in lobby
        applyMapTextDisplays(world);

        // 5. Save world to disk
        world.save();

        // 6. Reload plugin listeners and storage
        if (plugin.getBlockListener() != null) {
            plugin.getBlockListener().reload();
        }
    }

    /**
     * Updates all lobby TextDisplay entities to match the current language.
     * Called at initial patch time and on /rdebug setlanguage.
     */
    public static void applyMapTextDisplays(World world) {
        if (world == null) return;
        world.loadChunk(0, 0);
        world.loadChunk(-1, 0);

        java.util.Collection<org.bukkit.entity.Entity> nearby = world.getNearbyEntities(
                new Location(world, 1, -49, 6), 12, 6, 12,
                e -> e instanceof org.bukkit.entity.TextDisplay);

        if (nearby.isEmpty()) return;

        // Delete "Map from 1 to 8 players", "The team needs to score 5 wins", and one duplicate "Start"
        java.util.Set<java.util.UUID> deleted = new java.util.HashSet<>();
        for (org.bukkit.entity.Entity e : nearby) {
            if (nearPos(e, -1.94, -47.34, 6.50) || nearPos(e, -1.94, -47.64, 6.49)) {
                e.remove();
                deleted.add(e.getUniqueId());
            }
        }
        boolean startDeleted = false;
        for (org.bukkit.entity.Entity e : nearby) {
            if (!startDeleted && nearPos(e, 1.49, -49.88, 6.50)) {
                e.remove(); deleted.add(e.getUniqueId()); startDeleted = true; break;
            }
        }

        // Apply translated text to remaining TextDisplay entities
        for (org.bukkit.entity.Entity e : nearby) {
            if (deleted.contains(e.getUniqueId())) continue;
            if (!(e instanceof org.bukkit.entity.TextDisplay td)) continue;

            if      (nearPos(td, 1.52, -48.25,  3.06)) setTDText(td, "#FFAA00", true,  "map.ifrit-author", 1.275f);
            else if (nearPos(td, -2.00, -48.16, 2.99))  setTDText(td, "#5555FF", true,  "map.ifrit-team-blue", null);
            else if (nearPos(td, -2.01, -48.16, 10.00)) setTDText(td, "#55FF55", true,  "map.ifrit-team-green", null);
            else if (nearPos(td, 5.01, -48.16,  3.00))  setTDText(td, "#FFFF55", true,  "map.ifrit-team-yellow", null);
            else if (nearPos(td, 5.00, -48.16,  10.00)) setTDText(td, "#FF5555", true,  "map.ifrit-team-red", null);
            else if (nearPos(td, 1.49, -49.88,  6.50))  setTDText(td, "#FFAA00", true,  "map.ifrit-start", null);
            else if (nearPos(td, 4.94, -47.74,  6.51))  setTDText(td, "#55FF55", false, "map.ifrit-sound-music", 0.75f);
            else if (nearPos(td, 4.94, -48.03,  6.51))  setTDText(td, "#55FF55", false, "map.ifrit-sound-gun", 0.75f);
            else if (nearPos(td, 1.48, -47.25,  9.94))  setTDText(td, "#55FF55", false, "map.ifrit-ctrl-shield", null);
            else if (nearPos(td, 1.49, -47.94,  9.94))  setTDText(td, "#FFFF55", false, "map.ifrit-ctrl-shoot", null);
            else if (nearPos(td, 1.48, -47.57,  9.94))  setTDText(td, "#00AA00", false, "map.ifrit-ctrl-reload", null);
            // (1.51, -47.54, 3.06) "Rounds In Minecraft" — kept as-is
        }
    }

    private static boolean nearPos(org.bukkit.entity.Entity e, double x, double y, double z) {
        Location l = e.getLocation();
        return Math.abs(l.getX() - x) < 0.25 && Math.abs(l.getY() - y) < 0.12 && Math.abs(l.getZ() - z) < 0.25;
    }

    private static void setTDText(org.bukkit.entity.TextDisplay td, String hex, boolean bold, String key, Float scale) {
        net.kyori.adventure.text.Component c = net.kyori.adventure.text.Component
                .text(com.rounds.util.Messages.get(key))
                .color(net.kyori.adventure.text.format.TextColor.fromHexString(hex));
        if (bold) c = c.decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true);
        td.text(c);
        td.setLineWidth(400);
        if (scale != null) {
            try {
                org.bukkit.util.Transformation t = td.getTransformation();
                td.setTransformation(new org.bukkit.util.Transformation(
                        t.getTranslation(),
                        t.getLeftRotation(),
                        new org.joml.Vector3f(scale, scale, scale),
                        t.getRightRotation()
                ));
            } catch (Throwable ignored) {}
        }
    }

    private static void copyResourceToWorld(RoundsPlugin plugin, String resourcePath, File targetDir, String fileName) throws IOException {
        InputStream in = plugin.getResource(resourcePath);
        if (in == null) return;
        File targetFile = new File(targetDir, fileName);
        try (in) {
            Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

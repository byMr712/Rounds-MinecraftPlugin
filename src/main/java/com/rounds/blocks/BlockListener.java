package com.rounds.blocks;

import com.rounds.RoundsKeys;
import com.rounds.RoundsPlugin;
import com.rounds.teams.TeamManager;
import com.rounds.teams.TeamManager.GameTeam;
import com.rounds.util.Messages;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BlockListener implements Listener {

    private final RoundsPlugin plugin;
    private final File blocksFile;

    private static final Map<GameTeam, Material> TEAM_BLOCK_COLORS = Map.of(
        GameTeam.BLUE, Material.LIGHT_BLUE_WOOL,
        GameTeam.RED, Material.RED_WOOL,
        GameTeam.YELLOW, Material.YELLOW_WOOL,
        GameTeam.GREEN, Material.LIME_WOOL
    );

    private final Map<Location, GameTeam> joinBlocks = new HashMap<>();
    private final Set<Location> cdshootBlocks = new HashSet<>();

    private final Set<UUID> stepCooldown = new HashSet<>();

    public BlockListener(RoundsPlugin plugin) {
        this.plugin = plugin;
        this.blocksFile = new File(Bukkit.getWorlds().get(0).getWorldFolder(), "rounds-blocks.yml");
        File oldFile = new File(plugin.getDataFolder(), "placed-blocks.yml");
        if (oldFile.exists() && !blocksFile.exists()) {
            oldFile.renameTo(blocksFile);
        }
        loadBlocks();
    }

    public static ItemStack createJoinBlock(GameTeam team) {
        Material mat = TEAM_BLOCK_COLORS.getOrDefault(team, Material.WHITE_WOOL);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String teamName = Messages.get("team." + team.name().toLowerCase());
        meta.setDisplayName(team.getColor() + Messages.get("team.join-block", teamName));
        meta.getPersistentDataContainer().set(RoundsKeys.JOIN_BLOCK, PersistentDataType.STRING, team.name());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createCDShootBlock() {
        ItemStack item = new ItemStack(Material.IRON_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + Messages.get("block.tagshield-cd"));
        meta.getPersistentDataContainer().set(RoundsKeys.CDSHOOT_BLOCK, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static void giveAllBlocks(Player player) {
        for (GameTeam team : GameTeam.values()) {
            player.getInventory().addItem(createJoinBlock(team));
        }
        player.getInventory().addItem(createCDShootBlock());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        Location loc = event.getTo().clone().subtract(0, 1, 0).getBlock().getLocation();

        GameTeam team = joinBlocks.get(loc);
        if (team != null) {
            if (stepCooldown.contains(player.getUniqueId())) return;
            stepCooldown.add(player.getUniqueId());
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> stepCooldown.remove(player.getUniqueId()), 20L);

            if (!player.hasPermission("rounds.join")) return;
            if (plugin.getGameManager().isGameStarted()) {
                player.sendMessage(ChatColor.RED + Messages.get("team.game-in-progress"));
                return;
            }
            if (plugin.getTeamManager().joinTeam(player.getUniqueId(), team)) {
                String teamName = Messages.get("team." + team.name().toLowerCase());
                String msg = team.getColor() + "\u00A7l" + Messages.get("team.joined", teamName);
                player.sendActionBar(msg);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            }
            return;
        }

        if (cdshootBlocks.contains(loc)) {
            if (stepCooldown.contains(player.getUniqueId())) return;
            stepCooldown.add(player.getUniqueId());
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> stepCooldown.remove(player.getUniqueId()), 20L);
            if (!player.hasPermission("rounds.admin")) return;
            if (plugin.getGameManager().isGameStarted()) {
                player.sendMessage(ChatColor.RED + Messages.get("debug.game-already-started"));
                return;
            }
            plugin.getGameManager().startGame();
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getGameManager().isGameStarted()
                && !plugin.getGameManager().isParticipant(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        ItemStack item = event.getItemInHand();
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Location loc = event.getBlock().getLocation();

        String joinTeam = pdc.get(RoundsKeys.JOIN_BLOCK, PersistentDataType.STRING);
        if (joinTeam != null) {
            try {
                joinBlocks.put(loc, GameTeam.valueOf(joinTeam));
            } catch (IllegalArgumentException e) { return; }
            saveBlocks();
            return;
        }
        if (pdc.has(RoundsKeys.CDSHOOT_BLOCK, PersistentDataType.BYTE)) {
            cdshootBlocks.add(loc);
            saveBlocks();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        boolean removed = joinBlocks.remove(loc) != null;
        removed |= cdshootBlocks.remove(loc);
        if (removed) saveBlocks();
    }

    private void saveBlocks() {
        YamlConfiguration config = new YamlConfiguration();
        int i = 0;
        for (Map.Entry<Location, GameTeam> entry : joinBlocks.entrySet()) {
            String path = "join." + i;
            if (entry.getKey().getWorld() == null) { i++; continue; }
            config.set(path + ".world", entry.getKey().getWorld().getName());
            config.set(path + ".x", entry.getKey().getBlockX());
            config.set(path + ".y", entry.getKey().getBlockY());
            config.set(path + ".z", entry.getKey().getBlockZ());
            config.set(path + ".team", entry.getValue().name());
            i++;
        }
        i = 0;
        for (Location loc : cdshootBlocks) {
            String path = "cdshoot." + i;
            if (loc.getWorld() == null) { i++; continue; }
            config.set(path + ".world", loc.getWorld().getName());
            config.set(path + ".x", loc.getBlockX());
            config.set(path + ".y", loc.getBlockY());
            config.set(path + ".z", loc.getBlockZ());
            i++;
        }
        try {
            config.save(blocksFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save placed blocks: " + e.getMessage());
        }
    }

    private void loadBlocks() {
        if (!blocksFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(blocksFile);

        var joinSection = config.getConfigurationSection("join");
        if (joinSection != null) {
            for (String key : joinSection.getKeys(false)) {
                try {
                    String world = joinSection.getString(key + ".world");
                    int x = joinSection.getInt(key + ".x");
                    int y = joinSection.getInt(key + ".y");
                    int z = joinSection.getInt(key + ".z");
                    String teamName = joinSection.getString(key + ".team");
                    if (world == null || teamName == null) continue;
                    World w = Bukkit.getWorld(world);
                    if (w == null) continue;
                    Location loc = new Location(w, x, y, z);
                    joinBlocks.put(loc, GameTeam.valueOf(teamName));
                } catch (Exception ignored) {}
            }
        }

        loadLocationSet(config, "cdshoot", cdshootBlocks);
    }

    private void loadLocationSet(YamlConfiguration config, String sectionName, Set<Location> set) {
        var section = config.getConfigurationSection(sectionName);
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                String world = section.getString(key + ".world");
                int x = section.getInt(key + ".x");
                int y = section.getInt(key + ".y");
                int z = section.getInt(key + ".z");
                if (world == null) continue;
                World w = Bukkit.getWorld(world);
                if (w == null) continue;
                set.add(new Location(w, x, y, z));
            } catch (Exception ignored) {}
        }
    }
}

package com.rounds.command;

import com.rounds.RoundsConfig;
import com.rounds.RoundsKeys;
import com.rounds.RoundsPlugin;
import com.rounds.game.GameManager;
import com.rounds.item.GunItem;
import com.rounds.placeholder.RoundsPlaceholders;
import com.rounds.player.PlayerData;
import com.rounds.player.PlayerDataManager;
import com.rounds.teams.TeamManager;
import com.rounds.teams.TeamManager.GameTeam;
import com.rounds.util.Messages;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class DebugCommands implements CommandExecutor, TabCompleter {

    private final RoundsPlugin plugin;
    private static final List<String> STATS = Arrays.asList(
        "dmg", "atks", "atk_speed", "atkr", "bounce", "ammo", "bullets", "cold", "poison",
        "toxic_cloud", "leech", "homing", "poison_lvl", "cold_lvl", "parazit", "hp",
        "bomb_bullet", "bomb_on_block", "explode_bullets", "bullet_speed", "empower",
        "empower_charge", "dark_strength", "barage", "big_bullet", "grow",
        "truster_lvl", "jump_height", "dark", "atks_reload"
    );
    private static final List<String> EFFECTS = Arrays.asList(
        "SPEED", "SLOW", "FAST_DIGGING", "SLOW_DIGGING", "INCREASE_DAMAGE",
        "HEAL", "HARM", "JUMP", "CONFUSION", "BLINDNESS", "NIGHT_VISION",
        "FIRE_RESISTANCE", "WATER_BREATHING", "INVISIBILITY", "POISON",
        "REGENERATION", "RESISTANCE", "HEALTH_BOOST", "ABSORPTION", "SATURATION",
        "WEAKNESS", "WITHER", "LUCK", "UNLUCK", "LEVITATION", "DOLPHINS_GRACE",
        "BAD_OMEN", "HERO_OF_THE_VILLAGE"
    );

    public DebugCommands(RoundsPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══ Box drawing helpers ═══

    private static final String B = "\u00A76"; // ChatColor.GOLD
    private static final String W = "\u00A7f"; // ChatColor.WHITE
    private static final String G = "\u00A7a"; // ChatColor.GREEN
    private static final String Y = "\u00A7e"; // ChatColor.YELLOW
    private static final String A = "\u00A7b"; // ChatColor.AQUA
    private static final String R = "\u00A7c"; // ChatColor.RED
    private static final String GR = "\u00A77"; // ChatColor.GRAY
    private static final String LP = "\u00A7d"; // ChatColor.LIGHT_PURPLE
    private static final String BOLD = "\u00A7l";
    private static final String RESET = "\u00A7r";

    private void boxHeader(CommandSender sender, String title) {
        sender.sendMessage(B + "\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
        sender.sendMessage(B + "\u2551 " + BOLD + RESET + B + "\u2502 " + BOLD + title);
        sender.sendMessage(B + "\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
    }

    private void boxHeader(CommandSender sender, String title, String subtitle) {
        sender.sendMessage(B + "\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
        sender.sendMessage(B + "\u2551 " + BOLD + RESET + B + "\u2502 " + BOLD + title);
        if (subtitle != null) {
            sender.sendMessage(B + "\u2551   " + Y + subtitle);
        }
        sender.sendMessage(B + "\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
    }

    private void boxLine(CommandSender sender, String text) {
        sender.sendMessage(B + "\u2551 " + RESET + text);
    }

    private void boxLine(CommandSender sender, String colorPrefix, String text) {
        sender.sendMessage(B + "\u2551 " + RESET + colorPrefix + text);
    }

    private void boxLine(CommandSender sender, ChatColor color, String text) {
        sender.sendMessage(B + "\u2551 " + RESET + color + text);
    }

    private void boxSection(CommandSender sender, String title) {
        boxSection(sender, R, title);
    }

    private void boxSection(CommandSender sender, String colorPrefix, String title) {
        sender.sendMessage(B + "\u2560\u2550\u2550 " + BOLD + colorPrefix + title + " \u2193");
    }

    private void boxKv(CommandSender sender, String text) {
        sender.sendMessage(B + "\u2551   " + text);
    }

    private void boxKv(CommandSender sender, String key, String value) {
        sender.sendMessage(B + "\u2551   " + GR + key + ": " + G + value);
    }

    private void boxKv(CommandSender sender, String key, String value, ChatColor valueColor) {
        sender.sendMessage(B + "\u2551   " + GR + key + ": " + valueColor + value);
    }

    private void boxFooter(CommandSender sender) {
        sender.sendMessage(B + "\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
    }

    private void boxStat(CommandSender sender, String key, double value) {
        boxKv(sender, Messages.get("debug." + key), String.valueOf(PlayerData.round2(value)));
    }

    public static void register(RoundsPlugin plugin) {
        DebugCommands handler = new DebugCommands(plugin);
        plugin.getCommand("rdebug").setExecutor(handler);
        plugin.getCommand("rdebug").setTabCompleter(handler);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("join")) {
            handleJoin(sender);
            return true;
        }
        boolean freeplayActive = plugin.getRoundsConfig().isFreeplay();
        boolean admin = sender.hasPermission("rounds.admin");
        if (!admin && !(freeplayActive && FREEPLAY_COMMANDS.contains(sub)) && !PUBLIC_COMMANDS.contains(sub)) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.no-permission"));
            return true;
        }
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "start" -> {
                if (plugin.getGameManager().isGameStarted()) {
                    sender.sendMessage(ChatColor.RED + Messages.get("debug.game-already-started"));
                } else {
                    plugin.getGameManager().startGame();
                }
            }
            case "status", "state" -> handleState(sender);
            case "stop" -> handleStop(sender);
            case "rounds" -> handleRounds(sender, args);
            case "autodeath" -> handleAutoDeath(sender, args);
            case "info" -> handleInfo(sender);
            case "stats" -> handleStats(sender, args);
            case "givegun" -> handleGiveGun(sender, args);
            case "setstat" -> handleSetStat(sender, args);
            case "setteam" -> handleSetTeam(sender, args);
            case "setlanguage" -> handleSetLanguage(sender, args);
            case "effect" -> handleEffect(sender, args);
            case "heal" -> handleHeal(sender, args);
            case "cards", "card" -> handleCards(sender, args);
            case "resetstats" -> handleResetStats(sender);
            case "reload" -> handleReload(sender);
            case "giveblocks" -> handleBlocks(sender, args);
            case "setlobby" -> handleSetLobby(sender);
            case "jumppos1" -> handleJumpPos(sender, 1);
            case "jumppos2" -> handleJumpPos(sender, 2);
            case "jumpset" -> handleJumpSet(sender);
            case "uppos1" -> handleUpPos(sender, 1);
            case "uppos2" -> handleUpPos(sender, 2);
            case "upset" -> handleUpSet(sender);
            case "wheel" -> handleWheel(sender, args);
            case "freeplay" -> handleFreeplay(sender, args);
            case "tab" -> handleTab(sender, args);
            case "placeholders" -> handlePlaceholders(sender);
            default -> sender.sendMessage(ChatColor.RED + Messages.get("debug.unknown-command", args[0]));
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        boxHeader(sender, Messages.get("debug.title"));

        boxSection(sender, R, Messages.get("debug.help-section-game"));
        boxKv(sender, "/rdebug start", Messages.get("debug.help-start"));
        boxKv(sender, "/rdebug stop", Messages.get("debug.help-stop"));
        boxKv(sender, "/rdebug status", Messages.get("debug.help-status"));
        boxKv(sender, "/rdebug rounds <n>", Messages.get("debug.help-rounds"));
        boxKv(sender, "/rdebug info", Messages.get("debug.help-info"));
        boxKv(sender, "/rdebug join", Messages.get("debug.help-join"));
        boxKv(sender, "/rdebug freeplay on|off", Messages.get("debug.help-freeplay"));

        boxSection(sender, R, Messages.get("debug.help-section-map-blocks"));
        boxKv(sender, "/rdebug giveblocks", Messages.get("debug.help-giveblocks"));
        boxKv(sender, "/rdebug setlobby", Messages.get("debug.help-setlobby"));
        boxKv(sender, "/rdebug jumppos1 | jumppos2 | jumpset", Messages.get("debug.help-jumppos"));
        boxKv(sender, "/rdebug uppos1 | uppos2 | upset", Messages.get("debug.help-uppos"));

        boxSection(sender, R, Messages.get("debug.help-section-cards"));
        boxKv(sender, "/rdebug cards reload", Messages.get("debug.help-cards-reload"));
        boxKv(sender, "/rdebug cards show [player]", Messages.get("debug.help-cards-show"));
        boxKv(sender, "/rdebug cards add <name>", Messages.get("debug.help-cards-add"));
        boxKv(sender, "/rdebug wheel on|off", Messages.get("debug.help-wheel"));

        boxSection(sender, R, Messages.get("debug.help-section-scoreboard"));
        boxKv(sender, "/rdebug tab on|off", Messages.get("debug.help-tab"));
        boxKv(sender, "/rdebug tab name <title>", Messages.get("debug.help-tab-name"));

        boxSection(sender, R, Messages.get("debug.help-section-items"));
        boxKv(sender, "/rdebug givegun [player|@a]", Messages.get("debug.help-givegun"));

        boxSection(sender, R, Messages.get("debug.help-section-debug"));
        boxKv(sender, "/rdebug stats [player]", Messages.get("debug.help-stats"));
        boxKv(sender, "/rdebug stats <player> <stat> <value>", Messages.get("debug.help-stats-set"));
        boxKv(sender, "/rdebug setstat <stat> <value> [player]", Messages.get("debug.help-setstat"));
        boxKv(sender, "/rdebug setteam <COLOR>", Messages.get("debug.help-setteam"));
        boxKv(sender, "/rdebug setlanguage <lang>", Messages.get("debug.help-setlanguage"));
        boxKv(sender, "/rdebug effect <type> <amp> <dur>", Messages.get("debug.help-effect"));
        boxKv(sender, "/rdebug heal [amount]", Messages.get("debug.help-heal"));
        boxKv(sender, "/rdebug resetstats", Messages.get("debug.help-resetstats"));
        boxKv(sender, "/rdebug reload", Messages.get("debug.help-reload"));

        boxSection(sender, R, Messages.get("debug.help-section-placeholders"));
        boxKv(sender, "/rdebug placeholders", Messages.get("debug.help-placeholders"));

        boxLine(sender, GR, Messages.get("debug.help-public-note"));
        boxFooter(sender);
    }

    private void handlePlaceholders(CommandSender sender) {
        boxHeader(sender, Messages.get("debug.placeholders-title"));

        boxSection(sender, G, Messages.get("debug.ph-game-section"));
        for (RoundsPlaceholders.PlaceholderEntry ph : RoundsPlaceholders.getGamePlaceholders()) {
            boxKv(sender, "%" + ph.key() + "%", Messages.get(ph.descriptionKey()));
        }

        boxSection(sender, A, Messages.get("debug.ph-stats-section"));
        for (RoundsPlaceholders.PlaceholderEntry ph : RoundsPlaceholders.getStatPlaceholders()) {
            boxKv(sender, "%" + ph.key() + "%", Messages.get(ph.descriptionKey()));
        }

        boxFooter(sender);
    }

    private void handleJoin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player"));
            return;
        }
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameStarted()) {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.game-not-started"));
            return;
        }
        GameTeam existingTeam = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (existingTeam != null) {
            if (gm.isPendingCardJoiner(player.getUniqueId())) {
                String teamName = Messages.get("team." + existingTeam.name().toLowerCase());
                String msgKey = gm.isReturningJoiner(player.getUniqueId()) ? "team.restored-team" : "team.joined-team";
                sender.sendMessage(ChatColor.GOLD + Messages.get(msgKey, existingTeam.getColor() + teamName));
                return;
            }
            sender.sendMessage(ChatColor.GOLD + Messages.get("team.already-has-team"));
            return;
        }

        UUID uuid = player.getUniqueId();
        boolean returning = plugin.getPlayerDataManager().isActive(uuid);
        GameTeam team = gm.findSmallestTeam();

        if (returning) {
            PlayerDataManager.SavedPlayerData saved = plugin.getPlayerDataManager().loadPlayerFullData(uuid);
            if (saved != null) {
                plugin.getTeamManager().joinTeam(uuid, saved.team != null ? saved.team : team);
                plugin.getPlayerDataManager().applySavedData(uuid, saved);
            } else {
                plugin.getTeamManager().joinTeam(uuid, team);
            }
        } else {
            plugin.getTeamManager().joinTeam(uuid, team);
            plugin.getPlayerDataManager().trackPlayer(uuid);
            plugin.getPlayerDataManager().savePlayerFullData(uuid, team, null);
        }

        GameTeam finalTeam = plugin.getTeamManager().getPlayerTeam(uuid);
        gm.markPendingCardJoiner(uuid, returning);
        gm.applyTeamColor(player);
        gm.buildScoreboard(player);

        if (gm.getState() == GameManager.GameState.CARDS) {
            plugin.getCardManager().openCardSelection(player, finalTeam);
            player.setGameMode(GameMode.SPECTATOR);
            player.setInvulnerable(true);
        } else if (gm.getState() == GameManager.GameState.PLAYING) {
            player.setGameMode(GameMode.SPECTATOR);
            player.setInvulnerable(true);
            Location spawn = gm.getTeamSpawns().get(finalTeam);
            if (spawn != null) player.teleport(spawn);
        }

        String teamName = Messages.get("team." + finalTeam.name().toLowerCase());
        if (returning) {
            sender.sendMessage(ChatColor.GOLD + Messages.get("team.restored-team", finalTeam.getColor() + teamName));
        } else {
            sender.sendMessage(ChatColor.GOLD + Messages.get("team.joined-team", finalTeam.getColor() + teamName));
        }
    }

    // === GAME MANAGEMENT ===

    private void handleState(CommandSender sender) {
        GameManager gm = plugin.getGameManager();
        TeamManager tm = plugin.getTeamManager();
        boxHeader(sender, Messages.get("debug.state-title"));
        boxKv(sender, Messages.get("command.state", gm.getState()));
        boxKv(sender, Messages.get("command.rounds-to-win-cmd", (int) gm.getRounds()));
        boxKv(sender, Messages.get("command.current-round", (int) gm.getCurrentRound()));
        boxSection(sender, Messages.get("debug.section-teams"));
        for (GameTeam team : GameTeam.values()) {
            String teamName = Messages.get("team." + team.name().toLowerCase());
            boxLine(sender, team.getColor(), Messages.get("command.team-info", teamName, (int) tm.getWins(team), tm.getPlayerCount(team)));
        }
        boxFooter(sender);
    }

    private void handleStop(CommandSender sender) {
        GameManager gm = plugin.getGameManager();
        gm.stopGame();
        plugin.getTeamManager().clearAll();
        plugin.getPlayerDataManager().clearActivePlayers();
        gm.getStateManager().clear();
        org.bukkit.Location lobbyLoc = plugin.getBlockListener().getBlockStorage().getLobbyBlock();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.getInventory().clear();
            var attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) attr.setBaseValue(20);
            p.setHealth(20);
            p.setFoodLevel(20);
            p.setGameMode(GameMode.ADVENTURE);
            gm.clearCardEffects(p);
            if (lobbyLoc != null) {
                p.teleport(lobbyLoc.clone().add(0, 1, 0));
            }
        }
        sender.sendMessage(ChatColor.YELLOW + Messages.get("command.game-stopped"));
    }

    private void handleSetLobby(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player"));
            return;
        }
        org.bukkit.Location loc = player.getLocation().getBlock().getLocation();
        plugin.getBlockListener().getBlockStorage().setLobbyBlock(loc);
        sender.sendMessage(ChatColor.GREEN + Messages.get("block.lobby-placed",
            String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())));
    }

    private void handleJumpPos(CommandSender sender, int posNum) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player"));
            return;
        }
        org.bukkit.Location pos = player.getLocation().getBlock().getLocation().subtract(0, 1, 0);
        if (posNum == 1) {
            plugin.getBlockListener().setJumpPos1(pos);
        } else {
            plugin.getBlockListener().setJumpPos2(pos);
        }
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.pos-set", "Jump", posNum,
            String.format("(%d, %d, %d)", pos.getBlockX(), pos.getBlockY(), pos.getBlockZ())));
    }

    private void handleJumpSet(CommandSender sender) {
        com.rounds.blocks.BlockListener bl = plugin.getBlockListener();
        if (bl.getJumpPos1() == null || bl.getJumpPos2() == null) {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.pos-need-both", "jump"));
            return;
        }
        Set<org.bukkit.Location> filled = bl.fillJumpBlocks();
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.pos-filled", filled.size(), "jump"));
    }

    private void handleUpPos(CommandSender sender, int posNum) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player"));
            return;
        }
        org.bukkit.Location pos = player.getLocation().getBlock().getLocation().subtract(0, 1, 0);
        if (posNum == 1) {
            plugin.getBlockListener().setUpPos1(pos);
        } else {
            plugin.getBlockListener().setUpPos2(pos);
        }
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.pos-set", "Up", posNum,
            String.format("(%d, %d, %d)", pos.getBlockX(), pos.getBlockY(), pos.getBlockZ())));
    }

    private void handleUpSet(CommandSender sender) {
        com.rounds.blocks.BlockListener bl = plugin.getBlockListener();
        if (bl.getUpPos1() == null || bl.getUpPos2() == null) {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.pos-need-both", "up"));
            return;
        }
        Set<org.bukkit.Location> filled = bl.fillUpBlocks();
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.pos-filled", filled.size(), "up"));
    }

    private void handleRounds(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ChatColor.RED + Messages.get("command.usage-rounds")); return; }
        try {
            double amount = Double.parseDouble(args[1]);
            if (amount < 1) { sender.sendMessage(ChatColor.RED + Messages.get("command.amount-between", Integer.MAX_VALUE)); return; }
            plugin.getGameManager().setRounds((int) amount);
            Bukkit.broadcastMessage(ChatColor.GOLD + Messages.get("command.rounds-set", String.format("%.0f", amount)));
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.invalid-number", args[1]));
        }
    }

    private void handleAutoDeath(CommandSender sender, String[] args) {
        GameManager gm = plugin.getGameManager();
        if (args.length < 2) {
            String status = gm.isAutoDeathEnabled()
                    ? ChatColor.GREEN + Messages.get("command.state-on")
                    : ChatColor.RED + Messages.get("command.state-off");
            sender.sendMessage(ChatColor.YELLOW + Messages.get("debug.autodeath-status", status));
            sender.sendMessage(ChatColor.GRAY + "/rdebug autodeath <on|off>");
            return;
        }
        String arg = args[1].toLowerCase();
        if (arg.equals("on") || arg.equals("вкл")) {
            gm.setAutoDeathEnabled(true);
            sender.sendMessage(ChatColor.GREEN + Messages.get("debug.autodeath-on"));
        } else if (arg.equals("off") || arg.equals("выкл")) {
            gm.setAutoDeathEnabled(false);
            sender.sendMessage(ChatColor.GREEN + Messages.get("debug.autodeath-off"));
        } else {
            sender.sendMessage(ChatColor.RED + "/rdebug autodeath <on|off>");
        }
    }

    private void handleInfo(CommandSender sender) {
        GameManager gm = plugin.getGameManager();
        TeamManager tm = plugin.getTeamManager();
        boxHeader(sender, Messages.get("command.info-title"));
        boxKv(sender, Messages.get("command.version", plugin.getDescription().getVersion()));
        boxKv(sender, Messages.get("command.state", gm.getState()));
        boxKv(sender, Messages.get("command.rounds-to-win-cmd", (int) gm.getRounds()));
        boxKv(sender, Messages.get("command.players-online", Bukkit.getOnlinePlayers().size()));
        String onOff = plugin.getRoundsConfig().isFreeplay()
                ? Messages.get("command.state-on") : Messages.get("command.state-off");
        boxKv(sender, Messages.get("command.freeplay-status", onOff));
        boxKv(sender, Messages.get("command.lang-current", Messages.getLanguage()));
        boxSection(sender, Messages.get("debug.section-teams"));
        for (GameTeam team : GameTeam.values()) {
            String teamName = Messages.get("team." + team.name().toLowerCase());
            boxLine(sender, team.getColor(), Messages.get("command.team-info", teamName, (int) tm.getWins(team), tm.getPlayerCount(team)));
        }
        boxSection(sender, Messages.get("debug.section-cards"));
        boxKv(sender, Messages.get("command.cards-loaded",
                plugin.getCardManager().getRegistry().getAllCards().size(),
                plugin.getCardManager().getRegistry().getLoadedFileCount()));
        boxFooter(sender);
    }

    // === GUN ===

    private void handleGiveGun(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("@a")) {
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.getInventory().addItem(GunItem.createGunItem());
                count++;
            }
            sender.sendMessage(ChatColor.GREEN + Messages.get("debug.gun-given") + " -> @a (" + count + ")");
            return;
        }
        Player target = null;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) { sender.sendMessage(ChatColor.RED + Messages.get("debug.player-not-found", args[1])); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player"));
            return;
        }
        ItemStack gun = GunItem.createGunItem();
        target.getInventory().addItem(gun);
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.gun-given") + (args.length >= 2 ? " -> " + target.getName() : ""));
    }

    // === CARDS ===

    private void handleCards(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rounds.admin")) {
            if (!(sender instanceof Player viewer)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
            plugin.getCardGUI().openShow(viewer, viewer);
            return;
        }
        if (args.length < 2) {
            if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
            plugin.getCardManager().openCardSelection(player, GameTeam.BLUE);
            sender.sendMessage(ChatColor.GREEN + Messages.get("debug.card-gui-opened"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "reload" -> handleCardsReload(sender);
            case "show" -> handleCardsShow(sender, args);
            case "add" -> handleCardsAdd(sender, args);
            default -> sender.sendMessage(ChatColor.RED + Messages.get("debug.unknown-command", args[0] + " " + args[1]));
        }
    }

    private void handleCardsReload(CommandSender sender) {
        plugin.getCardManager().reload();
        sender.sendMessage(ChatColor.GREEN + Messages.get("command.cards-reloaded",
                plugin.getCardManager().getRegistry().getAllCards().size(),
                plugin.getCardManager().getRegistry().getLoadedFileCount()));
    }

    private void handleCardsShow(CommandSender sender, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player"));
            return;
        }
        Player target = viewer;
        int page = 1;

        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException notANumber) {
                target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + Messages.get("debug.player-not-found", args[2]));
                    return;
                }
                if (args.length >= 4) {
                    try {
                        page = Integer.parseInt(args[3]);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        plugin.getCardGUI().openShow(viewer, target, page);
    }

    private void handleCardsAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
        if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Usage: /rdebug cards add <name>"); return; }

        String nameInput = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        var card = plugin.getCardManager().getRegistry().findCardByName(nameInput);
        if (card == null) { sender.sendMessage(ChatColor.RED + Messages.get("command.card-not-found", nameInput)); return; }

        PlayerData data = plugin.getPlayerDataManager().getData(player);
        card.apply(player, data);
        data.setCard(card.getId(), true);
        sender.sendMessage(ChatColor.GREEN + Messages.get("command.applied-card", card.getColoredName(Messages.getLanguageCode())));
    }

    // === STATS ===

    private void handleStats(CommandSender sender, String[] args) {
        boolean canModify = sender.hasPermission("rounds.admin");
        if (!canModify && args.length >= 2) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.no-permission"));
            return;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) { sender.sendMessage(ChatColor.RED + Messages.get("debug.player-not-found", args[1])); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.specify-player")); return;
        }

        if (args.length >= 4) {
            String stat = args[2].toLowerCase();
            double value;
            try {
                value = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + Messages.get("command.invalid-number", args[3])); return;
            }
            PlayerData setData = plugin.getPlayerDataManager().getData(target);
            if (!applyStat(setData, target, stat, value)) {
                sender.sendMessage(ChatColor.RED + Messages.get("debug.unknown-stat", stat));
                return;
            }
            sender.sendMessage(ChatColor.GREEN + Messages.get("debug.setstat-done", stat, PlayerData.round2(value), target.getName()));
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getData(target);
        boxHeader(sender, Messages.get("debug.stats-title", target.getName()), Messages.get("debug.team-label", getTeamDisplay(target)) + " | " + Messages.get("debug.cards-label", countCards(data)));

        boxSection(sender, Messages.get("debug.combat-section"));
        boxStat(sender, "stat-dmg", data.dmg);
        boxStat(sender, "stat-atks", data.hp);
        boxStat(sender, "stat-atk-speed", data.atkSpeed);
        boxStat(sender, "stat-atkr", data.atkr);
        boxStat(sender, "stat-ammo", data.ammo);
        boxStat(sender, "stat-bullets", data.bullets);
        boxStat(sender, "stat-bullet-speed", data.bulletSpeed);
        double bigPenalty = data.bigBullet > 0 ? 1.0 + 0.3 * data.bigBullet : 1.0;
        boxStat(sender, "stat-reload-time", Math.max(
                100 * (1.0 - Math.min(data.reloadSpeed, 0.95)) * (1.0 + data.atksReload * 0.1) * bigPenalty, 4) / 20.0);
        boxStat(sender, "stat-bounce", data.bouncePl);
        boxStat(sender, "stat-homing", data.homing);
        boxStat(sender, "stat-big-bullet", data.bigBullet);

        boxSection(sender, Messages.get("debug.effects-section"));
        boxStat(sender, "stat-cold", data.cold);
        boxStat(sender, "stat-cold-lvl", data.coldLvl);
        boxStat(sender, "stat-poison", data.poison);
        boxStat(sender, "stat-poison-lvl", data.poisonLvl);
        boxStat(sender, "stat-parazit", data.parazit);
        boxStat(sender, "stat-parazit-lvl", data.parazitLvl);
        boxStat(sender, "stat-leech", data.leech);
        boxStat(sender, "stat-truster", data.trusterLvl);
        boxStat(sender, "stat-jump-height", data.jumpHeight);

        boxSection(sender, Messages.get("debug.special-section"));
        boxStat(sender, "stat-grow", data.grow);
        boxStat(sender, "stat-empower", data.empower);
        boxStat(sender, "stat-empower-charge", data.empowerCharge);
        boxStat(sender, "stat-dark-strength", data.darkStrength);
        boxStat(sender, "stat-dark", data.dark);
        boxStat(sender, "stat-bomb-bullet", data.bombBullet);
        boxStat(sender, "stat-bomb-on-block", data.bombOnBlock);

        boxSection(sender, Messages.get("debug.shield-section"));
        boxKv(sender, Messages.get("debug.stat-shield-active"), data.shieldActive ? "ON" : "OFF");
        boxKv(sender, Messages.get("debug.stat-shield-cd"),
                String.format("%.1fs", data.shieldCooldown / 20.0));

        boxFooter(sender);
    }

    private void handleSetStat(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(ChatColor.RED + Messages.get("debug.usage-setstat")); return; }

        String stat = args[1].toLowerCase();
        double value;
        try {
            value = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.invalid-number", args[2])); return;
        }

        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayer(args[3]);
            if (target == null) { sender.sendMessage(ChatColor.RED + Messages.get("debug.player-not-found", args[3])); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.specify-player")); return;
        }

        PlayerData data = plugin.getPlayerDataManager().getData(target);
        if (!applyStat(data, target, stat, value)) {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.unknown-stat", stat));
            return;
        }
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.setstat-done", stat, PlayerData.round2(value), target.getName()));
    }

    private boolean applyStat(PlayerData data, Player target, String stat, double value) {
        switch (stat) {
            case "dmg" -> data.dmg = value;
            case "atks" -> data.atks = value;
            case "atk_speed" -> data.atkSpeed = value;
            case "atkr" -> data.atkr = value;
            case "bounce" -> data.bouncePl = value;
            case "ammo" -> data.ammo = value;
            case "bullets" -> data.bullets = value;
            case "cold" -> data.cold = value;
            case "poison" -> data.poison = value;
            case "toxic_cloud" -> data.toxicCloud = value;
            case "highlight" -> data.highlight = value;
            case "leech" -> data.leech = value;
            case "homing" -> data.homing = value;
            case "poison_lvl" -> data.poisonLvl = value;
            case "cold_lvl" -> data.coldLvl = value;
            case "parazit" -> data.parazit = value;
            case "parazit_lvl" -> data.parazitLvl = value;
            case "hp" -> {
                double clamped = PlayerData.clampMaxHealth(value);
                data.hp = clamped;
                var attr = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (attr != null) attr.setBaseValue(clamped);
                target.setHealth(Math.min(target.getHealth(), clamped));
            }
            case "bomb_bullet" -> data.bombBullet = value;
            case "bomb_on_block" -> data.bombOnBlock = value;
            case "bullet_speed" -> data.bulletSpeed = value;
            case "empower" -> data.empower = value;
            case "empower_charge" -> data.empowerCharge = value;
            case "dark_strength" -> data.darkStrength = value;
            case "big_bullet" -> data.bigBullet = value;
            case "grow" -> data.grow = value;
            case "truster_lvl" -> data.trusterLvl = value;
            case "jump_height" -> data.jumpHeight = value;
            case "dark" -> data.dark = value;
            case "atks_reload" -> data.atksReload = value;
            default -> { return false; }
        }
        return true;
    }

    private void handleSetTeam(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ChatColor.RED + Messages.get("debug.usage-setteam")); return; }

        String colorName = args[1].toUpperCase();
        GameTeam team;
        try {
            team = GameTeam.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.unknown-color", colorName));
            return;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) { sender.sendMessage(ChatColor.RED + Messages.get("debug.player-not-found", args[2])); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.specify-player")); return;
        }

        plugin.getTeamManager().joinTeam(target.getUniqueId(), team);
        plugin.getGameManager().applyTeamColor(target);
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.team-set", target.getName(), team.getColor() + team.getName()));
    }

    private void handleSetLanguage(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ChatColor.RED + Messages.get("debug.usage-setlanguage")); return; }

        String lang = Messages.resolveLanguage(args[1]);
        if (lang == null) {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.language-invalid", args[1],
                    String.join(", ", Messages.getAvailableLanguages())));
            return;
        }

        plugin.getConfig().set("language", lang);
        plugin.saveConfig();
        Messages.reload(lang);
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.language-set", lang));
    }

    private void handleEffect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
        if (args.length < 4) { sender.sendMessage(ChatColor.RED + "Usage: /rdebug effect <type> <amp> <duration>"); return; }

        PotionEffectType type = PotionEffectType.getByName(args[1].toUpperCase());
        if (type == null) { sender.sendMessage(ChatColor.RED + Messages.get("debug.unknown-effect", args[1])); return; }

        int amp, dur;
        try {
            amp = Integer.parseInt(args[2]);
            dur = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.invalid-number")); return;
        }

        player.addPotionEffect(new org.bukkit.potion.PotionEffect(type, dur, amp));
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.effect-applied", type.getName(), amp, dur));
    }

    private void handleHeal(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
        var attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHP = attr != null ? attr.getValue() : 20;
        player.setHealth(maxHP);
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.healed", maxHP));
    }

    // === MISC ===

    private void handleFreeplay(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ChatColor.RED + Messages.get("debug.usage-freeplay")); return; }
        switch (args[1].toLowerCase()) {
            case "on" -> {
                plugin.getRoundsConfig().setFreeplay(true);
                sender.sendMessage(ChatColor.GREEN + Messages.get("debug.freeplay-on"));
            }
            case "off" -> {
                plugin.getRoundsConfig().setFreeplay(false);
                sender.sendMessage(ChatColor.GREEN + Messages.get("debug.freeplay-off"));
            }
            default -> sender.sendMessage(ChatColor.RED + Messages.get("debug.usage-freeplay"));
        }
    }

    private void handleResetStats(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
        PlayerData data = plugin.getPlayerDataManager().getData(player);
        data.resetStats();
        data.resetAllCards();
        var attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) attr.setBaseValue(20);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.getInventory().clear();
        plugin.getGameManager().clearCardEffects(player);
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.stats-reset"));
    }

    private void handleReload(CommandSender sender) {
        plugin.getRoundsConfig().reload();
        Messages.reload();
        plugin.getCardManager().reload();
        sender.sendMessage(ChatColor.GREEN + Messages.get("command.cards-reloaded", plugin.getCardManager().getRegistry().getAllCards().size()));
    }

    private void handleBlocks(CommandSender sender, String[] args) {
        Player target = null;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) { sender.sendMessage(ChatColor.RED + Messages.get("debug.player-not-found", args[1])); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player"));
            return;
        }
        target.setGameMode(GameMode.CREATIVE);
        com.rounds.blocks.BlockListener.giveAllBlocks(target);
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.blocks-given") + " -> " + target.getName());
    }

    private String getTeamDisplay(Player player) {
        GameTeam team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) return Messages.get("debug.none");
        return team.getColor() + Messages.get("team." + team.name().toLowerCase());
    }

    private int countCards(PlayerData data) {
        int count = 0;
        for (com.rounds.cards.Card card : plugin.getCardManager().getRegistry().getAllCards()) {
            if (data.getCard(card.getId())) count++;
        }
        return count;
    }

    private String formatLoc(Location loc) {
        return String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ());
    }

    private void handleWheel(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.wheel-usage"));
            return;
        }
        GameManager gm = plugin.getGameManager();
        switch (args[1].toLowerCase()) {
            case "on" -> {
                gm.setWheelEnabled(true);
                sender.sendMessage(ChatColor.GREEN + Messages.get("debug.wheel-enabled"));
            }
            case "off" -> {
                gm.setWheelEnabled(false);
                sender.sendMessage(ChatColor.RED + Messages.get("debug.wheel-disabled"));
            }
            default -> sender.sendMessage(ChatColor.RED + Messages.get("debug.wheel-usage"));
        }
    }

    private void handleTab(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.tab-usage"));
            return;
        }
        RoundsConfig config = plugin.getRoundsConfig();
        switch (args[1].toLowerCase()) {
            case "on" -> {
                config.setBuiltinScoreboard(true);
                plugin.getGameManager().updateScoreboard();
                sender.sendMessage(ChatColor.GREEN + Messages.get("debug.tab-enabled"));
            }
            case "off" -> {
                config.setBuiltinScoreboard(false);
                plugin.getGameManager().removeScoreboard();
                sender.sendMessage(ChatColor.RED + Messages.get("debug.tab-disabled"));
            }
            case "name" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + Messages.get("debug.tab-name-usage"));
                    return;
                }
                String title = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                config.setBuiltinScoreboardTitle(title);
                plugin.getGameManager().updateScoreboard();
                sender.sendMessage(ChatColor.GREEN + Messages.get("debug.tab-name-set", title));
            }
            default -> sender.sendMessage(ChatColor.RED + Messages.get("debug.tab-usage"));
        }
    }

    private static final List<String> SUBCOMMANDS = Arrays.asList(
        "help", "start", "stop", "status", "rounds", "info",
        "givegun", "cards", "card", "giveblocks", "join", "freeplay", "autodeath",
        "stats", "setstat", "setteam", "setlanguage", "effect", "heal",
        "resetstats", "reload", "setlobby",
        "jumppos1", "jumppos2", "jumpset", "uppos1", "uppos2", "upset",
        "wheel", "tab"
    );
    private static final List<String> FREEPLAY_COMMANDS = Arrays.asList(
        "start", "stop", "rounds", "givegun", "wheel"
    );
    private static final List<String> PUBLIC_COMMANDS = Arrays.asList(
        "info", "stats", "cards", "card"
    );
    private static final List<String> TEAM_COLORS = Arrays.asList("BLUE", "RED", "YELLOW", "GREEN");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission("rounds.admin");
        boolean freeplay = plugin.getRoundsConfig().isFreeplay();
        if (!admin && !freeplay) return Collections.emptyList();

        String input = args[args.length - 1].toLowerCase();

        if (!admin) {
            List<String> allowed = new ArrayList<>(PUBLIC_COMMANDS);
            if (freeplay) allowed.addAll(FREEPLAY_COMMANDS);
            if (args.length == 1) return filterStartsWith(allowed, input);
            String root = args[0].toLowerCase();
            boolean rootAllowed = PUBLIC_COMMANDS.contains(root)
                    || (freeplay && FREEPLAY_COMMANDS.contains(root));
            if (!rootAllowed) return Collections.emptyList();
            if (root.equals("stats")) return Collections.emptyList();
        } else {
            if (args.length == 1) return filterStartsWith(SUBCOMMANDS, input);
        }

        return switch (args[0].toLowerCase()) {
            case "givegun", "giveblocks" -> {
                List<String> list = new ArrayList<>();
                list.add("@a");
                Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
                yield filterStartsWith(list, input);
            }
            case "stats" -> {
                if (args.length == 2 || args.length == 4) {
                    List<String> list = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
                    yield filterStartsWith(list, input);
                }
                if (args.length == 3) yield filterStartsWith(STATS, input);
                yield Collections.emptyList();
            }
            case "setstat" -> {
                if (args.length == 2) yield filterStartsWith(STATS, input);
                if (args.length == 4) {
                    List<String> list = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
                    yield filterStartsWith(list, input);
                }
                yield Collections.emptyList();
            }
            case "setteam" -> {
                if (args.length == 2) yield filterStartsWith(TEAM_COLORS, input);
                if (args.length == 3) {
                    List<String> list = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
                    yield filterStartsWith(list, input);
                }
                yield Collections.emptyList();
            }
            case "setlanguage" -> {
                if (args.length == 2) yield filterStartsWith(new ArrayList<>(Messages.getAvailableLanguages()), input);
                yield Collections.emptyList();
            }
            case "effect" -> {
                if (args.length == 2) yield filterStartsWith(EFFECTS, input);
                if (args.length == 3) yield filterStartsWith(Arrays.asList("0", "1", "2", "3", "4"), input);
                if (args.length == 4) yield filterStartsWith(Arrays.asList("10", "20", "40", "60", "100", "200"), input);
                yield Collections.emptyList();
            }
            case "cards", "card" -> {
                if (!admin) {
                    yield args.length == 2
                            ? filterStartsWith(Collections.singletonList("show"), input)
                            : Collections.emptyList();
                }
                if (args.length == 2) yield filterStartsWith(Arrays.asList("reload", "show", "add"), input);
                if (args.length == 3 && args[1].equalsIgnoreCase("show")) {
                    List<String> list = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
                    yield filterStartsWith(list, input);
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("add")) {
                    yield filterStartsWith(plugin.getCardManager().getRegistry().getCardNameSuggestions(), input);
                }
                yield Collections.emptyList();
            }
            case "rounds" -> filterStartsWith(Arrays.asList("1", "5", "10", "15", "20", "50", "100", "500"), input);
            case "autodeath" -> filterStartsWith(Arrays.asList("on", "off"), input);
            case "freeplay" -> filterStartsWith(Arrays.asList("on", "off"), input);
            case "wheel" -> filterStartsWith(Arrays.asList("on", "off"), input);
            case "tab" -> {
                if (args.length == 2) yield filterStartsWith(Arrays.asList("on", "off", "name"), input);
                if (args.length == 3 && args[1].equalsIgnoreCase("name")) yield Collections.emptyList();
                yield Collections.emptyList();
            }
            default -> Collections.emptyList();
        };
    }

    private static List<String> filterStartsWith(List<String> options, String prefix) {
        if (prefix.isEmpty()) return options;
        List<String> result = new ArrayList<>();
        for (String s : options) {
            if (s.toLowerCase().startsWith(prefix)) {
                result.add(s);
            }
        }
        return result;
    }
}

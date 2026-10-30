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

import com.rounds.cards.Card;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

public class DebugCommands implements CommandExecutor, TabCompleter {

    private final RoundsPlugin plugin;
    private static final List<String> STATS = Arrays.asList(
        "dmg", "atks", "atk_speed", "atkr", "bounce", "ammo", "bullets", "cold", "poison",
        "toxic_cloud", "leech", "homing", "poison_lvl", "cold_lvl", "parazit", "hp",
        "bomb_bullet", "bomb_on_block", "bullet_speed", "empower",
        "empower_charge", "dark_strength", "big_bullet", "grow",
        "truster_lvl", "jump_height", "dark", "reload_time", "block_cd", "shield_duration", "shield_charges", "decay", "echo"
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

    private void boxAttachedHeader(CommandSender sender, String title) {
        sender.sendMessage(B + "\u2551 " + BOLD + RESET + B + "\u2502 " + BOLD + title);
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

    private void boxStatBool(CommandSender sender, String key, double value) {
        String val = (value > 0.5) ? Messages.get("debug.val-yes") : Messages.get("debug.val-no");
        boxKv(sender, Messages.get("debug." + key), val, (value > 0.5) ? ChatColor.GREEN : ChatColor.RED);
    }


    private String formatSeconds(double seconds) {
        String unit = Messages.get("debug.unit-sec");
        if (unit == null || unit.equals("debug.unit-sec")) {
            unit = "ru".equalsIgnoreCase(Messages.getLanguageCode()) ? "сек" : "sec";
        }
        if (Math.abs(seconds - Math.round(seconds)) < 0.001) {
            return String.format(Locale.US, "%d %s", Math.round(seconds), unit);
        }
        return String.format(Locale.US, "%.1f %s", seconds, unit);
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
            handleJoin(sender, args);
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
            case "debug" -> {
                handleDebugSub(sender, args);
                return true;
            }
            case "start" -> {
                if (plugin.getGameManager().isGameStarted()) {
                    sender.sendMessage(ChatColor.RED + Messages.get("debug.game-already-started"));
                } else {
                    plugin.getGameManager().startGame();
                }
            }
            case "status" -> handleStatus(sender);
            case "stop" -> handleStop(sender);
            case "rounds" -> handleRounds(sender, args);
            case "autodeath" -> handleAutoDeath(sender, args);
            case "info" -> handleInfo(sender);
            case "stats" -> handleStats(sender, args);
            case "statsend" -> handleStatsEnd(sender, args);
            case "givegun" -> handleGiveGun(sender, args);
            case "setteam" -> handleSetTeam(sender, args);
            case "setlanguage" -> handleSetLanguage(sender, args);
            case "effect" -> handleEffect(sender, args);
            case "heal" -> handleHeal(sender, args);
            case "cards" -> handleCards(sender, args);
            case "resetstats" -> handleResetStats(sender, args);
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
            case "bot" -> handleBot(sender, args);
            case "draft" -> handleDraft(sender, args);
            case "cardtest" -> handleCardTest(sender, args);
            case "cardchoicetime" -> handleCardChoiceTime(sender, args);
            case "bossbar" -> handleBossBar(sender, args);
            case "music" -> handleMusic(sender, args);
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
        boxKv(sender, "/rdebug cardchoicetime <sec>", Messages.get("debug.help-cardchoicetime"));
        boxKv(sender, "/rdebug cards show [player]", Messages.get("debug.help-cards-show"));
        boxKv(sender, "/rdebug cards add <player> <card> [rarity]", Messages.get("debug.help-cards-add"));
        boxKv(sender, "/rdebug wheel on|off", Messages.get("debug.help-wheel"));

        boxSection(sender, R, Messages.get("debug.help-section-scoreboard"));
        boxKv(sender, "/rdebug tab on|off", Messages.get("debug.help-tab"));
        boxKv(sender, "/rdebug tab name <title>", Messages.get("debug.help-tab-name"));
        boxKv(sender, "/rdebug bossbar on|off", Messages.get("debug.help-bossbar"));

        boxSection(sender, R, Messages.get("debug.help-section-items"));
        boxKv(sender, "/rdebug givegun [player|@a]", Messages.get("debug.help-givegun"));

        boxSection(sender, R, Messages.get("debug.help-section-debug"));
        boxKv(sender, "/rdebug bot <name> [team]", "Заспавнить бота в игру");
        boxKv(sender, "/rdebug draft [player]", "Открыть драфт карт игроку");
        boxKv(sender, "/rdebug cardtest [id|clear]", "Тест 3D-отображения карт (глифы/шрифты)");
        boxKv(sender, "/rdebug stats show <player>", Messages.get("debug.help-stats-show"));
        if (sender.hasPermission("rounds.admin")) {
            boxKv(sender, "/rdebug stats add <player> <stat> <value>", Messages.get("debug.help-stats-add"));
        }
        boxKv(sender, "/rdebug statsend [player]", Messages.get("debug.help-statsend"));
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

    private void handleJoin(CommandSender sender, String[] args) {
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
        GameTeam team = null;
        if (args != null && args.length >= 2 && !args[1].isBlank() && !args[1].equalsIgnoreCase("auto")) {
            try {
                team = GameTeam.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        if (team == null) {
            team = gm.findSmallestTeam();
        }

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
        gm.ensureTeamSpawn(finalTeam);
        gm.markPendingCardJoiner(uuid, returning);
        gm.applyTeamColor(player);
        gm.buildScoreboard(player);
        gm.updateScoreboard();

        if (gm.getState() == GameManager.GameState.CARDS) {
            gm.removeDeadPlayer(uuid);
            plugin.getCardManager().openCardSelection(player, finalTeam);
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setInvulnerable(true);
        } else if (gm.getState() == GameManager.GameState.PLAYING) {
            player.setGameMode(GameMode.SPECTATOR);
            player.setInvulnerable(true);
            Location spawn = gm.getTeamSpawns().get(finalTeam);
            if (spawn == null) spawn = gm.getCurrentZoneCenter();
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

    private void handleStatus(CommandSender sender) {
        GameManager gm = plugin.getGameManager();
        TeamManager tm = plugin.getTeamManager();
        String state = Messages.get("command.game-state-" + gm.getState().name().toLowerCase());
        
        sender.sendMessage(B + "\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
        sender.sendMessage(B + "\u2551 " + W + "\u2502 " + B + Messages.get("debug.state-title"));
        sender.sendMessage(B + "\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
        sender.sendMessage(B + "\u2551 " + W + Messages.get("command.state", state));
        sender.sendMessage(B + "\u2551 " + W + Messages.get("command.rounds-to-win-cmd", (int) gm.getRounds()));
        sender.sendMessage(B + "\u2551 " + W + Messages.get("command.current-round", (int) gm.getCurrentRound()));
        sender.sendMessage(B + "\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
        sender.sendMessage(B + "\u2551 " + W + "\u2502 " + B + Messages.get("debug.section-teams"));
        sender.sendMessage(B + "\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
        for (GameTeam team : GameTeam.values()) {
            String teamName = Messages.get("team." + team.name().toLowerCase());
            sender.sendMessage(B + "\u2551 " + Messages.get("command.team-info", team.getColor() + teamName, (int) tm.getWins(team), tm.getPlayerCount(team)));
        }
        sender.sendMessage(B + "\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
    }

    private void handleDebugSub(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /rdebug debug start | /rdebug debug start <from> | /rdebug debug start <from> stop <to> | /rdebug debug stop | /rdebug debug stop <to> | /rdebug debug status");
            return;
        }
        String sub = args[1].toLowerCase();
        switch (sub) {
            case "start" -> {
                String[] subArgs = new String[args.length - 1];
                System.arraycopy(args, 1, subArgs, 0, args.length - 1);
                handleDebugStart(sender, subArgs);
            }
            case "stop" -> {
                String[] subArgs = new String[args.length - 1];
                System.arraycopy(args, 1, subArgs, 0, args.length - 1);
                handleDebugStop(sender, subArgs);
            }
            case "status" -> handleDebugStatus(sender);
            default -> sender.sendMessage(ChatColor.RED + "Использование: /rdebug debug start | /rdebug debug start <from> | /rdebug debug start <from> stop <to> | /rdebug debug stop | /rdebug debug stop <to> | /rdebug debug status");
        }
    }

    private void handleDebugStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rounds.admin")) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.no-permission"));
            return;
        }
        if (com.rounds.debug.DebugSession.active) {
            sender.sendMessage(ChatColor.RED + "Дебаг уже запущен. Используй /rdebug debug stop для остановки.");
            return;
        }
        if (!com.rounds.debug.DebugSession.triggered && com.rounds.debug.DebugSession.startAt != -1) {
            String planned = "Дебаг уже запланирован: старт в R#" + com.rounds.debug.DebugSession.startAt;
            if (com.rounds.debug.DebugSession.stopAt != -1) {
                planned += ", автостоп в конце R#" + com.rounds.debug.DebugSession.stopAt;
            }
            sender.sendMessage(ChatColor.RED + planned + ". Сначала /rdebug debug stop для отмены плана.");
            return;
        }
        if (args.length == 1) {
            if (com.rounds.debug.DebugSession.active) {
                sender.sendMessage(ChatColor.RED + "Дебаг уже запущен.");
                return;
            }
            com.rounds.debug.DebugSession.active = true;
            com.rounds.debug.DebugSession.startAt = -1;
            com.rounds.debug.DebugSession.stopAt = -1;
            com.rounds.debug.DebugSession.triggered = true;
            com.rounds.debug.DebugLog.initSession();
            com.rounds.debug.DebugLog.log("DEBUG", "DEBUG SESSION STARTED (IMMEDIATE)");
            sender.sendMessage(ChatColor.GREEN + "RoundsDebug: дебаг включён. Файл: " + (com.rounds.debug.DebugSession.logFile == null ? "?" : com.rounds.debug.DebugSession.logFile.getAbsolutePath()));
            sender.sendMessage(ChatColor.GRAY + "Автостоп не запланирован. По окончанию каждого раунда будут дописываться CARDS/STATS/SNAP.");
            return;
        }
        if (args.length == 2) {
            int from;
            try {
                from = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Неверный номер раунда");
                return;
            }
            if (from < 1) {
                sender.sendMessage(ChatColor.RED + "Номер раунда должен быть >= 1");
                return;
            }
            int cur = (int) plugin.getGameManager().getCurrentRound();
            if (from < cur) {
                sender.sendMessage(ChatColor.RED + "Нельзя включить дебаг задним числом. Текущий раунд: " + cur);
                return;
            }
            boolean immediate = (from == cur)
                    && plugin.getGameManager().getState() == GameManager.GameState.PLAYING;
            com.rounds.debug.DebugSession.active = false;
            com.rounds.debug.DebugSession.startAt = from;
            com.rounds.debug.DebugSession.stopAt = -1;
            com.rounds.debug.DebugSession.triggered = false;
            if (immediate) {
                com.rounds.debug.DebugSession.triggered = true;
                com.rounds.debug.DebugSession.active = true;
                com.rounds.debug.DebugLog.initSession();
                com.rounds.debug.DebugLog.log("DEBUG", "DEBUG SESSION STARTED (IMMEDIATE in round R#" + cur + ")");
                sender.sendMessage(ChatColor.GREEN + "RoundsDebug: дебаг включён немедленно в текущем раунде " + cur);
                sender.sendMessage(ChatColor.GRAY + "Автостоп не запланирован.");
                return;
            }
            sender.sendMessage(ChatColor.GREEN + "RoundsDebug: дебаг будет включён автоматически при старте раунда " + from);
            sender.sendMessage(ChatColor.GRAY + "Автостоп не запланирован.");
            return;
        }
        if (args.length == 4 && args[2].equalsIgnoreCase("stop")) {
            int from;
            int to;
            try {
                from = Integer.parseInt(args[1]);
                to = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Неверный номер раунда");
                return;
            }
            if (from < 1 || to < 1) {
                sender.sendMessage(ChatColor.RED + "Номер раунда должен быть >= 1");
                return;
            }
            if (to < from) {
                sender.sendMessage(ChatColor.RED + "Значение stop не может быть меньше start");
                return;
            }
            int cur = (int) plugin.getGameManager().getCurrentRound();
            if (from < cur) {
                sender.sendMessage(ChatColor.RED + "Нельзя включить дебаг задним числом. Текущий раунд: " + cur);
                return;
            }
            boolean immediate = (from == cur)
                    && plugin.getGameManager().getState() == GameManager.GameState.PLAYING;
            com.rounds.debug.DebugSession.active = false;
            com.rounds.debug.DebugSession.startAt = from;
            com.rounds.debug.DebugSession.stopAt = to;
            com.rounds.debug.DebugSession.triggered = false;
            if (immediate) {
                com.rounds.debug.DebugSession.triggered = true;
                com.rounds.debug.DebugSession.active = true;
                com.rounds.debug.DebugLog.initSession();
                com.rounds.debug.DebugLog.log("DEBUG", "DEBUG SESSION STARTED (IMMEDIATE in round R#" + cur + ")");
                com.rounds.debug.DebugLog.log("DEBUG", "AUTO-STOP planned at end of R#" + to);
                sender.sendMessage(ChatColor.GREEN + "RoundsDebug: дебаг включён немедленно в текущем раунде " + cur
                        + ". Автостоп в конце раунда " + to + " с дампом.");
                return;
            }
            sender.sendMessage(ChatColor.GREEN + "RoundsDebug: дебаг будет включён автоматически при старте раунда " + from + ". Автостоп в конце раунда " + to + " с дампом.");
            return;
        }
        sender.sendMessage(ChatColor.RED + "Использование: /rdebug debug start | /rdebug debug start <from> | /rdebug debug start <from> stop <to>");
    }

    private void handleDebugStop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rounds.admin")) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.no-permission"));
            return;
        }
        if (args.length == 2) {
            if (!com.rounds.debug.DebugSession.active) {
                sender.sendMessage(ChatColor.RED + "Дебаг не запущен. Сначала используй /rdebug debug start");
                return;
            }
            int cur = (int) plugin.getGameManager().getCurrentRound();
            com.rounds.debug.DebugLog.flushFinal("MANUAL-STOP by command");
            com.rounds.debug.DebugSession.clear();
            sender.sendMessage(ChatColor.GREEN + "RoundsDebug: остановка дебага...");
            sender.sendMessage(ChatColor.GRAY + "Финальный блок дописан. Файл закрыт.");
            if (com.rounds.debug.DebugSession.logFile != null) {
                // logFile nulled after clear but we can’t show; message generic
            }
            sender.sendMessage(ChatColor.GRAY + "Дамп сохранён.");
            return;
        }
        if (args.length == 3) {
            if (!com.rounds.debug.DebugSession.active) {
                sender.sendMessage(ChatColor.RED + "Дебаг не запущен. Сначала используй /rdebug debug start");
                return;
            }
            int to;
            try {
                to = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Неверный номер раунда");
                return;
            }
            if (to < 1) {
                sender.sendMessage(ChatColor.RED + "Номер раунда должен быть >= 1");
                return;
            }
            int cur = (int) plugin.getGameManager().getCurrentRound();
            if (to <= cur) {
                sender.sendMessage(ChatColor.RED + "Нельзя поставить автостоп на уже прошедший или текущий завершённый раунд. Текущий раунд: " + cur);
                return;
            }
            com.rounds.debug.DebugSession.stopAt = to;
            sender.sendMessage(ChatColor.GREEN + "RoundsDebug: автостоп запланирован. Дамп будет сделан в конце раунда " + to + ".");
            return;
        }
        sender.sendMessage(ChatColor.RED + "Использование: /rdebug debug stop | /rdebug debug stop <to>");
    }

    private void handleDebugStatus(CommandSender sender) {
        if (!sender.hasPermission("rounds.admin")) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.no-permission"));
            return;
        }
        com.rounds.debug.DebugSession ds = null;
        sender.sendMessage(ChatColor.GOLD + "RoundsDebug status:");
        sender.sendMessage(ChatColor.GRAY + "активен: " + com.rounds.debug.DebugSession.active);
        sender.sendMessage(ChatColor.GRAY + "старт: " + (com.rounds.debug.DebugSession.startAt == -1 ? "сейчас" : "R#" + com.rounds.debug.DebugSession.startAt));
        sender.sendMessage(ChatColor.GRAY + "автостоп: " + (com.rounds.debug.DebugSession.stopAt == -1 ? "нет" : "конец R#" + com.rounds.debug.DebugSession.stopAt));
        sender.sendMessage(ChatColor.GRAY + "triggered: " + com.rounds.debug.DebugSession.triggered);
        sender.sendMessage(ChatColor.GRAY + "последний залогированный раунд: R#" + com.rounds.debug.DebugSession.lastLoggedRound);
        sender.sendMessage(ChatColor.GRAY + "строк в буфере: " + com.rounds.debug.DebugLog.bufferSize());
        if (com.rounds.debug.DebugSession.logFile != null) {
            sender.sendMessage(ChatColor.GRAY + "файл: " + com.rounds.debug.DebugSession.logFile.getAbsolutePath());
        }
    }

    private void handleStop(CommandSender sender) {
        try {
            if (com.rounds.debug.DebugSession.active) {
                com.rounds.debug.DebugLog.flushFinal("GAME STOPPED BY /rdebug stop");
                com.rounds.debug.DebugSession.closeWriter();
                com.rounds.debug.DebugSession.clear();
            } else if (!com.rounds.debug.DebugSession.triggered && com.rounds.debug.DebugSession.startAt != -1) {
                com.rounds.debug.DebugSession.clear();
            }
        } catch (Throwable ignored) {}
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
            if (amount < 1) {
                sender.sendMessage(ChatColor.RED + Messages.get("command.amount-between", Integer.MAX_VALUE));
                return;
            }
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

    private void handleBossBar(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.usage-bossbar"));
            return;
        }
        String arg = args[1].toLowerCase();
        if (arg.equals("on") || arg.equals("вкл")) {
            plugin.getRoundsConfig().setAutoDeathBossBarEnabled(true);
            sender.sendMessage(ChatColor.GREEN + Messages.get("debug.bossbar-on"));
        } else if (arg.equals("off") || arg.equals("выкл")) {
            plugin.getRoundsConfig().setAutoDeathBossBarEnabled(false);
            if (plugin.getGameManager() != null) {
                plugin.getGameManager().hideAutoDeathBossBar();
            }
            sender.sendMessage(ChatColor.GREEN + Messages.get("debug.bossbar-off"));
        } else {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.usage-bossbar"));
        }
    }

    private void handleMusic(CommandSender sender, String[] args) {
        if (plugin.getRoundMusicManager() == null) {
            sender.sendMessage(ChatColor.RED + "Music manager not loaded");
            return;
        }
        var mm = plugin.getRoundMusicManager();
        if (args.length == 1) {
            var current = mm.getCurrentTrack();
            sender.sendMessage(ChatColor.GOLD + "[Rounds] " + ChatColor.YELLOW + "Music status: "
                    + (mm.isEnabled() ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED")
                    + ChatColor.GRAY + " (volume: " + mm.getVolume() + ")");
            sender.sendMessage(ChatColor.GOLD + "[Rounds] " + ChatColor.YELLOW + "Current track: "
                    + (current != null ? ChatColor.AQUA + current.getName() : ChatColor.GRAY + "None")
                    + ChatColor.GRAY + " (next index: " + mm.getCurrentIndex() + "/" + mm.getPlaylist().size() + ")");
            return;
        }
        String arg = args[1].toLowerCase();
        if (arg.equals("on") || arg.equals("вкл")) {
            plugin.getRoundsConfig().setMusicEnabled(true);
            sender.sendMessage(ChatColor.GREEN + "[Rounds] Round music enabled");
        } else if (arg.equals("off") || arg.equals("выкл")) {
            plugin.getRoundsConfig().setMusicEnabled(false);
            mm.stop();
            sender.sendMessage(ChatColor.YELLOW + "[Rounds] Round music disabled");
        } else if (arg.equals("next") || arg.equals("след")) {
            var next = mm.nextTrack();
            sender.sendMessage(ChatColor.GREEN + "[Rounds] Switched to next track: "
                    + (next != null ? next.getName() : "None"));
        } else if (arg.equals("stop") || arg.equals("стоп")) {
            mm.stop();
            sender.sendMessage(ChatColor.YELLOW + "[Rounds] Round music stopped");
        } else if (arg.equals("play") || arg.equals("старт")) {
            mm.playCurrentTrack();
            sender.sendMessage(ChatColor.GREEN + "[Rounds] Playing current track: "
                    + (mm.getCurrentTrack() != null ? mm.getCurrentTrack().getName() : "None"));
        } else {
            try {
                double vol = Double.parseDouble(args[1]);
                plugin.getRoundsConfig().setMusicVolume(vol);
                sender.sendMessage(ChatColor.GREEN + "[Rounds] Music volume set to: " + plugin.getRoundsConfig().getMusicVolume());
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Usage: /rdebug music [on|off|next|stop|play|<volume 0.0..1.0>]");
            }
        }
    }

    private void handleInfo(CommandSender sender) {
        GameManager gm = plugin.getGameManager();
        TeamManager tm = plugin.getTeamManager();
        boxHeader(sender, Messages.get("command.info-title"));
        boxKv(sender, Messages.get("command.version", plugin.getDescription().getVersion()));
        boxKv(sender, Messages.get("command.state", Messages.get("command.game-state-" + gm.getState().name().toLowerCase())));
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
                plugin.getGameManager().giveGun(p);
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
        plugin.getGameManager().giveGun(target);
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.gun-given") + (args.length >= 2 ? " -> " + target.getName() : ""));
    }

    // === CARDS ===

    private void handleCards(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rounds.admin")) {
            if (!(sender instanceof Player viewer)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
            if (args.length < 2) {
                plugin.getCardGUI().openShow(viewer, viewer);
                return;
            }
            if (args[1].equalsIgnoreCase("show")) {
                handleCardsShow(sender, args);
                return;
            }
            sender.sendMessage(ChatColor.RED + Messages.get("command.no-permission"));
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
        Player target = (sender instanceof Player p) ? p : null;
        int page = 1;

        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException notANumber) {
                target = Bukkit.getPlayer(args[2]);
                if (target == null && plugin.getBotManager() != null) {
                    var bot = plugin.getBotManager().getBot(args[2]);
                    if (bot != null) {
                        target = bot.getBukkitPlayer();
                    }
                }
                if (target != null && args.length >= 4) {
                    try {
                        page = Integer.parseInt(args[3]);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        if (target == null) {
            if (args.length >= 3) {
                sender.sendMessage(ChatColor.RED + Messages.get("debug.player-not-found", args[2]));
            } else {
                sender.sendMessage(ChatColor.RED + "Usage: /rdebug cards show <nickname>");
            }
            return;
        }

        if (sender instanceof Player viewer) {
            plugin.getCardGUI().openShow(viewer, target, page);
        } else {
            List<Integer> ownedCardIds = plugin.getPlayerDataManager().getData(target).getOwnedCards();
            sender.sendMessage(ChatColor.GOLD + "Cards for " + target.getName() + ":");
            String lang = Messages.getLanguageCode();
            for (int id : ownedCardIds) {
                com.rounds.cards.Card card = plugin.getCardManager().getRegistry().getCard(id);
                if (card != null) {
                    sender.sendMessage("- " + card.getName(lang));
                }
            }
        }
    }

    private void handleCardsAdd(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /rdebug cards add <player> <card> [rarity]");
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.player-not-found", args[2]));
            return;
        }

        String lastToken = args[args.length - 1].toUpperCase();
        com.rounds.cards.CardRegistry.Rarity explicitRarity = null;
        try {
            explicitRarity = com.rounds.cards.CardRegistry.Rarity.valueOf(lastToken);
        } catch (IllegalArgumentException ignored) {}

        com.rounds.cards.Card baseCard = null;
        String cardQuery = null;

        if (explicitRarity != null && args.length >= 5) {
            cardQuery = String.join(" ", Arrays.copyOfRange(args, 3, args.length - 1));
            baseCard = plugin.getCardManager().getRegistry().findCardByName(cardQuery);
        }

        if (baseCard == null) {
            cardQuery = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            baseCard = plugin.getCardManager().getRegistry().findCardByName(cardQuery);
            explicitRarity = null;
        }

        if (baseCard == null) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.card-not-found", cardQuery));
            return;
        }

        com.rounds.cards.Card finalCard;
        if (explicitRarity != null) {
            finalCard = plugin.getCardManager().getRegistry().getFamilyCardByRarity(baseCard, explicitRarity);
            if (finalCard == null) {
                List<com.rounds.cards.CardRegistry.Rarity> avail = plugin.getCardManager().getRegistry().getAvailableRarities(baseCard);
                sender.sendMessage(ChatColor.RED + "Card '" + baseCard.getName("en") + "' has no rarity " + explicitRarity + ". Available: " + avail);
                return;
            }
        } else {
            finalCard = plugin.getCardManager().getRegistry().getRandomFamilyCard(baseCard);
        }

        boolean applied = plugin.getCardManager().applyCardToPlayer(target, finalCard);
        String lang = Messages.getLanguageCode();
        if (applied) {
            sender.sendMessage(ChatColor.GREEN + Messages.get("command.applied-card", finalCard.getColoredName(lang))
                    + " (" + finalCard.getRarity().getColor() + finalCard.getRarity().name() + ChatColor.GREEN + ")"
                    + (target != sender ? " -> " + target.getName() : ""));
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Игрок " + target.getName() + " уже имеет эту карту равной или более высокой редкости!");
        }
    }

    private static Double tryParseDouble(String str) {
        if (str == null) return null;
        try {
            return Double.parseDouble(str.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // === STATS ===

    private void handleStats(CommandSender sender, String[] args) {
        boolean admin = sender.hasPermission("rounds.admin");
        if (args.length < 2) {
            if (admin) {
                sender.sendMessage(ChatColor.RED + "Usage: /rdebug stats show <player> OR /rdebug stats add <player> <stat> <value>");
            } else {
                sender.sendMessage(ChatColor.RED + "Usage: /rdebug stats show <player>");
            }
            return;
        }

        String sub = args[1].toLowerCase();
        if (sub.equals("show")) {
            Player target = null;
            if (args.length < 3) {
                if (sender instanceof Player p) target = p;
                else {
                    sender.sendMessage(ChatColor.RED + "Usage: /rdebug stats show <player>");
                    return;
                }
            } else {
                target = Bukkit.getPlayer(args[2]);
            }
            if (target == null) {
                sender.sendMessage(ChatColor.RED + Messages.get("debug.player-not-found", args.length < 3 ? "you" : args[2]));
                return;
            }

            PlayerData data = plugin.getPlayerDataManager().getData(target);
            
            // Header design
            sender.sendMessage(B + "\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
            sender.sendMessage(B + "\u2551 " + W + "\u2502 " + B + "Статы " + target.getName());
            sender.sendMessage(B + "\u2551 " + W + "\u2502 " + B + "Команда: " + getTeamDisplay(target));
            sender.sendMessage(B + "\u2551 " + W + "\u2502 " + B + "Карты: " + countCards(data));
            sender.sendMessage(B + "\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");

            // Combat section
            com.rounds.DefaultStats def = com.rounds.DefaultStats.get();
            String hpLabel = "HP";
            
            boxSection(sender, Messages.get("debug.combat-section"));
            boxKv(sender, Messages.get("debug.stat-hp"), String.format(Locale.US, "%.1f " + hpLabel, target.getHealth()));
            boxKv(sender, Messages.get("debug.stat-dmg"), String.format(Locale.US, "%.1f", def.dmg + data.dmg));
            boxKv(sender, Messages.get("debug.stat-atks"), formatSeconds((def.atks + data.atks) / 20.0));
            if (data.atkSpeed != 0.0) {
                boxKv(sender, Messages.get("debug.stat-atk-speed"), String.format(Locale.US, "%+.0f%%", data.atkSpeed * 100.0));
            }
            boxKv(sender, Messages.get("debug.stat-atkr"), String.format(Locale.US, "%.1f", def.atkr + data.atkr));
            boxKv(sender, Messages.get("debug.stat-ammo"), String.format(Locale.US, "%.0f / %.0f", data.ammo, def.maxAmmo + data.maxAmmo));
            boxKv(sender, Messages.get("debug.stat-bullets"), String.valueOf((int) (def.bullets + data.bullets)));
            boxKv(sender, Messages.get("debug.stat-bullet-speed"), String.format(Locale.US, "%.1f", def.bulletSpeed + data.bulletSpeed));
            boxKv(sender, Messages.get("debug.stat-reload-time"), formatSeconds(GunItem.getReloadDurationSeconds(data)));
            boxKv(sender, Messages.get("debug.stat-bounce"), String.format(Locale.US, "%.0f", data.bouncePl));
            boxKv(sender, Messages.get("debug.stat-homing"), String.format(Locale.US, "%.1f", data.homing));
            boxKv(sender, Messages.get("debug.stat-big-bullet"), String.format(Locale.US, "%.1f", data.bigBullet));

            boxSection(sender, Messages.get("debug.effects-section"));
            boxStatBool(sender, "stat-cold", data.cold);
            boxStat(sender, "stat-cold-lvl", data.coldLvl);
            boxStatBool(sender, "stat-poison", data.poison);
            boxStat(sender, "stat-poison-lvl", data.poisonLvl);
            boxStatBool(sender, "stat-parazit", data.parazit);
            boxStat(sender, "stat-parazit-lvl", data.parazitLvl);
            boxStatBool(sender, "stat-leech", data.leech);
            boxStat(sender, "stat-truster", data.trusterLvl);
            boxKv(sender, Messages.get("debug.stat-jump-height"), String.format(Locale.US, "%.1f", 2.0 + data.jumpHeight));

            boxSection(sender, Messages.get("debug.special-section"));
            boxStat(sender, "stat-grow", data.grow);
            boxStat(sender, "stat-empower", data.empower);
            boxStat(sender, "stat-empower-charge", data.empowerCharge);
            boxStat(sender, "stat-dark-strength", data.darkStrength);
            boxStat(sender, "stat-dark", data.dark);
            boxStat(sender, "stat-bomb-bullet", data.bombBullet);
            boxStat(sender, "stat-bomb-on-block", data.bombOnBlock);
            if (data.decay > 0) {
                boxKv(sender, Messages.get("debug.stat-decay"), formatSeconds(data.decay));
            }
            if (data.echo > 0) {
                boxStat(sender, "stat-echo", data.echo);
            }

            boxSection(sender, Messages.get("debug.shield-section"));
            boxKv(sender, Messages.get("debug.stat-shield-duration"), formatSeconds(GunItem.getShieldDurationSeconds(plugin, data)));
            long actualBlockCdMs = GunItem.getActualShieldCooldownMs(plugin, data);
            boxKv(sender, Messages.get("debug.stat-block-cd"), formatSeconds(actualBlockCdMs / 1000.0));
            int extraCharges = (int) Math.max(data.shieldCharge + data.doubleBlock, 0);
            int currentCharges = GunItem.getShieldCharges(target.getUniqueId());
            boxKv(sender, Messages.get("debug.stat-shield-charges"), currentCharges + " / " + (1 + extraCharges));

            boxFooter(sender);
            return;
        }

        if (sub.equals("add") || sub.equals("set")) {
            if (!admin) {
                sender.sendMessage(ChatColor.RED + Messages.get("command.no-permission"));
                return;
            }
            if (args.length < 5) {
                sender.sendMessage(ChatColor.RED + "Usage: /rdebug stats add <player> <stat> <value>");
                return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + Messages.get("debug.player-not-found", args[2]));
                return;
            }
            String stat = args[3].toLowerCase();
            Double value = tryParseDouble(args[4]);
            if (value == null) {
                sender.sendMessage(ChatColor.RED + Messages.get("command.invalid-number", args[4]));
                return;
            }
            PlayerData setData = plugin.getPlayerDataManager().getData(target);
            if (!applyStat(setData, target, stat, value)) {
                sender.sendMessage(ChatColor.RED + Messages.get("debug.unknown-stat", stat));
                return;
            }
            sender.sendMessage(ChatColor.GREEN + Messages.get("debug.setstat-done", stat, PlayerData.round2(value), target.getName()));
            return;
        }

        if (admin) {
            sender.sendMessage(ChatColor.RED + "Usage: /rdebug stats show <player> OR /rdebug stats add <player> <stat> <value>");
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /rdebug stats show <player>");
        }
    }

    private void handleStatsEnd(CommandSender sender, String[] args) {
        String targetName;
        if (args.length >= 2) {
            targetName = args[1].trim();
        } else if (sender instanceof Player p) {
            targetName = p.getName();
        } else {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.specify-player"));
            return;
        }

        com.rounds.stats.MatchStatsManager.PlayerMatchStats stats = com.rounds.stats.MatchStatsManager.getLastMatchStatsByName(targetName);
        if (stats == null) {
            if (!com.rounds.stats.MatchStatsManager.hasFinishedGameData()) {
                sender.sendMessage(ChatColor.RED + Messages.get("debug.statsend-no-game"));
            } else {
                sender.sendMessage(ChatColor.RED + Messages.get("debug.statsend-player-not-found", targetName));
            }
            return;
        }

        boxAttachedHeader(sender, Messages.get("debug.statsend-title", stats.playerName));
        boxKv(sender, Messages.get("debug.stat-cards-count"), String.valueOf(stats.cardsCount));
        boxKv(sender, Messages.get("debug.stat-kills"), String.valueOf(stats.kills));
        boxKv(sender, Messages.get("debug.stat-deaths"), String.valueOf(stats.deaths));
        boxKv(sender, Messages.get("debug.stat-shots"), String.valueOf(stats.shots));
        boxKv(sender, Messages.get("debug.stat-damage-dealt"), String.format(Locale.US, "%.1f", stats.damageDealt));
        boxKv(sender, Messages.get("debug.stat-damage-received"), String.format(Locale.US, "%.1f", stats.damageReceived));
        boxKv(sender, Messages.get("debug.stat-max-shot-dmg"), String.format(Locale.US, "%.1f", stats.maxShotDamage));
        boxFooter(sender);
    }

    private boolean applyStat(PlayerData data, Player target, String stat, double value) {
        switch (stat) {
            case "dmg", "damage" -> data.dmg = Math.max(value, 0.5);
            case "atks", "atk_cooldown", "atk-cooldown" -> {
                double ticks = (value <= 5.0) ? value * 20.0 : value;
                data.atks = Math.max(ticks, 1.0);
            }
            case "atk_speed", "atk-speed" -> {
                double bonus = (Math.abs(value) > 1.0) ? value / 100.0 : value;
                data.atkSpeed = Math.max(bonus, -0.5);
            }
            case "atkr", "atk-range", "range" -> data.atkr = Math.max(value, 0);
            case "bounce" -> data.bouncePl = Math.max(value, 0);
            case "ammo", "max_ammo", "max-ammo" -> {
                data.maxAmmo = Math.max((int) value, 1);
                data.ammo = data.maxAmmo;
            }
            case "bullets" -> data.bullets = Math.max(value, 1);
            case "cold" -> data.cold = Math.max(value, 0);
            case "poison" -> data.poison = Math.max(value, 0);
            case "toxic_cloud", "toxic-cloud" -> data.toxicCloud = Math.max(value, 0);
            case "highlight" -> data.highlight = Math.max(value, 0);
            case "leech" -> data.leech = Math.max(value, 0);
            case "homing" -> data.homing = Math.max(value, 0);
            case "poison_lvl", "poison-lvl" -> data.poisonLvl = Math.max(value, 0);
            case "cold_lvl", "cold-lvl" -> data.coldLvl = Math.max(value, 0);
            case "parazit" -> data.parazit = Math.max(value, 0);
            case "parazit_lvl", "parazit-lvl" -> data.parazitLvl = Math.max(value, 0);
            case "hp", "health" -> {
                double clamped = PlayerData.clampMaxHealth(value);
                data.hp = clamped;
                var attr = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (attr != null) attr.setBaseValue(clamped);
                target.setHealth(Math.min(target.getHealth(), clamped));
            }
            case "bomb_bullet", "bomb-bullet" -> data.bombBullet = Math.max(value, 0);
            case "bomb_on_block", "bomb-on-block" -> data.bombOnBlock = Math.max(value, 0);
            case "bullet_speed", "bullet-speed" -> data.bulletSpeed = Math.max(value, 0.5);
            case "empower" -> data.empower = Math.max(value, 0);
            case "empower_charge", "empower-charge" -> data.empowerCharge = Math.max(value, 0);
            case "dark_strength", "dark-strength" -> data.darkStrength = Math.max(value, 0);
            case "big_bullet", "big-bullet" -> data.bigBullet = Math.max(value, 0);
            case "grow" -> data.grow = Math.max(value, 0);
            case "truster_lvl", "truster-lvl" -> data.trusterLvl = Math.max(value, 0);
            case "jump_height", "jump-height" -> data.jumpHeight = Math.max(value, 0);
            case "dark" -> data.dark = Math.max(value, 0);
            case "atks_reload", "atks-reload" -> data.atksReload = Math.max(value, 0);
            case "reload", "reload_time", "reload-time" -> {
                double baseReloadSec = 5.0;
                double targetSec = Math.max(0.2, value);
                data.reloadSpeed = Math.max(-1.0, Math.min(0.95, 1.0 - (targetSec / baseReloadSec)));
            }
            case "block_cd", "block-cd", "shield_cooldown", "shield-cooldown", "shield_cd", "shield-cd" -> {
                long baseMs = GunItem.getActualShieldCooldownMs(plugin, (PlayerData) null);
                double baseSec = baseMs / 1000.0;
                if (value >= 0) {
                    double targetSec = Math.max(0.05, value);
                    data.blockCdMult = targetSec / baseSec;
                    data.blockCd = (targetSec / baseSec) - 1.0;
                    data.shieldCooldown = 0;
                } else {
                    data.blockCd = value;
                    data.blockCdMult = Math.max(0.05, 1.0 + value);
                    data.shieldCooldown = 0;
                }
            }
            case "shield_duration", "shield-duration" -> {
                data.shieldsUp = Math.max(0, value - 2.0);
            }
            case "shield_charges", "shield-charges" -> {
                data.shieldCharge = Math.max(0, (int) value - 1);
            }
            case "decay" -> data.decay = Math.max(value, 0);
            case "echo" -> data.echo = Math.max(value, 0);
            default -> { return false; }
        }
        return true;
    }

    private void handleSetTeam(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ChatColor.RED + Messages.get("debug.usage-setteam")); return; }

        String colorName = args[1].toUpperCase();
        if (colorName.equalsIgnoreCase("NONE")) {
            Player target;
            if (args.length >= 3) {
                target = Bukkit.getPlayer(args[2]);
                if (target == null) { sender.sendMessage(ChatColor.RED + Messages.get("debug.player-not-found", args[2])); return; }
            } else if (sender instanceof Player p) {
                target = p;
            } else {
                sender.sendMessage(ChatColor.RED + Messages.get("debug.specify-player")); return;
            }
            plugin.getTeamManager().leaveTeam(target.getUniqueId());
            if (plugin.getBotManager() != null) {
                var bot = plugin.getBotManager().getBot(target.getName());
                if (bot != null) {
                    bot.setAssignedTeam(null);
                    if (bot.getAi() != null) bot.getAi().clearTarget();
                }
            }
            plugin.getGameManager().applyTeamColor(target);
            plugin.getGameManager().showNameTagsInGame();
            sender.sendMessage(ChatColor.GREEN + Messages.get("debug.team-set", target.getName(), Messages.get("team.none")));
            return;
        }

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
        if (plugin.getBotManager() != null) {
            var bot = plugin.getBotManager().getBot(target.getName());
            if (bot != null) {
                bot.setAssignedTeam(team);
                if (bot.getAi() != null) bot.getAi().clearTarget();
            }
        }
        plugin.getGameManager().applyTeamColor(target);
        plugin.getGameManager().showNameTagsInGame();
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
        plugin.getGameManager().updateScoreboard();
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

    private void handleResetStats(CommandSender sender, String[] args) {
        Player player = null;
        if (args.length >= 2) {
            player = Bukkit.getPlayer(args[1]);
            if (player == null) {
                sender.sendMessage(ChatColor.RED + Messages.get("debug.player-not-found", args[1]));
                return;
            }
        } else if (sender instanceof Player p) {
            player = p;
        } else {
            sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player"));
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().getData(player);
        data.resetStats();
        data.resetAllCards();
        var attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) attr.setBaseValue(20);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.getInventory().clear();
        plugin.getGameManager().clearCardEffects(player);
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.stats-reset") + (player != sender ? " -> " + player.getName() : ""));
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
        return (data != null && data.getOwnedCards() != null) ? data.getOwnedCards().size() : 0;
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
                plugin.getGameManager().setForceScoreboardPreview(true);
                plugin.getGameManager().updateScoreboard();
                sender.sendMessage(ChatColor.GREEN + Messages.get("debug.tab-enabled"));
            }
            case "off" -> {
                config.setBuiltinScoreboard(false);
                plugin.getGameManager().setForceScoreboardPreview(false);
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

    private void handleCardChoiceTime(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rounds.admin")) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.no-permission"));
            return;
        }

        if (args.length < 2) {
            int current = plugin.getRoundsConfig().getCardChoiceTimeSeconds();
            sender.sendMessage(ChatColor.YELLOW + Messages.get("debug.cardchoicetime-usage", current));
            return;
        }

        int sec;
        try {
            sec = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.invalid-number"));
            return;
        }

        if (sec < 10) {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.cardchoicetime-min"));
            return;
        }

        plugin.getRoundsConfig().setCardChoiceTimeSeconds(sec);
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.cardchoicetime-set", sec));
    }

    private static final List<String> SUBCOMMANDS = Arrays.asList(
        "help", "start", "stop", "status", "rounds", "info",
        "givegun", "cards", "giveblocks", "join", "freeplay", "autodeath",
        "stats", "statsend", "setteam", "setlanguage", "effect", "heal",
        "resetstats", "reload", "setlobby",
        "jumppos1", "jumppos2", "jumpset", "uppos1", "uppos2", "upset",
        "wheel", "tab", "bot", "draft", "cardchoicetime", "bossbar", "debug", "music"
    );

    private List<String> getOnlinePlayerNames() {
        List<String> list = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
        return list;
    }
    private static final List<String> FREEPLAY_COMMANDS = Arrays.asList(
        "start", "stop", "rounds", "givegun", "wheel", "bot", "bossbar"
    );
    private static final List<String> PUBLIC_COMMANDS = Arrays.asList(
        "info", "status", "stats", "cards", "statsend"
    );
    private static final List<String> TEAM_COLORS = Arrays.asList("BLUE", "RED", "YELLOW", "GREEN", "NONE");

    private void handleBot(CommandSender sender, String[] args) {
        boolean admin = sender.hasPermission("rounds.admin");
        boolean freeplay = plugin.getRoundsConfig().isFreeplay();
        if (!admin && !freeplay) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to spawn bots. (Requires rounds.admin or /rdebug freeplay on)");
            return;
        }

        com.rounds.bot.BotManager bm = plugin.getBotManager();
        if (bm == null) {
            sender.sendMessage(ChatColor.RED + "BotManager is not available.");
            return;
        }

        Location loc = (sender instanceof Player p) ? p.getLocation() : null;

        // 1. /rdebug bot (with no extra args -> show usage, do NOT spawn)
        if (args.length == 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /rdebug bot <spawn|despawn|level> [args]");
            sender.sendMessage(ChatColor.GRAY + "Example: /rdebug bot spawn");
            return;
        }

        // 2. /rdebug bot despawn ...
        if (args[1].equalsIgnoreCase("despawn") || args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("kill")) {
            if (args.length >= 3 && args[2].equalsIgnoreCase("all")) {
                int count = bm.despawnAll();
                sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Despawned all active bots (total: " + count + ")");
                return;
            }
            if (args.length >= 4 && args[2].equalsIgnoreCase("count")) {
                try {
                    int count = Integer.parseInt(args[3]);
                    int removed = bm.despawnBots(count);
                    sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Despawned " + removed + " bots (prioritizing largest teams).");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid number: " + args[3]);
                }
                return;
            }
            if (args.length >= 3 && args[2].matches("\\d+")) {
                int count = Integer.parseInt(args[2]);
                int removed = bm.despawnBots(count);
                sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Despawned " + removed + " bots (prioritizing largest teams).");
                return;
            }
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /rdebug bot despawn <bot_name|all|count <int>>");
                return;
            }
            String name = args[2];
            boolean ok = bm.despawnBot(name);
            if (ok) {
                sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Despawned bot " + name);
            } else {
                sender.sendMessage(ChatColor.RED + "[RoundsBot] Bot not found: " + name);
            }
            return;
        }

        // /rdebug bot <name> despawn
        if (args.length >= 3 && (args[2].equalsIgnoreCase("despawn") || args[2].equalsIgnoreCase("remove") || args[2].equalsIgnoreCase("kill"))) {
            String name = args[1];
            if (name.equalsIgnoreCase("all")) {
                int count = bm.despawnAll();
                sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Despawned all active bots (total: " + count + ")");
                return;
            }
            boolean ok = bm.despawnBot(name);
            if (ok) {
                sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Despawned bot " + name);
            } else {
                sender.sendMessage(ChatColor.RED + "[RoundsBot] Bot not found: " + name);
            }
            return;
        }

        // 3. /rdebug bot spawn count <int> / /rdebug bot spawn
        boolean isSpawnKeyword = args[1].equalsIgnoreCase("spawn");
        if (isSpawnKeyword) {
            int countIdx = -1;
            if (args.length >= 4 && args[2].equalsIgnoreCase("count")) {
                countIdx = 3;
            } else if (args.length >= 3 && args[2].matches("\\d+")) {
                countIdx = 2;
            } else if (args.length == 2) {
                com.rounds.bot.FakeBot bot = bm.spawnBot(null, null, loc);
                if (bot != null) {
                    sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Spawned bot " + ChatColor.AQUA + bot.getName() +
                            ChatColor.GREEN + " on team " + ChatColor.GOLD + bot.getTeamName() +
                            ChatColor.GRAY + " [Level: " + bot.getLevel() + " (" + (bot.getLevel() * 10) + "%)]");
                }
                return;
            }

            if (countIdx > 0) {
                try {
                    int count = Integer.parseInt(args[countIdx]);
                    List<com.rounds.bot.FakeBot> spawned = bm.spawnBots(count, loc);
                    sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Spawned " + spawned.size() + " bots with balanced teams.");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid number: " + args[countIdx]);
                }
                return;
            }
        }

        // 5. Difficulty level check/set: /rdebug bot level <name> [1..10]
        if (args.length >= 3 && (args[1].equalsIgnoreCase("level") || args[1].equalsIgnoreCase("lvl"))) {
            com.rounds.bot.FakeBot bot = bm.getBot(args[2]);
            if (bot == null) {
                sender.sendMessage(ChatColor.RED + "[RoundsBot] Bot not found: " + args[2]);
                return;
            }
            if (args.length >= 4) {
                try {
                    int lvl = Integer.parseInt(args[3]);
                    bot.setLevel(lvl);
                    sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Set " + bot.getName() + " difficulty to Level " +
                            bot.getLevel() + " (" + (bot.getLevel() * 10) + "%)");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid level number: " + args[3]);
                }
            } else {
                sender.sendMessage(ChatColor.GOLD + "[RoundsBot] " + bot.getName() + " difficulty: Level " +
                        bot.getLevel() + " (" + (bot.getLevel() * 10) + "%)");
            }
            return;
        }

        // 5. Spawn: /rdebug bot <name> [team] [1..10] or /rdebug bot spawn <name> [team] [1..10]
        int nameIndex = 1;
        int teamIndex = 2;
        if (args[1].equalsIgnoreCase("spawn") && args.length >= 3) {
            nameIndex = 2;
            teamIndex = 3;
        }

        String name = args[nameIndex];
        String team = (args.length > teamIndex && !args[teamIndex].isBlank())
                ? args[teamIndex].toUpperCase()
                : bm.pickBestTeam(loc);

        Integer initialLevel = null;
        if (args.length > teamIndex + 1) {
            try {
                initialLevel = Integer.parseInt(args[teamIndex + 1]);
            } catch (NumberFormatException ignored) {}
        }

        com.rounds.bot.FakeBot bot = bm.spawnBot(name, team, loc, initialLevel);
        if (bot != null) {
            sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Spawned bot " + ChatColor.AQUA + bot.getName() +
                    ChatColor.GREEN + " on team " + ChatColor.GOLD + bot.getTeamName() +
                    ChatColor.GRAY + " [Level: " + bot.getLevel() + " (" + (bot.getLevel() * 10) + "%)]");
        }
    }

    private void handleDraft(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + Messages.get("debug.player-not-found", args[1]));
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /rdebug draft <player>");
            return;
        }

        GameTeam team = plugin.getTeamManager().getPlayerTeam(target.getUniqueId());
        if (team == null) team = GameTeam.BLUE;
        plugin.getCardManager().openCardSelection(target, team);
        sender.sendMessage(ChatColor.GREEN + "Opened card draft GUI for " + target.getName());
    }

    private void handleCardTest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be executed by a player.");
            return;
        }

        if (args.length >= 2 && "clear".equalsIgnoreCase(args[1])) {
            int count = 0;
            for (TextDisplay td : player.getWorld().getEntitiesByClass(TextDisplay.class)) {
                if (td.getScoreboardTags().contains("rounds_test_display")) {
                    td.remove();
                    count++;
                }
            }
            player.sendMessage(ChatColor.GREEN + "[CardTest] Removed " + count + " test displays.");
            return;
        }

        int cardId = 1;
        if (args.length >= 2) {
            try {
                cardId = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                Card found = plugin.getCardManager().getRegistry().findCardByName(args[1]);
                if (found != null) cardId = found.getId();
            }
        }
        Card card = plugin.getCardManager().getRegistry().getCard(cardId);
        int familyId = card != null ? card.getFamilyId() : cardId;
        int glyphCp = card != null ? card.getGlyphCodepoint() : 0xE000 + cardId * 10;
        String cardName = card != null ? card.getName("ru") : ("Card #" + cardId);

        for (TextDisplay td : player.getWorld().getEntitiesByClass(TextDisplay.class)) {
            if (td.getScoreboardTags().contains("rounds_test_display")) {
                td.remove();
            }
        }

        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection().setY(0).normalize();
        if (dir.lengthSquared() < 0.01) dir = new org.bukkit.util.Vector(0, 0, 1);
        org.bukkit.util.Vector side = new org.bukkit.util.Vector(-dir.getZ(), 0, dir.getX());

        Location centerLoc = eye.clone().add(dir.clone().multiply(2.2)).add(0, -1.2, 0);
        TextDisplay tdCenter = player.getWorld().spawn(centerLoc, TextDisplay.class);
        tdCenter.text(Component.text(String.valueOf((char) glyphCp)).font(Key.key("rounds", "default")));
        tdCenter.setBillboard(Display.Billboard.CENTER);
        tdCenter.setSeeThrough(false);
        tdCenter.setViewRange(1.0f);
        tdCenter.setBrightness(new Display.Brightness(15, 15));
        tdCenter.addScoreboardTag("rounds_test_display");

        Location leftLoc = centerLoc.clone().add(side.clone().multiply(-1.6));
        TextDisplay tdLeft = player.getWorld().spawn(leftLoc, TextDisplay.class);
        tdLeft.text(Component.text(String.valueOf((char) glyphCp)));
        tdLeft.setBillboard(Display.Billboard.CENTER);
        tdLeft.setSeeThrough(false);
        tdLeft.setViewRange(1.0f);
        tdLeft.setBrightness(new Display.Brightness(15, 15));
        tdLeft.addScoreboardTag("rounds_test_display");

        Location rightLoc = centerLoc.clone().add(side.clone().multiply(1.6));
        TextDisplay tdRight = player.getWorld().spawn(rightLoc, TextDisplay.class);
        tdRight.text(Component.text("TEST LABEL\n" + cardName + "\nID: " + cardId + " (fid: " + familyId + ")\nChar: \\u" + Integer.toHexString(glyphCp).toUpperCase()));
        tdRight.setBillboard(Display.Billboard.CENTER);
        tdRight.setSeeThrough(false);
        tdRight.setViewRange(1.0f);
        tdRight.setBrightness(new Display.Brightness(15, 15));
        tdRight.addScoreboardTag("rounds_test_display");

        player.sendMessage(ChatColor.GOLD + "=== [Rounds CardTest Debug] ===");
        player.sendMessage(ChatColor.YELLOW + "Spawned 3 test displays 2.2m ahead:");
        player.sendMessage(ChatColor.AQUA + "  Center: " + ChatColor.WHITE + "Glyph \\u" + Integer.toHexString(glyphCp).toUpperCase() + " [font: rounds:default]");
        player.sendMessage(ChatColor.GREEN + "  Left:   " + ChatColor.WHITE + "Glyph \\u" + Integer.toHexString(glyphCp).toUpperCase() + " [font: minecraft:default]");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "  Right:  " + ChatColor.WHITE + "Plain Text Label (" + cardName + ")");
        player.sendMessage(ChatColor.GRAY + "If you see textures: resourcepack font works!");
        player.sendMessage(ChatColor.GRAY + "If you see square/tofu: font mapping missing in pack.");
        player.sendMessage(ChatColor.GRAY + "If you see only Right: custom glyph failed to render.");
        player.sendMessage(ChatColor.RED + "Run '/rdebug cardtest clear' to remove.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission("rounds.admin");
        boolean freeplay = plugin.getRoundsConfig().isFreeplay();
        String input = args[args.length - 1].toLowerCase();

        if (!admin) {
            List<String> allowed = new ArrayList<>(PUBLIC_COMMANDS);
            if (freeplay) allowed.addAll(FREEPLAY_COMMANDS);
            if (args.length == 1) return filterStartsWith(allowed, input);
            String root = args[0].toLowerCase();
            boolean rootAllowed = PUBLIC_COMMANDS.contains(root)
                    || (freeplay && FREEPLAY_COMMANDS.contains(root));
            if (!rootAllowed) return Collections.emptyList();
            if (root.equals("stats")) {
                if (args.length == 2) {
                    List<String> list = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
                    return filterStartsWith(list, input);
                }
                return Collections.emptyList();
            }
            if (root.equals("statsend")) {
                if (args.length == 2) {
                    List<String> list = new ArrayList<>();
                    for (com.rounds.stats.MatchStatsManager.PlayerMatchStats s : com.rounds.stats.MatchStatsManager.getAllLastMatchStats()) {
                        list.add(s.playerName);
                    }
                    if (list.isEmpty()) {
                        Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
                    }
                    return filterStartsWith(list, input);
                }
                return Collections.emptyList();
            }
            if (root.equals("cards")) {
                if (args.length == 2) return filterStartsWith(Collections.singletonList("show"), input);
                if (args.length == 3 && args[1].equalsIgnoreCase("show")) {
                    List<String> list = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
                    return filterStartsWith(list, input);
                }
                return Collections.emptyList();
            }
        } else {
            if (args.length == 1) return filterStartsWith(SUBCOMMANDS, input);
        }

        return switch (args[0].toLowerCase()) {
            case "debug" -> {
                if (args.length == 1) {
                    yield filterStartsWith(Arrays.asList("start", "stop", "status"), input);
                }
                if (args.length == 2) {
                    if (args[1].equalsIgnoreCase("start")) {
                        yield filterStartsWith(Arrays.asList("1", "10", "20", "30", "40", "50"), input);
                    }
                    if (args[1].equalsIgnoreCase("stop")) {
                        yield filterStartsWith(Arrays.asList("50", "55", "60"), input);
                    }
                    yield Collections.emptyList();
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("start")) {
                    yield filterStartsWith(Collections.singletonList("stop"), input);
                }
                if (args.length == 4 && args[1].equalsIgnoreCase("start") && args[2].equalsIgnoreCase("stop")) {
                    yield filterStartsWith(Arrays.asList("50", "55", "60"), input);
                }
                yield Collections.emptyList();
            }
            case "bot" -> {
                if (args.length == 2) {
                    List<String> list = new ArrayList<>(Arrays.asList("spawn", "despawn", "level", "list"));
                    if (plugin.getBotManager() != null) {
                        plugin.getBotManager().getBots().forEach(b -> list.add(b.getName()));
                    }
                    Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
                    yield filterStartsWith(list, input);
                }
                if (args.length == 3) {
                    if (args[1].equalsIgnoreCase("spawn")) {
                        List<String> list = new ArrayList<>(Arrays.asList("count", "auto", "1", "2", "3", "4", "5"));
                        yield filterStartsWith(list, input);
                    }
                    if (args[1].equalsIgnoreCase("despawn")) {
                        List<String> list = new ArrayList<>(Arrays.asList("all", "count"));
                        if (plugin.getBotManager() != null) {
                            plugin.getBotManager().getBots().forEach(b -> list.add(b.getName()));
                        }
                        Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
                        yield filterStartsWith(list, input);
                    }
                    List<String> teams = new ArrayList<>(TEAM_COLORS);
                    teams.add("despawn");
                    yield filterStartsWith(teams, input);
                }
                if (args.length == 4) {
                    if (args[1].equalsIgnoreCase("spawn") && args[2].equalsIgnoreCase("count")) {
                        yield filterStartsWith(Arrays.asList("1", "2", "3", "4", "5", "8"), input);
                    }
                    if (args[1].equalsIgnoreCase("despawn") && args[2].equalsIgnoreCase("count")) {
                        yield filterStartsWith(Arrays.asList("1", "2", "3", "4", "5", "8"), input);
                    }
                }
                yield Collections.emptyList();
            }
            case "draft" -> {
                if (args.length == 2) {
                    List<String> list = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
                    yield filterStartsWith(list, input);
                }
                yield Collections.emptyList();
            }
            case "cardtest" -> {
                if (args.length == 2) {
                    List<String> list = new ArrayList<>(Arrays.asList("clear", "1", "2", "3", "4", "5", "10", "20", "84"));
                    yield filterStartsWith(list, input);
                }
                yield Collections.emptyList();
            }
            case "givegun", "giveblocks" -> {
                List<String> list = new ArrayList<>();
                list.add("@a");
                Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
                yield filterStartsWith(list, input);
            }
            case "stats" -> {
                if (args.length == 2) {
                    if (admin) {
                        yield filterStartsWith(Arrays.asList("show", "add"), input);
                    } else {
                        yield filterStartsWith(Collections.singletonList("show"), input);
                    }
                }
                String sub = args[1].toLowerCase();
                if (sub.equals("show")) {
                    if (args.length == 3) {
                        yield filterStartsWith(getOnlinePlayerNames(), input);
                    }
                } else if (sub.equals("add") && admin) {
                    if (args.length == 3) {
                        yield filterStartsWith(getOnlinePlayerNames(), input);
                    }
                    if (args.length == 4) {
                        yield filterStartsWith(STATS, input);
                    }
                    if (args.length == 5) {
                        yield filterStartsWith(Arrays.asList("1", "2", "3", "5", "10", "20"), input);
                    }
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
            case "cards" -> {
                if (!admin) {
                    yield args.length == 2
                            ? filterStartsWith(Collections.singletonList("show"), input)
                            : (args.length == 3 && args[1].equalsIgnoreCase("show")
                                ? filterStartsWith(getOnlinePlayerNames(), input)
                                : Collections.emptyList());
                }
                if (args.length == 2) yield filterStartsWith(Arrays.asList("reload", "show", "add"), input);
                String sub = args[1].toLowerCase();
                if (sub.equals("show")) {
                    if (args.length == 3) {
                        yield filterStartsWith(getOnlinePlayerNames(), input);
                    }
                } else if (sub.equals("add")) {
                    if (args.length == 3) {
                        yield filterStartsWith(getOnlinePlayerNames(), input);
                    } else if (args.length == 4) {
                        yield filterStartsWith(plugin.getCardManager().getRegistry().getCardNameSuggestions(), input);
                    } else if (args.length >= 5) {
                        var reg = plugin.getCardManager().getRegistry();
                        String joined = String.join(" ", Arrays.copyOfRange(args, 3, args.length - 1));
                        com.rounds.cards.Card c = reg.findCardByName(joined);
                        if (c != null) {
                            List<String> rarities = new ArrayList<>();
                            for (com.rounds.cards.CardRegistry.Rarity r : reg.getAvailableRarities(c)) {
                                rarities.add(r.name());
                            }
                            yield filterStartsWith(rarities, input);
                        }
                        String prefixSoFar = joined.toLowerCase() + " ";
                        List<String> continuations = new ArrayList<>();
                        for (String fullCard : reg.getCardNameSuggestions()) {
                            if (fullCard.toLowerCase().startsWith(prefixSoFar)) {
                                continuations.add(fullCard.substring(prefixSoFar.length()));
                            }
                        }
                        yield filterStartsWith(continuations, input);
                    }
                }
                yield Collections.emptyList();
            }
            case "statsend" -> {
                if (args.length == 2) {
                    List<String> list = new ArrayList<>();
                    for (com.rounds.stats.MatchStatsManager.PlayerMatchStats s : com.rounds.stats.MatchStatsManager.getAllLastMatchStats()) {
                        list.add(s.playerName);
                    }
                    if (list.isEmpty()) {
                        Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
                    }
                    yield filterStartsWith(list, input);
                }
                yield Collections.emptyList();
            }
            case "rounds" -> filterStartsWith(Arrays.asList("1", "5", "10", "15", "20", "50", "100", "500"), input);
            case "cardchoicetime" -> {
                if (args.length == 2) yield filterStartsWith(Arrays.asList("10", "15", "20", "30", "45", "60"), input);
                yield Collections.emptyList();
            }
            case "autodeath" -> filterStartsWith(Arrays.asList("on", "off"), input);
            case "bossbar" -> filterStartsWith(Arrays.asList("on", "off"), input);
            case "music" -> filterStartsWith(Arrays.asList("on", "off", "next", "stop", "play"), input);
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

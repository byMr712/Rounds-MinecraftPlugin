package com.rounds.command;

import com.rounds.RoundsConfig;
import com.rounds.RoundsKeys;
import com.rounds.RoundsPlugin;
import com.rounds.entity.RoundsEntities;
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
        "truster_lvl", "dark", "atks_reload"
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
        String filler = "\u2550".repeat(Math.max(0, 28 - title.length()));
        sender.sendMessage(B + "\u2560\u2550\u2550 " + A + title + " " + B + filler);
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
        if (args[0].toLowerCase().equals("join")) {
            handleJoin(sender);
            return true;
        }
        if (!sender.hasPermission("rounds.admin")) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.no-permission"));
            return true;
        }
        switch (args[0].toLowerCase()) {
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
            case "info" -> handleInfo(sender);
            case "test" -> sender.sendMessage(ChatColor.GREEN + Messages.get("command.plugin-working"));
            case "stats" -> handleStats(sender, args);
            case "givegun" -> handleGiveGun(sender, args);
            case "giveall" -> handleGiveAll(sender);
            case "setstat" -> handleSetStat(sender, args);
            case "setteam" -> handleSetTeam(sender, args);
            case "setlanguage" -> handleSetLanguage(sender, args);
            case "effect" -> handleEffect(sender, args);
            case "heal" -> handleHeal(sender, args);
            case "spawnbomb" -> handleSpawnBomb(sender);
            case "spawnheal" -> handleSpawnHeal(sender);
            case "spawntoxic" -> handleSpawnToxic(sender);
            case "spawnshield" -> handleSpawnShield(sender);
            case "entities" -> handleEntities(sender);
            case "cards" -> handleCards(sender, args);
            case "applycard" -> handleApplyCard(sender, args);
            case "resetstats" -> handleResetStats(sender);
            case "reload" -> handleReload(sender);
            case "version" -> handleVersion(sender);
            case "killround" -> handleKillRound(sender);
            case "iteminfo" -> handleItemInfo(sender);
            case "giveblocks" -> handleBlocks(sender, args);
            case "setlobby" -> handleSetLobby(sender);
            case "jumppos1" -> handleJumpPos(sender, 1);
            case "jumppos2" -> handleJumpPos(sender, 2);
            case "jumpset" -> handleJumpSet(sender);
            case "wheel" -> handleWheel(sender, args);
            case "tab" -> handleTab(sender, args);
            case "placeholders" -> handlePlaceholders(sender);
            default -> sender.sendMessage(ChatColor.RED + Messages.get("debug.unknown-command", args[0]));
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        boxHeader(sender, Messages.get("debug.title"));

        boxSection(sender, "GAME");
        boxKv(sender, "/rdebug start", Messages.get("debug.help-start"));
        boxKv(sender, "/rdebug stop", Messages.get("debug.help-stop"));
        boxKv(sender, "/rdebug status", Messages.get("debug.help-status"));
        boxKv(sender, "/rdebug rounds <n>", Messages.get("debug.help-rounds"));
        boxKv(sender, "/rdebug info", Messages.get("debug.help-info"));
        boxKv(sender, "/rdebug join", Messages.get("debug.help-join"));

        boxSection(sender, "MAP BLOCKS");
        boxKv(sender, "/rdebug giveblocks", Messages.get("debug.help-giveblocks"));
        boxKv(sender, "/rdebug setlobby", "Установить блок лобби на вашей позиции");
        boxKv(sender, "/rdebug jumppos1/pos2/set", "Заполнить область jump-блоками");

        boxSection(sender, "CARDS");
        boxKv(sender, "/rdebug cards", Messages.get("debug.help-cards"));
        boxKv(sender, "/rdebug cards reload", Messages.get("debug.help-cards-reload"));
        boxKv(sender, "/rdebug cards test [id]", Messages.get("debug.help-cards-test"));
        boxKv(sender, "/rdebug applycard <name>", Messages.get("debug.help-applycard"));
        boxKv(sender, "/rdebug wheel on|off", Messages.get("debug.help-wheel"));

        boxSection(sender, "SCOREBOARD");
        boxKv(sender, "/rdebug tab on|off", Messages.get("debug.help-tab"));
        boxKv(sender, "/rdebug tab name <title>", Messages.get("debug.help-tab-name"));

        boxSection(sender, "ITEMS");
        boxKv(sender, "/rdebug givegun [player|@a]", Messages.get("debug.help-givegun"));
        boxKv(sender, "/rdebug giveall", Messages.get("debug.help-giveall"));

        boxSection(sender, "DEBUG");
        boxKv(sender, "/rdebug stats [player]", Messages.get("debug.help-stats"));
        boxKv(sender, "/rdebug setstat <stat> <value>", Messages.get("debug.help-setstat"));
        boxKv(sender, "/rdebug setteam <COLOR>", Messages.get("debug.help-setteam"));
        boxKv(sender, "/rdebug setlanguage <ru|en>", Messages.get("debug.help-setlanguage"));
        boxKv(sender, "/rdebug effect <type> <amp> <dur>", Messages.get("debug.help-effect"));
        boxKv(sender, "/rdebug heal [amount]", Messages.get("debug.help-heal"));
        boxKv(sender, "/rdebug spawnbomb/heal/toxic/shield", Messages.get("debug.help-spawn"));
        boxKv(sender, "/rdebug entities", Messages.get("debug.help-entities"));
        boxKv(sender, "/rdebug resetstats", Messages.get("debug.help-resetstats"));
        boxKv(sender, "/rdebug reload", Messages.get("debug.help-reload"));
        boxKv(sender, "/rdebug version", Messages.get("debug.help-version"));
        boxKv(sender, "/rdebug killround", Messages.get("debug.help-killround"));
        boxKv(sender, "/rdebug iteminfo", Messages.get("debug.help-iteminfo"));
        boxKv(sender, "/rdebug test", Messages.get("debug.help-test"));

        boxSection(sender, "PLACEHOLDERS");
        boxKv(sender, "/rdebug placeholders", Messages.get("debug.help-placeholders"));

        boxFooter(sender);
    }

    private void handlePlaceholders(CommandSender sender) {
        boxHeader(sender, Messages.get("debug.placeholders-title"));

        boxSection(sender, Messages.get("debug.ph-game-section"));
        for (RoundsPlaceholders.PlaceholderEntry ph : RoundsPlaceholders.getGamePlaceholders()) {
            boxKv(sender, "%" + ph.key() + "%", Messages.get(ph.descriptionKey()));
        }

        boxSection(sender, Messages.get("debug.ph-stats-section"));
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
            sender.sendMessage(ChatColor.RED + Messages.get("game.not-started"));
            return;
        }
        GameTeam existingTeam = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (existingTeam != null) {
            sender.sendMessage(ChatColor.GOLD + Messages.get("game.already-has-team"));
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
            plugin.getPlayerDataManager().removeActivePlayer(uuid);
            plugin.getPlayerDataManager().removePlayerData(uuid);
        } else {
            plugin.getTeamManager().joinTeam(uuid, team);
            plugin.getPlayerDataManager().trackPlayer(uuid);
            plugin.getPlayerDataManager().savePlayerFullData(uuid, team, null);
        }

        GameTeam finalTeam = plugin.getTeamManager().getPlayerTeam(uuid);
        gm.markPendingCardJoiner(uuid);
        gm.applyTeamColor(player);
        gm.buildScoreboard(player);

        if (gm.getState() == GameManager.GameState.CARDS) {
            plugin.getCardManager().openCardSelection(player, finalTeam);
            player.setInvulnerable(true);
        } else if (gm.getState() == GameManager.GameState.PLAYING) {
            player.setGameMode(GameMode.SPECTATOR);
            player.setInvulnerable(true);
            Location spawn = gm.getTeamSpawns().get(finalTeam);
            if (spawn != null) player.teleport(spawn);
        }

        String teamName = Messages.get("team." + finalTeam.name().toLowerCase());
        if (returning) {
            sender.sendMessage(ChatColor.GOLD + Messages.get("game.restored-team", finalTeam.getColor() + teamName));
        } else {
            sender.sendMessage(ChatColor.GOLD + Messages.get("game.joined-team", finalTeam.getColor() + teamName));
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
        boxSection(sender, "Teams");
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
        sender.sendMessage(ChatColor.GREEN + "Блок лобби установлен на " +
            String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
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
        sender.sendMessage(ChatColor.GREEN + "Jump pos" + posNum + " установлен на " +
            String.format("(%d, %d, %d)", pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()));
    }

    private void handleJumpSet(CommandSender sender) {
        com.rounds.blocks.BlockListener bl = plugin.getBlockListener();
        if (bl.getJumpPos1() == null || bl.getJumpPos2() == null) {
            sender.sendMessage(ChatColor.RED + "Сначала установи jumppos1 и jumppos2");
            return;
        }
        Set<org.bukkit.Location> filled = bl.fillJumpBlocks();
        sender.sendMessage(ChatColor.GREEN + "Установлено " + filled.size() + " jump-блоков");
    }

    private void handleRounds(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ChatColor.RED + Messages.get("command.usage-rounds")); return; }
        try {
            double amount = Double.parseDouble(args[1]);
            int max = plugin.getRoundsConfig().getMaxRounds();
            if (amount < 1 || amount > max) { sender.sendMessage(ChatColor.RED + Messages.get("command.amount-between", max)); return; }
            plugin.getGameManager().setRounds((int) amount);
            Bukkit.broadcastMessage(ChatColor.GOLD + Messages.get("command.rounds-set", String.format("%.0f", amount)));
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.invalid-number", args[1]));
        }
    }

    private void handleInfo(CommandSender sender) {
        GameManager gm = plugin.getGameManager();
        TeamManager tm = plugin.getTeamManager();
        boxHeader(sender, Messages.get("command.info-title"));
        boxKv(sender, Messages.get("command.version", plugin.getDescription().getVersion()));
        boxKv(sender, Messages.get("command.state", gm.getState()));
        boxKv(sender, Messages.get("command.rounds-to-win-cmd", (int) gm.getRounds()));
        boxSection(sender, "Teams");
        for (GameTeam team : GameTeam.values()) {
            String teamName = Messages.get("team." + team.name().toLowerCase());
            boxLine(sender, team.getColor(), Messages.get("command.team-info", teamName, (int) tm.getWins(team), tm.getPlayerCount(team)));
        }
        boxSection(sender, "Cards");
        boxKv(sender, Messages.get("command.cards-loaded", plugin.getCardManager().getRegistry().getAllCards().size()));
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
        if (args.length < 2) {
            if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
            plugin.getCardManager().openCardSelection(player, GameTeam.BLUE);
            sender.sendMessage(ChatColor.GREEN + Messages.get("debug.card-gui-opened"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "reload" -> handleCardsReload(sender);
            case "test" -> handleCardsTest(sender, args);
            case "giveall" -> handleGiveAll(sender);
            default -> sender.sendMessage(ChatColor.RED + Messages.get("debug.unknown-command", args[0] + " " + args[1]));
        }
    }

    private void handleCardsReload(CommandSender sender) {
        plugin.getCardManager().reload();
        sender.sendMessage(ChatColor.GREEN + Messages.get("command.cards-reloaded", plugin.getCardManager().getRegistry().getAllCards().size()));
    }

    private void handleCardsTest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
        if (args.length >= 3) {
            try {
                int cardId = Integer.parseInt(args[2]);
                var card = plugin.getCardManager().getRegistry().getCard(cardId);
                if (card == null) { sender.sendMessage(ChatColor.RED + Messages.get("command.card-not-found", cardId)); return; }
                var data = plugin.getPlayerDataManager().getData(player);
                card.apply(player, data);
                data.setCard(cardId, true);
                sender.sendMessage(ChatColor.GREEN + Messages.get("command.applied-card", card.getColoredName(Messages.getLanguage())));
            } catch (NumberFormatException e) { sender.sendMessage(ChatColor.RED + Messages.get("command.invalid-card-id")); }
        } else {
            plugin.getCardManager().openCardSelection(player, GameTeam.BLUE);
        }
    }

    private void handleGiveAll(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
        PlayerData data = plugin.getPlayerDataManager().getData(player);
        for (int i = 1; i <= 43; i++) {
            data.setCard(i, true);
        }
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.all-cards-unlocked"));
    }

    // === STATS ===

    private void handleStats(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) { sender.sendMessage(ChatColor.RED + Messages.get("debug.player-not-found", args[1])); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.specify-player")); return;
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
            case "leech" -> data.leech = value;
            case "homing" -> data.homing = value;
            case "poison_lvl" -> data.poisonLvl = value;
            case "cold_lvl" -> data.coldLvl = value;
            case "parazit" -> data.parazit = value;
            case "parazit_lvl" -> data.parazitLvl = value;
            case "hp" -> {
                data.hp = value;
                var attr = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (attr != null) attr.setBaseValue(Math.max(value, 2));
                target.setHealth(Math.min(target.getHealth(), Math.max(value, 2)));
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
            case "dark" -> data.dark = value;
            case "atks_reload" -> data.atksReload = value;
            default -> { sender.sendMessage(ChatColor.RED + Messages.get("debug.unknown-stat", stat)); return; }
        }
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.setstat-done", stat, PlayerData.round2(value), target.getName()));
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

        String lang = args[1].toLowerCase();
        if (!lang.equals("ru") && !lang.equals("en")) {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.language-invalid", lang));
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

    // === SPAWNS ===

    private void handleSpawnBomb(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
        RoundsEntities.spawnBomb(player.getLocation(), player.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.bomb-spawned"));
    }

    private void handleSpawnHeal(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
        RoundsEntities.spawnHealRing(player.getLocation(), player.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.heal-ring-spawned"));
    }

    private void handleSpawnToxic(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
        RoundsEntities.spawnToxicRing(player.getLocation(), player.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.toxic-ring-spawned"));
    }

    private void handleSpawnShield(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
        RoundsEntities.spawnBombShield(player.getLocation(), player.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.shield-bomb-spawned"));
    }

    // === MISC ===

    private void handleEntities(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
        List<Entity> nearby = player.getNearbyEntities(20, 20, 20);
        List<String> found = new ArrayList<>();
        for (Entity e : nearby) {
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            if (pdc.has(RoundsKeys.IS_BULLET, PersistentDataType.BYTE) ||
                pdc.has(RoundsKeys.IS_BOMB, PersistentDataType.BYTE) ||
                pdc.has(RoundsKeys.IS_HEAL_RING, PersistentDataType.BYTE) ||
                pdc.has(RoundsKeys.IS_TOXIC_RING, PersistentDataType.BYTE) ||
                pdc.has(RoundsKeys.IS_SHIELD_BOMB, PersistentDataType.BYTE)) {
                found.add(Messages.get("debug.entity-at", e.getType(), formatLoc(e.getLocation())));
            }
        }
        boxHeader(sender, Messages.get("debug.entities-found", found.size()));
        for (String line : found) {
            boxLine(sender, Y, line);
        }
        if (found.isEmpty()) {
            boxLine(sender, GR, "...");
        }
        boxFooter(sender);
    }

    private void handleApplyCard(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
        if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /rdebug applycard <name>"); return; }

        String nameInput = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        var card = plugin.getCardManager().getRegistry().findCardByName(nameInput);
        if (card == null) { sender.sendMessage(ChatColor.RED + Messages.get("command.card-not-found", nameInput)); return; }

        PlayerData data = plugin.getPlayerDataManager().getData(player);
        card.apply(player, data);
        data.setCard(card.getId(), true);
        sender.sendMessage(ChatColor.GREEN + Messages.get("command.applied-card", card.getColoredName(Messages.getLanguage())));
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
        Messages.reload();
        plugin.getRoundsConfig().reload();
        plugin.getCardManager().reload();
        sender.sendMessage(ChatColor.GREEN + Messages.get("command.cards-reloaded", plugin.getCardManager().getRegistry().getAllCards().size()));
    }

    private void handleVersion(CommandSender sender) {
        boxHeader(sender, Messages.get("debug.plugin-version", plugin.getDescription().getVersion()));
        boxKv(sender, Messages.get("debug.server-version", Bukkit.getVersion()));
        boxKv(sender, Messages.get("debug.online-players", Bukkit.getOnlinePlayers().size()));
        boxKv(sender, Messages.get("command.cards-loaded", plugin.getCardManager().getRegistry().getAllCards().size()));
        boxKv(sender, Messages.get("debug.game-state"), String.valueOf(plugin.getGameManager().getState()));
        boxFooter(sender);
    }

    private void handleKillRound(CommandSender sender) {
        GameManager gm = plugin.getGameManager();
        if (gm.getState() != GameManager.GameState.PLAYING) {
            sender.sendMessage(ChatColor.RED + Messages.get("debug.game-not-playing")); return;
        }
        Player killer = sender instanceof Player p ? p : null;
        int killed = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (killer != null && target.equals(killer)) continue;
            GameTeam targetTeam = plugin.getTeamManager().getPlayerTeam(target.getUniqueId());
            GameTeam killerTeam = killer != null ? plugin.getTeamManager().getPlayerTeam(killer.getUniqueId()) : null;
            if (targetTeam != null && targetTeam != killerTeam) {
                target.setHealth(0);
                killed++;
            }
        }
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.killed-enemies", killed));
    }

    private void handleItemInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) { sender.sendMessage(ChatColor.RED + Messages.get("debug.holding-nothing")); return; }

        ItemMeta meta = item.getItemMeta();
        boxHeader(sender, Messages.get("debug.item-title"));
        boxKv(sender, Messages.get("debug.material", item.getType()));
        boxKv(sender, Messages.get("debug.amount", item.getAmount()));
        boxKv(sender, Messages.get("debug.display", (meta != null && meta.hasDisplayName() ? meta.getDisplayName() : Messages.get("debug.none"))));

        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (!pdc.getKeys().isEmpty()) {
                boxSection(sender, Messages.get("debug.pdc-keys"));
                for (org.bukkit.NamespacedKey key : pdc.getKeys()) {
                    boxKv(sender, key.toString(), pdc.get(key, PersistentDataType.STRING));
                }
            }
        }
        boxFooter(sender);
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
        target.setGameMode(GameMode.SURVIVAL);
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
        for (int i = 1; i <= 43; i++) {
            if (data.getCard(i)) count++;
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
        "help", "start", "stop", "status", "rounds", "info", "test",
        "givegun", "giveall", "cards", "giveblocks", "join",
        "stats", "setstat", "setteam", "setlanguage", "effect", "heal",
        "spawnbomb", "spawnheal", "spawntoxic", "spawnshield",
        "entities", "applycard", "resetstats", "reload",
        "version", "killround", "iteminfo", "setlobby", "jumppos1", "jumppos2", "jumpset", "wheel", "tab"
    );
    private static final List<String> TEAM_COLORS = Arrays.asList("BLUE", "RED", "YELLOW", "GREEN");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("rounds.admin")) return Collections.emptyList();

        String input = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            return filterStartsWith(SUBCOMMANDS, input);
        }

        return switch (args[0].toLowerCase()) {
            case "givegun", "giveblocks" -> {
                List<String> list = new ArrayList<>();
                list.add("@a");
                Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
                yield filterStartsWith(list, input);
            }
            case "stats" -> {
                List<String> list = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(p -> list.add(p.getName()));
                yield filterStartsWith(list, input);
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
                if (args.length == 2) yield filterStartsWith(Arrays.asList("ru", "en"), input);
                yield Collections.emptyList();
            }
            case "effect" -> {
                if (args.length == 2) yield filterStartsWith(EFFECTS, input);
                if (args.length == 3) yield filterStartsWith(Arrays.asList("0", "1", "2", "3", "4"), input);
                if (args.length == 4) yield filterStartsWith(Arrays.asList("10", "20", "40", "60", "100", "200"), input);
                yield Collections.emptyList();
            }
            case "applycard" -> {
                List<String> cards = plugin.getCardManager().getRegistry().getCardNameSuggestions();
                yield filterStartsWith(cards, input);
            }
            case "cards" -> {
                if (args.length == 2) yield filterStartsWith(Arrays.asList("reload", "test", "giveall"), input);
                if (args.length == 3 && args[1].equalsIgnoreCase("test")) {
                    List<String> list = new ArrayList<>();
                    for (int i = 1; i <= 43; i++) list.add(String.valueOf(i));
                    yield filterStartsWith(list, input);
                }
                yield Collections.emptyList();
            }
            case "rounds" -> filterStartsWith(Arrays.asList("1", "5", "10", "15", "20"), input);
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

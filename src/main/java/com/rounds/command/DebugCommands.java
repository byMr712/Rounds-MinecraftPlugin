package com.rounds.command;

import com.rounds.RoundsKeys;
import com.rounds.RoundsPlugin;
import com.rounds.entity.RoundsEntities;
import com.rounds.game.GameManager;
import com.rounds.item.GunItem;
import com.rounds.player.PlayerData;
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
        "leech", "homing", "poison_lvl", "cold_lvl", "parazit", "hp",
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

    public static void register(RoundsPlugin plugin) {
        DebugCommands handler = new DebugCommands(plugin);
        plugin.getCommand("rdebug").setExecutor(handler);
        plugin.getCommand("rdebug").setTabCompleter(handler);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rounds.admin")) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "help" -> sendHelp(sender);
            case "start" -> plugin.getGameManager().startGame();
            case "status", "state" -> handleState(sender);
            case "stop" -> handleStop(sender);
            case "rounds" -> handleRounds(sender, args);
            case "info" -> handleInfo(sender);
            case "test" -> sender.sendMessage(ChatColor.GREEN + Messages.get("command.plugin-working"));
            case "stats" -> handleStats(sender, args);
            case "givegun" -> handleGiveGun(sender, args);
            case "giveall" -> handleGiveAll(sender);
            case "setstat" -> handleSetStat(sender, args);
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
            case "wheel" -> handleWheel(sender, args);
            default -> sender.sendMessage(ChatColor.RED + Messages.get("debug.unknown-command", args[0]));
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== " + Messages.get("debug.title") + " ===");
        String[][] help = {
            {"/rdebug start", "debug.help-start"},
            {"/rdebug stop", "debug.help-stop"},
            {"/rdebug status", "debug.help-status"},
            {"/rdebug rounds <число>", "debug.help-rounds"},
            {"/rdebug info", "debug.help-info"},
            {"/rdebug test", "debug.help-test"},
            {"/rdebug givegun [игрок|@a]", "debug.help-givegun"},
            {"/rdebug giveall", "debug.help-giveall"},
            {"/rdebug cards reload", "debug.help-cards-reload"},
            {"/rdebug cards test [id]", "debug.help-cards-test"},
            {"/rdebug giveblocks", "debug.help-giveblocks"},
            {"/rdebug stats [игрок]", "debug.help-stats"},
            {"/rdebug setstat <стат> <значение>", "debug.help-setstat"},
            {"/rdebug effect <тип> <ур> <длит>", "debug.help-effect"},
            {"/rdebug heal [кол-во]", "debug.help-heal"},
            {"/rdebug spawnbomb/heal/toxic/shield", "debug.help-spawn"},
            {"/rdebug entities", "debug.help-entities"},
            {"/rdebug applycard <name>", "debug.help-applycard"},
            {"/rdebug resetstats", "debug.help-resetstats"},
            {"/rdebug reload", "debug.help-reload"},
            {"/rdebug version", "debug.help-version"},
            {"/rdebug killround", "debug.help-killround"},
            {"/rdebug iteminfo", "debug.help-iteminfo"},
            {"/rdebug wheel on|off", "Вкл/выкл прокрутку карточек"}
        };
        for (String[] h : help) {
            sender.sendMessage(ChatColor.YELLOW + h[0] + ChatColor.GRAY + " - " + Messages.get(h[1]));
        }
    }

    // === GAME MANAGEMENT ===

    private void handleState(CommandSender sender) {
        GameManager gm = plugin.getGameManager();
        TeamManager tm = plugin.getTeamManager();
        sender.sendMessage(ChatColor.GOLD + "=== " + Messages.get("debug.state-title") + " ===");
        sender.sendMessage(ChatColor.YELLOW + Messages.get("command.state", gm.getState()));
        sender.sendMessage(ChatColor.YELLOW + Messages.get("command.rounds-to-win-cmd", (int) gm.getRounds()));
        sender.sendMessage(ChatColor.YELLOW + Messages.get("command.current-round", (int) gm.getCurrentRound()));
        for (GameTeam team : GameTeam.values()) {
            String teamName = Messages.get("team." + team.name().toLowerCase());
            sender.sendMessage(team.getColor() + Messages.get("command.team-info", teamName, (int) tm.getWins(team), tm.getPlayerCount(team)));
        }
    }

    private void handleStop(CommandSender sender) {
        GameManager gm = plugin.getGameManager();
        gm.stopGame();
        plugin.getCardManager().resetAllCards();
        plugin.getTeamManager().clearAll();
        plugin.getPlayerDataManager().clearActivePlayers();
        gm.getStateManager().clear();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.getInventory().clear();
            var attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) attr.setBaseValue(20);
            p.setHealth(20);
            p.setFoodLevel(20);
            p.setGameMode(GameMode.ADVENTURE);
            gm.clearCardEffects(p);
        }
        sender.sendMessage(ChatColor.YELLOW + Messages.get("command.game-stopped"));
    }

    private void handleRounds(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ChatColor.RED + Messages.get("command.usage-rounds")); return; }
        try {
            double amount = Double.parseDouble(args[1]);
            int max = plugin.getRoundsConfig().getMaxRounds();
            if (amount < 1 || amount > max) { sender.sendMessage(ChatColor.RED + Messages.get("command.amount-between", max)); return; }
            plugin.getGameManager().setRounds(amount);
            Bukkit.broadcastMessage(ChatColor.GOLD + Messages.get("command.rounds-set", String.format("%.0f", amount)));
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.invalid-number", args[1]));
        }
    }

    private void handleInfo(CommandSender sender) {
        GameManager gm = plugin.getGameManager();
        TeamManager tm = plugin.getTeamManager();
        sender.sendMessage(ChatColor.GOLD + "=== " + Messages.get("command.info-title") + " ===");
        sender.sendMessage(ChatColor.YELLOW + Messages.get("command.version", plugin.getDescription().getVersion()));
        sender.sendMessage(ChatColor.YELLOW + Messages.get("command.state", gm.getState()));
        sender.sendMessage(ChatColor.YELLOW + Messages.get("command.rounds-to-win-cmd", (int) gm.getRounds()));
        for (GameTeam team : GameTeam.values()) {
            String teamName = Messages.get("team." + team.name().toLowerCase());
            sender.sendMessage(team.getColor() + Messages.get("command.team-info", teamName, (int) tm.getWins(team), tm.getPlayerCount(team)));
        }
        sender.sendMessage(ChatColor.YELLOW + Messages.get("command.cards-loaded", plugin.getCardManager().getRegistry().getAllCards().size()));
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
        sender.sendMessage(ChatColor.GOLD + "=== " + Messages.get("debug.stats-title", target.getName()) + " ===");
        sender.sendMessage(ChatColor.YELLOW + Messages.get("debug.team-label", getTeamDisplay(target)));
        sender.sendMessage(ChatColor.YELLOW + Messages.get("debug.cards-label", countCards(data)));
        sender.sendMessage(ChatColor.AQUA + Messages.get("debug.combat-section"));
        sender.sendMessage(ChatColor.WHITE + "  dmg=" + data.dmg + " atks=" + data.atks + " atk_speed=" + data.atkSpeed + " atkr=" + data.atkr);
        sender.sendMessage(ChatColor.WHITE + "  ammo=" + data.ammo + " atks_reload=" + data.atksReload + " bullets=" + data.bullets);
        sender.sendMessage(ChatColor.WHITE + "  bullet_speed=" + data.bulletSpeed + " bounce=" + data.bouncePl + " homing=" + data.homing);
        sender.sendMessage(ChatColor.WHITE + "  big_bullet=" + data.bigBullet);
        sender.sendMessage(ChatColor.AQUA + Messages.get("debug.effects-section"));
        sender.sendMessage(ChatColor.WHITE + "  cold=" + data.cold + " cold_lvl=" + data.coldLvl);
        sender.sendMessage(ChatColor.WHITE + "  poison=" + data.poison + " poison_lvl=" + data.poisonLvl);
        sender.sendMessage(ChatColor.WHITE + "  parazit=" + data.parazit + " parazit_lvl=" + data.parazitLvl);
        sender.sendMessage(ChatColor.WHITE + "  leech=" + data.leech + " truster=" + data.trusterLvl);
        sender.sendMessage(ChatColor.AQUA + Messages.get("debug.special-section"));
        sender.sendMessage(ChatColor.WHITE + "  hp=" + data.hp + " grow=" + data.grow);
        sender.sendMessage(ChatColor.WHITE + "  empower=" + data.empower + " empower_charge=" + data.empowerCharge);
        sender.sendMessage(ChatColor.WHITE + "  dark_strength=" + data.darkStrength + " dark=" + data.dark);
        sender.sendMessage(ChatColor.WHITE + "  bomb_bullet=" + data.bombBullet + " bomb_on_block=" + data.bombOnBlock);
        sender.sendMessage(ChatColor.AQUA + Messages.get("debug.shield-section"));
        sender.sendMessage(ChatColor.WHITE + "  active=" + data.shieldActive + " hp=" + data.shieldHp + " cd=" + data.shieldCooldown);
        sender.sendMessage(ChatColor.AQUA + Messages.get("debug.misc-section"));
        sender.sendMessage(ChatColor.WHITE + "  card_uses=" + data.cardUses + " rare_card=" + data.rareCard);
    }

    private void handleSetStat(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + Messages.get("command.must-be-player")); return; }
        if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Usage: /rdebug setstat <stat> <value>"); return; }

        String stat = args[1].toLowerCase();
        double value;
        try {
            value = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + Messages.get("command.invalid-number", args[2])); return;
        }

        PlayerData data = plugin.getPlayerDataManager().getData(player);
        switch (stat) {
            case "dmg" -> data.dmg = value;
            case "atks" -> data.atks = value;
            case "atk_speed" -> data.atkSpeed = value;
            case "atkr" -> data.atkr = value;
            case "bounce" -> { data.bouncePl = value; data.bouncePlayer = value; }
            case "ammo" -> data.ammo = value;
            case "bullets" -> data.bullets = value;
            case "cold" -> data.cold = value;
            case "poison" -> data.poison = value;
            case "leech" -> data.leech = value;
            case "homing" -> data.homing = value;
            case "poison_lvl" -> data.poisonLvl = value;
            case "cold_lvl" -> data.coldLvl = value;
            case "parazit" -> data.parazit = value;
            case "parazit_lvl" -> data.parazitLvl = value;
            case "hp" -> {
                data.hp = value;
                var attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (attr != null) attr.setBaseValue(Math.max(value, 2));
                player.setHealth(Math.min(player.getHealth(), Math.max(value, 2)));
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
        sender.sendMessage(ChatColor.GREEN + Messages.get("debug.stat-set", stat, value));
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
        double maxHP = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
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
        int count = 0;
        for (Entity e : nearby) {
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            if (pdc.has(RoundsKeys.IS_BULLET, PersistentDataType.BYTE) ||
                pdc.has(RoundsKeys.IS_BOMB, PersistentDataType.BYTE) ||
                pdc.has(RoundsKeys.IS_HEAL_RING, PersistentDataType.BYTE) ||
                pdc.has(RoundsKeys.IS_TOXIC_RING, PersistentDataType.BYTE) ||
                pdc.has(RoundsKeys.IS_SHIELD_BOMB, PersistentDataType.BYTE)) {
                sender.sendMessage(ChatColor.YELLOW + "  " + e.getType() + " at " + formatLoc(e.getLocation()));
                count++;
            }
        }
        sender.sendMessage(ChatColor.GOLD + Messages.get("debug.entities-found", count));
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
        plugin.reloadConfig();
        Messages.reload();
        plugin.getRoundsConfig().reload();
        plugin.getCardManager().reload();
        sender.sendMessage(ChatColor.GREEN + Messages.get("command.cards-reloaded", plugin.getCardManager().getRegistry().getAllCards().size()));
    }

    private void handleVersion(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + Messages.get("debug.plugin-version", plugin.getDescription().getVersion()));
        sender.sendMessage(ChatColor.YELLOW + Messages.get("debug.server-version", Bukkit.getVersion()));
        sender.sendMessage(ChatColor.YELLOW + Messages.get("debug.online-players", Bukkit.getOnlinePlayers().size()));
        sender.sendMessage(ChatColor.YELLOW + Messages.get("command.cards-loaded", plugin.getCardManager().getRegistry().getAllCards().size()));
        sender.sendMessage(ChatColor.YELLOW + Messages.get("debug.game-state") + ": " + plugin.getGameManager().getState());
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
        sender.sendMessage(ChatColor.GOLD + "=== " + Messages.get("debug.item-title") + " ===");
        sender.sendMessage(ChatColor.YELLOW + Messages.get("debug.material", item.getType()));
        sender.sendMessage(ChatColor.YELLOW + Messages.get("debug.amount", item.getAmount()));
        sender.sendMessage(ChatColor.YELLOW + Messages.get("debug.display", (meta != null && meta.hasDisplayName() ? meta.getDisplayName() : Messages.get("debug.none"))));

        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            sender.sendMessage(ChatColor.YELLOW + Messages.get("debug.pdc-keys"));
            for (org.bukkit.NamespacedKey key : pdc.getKeys()) {
                sender.sendMessage(ChatColor.WHITE + "  " + key + " = " + pdc.get(key, PersistentDataType.STRING));
            }
        }
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
            sender.sendMessage(ChatColor.RED + "Использование: /rdebug wheel on|off");
            return;
        }
        GameManager gm = plugin.getGameManager();
        switch (args[1].toLowerCase()) {
            case "on" -> {
                gm.setWheelEnabled(true);
                sender.sendMessage(ChatColor.GREEN + "Прокрутка карточек ВКЛЮЧЕНА");
            }
            case "off" -> {
                gm.setWheelEnabled(false);
                sender.sendMessage(ChatColor.RED + "Прокрутка карточек ВЫКЛЮЧЕНА");
            }
            default -> sender.sendMessage(ChatColor.RED + "Использование: /rdebug wheel on|off");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("rounds.admin")) return completions;

        if (args.length == 1) {
            completions.addAll(Arrays.asList("help", "start", "stop", "status",
                "rounds", "info", "test", "givegun", "giveall", "cards", "giveblocks",
                "stats", "setstat", "effect", "heal", "spawnbomb", "spawnheal", "spawntoxic", "spawnshield",
                "entities", "applycard", "resetstats", "reload", "version", "killround", "iteminfo", "wheel"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "givegun", "giveblocks" -> {
                    completions.add("@a");
                    Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                }
                case "stats" -> Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                case "setstat" -> completions.addAll(STATS);
                case "effect" -> completions.addAll(EFFECTS);
                case "applycard" -> completions.addAll(plugin.getCardManager().getRegistry().getCardNameSuggestions());
                case "cards" -> completions.addAll(Arrays.asList("reload", "test", "giveall"));
                case "rounds" -> completions.addAll(Arrays.asList("1", "5", "10", "15", "20"));
                case "wheel" -> completions.addAll(Arrays.asList("on", "off"));
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("cards") && args[1].equalsIgnoreCase("test")) {
                for (int i = 1; i <= 43; i++) completions.add(String.valueOf(i));
            } else if (args[0].equalsIgnoreCase("effect")) {
                completions.addAll(Arrays.asList("1", "2", "3", "4"));
            }
        }

        return completions;
    }
}

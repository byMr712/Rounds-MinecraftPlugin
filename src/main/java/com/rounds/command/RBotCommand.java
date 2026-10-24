package com.rounds.command;

import com.rounds.bot.BotManager;
import com.rounds.bot.BotStrategy;
import com.rounds.bot.FakeBot;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class RBotCommand implements CommandExecutor, TabCompleter {

    private final BotManager botManager;

    private static final List<String> ROOT_ACTIONS = Arrays.asList("spawn", "despawn", "list", "level");
    private static final List<String> BOT_ACTIONS = Arrays.asList("level", "strategy", "pick", "shoot", "block");
    private static final List<String> TEAMS = Arrays.asList("BLUE", "RED", "YELLOW", "GREEN", "NONE");
    private static final List<String> STRATEGIES = Arrays.asList("aggressive", "defensive", "balanced");
    private static final List<String> LEVELS = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");

    public RBotCommand(BotManager botManager) {
        this.botManager = botManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rounds.admin") && !botManager.getPlugin().getRoundsConfig().isFreeplay()) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to manage bots.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String first = args[0].toLowerCase();

        switch (first) {
            case "spawn" -> {
                Location loc = (sender instanceof Player p) ? p.getLocation() : null;

                // 1. /rbot spawn count <int>
                if (args.length >= 3 && args[1].equalsIgnoreCase("count")) {
                    try {
                        int count = Integer.parseInt(args[2]);
                        if (count <= 0) {
                            sender.sendMessage(ChatColor.RED + "Count must be greater than 0.");
                            return true;
                        }
                        List<FakeBot> spawned = botManager.spawnBots(count, loc);
                        sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Spawned " + spawned.size() + " bots with balanced teams.");
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Invalid number: " + args[2]);
                    }
                    return true;
                }

                // 2. /rbot spawn (no name or team specified -> auto generate)
                if (args.length == 1) {
                    FakeBot bot = botManager.spawnBot(null, null, loc);
                    if (bot != null) {
                        sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Spawned bot " + ChatColor.AQUA + bot.getName() +
                                ChatColor.GREEN + " on team " + ChatColor.GOLD + bot.getTeamName() +
                                ChatColor.GRAY + " [Level: " + bot.getLevel() + " (" + (bot.getLevel() * 10) + "%)]");
                    }
                    return true;
                }

                // 3. /rbot spawn <count_number>
                if (args.length == 2 && args[1].matches("\\d+")) {
                    int count = Integer.parseInt(args[1]);
                    List<FakeBot> spawned = botManager.spawnBots(count, loc);
                    sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Spawned " + spawned.size() + " bots with balanced teams.");
                    return true;
                }

                // 4. /rbot spawn <name> [team] [1..10]
                String name = args[1];
                String team = (args.length >= 3 && !args[2].equalsIgnoreCase("auto"))
                        ? args[2].toUpperCase()
                        : botManager.pickBestTeam();

                Integer initialLevel = null;
                if (args.length >= 4) {
                    try {
                        initialLevel = Integer.parseInt(args[3]);
                    } catch (NumberFormatException ignored) {}
                }

                FakeBot bot = botManager.spawnBot(name, team, loc, initialLevel);
                if (bot == null) {
                    sender.sendMessage(ChatColor.RED + "[RoundsBot] Invalid bot name: " + name + " (1-16 chars [a-zA-Z0-9_])");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Spawned bot " + ChatColor.AQUA + bot.getName() +
                        ChatColor.GREEN + " on team " + ChatColor.GOLD + bot.getTeamName() +
                        ChatColor.GRAY + " [Level: " + bot.getLevel() + " (" + (bot.getLevel() * 10) + "%)]");
                return true;
            }
            case "despawn", "remove", "kill" -> {
                // 1. /rbot despawn all
                if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
                    int removed = botManager.despawnAll();
                    sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Despawned all active bots (total: " + removed + ")");
                    return true;
                }

                // 2. /rbot despawn count <int>
                if (args.length >= 3 && args[1].equalsIgnoreCase("count")) {
                    try {
                        int count = Integer.parseInt(args[2]);
                        int removed = botManager.despawnBots(count);
                        sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Despawned " + removed + " bots (prioritizing largest teams).");
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Invalid number: " + args[2]);
                    }
                    return true;
                }

                // 3. /rbot despawn <count_number>
                if (args.length >= 2 && args[1].matches("\\d+")) {
                    int count = Integer.parseInt(args[1]);
                    int removed = botManager.despawnBots(count);
                    sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Despawned " + removed + " bots (prioritizing largest teams).");
                    return true;
                }

                // 4. /rbot despawn <name>
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rbot despawn <name|all|count <int>>");
                    return true;
                }
                String name = args[1];
                boolean ok = botManager.despawnBot(name);
                if (ok) {
                    sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Despawned bot " + name);
                } else {
                    sender.sendMessage(ChatColor.RED + "[RoundsBot] Bot not found: " + name);
                }
                return true;
            }
            case "list" -> {
                Collection<FakeBot> bots = botManager.getBots();
                if (bots.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "[RoundsBot] No active bots.");
                    return true;
                }
                sender.sendMessage(ChatColor.GOLD + "=== Active Bots (" + bots.size() + ") ===");
                for (FakeBot b : bots) {
                    Player p = b.getBukkitPlayer();
                    sender.sendMessage(ChatColor.AQUA + " - " + b.getName() +
                            ChatColor.GRAY + " [HP: " + String.format("%.1f", p.getHealth()) +
                            ", Lvl: " + b.getLevel() + " (" + (b.getLevel() * 10) + "%)]");
                }
                return true;
            }
            case "level", "lvl", "difficulty" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rbot level <botName> [1..10]");
                    return true;
                }
                FakeBot bot = botManager.getBot(args[1]);
                if (bot == null) {
                    sender.sendMessage(ChatColor.RED + "[RoundsBot] Bot not found: " + args[1]);
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.GOLD + "[RoundsBot] " + bot.getName() + " difficulty: Level " +
                            bot.getLevel() + " (" + (bot.getLevel() * 10) + "%)");
                    return true;
                }
                try {
                    int lvl = Integer.parseInt(args[2]);
                    if (lvl < 1 || lvl > 10) {
                        sender.sendMessage(ChatColor.RED + "Difficulty level must be between 1 and 10.");
                        return true;
                    }
                    bot.setLevel(lvl);
                    sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Set " + bot.getName() + " difficulty to Level " +
                            lvl + " (" + (lvl * 10) + "%)");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid number: " + args[2]);
                }
                return true;
            }
            default -> {
                // Check if first arg is an active bot name
                FakeBot bot = botManager.getBot(first);
                if (bot != null) {
                    return handleBotAction(sender, bot, args);
                }
                sendHelp(sender);
                return true;
            }
        }
    }

    private boolean handleBotAction(CommandSender sender, FakeBot bot, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rbot " + bot.getName() + " <strategy|pick|shoot|block> [args]");
            return true;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "level", "lvl", "difficulty" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.GOLD + "[RoundsBot] " + bot.getName() + " is Difficulty Level " +
                            bot.getLevel() + " (" + (bot.getLevel() * 10) + "%)");
                    return true;
                }
                try {
                    int lvl = Integer.parseInt(args[2]);
                    if (lvl < 1 || lvl > 10) {
                        sender.sendMessage(ChatColor.RED + "Difficulty level must be between 1 and 10.");
                        return true;
                    }
                    bot.setLevel(lvl);
                    sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Set " + bot.getName() + " difficulty to Level " +
                            lvl + " (" + (lvl * 10) + "%)");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid level number: " + args[2]);
                }
            }
            case "strategy", "strat" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rbot " + bot.getName() + " strategy <aggressive|defensive|balanced>");
                    return true;
                }
                BotStrategy strategy = BotStrategy.fromString(args[2]);
                bot.setStrategy(strategy);
                sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Set " + bot.getName() + " strategy to " + strategy.name());
            }
            case "pick", "pickcard" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rbot " + bot.getName() + " pick <0..4>");
                    return true;
                }
                try {
                    int slot = Integer.parseInt(args[2]);
                    if (slot < 0 || slot > 4) {
                        sender.sendMessage(ChatColor.RED + "Slot index must be 0..4");
                        return true;
                    }
                    bot.pickCard(slot);
                    sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Bot " + bot.getName() + " picking card slot " + slot);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid number: " + args[2]);
                }
            }
            case "shoot" -> {
                bot.shoot();
                sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Bot " + bot.getName() + " shot weapon.");
            }
            case "block", "shield" -> {
                bot.block();
                sender.sendMessage(ChatColor.GREEN + "[RoundsBot] Bot " + bot.getName() + " raised shield.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown bot action: " + action);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== RoundsBot Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/rbot spawn [name] [team] [1..10] " + ChatColor.GRAY + "- Spawn bot (auto name/team if omitted)");
        sender.sendMessage(ChatColor.YELLOW + "/rbot spawn count <int> " + ChatColor.GRAY + "- Spawn multiple bots with balanced teams");
        sender.sendMessage(ChatColor.YELLOW + "/rbot despawn <name> " + ChatColor.GRAY + "- Remove a fake player");
        sender.sendMessage(ChatColor.YELLOW + "/rbot despawn count <int> " + ChatColor.GRAY + "- Remove bots (prioritizing largest teams)");
        sender.sendMessage(ChatColor.YELLOW + "/rbot despawn all " + ChatColor.GRAY + "- Remove all fake players");
        sender.sendMessage(ChatColor.YELLOW + "/rbot list " + ChatColor.GRAY + "- List active bots & difficulty levels");
        sender.sendMessage(ChatColor.YELLOW + "/rbot <name> level [1..10] " + ChatColor.GRAY + "- Set/view bot difficulty level (1..10)");
        sender.sendMessage(ChatColor.YELLOW + "/rbot <name> pick <0..4> " + ChatColor.GRAY + "- Pick draft card slot");
        sender.sendMessage(ChatColor.YELLOW + "/rbot <name> shoot " + ChatColor.GRAY + "- Force shoot");
        sender.sendMessage(ChatColor.YELLOW + "/rbot <name> block " + ChatColor.GRAY + "- Force shield block");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("rounds.admin") && !botManager.getPlugin().getRoundsConfig().isFreeplay()) {
            return Collections.emptyList();
        }

        String current = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            List<String> list = new ArrayList<>(ROOT_ACTIONS);
            botManager.getBots().forEach(b -> list.add(b.getName()));
            return filterStartsWith(list, current);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("spawn")) {
                List<String> list = new ArrayList<>(Arrays.asList("count", "auto", "bot1", "bot2"));
                return filterStartsWith(list, current);
            }
            if (args[0].equalsIgnoreCase("despawn")) {
                List<String> list = new ArrayList<>(Arrays.asList("all", "count"));
                botManager.getBots().forEach(b -> list.add(b.getName()));
                return filterStartsWith(list, current);
            }
            if (args[0].equalsIgnoreCase("level")) {
                List<String> botNames = botManager.getBots().stream().map(FakeBot::getName).collect(Collectors.toList());
                return filterStartsWith(botNames, current);
            }
            FakeBot bot = botManager.getBot(args[0]);
            if (bot != null) {
                return filterStartsWith(BOT_ACTIONS, current);
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("spawn")) {
                if (args[1].equalsIgnoreCase("count")) {
                    return filterStartsWith(Arrays.asList("1", "2", "3", "4", "5", "8"), current);
                }
                return filterStartsWith(TEAMS, current);
            }
            if (args[0].equalsIgnoreCase("despawn") && args[1].equalsIgnoreCase("count")) {
                return filterStartsWith(Arrays.asList("1", "2", "3", "4", "5", "8"), current);
            }
            if (args[0].equalsIgnoreCase("level")) {
                return filterStartsWith(LEVELS, current);
            }
            FakeBot bot = botManager.getBot(args[0]);
            if (bot != null) {
                if (args[1].equalsIgnoreCase("level")) {
                    return filterStartsWith(LEVELS, current);
                }
                if (args[1].equalsIgnoreCase("strategy")) {
                    return filterStartsWith(STRATEGIES, current);
                }
                if (args[1].equalsIgnoreCase("pick")) {
                    return filterStartsWith(Arrays.asList("0", "1", "2", "3", "4"), current);
                }
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("spawn")) {
                return filterStartsWith(LEVELS, current);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterStartsWith(Collection<String> items, String prefix) {
        List<String> result = new ArrayList<>();
        for (String item : items) {
            if (item.toLowerCase().startsWith(prefix)) {
                result.add(item);
            }
        }
        return result;
    }
}

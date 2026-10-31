package com.rounds.metrics;

import com.rounds.RoundsPlugin;
import com.rounds.bot.BotStrategy;
import com.rounds.bot.FakeBot;
import com.rounds.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Rounds plugin analytics integration for bStats.
 * Registers plugin-specific charts for match states, bot AI analytics, languages and feature usage.
 */
public final class RoundsMetrics {

    public static final int DEFAULT_PLUGIN_ID = 35041;
    private static Metrics metrics;

    private RoundsMetrics() {}

    /**
     * Initializes bStats metrics and all custom charts.
     *
     * @param plugin main plugin instance
     */
    public static void init(RoundsPlugin plugin) {
        if (plugin == null) return;

        boolean enabled = plugin.getConfig().getBoolean("bstats.enabled", true);
        if (!enabled) {
            plugin.getLogger().info("bStats metrics disabled in config.yml");
            return;
        }

        int pluginId = plugin.getConfig().getInt("bstats.plugin-id", DEFAULT_PLUGIN_ID);

        try {
            metrics = new Metrics(plugin, pluginId);

            // 1. Used Language
            metrics.addCustomChart(new Metrics.SimplePie("used_language", () -> {
                String lang = Messages.getLanguage();
                return (lang != null && !lang.isBlank()) ? lang : "EN_en";
            }));

            // 2. Real Players Count (excluding fake bots)
            metrics.addCustomChart(new Metrics.SingleLineChart("real_players_count", () -> {
                return (int) Bukkit.getOnlinePlayers().stream()
                        .filter(p -> plugin.getBotManager() == null || !plugin.getBotManager().isBot(p))
                        .count();
            }));

            // 3. Active Bots Count
            metrics.addCustomChart(new Metrics.SingleLineChart("active_bots_count", () -> {
                return plugin.getBotManager() != null ? plugin.getBotManager().getBots().size() : 0;
            }));

            // 4. Bots in Game Status (Yes / No)
            metrics.addCustomChart(new Metrics.SimplePie("bots_in_game", () -> {
                return (plugin.getBotManager() != null && !plugin.getBotManager().getBots().isEmpty())
                        ? "Active Bots"
                        : "No Bots";
            }));

            // 5. Bot AI Strategies distribution
            metrics.addCustomChart(new Metrics.AdvancedPie("bot_strategies", () -> {
                Map<String, Integer> map = new HashMap<>();
                if (plugin.getBotManager() != null) {
                    for (FakeBot bot : plugin.getBotManager().getBots()) {
                        BotStrategy strategy = bot.getStrategy();
                        String name = (strategy != null) ? strategy.name() : "UNKNOWN";
                        map.put(name, map.getOrDefault(name, 0) + 1);
                    }
                }
                return map;
            }));

            // 6. Bot Difficulty Level Tiers
            metrics.addCustomChart(new Metrics.AdvancedPie("bot_difficulty_tiers", () -> {
                Map<String, Integer> map = new HashMap<>();
                if (plugin.getBotManager() != null) {
                    for (FakeBot bot : plugin.getBotManager().getBots()) {
                        int lvl = bot.getLevel();
                        String tier;
                        if (lvl <= 3) {
                            tier = "Novice (Lvl 1-3)";
                        } else if (lvl <= 6) {
                            tier = "Intermediate (Lvl 4-6)";
                        } else if (lvl <= 8) {
                            tier = "Advanced (Lvl 7-8)";
                        } else {
                            tier = "Nightmare (Lvl 9-10)";
                        }
                        map.put(tier, map.getOrDefault(tier, 0) + 1);
                    }
                }
                return map;
            }));

            // 7. Match Game State
            metrics.addCustomChart(new Metrics.SimplePie("match_game_state", () -> {
                if (plugin.getGameManager() == null) return "NOT_INITIALIZED";
                return plugin.getGameManager().getState().name();
            }));

            // 8. NMS Bridge Status / Server Version
            metrics.addCustomChart(new Metrics.SimplePie("nms_bridge_status", () -> {
                if (plugin.getNmsBridge() == null) {
                    return "Disabled / Unsupported";
                }
                return "Active (" + Bukkit.getMinecraftVersion() + ")";
            }));

            // 9. HUD Engine Screen Indicators
            metrics.addCustomChart(new Metrics.SimplePie("hud_overlay_enabled", () -> {
                return plugin.getRoundsConfig().isScreenIndicatorsEnabled() ? "Enabled" : "Disabled";
            }));

            // 10. Built-in Scoreboard (considers TAB override: disabled if TAB is present and active)
            metrics.addCustomChart(new Metrics.SimplePie("builtin_scoreboard_enabled", () -> {
                if (plugin.getGameManager() != null) {
                    return plugin.getGameManager().shouldShowBuiltinScoreboard() ? "Enabled" : "Disabled";
                }
                if (!plugin.getRoundsConfig().isBuiltinScoreboard()) return "Disabled";
                org.bukkit.plugin.Plugin tab = Bukkit.getPluginManager().getPlugin("TAB");
                if (tab != null && tab.isEnabled()) return "Disabled";
                return "Enabled";
            }));

            // 11. Auto-Death Reaping Mechanic
            metrics.addCustomChart(new Metrics.SimplePie("auto_death_enabled", () -> {
                return plugin.getRoundsConfig().isAutoDeathEnabled() ? "Enabled" : "Disabled";
            }));

            // 12. Configured Default Rounds To Win
            metrics.addCustomChart(new Metrics.SimplePie("rounds_to_win", () -> {
                return plugin.getRoundsConfig().getDefaultRounds() + " Rounds";
            }));

            // 13. Active Enabled Teams Count
            metrics.addCustomChart(new Metrics.SimplePie("enabled_teams_mode", () -> {
                int count = plugin.getRoundsConfig().getEnabledTeams().size();
                return count + " Teams Mode";
            }));

            // 14. Total Registered Cards
            metrics.addCustomChart(new Metrics.SingleLineChart("registered_cards_count", () -> {
                if (plugin.getCardManager() == null || plugin.getCardManager().getRegistry() == null) return 0;
                return plugin.getCardManager().getRegistry().getAllCards().size();
            }));

            plugin.getLogger().info("bStats metrics initialized (serviceId: " + pluginId + ")");
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to initialize bStats metrics: " + t.getMessage());
        }
    }

    /**
     * Shuts down metrics scheduler when plugin is disabled.
     */
    public static void shutdown() {
        if (metrics != null) {
            try {
                metrics.shutdown();
            } catch (Throwable ignored) {}
            metrics = null;
        }
    }
}

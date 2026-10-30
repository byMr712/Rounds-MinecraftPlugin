package com.rounds.hud;

import com.rounds.RoundsPlugin;
import com.rounds.game.GameManager;
import com.rounds.teams.TeamManager;
import com.rounds.teams.TeamManager.GameTeam;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class HudEngineHook implements Listener {

    private static final List<String> REGISTERED_KEYS = new ArrayList<>();
    private static boolean hooked = false;
    private static RoundsPlugin pluginInstance;

    private HudEngineHook() {}

    public static void init(RoundsPlugin plugin) {
        pluginInstance = plugin;
        Plugin hudPlugin = Bukkit.getPluginManager().getPlugin("HUDEngine");
        if (hudPlugin == null) {
            plugin.getLogger().info("HUDEngine not found - HUD match score overlay disabled");
            return;
        }

        // Deploy configuration files and assets to HUDEngine folder
        boolean deployedAny = deployAssetsAndConfigs(plugin, hudPlugin.getDataFolder());

        // Register join listener to hide boss bar when player joins in lobby
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                GameManager gm = plugin.getGameManager();
                if (gm == null || !gm.isGameActive()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> setHudVisible(event.getPlayer(), false), 2L);
                }
            }
        }, plugin);

        // Run after 5 ticks to let HUDEngine finish service registration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (deployedAny) {
                    plugin.getLogger().info("Deployed Rounds HUD templates to HUDEngine. Reloading HUDEngine...");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hudengine reload");
                }
                registerValues(plugin);

                // Initial visibility check for currently online players
                GameManager gm = plugin.getGameManager();
                if (gm == null || !gm.isGameActive()) {
                    hideAll();
                } else {
                    showAll();
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to initialize HUDEngine hook: " + t.getMessage());
            }
        }, 5L);
    }

    public static void setHudVisible(Player player, boolean visible) {
        if (player == null || !player.isOnline()) return;
        try {
            Class<?> hudEngineClass = Class.forName("io.github.nacvark.hudengine.api.HudEngine");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(hudEngineClass);
            if (rsp == null) return;
            Object engine = rsp.getProvider();
            if (engine == null) return;

            Method playerMethod = hudEngineClass.getMethod("player", Player.class);
            Object playerHuds = playerMethod.invoke(engine, player);
            if (playerHuds == null) return;

            Method setVisibleMethod = playerHuds.getClass().getMethod("setVisible", boolean.class);
            setVisibleMethod.invoke(playerHuds, visible);
        } catch (Throwable ignored) {}
    }

    public static void showAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            setHudVisible(p, true);
        }
    }

    public static void hideAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            setHudVisible(p, false);
        }
    }

    private static boolean deployAssetsAndConfigs(RoundsPlugin plugin, File hudEngineDir) {
        if (!hudEngineDir.exists()) {
            hudEngineDir.mkdirs();
        }

        boolean deployed = false;

        // Assets
        File assetsDir = new File(hudEngineDir, "assets");
        if (!assetsDir.exists()) assetsDir.mkdirs();
        String[] frames = {"frame_red.png", "frame_green.png", "frame_yellow.png", "frame_blue.png", "frame_empty.png"};
        for (String frame : frames) {
            File target = new File(assetsDir, frame);
            if (extractResource(plugin, "hudengine/assets/" + frame, target)) {
                deployed = true;
            }
        }

        // Images
        File imagesDir = new File(hudEngineDir, "images");
        if (!imagesDir.exists()) imagesDir.mkdirs();
        File matchImages = new File(imagesDir, "rounds_match.yml");
        if (!matchImages.exists()) {
            if (extractResource(plugin, "hudengine/images/rounds_match.yml", matchImages)) {
                deployed = true;
            }
        }

        // Layouts
        File layoutsDir = new File(hudEngineDir, "layouts");
        if (!layoutsDir.exists()) layoutsDir.mkdirs();
        File matchLayout = new File(layoutsDir, "rounds_score.yml");
        if (extractResource(plugin, "hudengine/layouts/rounds_score.yml", matchLayout)) {
            deployed = true;
        }

        // Huds
        File hudsDir = new File(hudEngineDir, "huds");
        if (!hudsDir.exists()) hudsDir.mkdirs();
        File matchHud = new File(hudsDir, "rounds.yml");
        if (!matchHud.exists()) {
            if (extractResource(plugin, "hudengine/huds/rounds.yml", matchHud)) {
                deployed = true;
            }
        }

        // Config default huds
        File configFile = new File(hudEngineDir, "config.yml");
        if (configFile.exists()) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                List<String> defaultHuds = config.getStringList("huds.default");
                if (!defaultHuds.contains("rounds_score")) {
                    defaultHuds.add("rounds_score");
                    config.set("huds.default", defaultHuds);
                    deployed = true;
                }
                if (!"pink".equalsIgnoreCase(config.getString("boss-bar-color"))) {
                    config.set("boss-bar-color", "pink");
                    deployed = true;
                }
                if (config.getInt("boss-bar-line", 1) != 2) {
                    config.set("boss-bar-line", 2);
                    deployed = true;
                }
                if (deployed) {
                    config.save(configFile);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not update HUDEngine config.yml: " + e.getMessage());
            }
        }

        return deployed;
    }

    private static boolean extractResource(RoundsPlugin plugin, String resourcePath, File target) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return false;
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to extract " + resourcePath + ": " + e.getMessage());
            return false;
        }
    }

    private static void registerValues(RoundsPlugin plugin) {
        try {
            Class<?> hudEngineClass = Class.forName("io.github.nacvark.hudengine.api.HudEngine");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(hudEngineClass);
            if (rsp == null) {
                plugin.getLogger().warning("HUDEngine service is not registered yet.");
                return;
            }
            Object engine = rsp.getProvider();
            if (engine == null) return;

            Method valuesMethod = hudEngineClass.getMethod("values");
            Object hudValues = valuesMethod.invoke(engine);
            if (hudValues == null) return;

            Class<?> hudValuesClass = Class.forName("io.github.nacvark.hudengine.api.HudValues");
            Method registerMethod = hudValuesClass.getMethod("register", String.class, Function.class);

            // 1. rounds_active
            registerKey(hudValues, registerMethod, "rounds_active", p -> {
                GameManager gm = plugin.getGameManager();
                return (gm != null && gm.isGameActive()) ? "1" : "0";
            });

            // 2. slots 1..100
            for (int i = 1; i <= 100; i++) {
                final int slot = i;
                registerKey(hudValues, registerMethod, "rounds_slot_" + slot + "_active", p -> {
                    GameManager gm = plugin.getGameManager();
                    return (gm != null && gm.isGameActive() && slot <= gm.getRounds()) ? "1" : "0";
                });

                for (GameTeam team : GameTeam.values()) {
                    String key = "rounds_" + team.name().toLowerCase() + "_win_" + slot;
                    registerKey(hudValues, registerMethod, key, p -> {
                        GameManager gm = plugin.getGameManager();
                        TeamManager tm = plugin.getTeamManager();
                        if (gm != null && gm.isGameActive() && tm != null) {
                            if (slot <= gm.getRounds() && slot <= tm.getWins(team)) {
                                return "1";
                            }
                        }
                        return "0";
                    });
                }
            }

            hooked = true;
            plugin.getLogger().info("Successfully hooked into HUDEngine with " + REGISTERED_KEYS.size() + " score elements.");
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to register HUDEngine values: " + t.getMessage());
        }
    }

    private static void registerKey(Object hudValues, Method registerMethod, String key, Function<Player, String> provider) {
        try {
            registerMethod.invoke(hudValues, key, provider);
            REGISTERED_KEYS.add(key);
        } catch (Exception ignored) {}
    }

    public static void shutdown(RoundsPlugin plugin) {
        if (!hooked || REGISTERED_KEYS.isEmpty()) return;
        try {
            Class<?> hudEngineClass = Class.forName("io.github.nacvark.hudengine.api.HudEngine");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(hudEngineClass);
            if (rsp != null && rsp.getProvider() != null) {
                Object engine = rsp.getProvider();
                Method valuesMethod = hudEngineClass.getMethod("values");
                Object hudValues = valuesMethod.invoke(engine);
                if (hudValues != null) {
                    Class<?> hudValuesClass = Class.forName("io.github.nacvark.hudengine.api.HudValues");
                    Method unregisterMethod = hudValuesClass.getMethod("unregister", String.class);
                    for (String key : REGISTERED_KEYS) {
                        unregisterMethod.invoke(hudValues, key);
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            REGISTERED_KEYS.clear();
            hooked = false;
        }
    }
}

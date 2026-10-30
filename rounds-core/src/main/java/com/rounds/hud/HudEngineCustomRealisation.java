package com.rounds.hud;

import com.rounds.RoundsPlugin;
import com.rounds.game.GameManager;
import com.rounds.teams.TeamManager;
import com.rounds.teams.TeamManager.GameTeam;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in custom HUD engine implementation for Rounds match score boxes ("квадратики").
 * Replaces the external HUDEngine dependency while maintaining 100% 1:1 pixel, codepoint,
 * and font compatibility with existing resource packs.
 *
 * Credit: Based on the client font encoding and boss bar title technique from HUDEngine
 * by Nacvark (https://github.com/Nacvark/HUDEngine) under the MIT License.
 */
public final class HudEngineCustomRealisation implements Listener {

    private static final Key HUD_FONT = Key.key("hudengine", "hud");
    private static final TextColor TEXT_COLOR = TextColor.color(0xFFFFFF);

    private static final int SPACE_BASE = 0xF0000;
    private static final int SPACE_RANGE = 8192; // 0xF2000 is 0-advance
    private static final int GLYPH_BASE = 0xF5000;
    private static final int DYNAMIC_GLYPH_BASE = 0xF5400;
    private static final int BOX_STEP = 14; // 11px box width + 3px spacing gap
    private static final GameTeam[] TEAM_ORDER = {GameTeam.RED, GameTeam.BLUE, GameTeam.GREEN, GameTeam.YELLOW};

    private static RoundsPlugin plugin;
    private static HudEngineCustomRealisation listenerInstance;
    private static final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private static final Set<UUID> visiblePlayers = ConcurrentHashMap.newKeySet();
    private static BukkitTask tickerTask;

    private static int lastRounds = -1;
    private static List<GameTeam> lastActiveTeams = Collections.emptyList();
    private static List<Integer> lastWins = Collections.emptyList();
    private static boolean lastActive = false;
    private static Component cachedComponent = Component.empty();

    private HudEngineCustomRealisation() {}

    public static void init(RoundsPlugin pluginInstance) {
        plugin = pluginInstance;
        if (listenerInstance == null) {
            listenerInstance = new HudEngineCustomRealisation();
            Bukkit.getPluginManager().registerEvents(listenerInstance, plugin);
        }

        if (tickerTask != null) {
            tickerTask.cancel();
        }
        // Live match score synchronization every 10 ticks (0.5s)
        tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, HudEngineCustomRealisation::update, 10L, 10L);

        GameManager gm = plugin.getGameManager();
        if (gm != null && gm.isGameActive()) {
            showAll();
        } else {
            hideAll();
        }
        plugin.getLogger().info("HudEngineCustomRealisation initialized (native HUD match score overlay).");
    }

    public static void shutdown(RoundsPlugin pluginInstance) {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
        if (listenerInstance != null) {
            HandlerList.unregisterAll(listenerInstance);
            listenerInstance = null;
        }
        hideAll();
        bars.clear();
        visiblePlayers.clear();
    }

    public static void setHudVisible(Player player, boolean visible) {
        if (player == null || !player.isOnline()) return;
        boolean indicatorsEnabled = (plugin == null || plugin.getRoundsConfig() == null
                || plugin.getRoundsConfig().isScreenIndicatorsEnabled());
        if (!indicatorsEnabled) {
            visible = false;
        }
        UUID uuid = player.getUniqueId();
        if (visible) {
            visiblePlayers.add(uuid);
            BossBar bar = getOrCreateBar(player);
            bar.name(cachedComponent);
            player.showBossBar(bar);
        } else {
            visiblePlayers.remove(uuid);
            BossBar bar = bars.get(uuid);
            if (bar != null) {
                player.hideBossBar(bar);
            }
        }
    }

    public static void showAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            setHudVisible(p, true);
        }
        update();
    }

    public static void refresh() {
        lastActive = false;
        lastRounds = -1;
        lastActiveTeams = Collections.emptyList();
        lastWins = Collections.emptyList();
        if (plugin != null && plugin.getGameManager() != null && plugin.getGameManager().isGameActive()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                setHudVisible(p, true);
            }
        }
        update();
    }

    public static void hideAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            setHudVisible(p, false);
        }
    }

    private static BossBar getOrCreateBar(Player player) {
        return bars.computeIfAbsent(player.getUniqueId(), id ->
                BossBar.bossBar(cachedComponent, 0.0f, BossBar.Color.PINK, BossBar.Overlay.PROGRESS));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        GameManager gm = (plugin != null) ? plugin.getGameManager() : null;
        if (gm != null && gm.isGameActive()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> setHudVisible(player, true), 2L);
        } else {
            setHudVisible(player, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (visiblePlayers.contains(player.getUniqueId())) {
            BossBar bar = getOrCreateBar(player);
            bar.name(cachedComponent);
            player.showBossBar(bar);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        BossBar bar = bars.remove(uuid);
        if (bar != null) {
            event.getPlayer().hideBossBar(bar);
        }
        visiblePlayers.remove(uuid);
    }

    public static List<GameTeam> getActiveTeams(TeamManager tm) {
        List<GameTeam> active = new ArrayList<>();
        if (tm == null) return active;
        for (GameTeam team : TEAM_ORDER) {
            if (tm.getPlayerCount(team) > 0) {
                active.add(team);
            }
        }
        if (active.isEmpty()) {
            active.add(GameTeam.RED);
            active.add(GameTeam.BLUE);
        }
        return active;
    }

    private static int getTeamColorIndex(GameTeam team) {
        if (team == null) return 1;
        switch (team) {
            case RED: return 1;
            case GREEN: return 2;
            case YELLOW: return 3;
            case BLUE: return 4;
            default: return 1;
        }
    }

    public static void update() {
        if (plugin == null) return;
        boolean indicatorsEnabled = (plugin.getRoundsConfig() == null
                || plugin.getRoundsConfig().isScreenIndicatorsEnabled());
        if (!indicatorsEnabled) {
            if (!visiblePlayers.isEmpty()) {
                hideAll();
            }
            return;
        }

        GameManager gm = plugin.getGameManager();
        TeamManager tm = plugin.getTeamManager();

        boolean active = (gm != null && gm.isGameActive());
        int rounds = (active && gm != null) ? Math.min(100, Math.max(1, gm.getRounds())) : 0;
        List<GameTeam> activeTeams = active ? getActiveTeams(tm) : Collections.emptyList();
        List<Integer> currentWins = new ArrayList<>();
        for (GameTeam t : activeTeams) {
            currentWins.add((tm != null) ? tm.getWins(t) : 0);
        }

        if (active != lastActive || rounds != lastRounds
                || !activeTeams.equals(lastActiveTeams) || !currentWins.equals(lastWins)) {
            lastActive = active;
            lastRounds = rounds;
            lastActiveTeams = activeTeams;
            lastWins = currentWins;

            if (!active || rounds <= 0 || activeTeams.isEmpty()) {
                cachedComponent = Component.empty();
            } else {
                cachedComponent = compileTitle(rounds, activeTeams, tm);
            }

            for (UUID uuid : visiblePlayers) {
                BossBar bar = bars.get(uuid);
                if (bar != null) {
                    bar.name(cachedComponent);
                }
            }
        }
    }

    /**
     * Backward-compatible overload.
     */
    public static Component compileTitle(int rounds, int redWins, int greenWins, int yellowWins, int blueWins) {
        List<GameTeam> teams = new ArrayList<>();
        if (redWins > 0 || (greenWins == 0 && yellowWins == 0 && blueWins == 0)) teams.add(GameTeam.RED);
        if (greenWins > 0) teams.add(GameTeam.GREEN);
        if (yellowWins > 0) teams.add(GameTeam.YELLOW);
        if (blueWins > 0 || teams.size() < 2) teams.add(GameTeam.BLUE);
        return compileTitle(rounds, teams, null);
    }

    /**
     * Compiles dynamic Adventure Component showing only active teams stacked from top down.
     */
    public static Component compileTitle(int rounds, List<GameTeam> activeTeams, TeamManager tm) {
        StringBuilder sb = new StringBuilder();
        int cursor = 0;
        int numLines = Math.min(4, activeTeams.size());

        // Layer 0: Empty frames for each active team line
        for (int line = 0; line < numLines; line++) {
            int emptyGlyph = DYNAMIC_GLYPH_BASE + line * 5;
            for (int s = 1; s <= rounds; s++) {
                int targetX = (s - 1) * BOX_STEP;
                int dx = targetX - cursor;
                while (dx != 0) {
                    int step = Math.max(-SPACE_RANGE, Math.min(SPACE_RANGE, dx));
                    sb.appendCodePoint(SPACE_BASE + SPACE_RANGE + step);
                    cursor += step;
                    dx -= step;
                }
                sb.appendCodePoint(emptyGlyph);
                cursor += 12;
            }
        }

        // Layer 1: Win frames for each active team
        for (int line = 0; line < numLines; line++) {
            GameTeam team = activeTeams.get(line);
            int teamWins = (tm != null) ? tm.getWins(team) : 0;
            int limit = Math.min(rounds, teamWins);
            if (limit <= 0) continue;

            int winGlyph = DYNAMIC_GLYPH_BASE + line * 5 + getTeamColorIndex(team);
            for (int s = 1; s <= limit; s++) {
                int targetX = (s - 1) * BOX_STEP;
                int dx = targetX - cursor;
                while (dx != 0) {
                    int step = Math.max(-SPACE_RANGE, Math.min(SPACE_RANGE, dx));
                    sb.appendCodePoint(SPACE_BASE + SPACE_RANGE + step);
                    cursor += step;
                    dx -= step;
                }
                sb.appendCodePoint(winGlyph);
                cursor += 12;
            }
        }

        // Zero out total advance so client centers the title at screen center
        int dx = -cursor;
        while (dx != 0) {
            int step = Math.max(-SPACE_RANGE, Math.min(SPACE_RANGE, dx));
            sb.appendCodePoint(SPACE_BASE + SPACE_RANGE + step);
            cursor += step;
            dx -= step;
        }

        TextComponent.Builder builder = Component.text()
                .content(sb.toString())
                .font(HUD_FONT)
                .color(TEXT_COLOR);

        // Disable text shadow on Paper 1.21.4+ via reflection
        try {
            Class<?> scClass = Class.forName("net.kyori.adventure.text.format.ShadowColor");
            Method noneMethod = scClass.getMethod("none");
            Object none = noneMethod.invoke(null);
            Method shadowColorMethod = builder.getClass().getMethod("shadowColor", scClass);
            shadowColorMethod.invoke(builder, none);
        } catch (Throwable ignored) {}

        return builder.build();
    }
}

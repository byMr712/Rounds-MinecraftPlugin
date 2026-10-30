package com.rounds.bot;

import com.rounds.RoundsPlugin;
import com.rounds.nms.NmsPlayerHandle;
import com.rounds.teams.TeamManager.GameTeam;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class BotManager implements Listener {

    private final RoundsPlugin plugin;
    private final Map<String, FakeBot> bots = new ConcurrentHashMap<>();
    private ArenaNavMesh navMesh = null;

    public BotManager(RoundsPlugin plugin) {
        this.plugin = plugin;
    }

    public ArenaNavMesh getNavMesh() {
        return navMesh;
    }

    public RoundsPlugin getPlugin() {
        return plugin;
    }

    public FakeBot getBot(String name) {
        if (name == null) return null;
        return bots.get(name.toLowerCase());
    }

    public FakeBot getBot(UUID uuid) {
        if (uuid == null) return null;
        for (FakeBot bot : bots.values()) {
            Player p = bot.getBukkitPlayer();
            if (p != null && uuid.equals(p.getUniqueId())) {
                return bot;
            }
        }
        return null;
    }

    public boolean isBot(Player player) {
        return player != null && bots.containsKey(player.getName().toLowerCase());
    }

    public Collection<FakeBot> getBots() {
        return Collections.unmodifiableCollection(bots.values());
    }

    public String generateUniqueBotName() {
        Set<String> taken = new HashSet<>();
        for (String n : bots.keySet()) {
            taken.add(n.toLowerCase());
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            taken.add(p.getName().toLowerCase());
        }

        for (int i = 0; i < 10000; i++) {
            int num = ThreadLocalRandom.current().nextInt(1, 10000);
            String candidate = "rbot" + num;
            if (!taken.contains(candidate.toLowerCase())) {
                return candidate;
            }
        }
        return "rbot" + (System.currentTimeMillis() % 10000);
    }

    public List<FakeBot> spawnBots(int count, Location location) {
        List<FakeBot> spawned = new ArrayList<>();
        int maxBulk = (plugin != null) ? plugin.getRoundsConfig().getBotBulkLimit() : 500;
        int clamped = Math.max(1, Math.min(maxBulk, count));
        for (int i = 0; i < clamped; i++) {
            String name = generateUniqueBotName();
            String team = pickBestTeam(location);
            FakeBot bot = spawnBot(name, team, location);
            if (bot != null) {
                spawned.add(bot);
            }
        }
        return spawned;
    }

    public FakeBot spawnBot(String name, String teamName, Location location) {
        return spawnBot(name, teamName, location, null);
    }

    public FakeBot spawnBot(String name, String teamName, Location location, Integer level) {
        if (name == null || name.isBlank() || name.equalsIgnoreCase("auto") || name.equalsIgnoreCase("rbot")) {
            name = generateUniqueBotName();
        }
        if (!name.matches("^[a-zA-Z0-9_]{1,16}$")) {
            plugin.getLogger().warning("Invalid bot name: " + name + " (must be 1-16 chars [a-zA-Z0-9_])");
            return null;
        }

        if (bots.containsKey(name.toLowerCase())) {
            despawnBot(name);
        }

        Location loc = (location != null) ? location.clone() : getDefaultSpawn();
        World world = loc.getWorld();
        if (world == null) {
            world = Bukkit.getWorlds().get(0);
            loc.setWorld(world);
        }

        com.rounds.nms.NmsBridge bridge = plugin.getNmsBridge();
        if (bridge == null) {
            plugin.getLogger().warning("NMS bridge not available on this server version - bot spawn skipped (" + name + ")");
            return null;
        }

        UUID botUuid = UUID.randomUUID();
        NmsPlayerHandle handle = bridge.createFakePlayer(world, name, botUuid, loc);
        if (handle == null) {
            plugin.getLogger().warning("Failed to create fake player " + name + " (NMS adapter error)");
            return null;
        }
        Player bukkitPlayer = handle.getBukkitEntity();        bukkitPlayer.setGameMode(GameMode.SURVIVAL);
        bukkitPlayer.setAllowFlight(false);
        bukkitPlayer.setFlying(false);
        bukkitPlayer.setInvulnerable(false);
        bukkitPlayer.setOp(false);
        bukkitPlayer.teleport(loc);

        // Register in RoundsPlugin active players so gun bullets can hit the bot
        plugin.getPlayerDataManager().trackPlayer(bukkitPlayer.getUniqueId());

        // 4. Team assignment and Gun equipment
        Set<GameTeam> availableTeams = getAvailableTeams(loc);
        String targetTeam;
        if (teamName != null && !teamName.isBlank()) {
            try {
                GameTeam requested = GameTeam.valueOf(teamName.toUpperCase());
                if (!availableTeams.isEmpty() && !availableTeams.contains(requested)) {
                    plugin.getLogger().warning("Team " + requested + " has no spawn/join blocks on map. Reassigning bot " + name + " to available team.");
                    targetTeam = pickBestTeam(loc);
                } else {
                    targetTeam = requested.name();
                }
            } catch (IllegalArgumentException e) {
                targetTeam = pickBestTeam(loc);
            }
        } else {
            targetTeam = pickBestTeam(loc);
        }
        assignTeam(bukkitPlayer, targetTeam);

        // Give gun directly
        plugin.getGameManager().giveGun(bukkitPlayer);

        FakeBot bot = new FakeBot(this, handle);
        bot.resetPhysics();
        try {
            bot.setAssignedTeam(GameTeam.valueOf(targetTeam.toUpperCase()));
        } catch (Exception ignored) {}
        if (level != null && level >= 1 && level <= 10) {
            bot.setLevel(level);
        }
        bots.put(name.toLowerCase(), bot);
        com.rounds.stats.MatchStatsManager.registerParticipant(bukkitPlayer.getUniqueId(), name);
        if (plugin.getGameManager() != null) {
            plugin.getGameManager().showNameTagsInGame();
        }
        plugin.getLogger().info("Spawned fake bot: " + name + " [team: " + targetTeam + ", level: " + bot.getLevel() + "]");
        return bot;
    }

    public void randomizeBotLevels() {
        if (bots.isEmpty()) return;
        for (FakeBot bot : bots.values()) {
            bot.randomizeLevel();
            plugin.getLogger().info("Rerolled bot " + bot.getName() + " difficulty to level " + bot.getLevel() + " (" + (bot.getLevel() * 10) + "%)");
        }
        if (plugin.getGameManager() != null) {
            plugin.getGameManager().showNameTagsInGame();
        }
    }

    public void onRoundStart() {
        if (bots.isEmpty()) return;
        if (plugin.getGameManager() != null) {
            org.bukkit.Location center = plugin.getGameManager().getCurrentZoneCenter();
            if (center != null) {
                this.navMesh = ArenaNavMesh.build(center, plugin.getBlockListener(), 24, 4);
            }
        }
        for (FakeBot bot : bots.values()) {
            bot.resetPhysics();
            if (bot.getAi() != null) {
                bot.getAi().onRoundStart();
            }
        }
    }

    public boolean despawnBot(String name) {
        FakeBot bot = bots.remove(name.toLowerCase());
        if (bot == null) return false;

        Player bukkitPlayer = bot.getBukkitPlayer();
        UUID botUuid = (bukkitPlayer != null) ? bukkitPlayer.getUniqueId() : null;

        com.rounds.nms.NmsBridge bridge = plugin.getNmsBridge();
        NmsPlayerHandle handle = (bridge != null) ? bot.getHandle() : null;

        // 1. Cleanup bot AI and internal state
        bot.cleanup();

        // 2. Disconnect and remove NMS player cleanly from level and connection
        if (bridge != null && handle != null) {
            bridge.despawnFakePlayer(handle);
        }

        // 3. Clear all plugin registrations and memory caches across managers
        if (botUuid != null) {
            plugin.getPlayerDataManager().unregisterPlayer(botUuid);
            plugin.getTeamManager().leaveTeam(botUuid);
            if (plugin.getGameManager() != null) {
                plugin.getGameManager().onPlayerQuit(botUuid);
            }
            if (plugin.getCardManager() != null) {
                plugin.getCardManager().cleanupPlayer(botUuid);
            }
            if (plugin.getCardGUI() != null) {
                plugin.getCardGUI().cleanupPlayer(botUuid);
            }
            com.rounds.entity.RoundsEntities.clearPlayer(botUuid);
            plugin.getCardDisplay().cleanupPlayer(botUuid);
            if (plugin.getBlockListener() != null) {
                plugin.getBlockListener().cleanupPlayer(botUuid);
            }
        }

        // 4. Unregister bot dedicated team if present
        Scoreboard mainSb = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team bt = mainSb.getTeam("rb_" + name.toLowerCase());
        if (bt != null) {
            try { bt.unregister(); } catch (Exception ignored) {}
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard pSb = p.getScoreboard();
            if (pSb != null && !pSb.equals(mainSb)) {
                org.bukkit.scoreboard.Team pbt = pSb.getTeam("rb_" + name.toLowerCase());
                if (pbt != null) {
                    try { pbt.unregister(); } catch (Exception ignored) {}
                }
            }
        }

        plugin.getLogger().info("Despawned fake bot: " + name);
        return true;
    }

    public int despawnAll() {
        int count = bots.size();
        for (String name : new ArrayList<>(bots.keySet())) {
            despawnBot(name);
        }
        this.navMesh = null;
        System.gc();
        return count;
    }

    public int despawnBots(int count) {
        if (bots.isEmpty() || count <= 0) return 0;
        int despawned = 0;

        for (int i = 0; i < count && !bots.isEmpty(); i++) {
            Map<GameTeam, List<FakeBot>> botsByTeam = new HashMap<>();
            for (FakeBot b : bots.values()) {
                Player bp = b.getBukkitPlayer();
                GameTeam gt = (bp != null) ? plugin.getTeamManager().getPlayerTeam(bp.getUniqueId()) : null;
                if (gt == null) gt = GameTeam.BLUE;
                botsByTeam.computeIfAbsent(gt, k -> new ArrayList<>()).add(b);
            }

            if (botsByTeam.isEmpty()) break;

            int maxPlayerCount = -1;
            List<GameTeam> candidateTeams = new ArrayList<>();
            for (GameTeam gt : botsByTeam.keySet()) {
                int teamCount = plugin.getTeamManager().getPlayerCount(gt);
                if (teamCount > maxPlayerCount) {
                    maxPlayerCount = teamCount;
                    candidateTeams.clear();
                    candidateTeams.add(gt);
                } else if (teamCount == maxPlayerCount) {
                    candidateTeams.add(gt);
                }
            }

            GameTeam chosenTeam = candidateTeams.get(ThreadLocalRandom.current().nextInt(candidateTeams.size()));
            List<FakeBot> teamBots = botsByTeam.get(chosenTeam);
            FakeBot toDespawn = teamBots.get(ThreadLocalRandom.current().nextInt(teamBots.size()));

            if (despawnBot(toDespawn.getName())) {
                despawned++;
            }
        }
        if (bots.isEmpty()) {
            this.navMesh = null;
        }
        System.gc();
        return despawned;
    }

    public void assignTeam(Player player, String teamName) {
        if (teamName == null || teamName.isBlank()) return;
        if (teamName.equalsIgnoreCase("NONE")) {
            plugin.getTeamManager().leaveTeam(player.getUniqueId());
            FakeBot bot = getBot(player.getName());
            if (bot != null) bot.setAssignedTeam(null);
            return;
        }
        GameTeam gt;
        try {
            gt = GameTeam.valueOf(teamName.toUpperCase());
            plugin.getTeamManager().joinTeam(player.getUniqueId(), gt);
        } catch (IllegalArgumentException ignored) {
            gt = GameTeam.BLUE;
            plugin.getTeamManager().joinTeam(player.getUniqueId(), gt);
        }
        FakeBot bot = getBot(player.getName());
        if (bot != null) {
            bot.setAssignedTeam(gt);
        }
        if (plugin.getGameManager() != null) {
            plugin.getGameManager().ensureTeamSpawn(gt);
            plugin.getGameManager().applyTeamColor(player);
            plugin.getGameManager().showNameTagsInGame();
            plugin.getGameManager().updateScoreboard();
        }
    }

    public Set<GameTeam> getAvailableTeams() {
        return getAvailableTeams(null);
    }

    public Set<GameTeam> getAvailableTeams(Location loc) {
        if (plugin.getGameManager() != null) {
            return plugin.getGameManager().getAvailableTeams();
        }
        Collection<GameTeam> configTeams = plugin.getRoundsConfig().getEnabledTeams();
        if (configTeams != null && !configTeams.isEmpty()) {
            return new HashSet<>(configTeams);
        }
        return EnumSet.allOf(GameTeam.class);
    }

    public String pickBestTeam() {
        return pickBestTeam(null);
    }

    public String pickBestTeam(Location location) {
        if (plugin.getGameManager() != null) {
            GameTeam smallest = plugin.getGameManager().findSmallestTeam();
            if (smallest != null) return smallest.name();
        }
        Set<GameTeam> teams = getAvailableTeams(location);
        GameTeam best = null;
        int minCount = Integer.MAX_VALUE;
        for (GameTeam gt : teams) {
            int count = plugin.getTeamManager().getPlayerCount(gt);
            if (count < minCount) {
                minCount = count;
                best = gt;
            }
        }
        if (best != null) return best.name();
        return teams.isEmpty() ? "BLUE" : teams.iterator().next().name();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (bots.isEmpty()) return;
        Player player = event.getEntity();
        FakeBot bot = getBot(player.getName());
        if (bot == null) return;

        // Auto respawn bot after configured delay ticks
        long delayTicks = (plugin != null && plugin.getRoundsConfig() != null)
                ? plugin.getRoundsConfig().getRespawnDelayTicks()
                : 10L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
                if (plugin.getRoundsConfig().isFreeplay() || !plugin.getGameManager().isGameStarted()) {
                    plugin.getGameManager().giveGun(player);
                }
            }
        }, Math.max(1L, delayTicks));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (bots.isEmpty()) return;
        Player p = event.getPlayer();
        p.setInvulnerable(false);
        p.setNoDamageTicks(0);
        FakeBot bot = getBot(p.getName());
        if (bot != null) {
            bot.updatePlayer(p);
            if (plugin.getGameManager() != null) {
                plugin.getGameManager().showNameTagsInGame();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (bots.isEmpty()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        FakeBot bot = getBot(victim.getName());
        if (bot == null) return;

        Location damageOrigin = null;
        UUID attackerId = null;
        if (event instanceof EntityDamageByEntityEvent edbe) {
            damageOrigin = edbe.getDamager().getLocation();
            attackerId = edbe.getDamager().getUniqueId();
        }
        bot.onAttacked(damageOrigin, attackerId);
    }

    private Location getDefaultSpawn() {
        if (!Bukkit.getWorlds().isEmpty()) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return new Location(null, 0, 100, 0);
    }
}

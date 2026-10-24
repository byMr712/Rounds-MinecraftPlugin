package com.rounds.bot;

import com.mojang.authlib.GameProfile;
import com.rounds.RoundsPlugin;
import com.rounds.nms.FakeConnection;
import com.rounds.nms.NoopConnection;
import com.rounds.teams.TeamManager.GameTeam;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
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

    public BotManager(RoundsPlugin plugin) {
        this.plugin = plugin;
    }

    public RoundsPlugin getPlugin() {
        return plugin;
    }

    public FakeBot getBot(String name) {
        if (name == null) return null;
        return bots.get(name.toLowerCase());
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
        int clamped = Math.max(1, Math.min(50, count));
        for (int i = 0; i < clamped; i++) {
            String name = generateUniqueBotName();
            String team = pickBestTeam();
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

        MinecraftServer nmsServer = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel nmsLevel = ((CraftWorld) world).getHandle();

        // 1. Profile and ServerPlayer
        UUID botUuid = UUID.randomUUID();
        GameProfile profile = new GameProfile(botUuid, name);
        ClientInformation clientInfo = ClientInformation.createDefault();

        ServerPlayer nmsPlayer = new ServerPlayer(nmsServer, nmsLevel, profile, clientInfo);
        nmsPlayer.setPos(loc.getX(), loc.getY(), loc.getZ());
        nmsPlayer.setRot(loc.getYaw(), loc.getPitch());

        // 2. Setup NoopConnection & place into server PlayerList
        NoopConnection noopConn = new NoopConnection(PacketFlow.SERVERBOUND);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);

        nmsServer.getPlayerList().placeNewPlayer(noopConn, nmsPlayer, cookie);

        // 3. Swap with FakeConnection to prevent timeout disconnects
        nmsPlayer.connection = new FakeConnection(nmsServer, noopConn, nmsPlayer, cookie);

        Player bukkitPlayer = nmsPlayer.getBukkitEntity();
        bukkitPlayer.setGameMode(GameMode.SURVIVAL);
        bukkitPlayer.setAllowFlight(false);
        bukkitPlayer.setFlying(false);
        bukkitPlayer.setInvulnerable(false);
        bukkitPlayer.setOp(false);
        bukkitPlayer.teleport(loc);

        // Register in RoundsPlugin active players so gun bullets can hit the bot
        plugin.getPlayerDataManager().trackPlayer(bukkitPlayer.getUniqueId());

        // 4. Team assignment and Gun equipment
        String targetTeam = (teamName != null && !teamName.isBlank()) ? teamName : pickBestTeam();
        assignTeam(bukkitPlayer, targetTeam);

        // Give gun directly
        plugin.getGameManager().giveGun(bukkitPlayer);

        FakeBot bot = new FakeBot(this, bukkitPlayer, nmsPlayer);
        if (level != null && level >= 1 && level <= 10) {
            bot.setLevel(level);
        }
        bots.put(name.toLowerCase(), bot);
        plugin.getLogger().info("Spawned fake bot: " + name + " [team: " + targetTeam + ", level: " + bot.getLevel() + "]");
        return bot;
    }

    public void randomizeBotLevels() {
        if (bots.isEmpty()) return;
        for (FakeBot bot : bots.values()) {
            bot.randomizeLevel();
            plugin.getLogger().info("Rerolled bot " + bot.getName() + " difficulty to level " + bot.getLevel() + " (" + (bot.getLevel() * 10) + "%)");
        }
    }

    public void onRoundStart() {
        if (bots.isEmpty()) return;
        for (FakeBot bot : bots.values()) {
            if (bot.getAi() != null) {
                bot.getAi().onRoundStart();
            }
        }
    }

    public boolean despawnBot(String name) {
        FakeBot bot = bots.remove(name.toLowerCase());
        if (bot == null) return false;

        bot.cleanup();
        MinecraftServer nmsServer = ((CraftServer) Bukkit.getServer()).getServer();
        nmsServer.getPlayerList().remove(bot.getNmsPlayer());

        if (bot.getBukkitPlayer().isValid()) {
            bot.getBukkitPlayer().remove();
        }

        // Remove from RoundsPlugin active players and team
        plugin.getPlayerDataManager().removeActivePlayer(bot.getBukkitPlayer().getUniqueId());
        plugin.getTeamManager().leaveTeam(bot.getBukkitPlayer().getUniqueId());

        plugin.getLogger().info("Despawned fake bot: " + name);
        return true;
    }

    public int despawnAll() {
        int count = bots.size();
        for (String name : new ArrayList<>(bots.keySet())) {
            despawnBot(name);
        }
        return count;
    }

    public int despawnBots(int count) {
        if (bots.isEmpty() || count <= 0) return 0;
        int despawned = 0;

        for (int i = 0; i < count && !bots.isEmpty(); i++) {
            Map<GameTeam, List<FakeBot>> botsByTeam = new HashMap<>();
            for (FakeBot b : bots.values()) {
                GameTeam gt = plugin.getTeamManager().getPlayerTeam(b.getBukkitPlayer().getUniqueId());
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
        return despawned;
    }

    public void assignTeam(Player player, String teamName) {
        if (teamName == null || teamName.isBlank()) return;
        try {
            GameTeam gt = GameTeam.valueOf(teamName.toUpperCase());
            plugin.getTeamManager().joinTeam(player.getUniqueId(), gt);
        } catch (IllegalArgumentException ignored) {
            plugin.getTeamManager().joinTeam(player.getUniqueId(), GameTeam.BLUE);
        }
    }

    public String pickBestTeam() {
        Collection<GameTeam> teams = plugin.getRoundsConfig().getEnabledTeams();
        if (teams == null || teams.isEmpty()) {
            teams = EnumSet.allOf(GameTeam.class);
        }
        GameTeam best = null;
        int minCount = Integer.MAX_VALUE;
        for (GameTeam gt : teams) {
            int count = plugin.getTeamManager().getPlayerCount(gt);
            if (count < minCount) {
                minCount = count;
                best = gt;
            }
        }
        return (best != null) ? best.name() : "BLUE";
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (bots.isEmpty()) return;
        Player player = event.getEntity();
        FakeBot bot = getBot(player.getName());
        if (bot == null) return;

        // Auto respawn bot 10 ticks after death
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
                if (plugin.getRoundsConfig().isFreeplay() || !plugin.getGameManager().isGameStarted()) {
                    plugin.getGameManager().giveGun(player);
                }
            }
        }, 10L);
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

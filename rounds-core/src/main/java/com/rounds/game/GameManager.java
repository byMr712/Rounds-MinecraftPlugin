package com.rounds.game;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.util.Vector;
import com.rounds.RoundsConfig;
import com.rounds.RoundsPlugin;
import com.rounds.blocks.BlockStorage;
import com.rounds.blocks.SpawnManager;
import com.rounds.bot.FakeBot;
import com.rounds.entity.RoundsEntities;
import com.rounds.item.GunItem;
import com.rounds.player.PlayerData;
import com.rounds.player.PlayerDataManager;
import com.rounds.teams.TeamManager;
import com.rounds.game.GameStateManager.SavedState;
import com.rounds.teams.TeamManager.GameTeam;
import com.rounds.util.Messages;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.block.Block;
import com.rounds.RoundsKeys;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Vector;

import org.bukkit.Location;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Random;

public class GameManager implements Listener {

    public enum GameState { WAITING, PLAYING, CARDS, ROUND_END, GAME_END }

    private final RoundsPlugin plugin;
    private GameState state = GameState.WAITING;
    private int roundsToWin;
    private int currentRound = 0;
    private double musicTick = 0;
    private int tickTaskId = -1;
    private final Set<UUID> deadPlayers = new HashSet<>();
    private final Map<UUID, Location> abyssalLastLocations = new HashMap<>();
    private final Map<UUID, Long> lastSilenceAuraTime = new HashMap<>();
    private final Map<UUID, Integer> syncedHearts = new HashMap<>();
    private final Map<UUID, Long> highlightGlowUntil = new HashMap<>();
    private final Set<GameTeam> losingTeams = new HashSet<>();
    private final GameStateManager stateManager;
    private final com.rounds.util.TabCompat tabCompat;
    private boolean wheelEnabled = false;
    private final Set<Integer> scheduledTaskIds = new HashSet<>();
    private final Set<Integer> phantomTaskIds = new HashSet<>();
    private int gameGeneration = 0;
    private static final Random RANDOM = new Random();
    private final Set<UUID> pendingCardJoiners = new HashSet<>();
    private final Set<UUID> returningJoiners = new HashSet<>();
    private final Map<GameRule<?>, Object> savedGameRules = new HashMap<>();
    private final Map<GameTeam, Location> teamSpawns = new HashMap<>();
    private Location currentZoneCenter = null;

    private static class WrongMapTeleportTask {
        final UUID playerId;
        final int startRound;
        final int startGeneration;
        final Location startZoneCenter;
        final long finishTimeMs;
        int lastAnnouncedSecond;

        WrongMapTeleportTask(UUID playerId, int startRound, int startGeneration, Location startZoneCenter) {
            this.playerId = playerId;
            this.startRound = startRound;
            this.startGeneration = startGeneration;
            this.startZoneCenter = startZoneCenter != null ? startZoneCenter.clone() : null;
            this.finishTimeMs = System.currentTimeMillis() + 5000L;
            this.lastAnnouncedSecond = 5;
        }
    }

    private final Map<UUID, WrongMapTeleportTask> wrongMapTasks = new HashMap<>();
    private final Map<UUID, Long> lastWrongMapCheckTime = new HashMap<>();
    private final Map<UUID, Boolean> lastWrongMapResult = new HashMap<>();
    private final Map<UUID, Location> chameleonLastLoc = new HashMap<>();
    private final Map<UUID, Integer> chameleonStillTicks = new HashMap<>();
    private static final UUID SNOWBALL_MOD_ID = UUID.nameUUIDFromBytes("rounds_snowball".getBytes());
    private boolean gameActive = false;
    private boolean autoDeathEnabled = true;
    private long autoDeathNextEvent = 0;
    private boolean autoDeathNextIsKill = false;
    private boolean autoDeathInitialPhase = true;
    private long autoDeathPhaseDurationMs = 180000L;
    private BossBar autoDeathBossBar = null;
    private static final long AUTO_DEATH_DELAY_MS = 240_000L;
    private static final long AUTO_DEATH_INTERVAL_MS = 60_000L;

    public GameManager(RoundsPlugin plugin) {
        this.plugin = plugin;
        this.roundsToWin = plugin.getRoundsConfig().getDefaultRounds();
        this.stateManager = new GameStateManager(plugin);
        this.tabCompat = new com.rounds.util.TabCompat();
        startScoreboardTask();
    }

    public boolean isWheelEnabled() { return wheelEnabled; }
    public void setWheelEnabled(boolean enabled) { this.wheelEnabled = enabled; }
    public boolean isAutoDeathEnabled() { return autoDeathEnabled; }
    public void setAutoDeathEnabled(boolean enabled) { this.autoDeathEnabled = enabled; }

    public Location getGroundLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return loc;
        World w = loc.getWorld();
        Location ground = loc.clone();
        int startY = Math.min(loc.getBlockY(), w.getMaxHeight() - 1);
        int minY = w.getMinHeight();
        for (int y = startY; y >= minY; y--) {
            Material mat = w.getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType();
            if (mat.isSolid()) {
                ground.setY(y + 1.0);
                return ground;
            }
        }
        ground.setY(w.getHighestBlockYAt(loc) + 1.0);
        return ground;
    }

    private void cancelScheduledTasks() {
        for (int id : scheduledTaskIds) {
            Bukkit.getScheduler().cancelTask(id);
        }
        scheduledTaskIds.clear();
    }

    private int scheduleDelayed(Runnable runnable, long delay) {
        final int[] idHolder = {-1};
        int id = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            scheduledTaskIds.remove(idHolder[0]);
            runnable.run();
        }, delay);
        idHolder[0] = id;
        scheduledTaskIds.add(id);
        return id;
    }

    public void startGame() {
        try {
            int cur = (int) getCurrentRound();
            if (!com.rounds.debug.DebugSession.active && !com.rounds.debug.DebugSession.triggered && com.rounds.debug.DebugSession.startAt == cur) {
                com.rounds.debug.DebugSession.triggered = true;
                com.rounds.debug.DebugSession.active = true;
                com.rounds.debug.DebugLog.initSession();
                com.rounds.debug.DebugLog.log("DEBUG", "AUTO-START triggered at round start of R#" + cur);
            }
            if (com.rounds.debug.DebugSession.active) {
                com.rounds.debug.DebugLog.log("GAME", "START round=" + cur);
            }
        } catch (Throwable ignored) {}
        List<Player> readyPlayers = getReadyPlayers();
        if (readyPlayers.size() < 2) {
            plugin.getServer().broadcastMessage(ChatColor.RED + Messages.get("game.not-enough-players"));
            return;
        }

        Set<GameTeam> teamsWithPlayers = new HashSet<>();
        for (Player p : readyPlayers) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (team != null) teamsWithPlayers.add(team);
        }
        if (teamsWithPlayers.size() < 2) {
            plugin.getServer().broadcastMessage(ChatColor.RED + Messages.get("game.not-enough-teams"));
            return;
        }
        if (isGameActive()) return;
        gameActive = true;

        resetWins();
        resetAllCards();
        gameGeneration++;
        musicTick = 0;
        currentRound = 0;
        deadPlayers.clear();
        abyssalLastLocations.clear();
        lastSilenceAuraTime.clear();
        syncedHearts.clear();
        teamSpawns.clear();
        chameleonLastLoc.clear();
        chameleonStillTicks.clear();
        com.rounds.listener.LegendaryEffects.resetAll();
        com.rounds.stats.MatchStatsManager.onGameStart();

        saveState();
        updateScoreboard();
        showNameTagsInGame();

        plugin.getCardManager().clearPendingPicks();
        plugin.getPlayerDataManager().clearActivePlayers();
        applyGameRules();

        for (Player p : readyPlayers) {
            com.rounds.stats.MatchStatsManager.registerParticipant(p.getUniqueId(), p.getName());
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            plugin.getPlayerDataManager().trackPlayer(p.getUniqueId());
            plugin.getPlayerDataManager().savePlayerFullData(p.getUniqueId(), team, null);
        }

        BlockStorage bs = plugin.getBlockListener().getBlockStorage();
        Location lobby = bs.getLobbyBlock();

        if (lobby != null) {
            Location lobbyTp = getGroundLocation(lobby);
            for (Player p : readyPlayers) {
                p.teleport(lobbyTp);
            }
        }

        startCountdown(readyPlayers, teamsWithPlayers);
    }

    private void startCountdown(List<Player> readyPlayers, Set<GameTeam> teamsWithPlayers) {
        BlockStorage bs = plugin.getBlockListener().getBlockStorage();
        List<Location> allSpawns = bs.getSpawnBlocks();

        state = GameState.WAITING;

        Map<GameTeam, Location> spawnAssignment = new HashMap<>();
        if (!allSpawns.isEmpty()) {
            spawnAssignment = SpawnManager.assignSpawns(teamsWithPlayers, allSpawns);
        } else {
            Location base = bs.getLobbyBlock();
            if (base != null) {
                Location ground = getGroundLocation(base);
                spawnAssignment.put(GameTeam.RED, ground.clone().add(-2.0, 0, 0));
                spawnAssignment.put(GameTeam.BLUE, ground.clone().add(2.0, 0, 0));
            }
        }
        teamSpawns.clear();
        teamSpawns.putAll(spawnAssignment);

        applyDarkness();

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getTeamManager().getPlayerTeam(p.getUniqueId()) == null) {
                p.setGameMode(GameMode.SPECTATOR);
                p.setInvulnerable(true);
                p.setAllowFlight(true);
                p.setFlying(true);
                if (currentZoneCenter != null && currentZoneCenter.getWorld() != null) {
                    p.teleport(currentZoneCenter.clone().add(0, 3.0, 0));
                }
            }
        }

        BukkitRunnable countdown = new BukkitRunnable() {
            int count = (plugin != null) ? plugin.getRoundsConfig().getCountdownSeconds() : 3;
            final int gen = gameGeneration;

            @Override
            public void run() {
                if (gen != gameGeneration) {
                    // Игра была остановлена/перезапущена во время отсчёта — отменяем.
                    scheduledTaskIds.remove(getTaskId());
                    cancel();
                    return;
                }
                if (count <= 0) {
                    scheduledTaskIds.remove(getTaskId());
                    cancel();
                    finishStart(readyPlayers, teamsWithPlayers);
                    return;
                }

                plugin.getServer().broadcastMessage(ChatColor.YELLOW + "" + count);
                applyDarkness();

                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
                    if (team == null) {
                        p.setGameMode(GameMode.SPECTATOR);
                        p.setAllowFlight(true);
                        p.setFlying(true);
                    } else {
                        p.setGameMode(GameMode.ADVENTURE);
                        p.setAllowFlight(false);
                        p.setFlying(false);
                    }
                    p.setInvulnerable(true);
                }
                count--;
            }
        };
        countdown.runTaskTimer(plugin, 20L, 20L);
        scheduledTaskIds.add(countdown.getTaskId());
    }

    private void finishStart(List<Player> readyPlayers, Set<GameTeam> teamsWithPlayers) {
        plugin.getServer().broadcastMessage(ChatColor.GREEN + Messages.get("game.started"));
        plugin.getServer().broadcastMessage(ChatColor.GOLD + Messages.get("game.rounds-to-win", roundsToWin));

        state = GameState.CARDS;
        startGameTick();
        currentRound = 0;

        pickRandomZoneAndAssignSpawns();

        removeDarkness();

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (team == null) {
                p.setGameMode(GameMode.SPECTATOR);
                p.setAllowFlight(true);
                p.setFlying(true);
                p.setInvulnerable(true);
                if (currentZoneCenter != null && currentZoneCenter.getWorld() != null) {
                    p.teleport(currentZoneCenter.clone().add(0, 3.0, 0));
                }
            }
        }

        Map<GameTeam, Integer> startTeamMemberCount = new HashMap<>();
        for (Player p : readyPlayers) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (team != null) {
                Location baseSpawn = teamSpawns.get(team);
                if (baseSpawn == null) {
                    baseSpawn = getGroundLocation(p.getLocation());
                }
                if (baseSpawn.getWorld() != null) {
                    baseSpawn.getChunk().load();
                }
                int memberIdx = startTeamMemberCount.getOrDefault(team, 0);
                startTeamMemberCount.put(team, memberIdx + 1);
                Location spawn = getTeammateSpawnLocation(baseSpawn, memberIdx);
                p.teleport(spawn);
                com.rounds.bot.FakeBot bot = (plugin.getBotManager() != null) ? plugin.getBotManager().getBot(p.getName()) : null;
                if (bot != null) {
                    bot.resetPhysics();
                }
                p.setGameMode(GameMode.ADVENTURE);
                p.setAllowFlight(false);
                p.setFlying(false);
                p.setFoodLevel(20);
                p.setSaturation(5.0f);
                p.setExhaustion(0f);
                p.setFireTicks(0);
                p.setHealth(p.getMaxHealth());
                p.setInvulnerable(true);
                p.setVelocity(new Vector(0, 0, 0));
                p.setNoDamageTicks(0);
                plugin.getCardManager().openCardSelection(p, team);
            }
        }
        saveState();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    public void giveGun(Player player) {
        ItemStack gun = GunItem.createGunItem();
        player.getInventory().setItemInMainHand(gun);
    }

    public void applyPlayerHP(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getData(player);
        double baseMaxHP = data.getMaxHealth();
        if (data.grow > 0) {
            double scaleBonus = data.grow * 0.2;
            baseMaxHP += scaleBonus * 10;
        }
        double maxHP = baseMaxHP;
        if (data.pristinePerseverance > 0) {
            maxHP = baseMaxHP * (1.0 + data.pristinePerseverance);
        }
        maxHP = com.rounds.player.PlayerData.clampMaxHealth(maxHP);
        var attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(maxHP);
            player.setHealth(Math.min(maxHP, attr.getValue()));
        }
    }

    private void applyRoundEffects(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getData(player);
        if (data != null) {
            data.phoenixUses = (int) data.phoenix;
        }
    }

    private void applyPeriodicEffects() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!p.isValid()) continue;
            if (plugin.getTeamManager().getPlayerTeam(p.getUniqueId()) == null) continue;
            if (deadPlayers.contains(p.getUniqueId())) continue;
            PlayerData data = plugin.getPlayerDataManager().getData(p);
            GameTeam myTeam = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());

            if (plugin.getRoundsConfig().isColorNicknames()) {
                int hearts = getHeartCount(p);
                Integer lastSent = syncedHearts.get(p.getUniqueId());
                if (lastSent == null || lastSent != hearts) {
                    syncedHearts.put(p.getUniqueId(), hearts);
                    updateColoredName(p, myTeam);
                }
            }

            // Один общий запрос сущностей по максимальному нужному радиусу.
            double maxRadius = 0;
            if (data.highlight > 0) maxRadius = Math.max(maxRadius, 8.0);
            if (data.lifestealAura > 0) maxRadius = Math.max(maxRadius, 2.5);
            if (data.silenceAura > 0) maxRadius = Math.max(maxRadius, 5.0);
            if (data.speed > 0) maxRadius = Math.max(maxRadius, 5.0 + data.speed * 3.0);
            List<Entity> nearby = maxRadius > 0 ? p.getNearbyEntities(maxRadius, maxRadius, maxRadius)
                    : Collections.emptyList();

            if (data.highlight > 0) {
                for (Entity entity : nearby) {
                    if (!(entity instanceof Player radTarget) || radTarget.equals(p)) continue;
                    if (radTarget.getLocation().distanceSquared(p.getLocation()) > 64.0) continue; // 8^2
                    if (radTarget.getGameMode() == GameMode.SPECTATOR) continue;
                    GameTeam targetTeam = plugin.getTeamManager().getPlayerTeam(radTarget.getUniqueId());
                    if (myTeam != targetTeam) {
                        highlightGlowUntil.put(radTarget.getUniqueId(), (long) musicTick + 200L); // 10 seconds limit after leaving
                        radTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            PotionEffectType.GLOWING, 200, 0), true);
                    }
                }
            }
            if (data.autoReload > 0 && p.getGameMode() != GameMode.SPECTATOR) {
                if (data.ammo < data.maxAmmo && !GunItem.isReloading(p.getUniqueId())) {
                    data.ammo = Math.min(data.ammo + data.autoReload * 0.1, data.maxAmmo);
                }
                data.ammo = Math.min(data.ammo, data.maxAmmo);
            }
            if (data.autoReloadDelay > 0 && p.getGameMode() != GameMode.SPECTATOR) {
                if (data.ammo <= 0 && !GunItem.isReloading(p.getUniqueId())) {
                    data.zeroAmmoTicks++;
                    int requiredTicks = (int) Math.round(data.autoReloadDelay * 20.0);
                    if (data.zeroAmmoTicks >= requiredTicks) {
                        data.zeroAmmoTicks = 0;
                        GunItem.getInstance().startReload(p, data);
                    }
                } else {
                    data.zeroAmmoTicks = 0;
                }
            }
            if (data.lifestealAura > 0) {
                for (Entity entity : nearby) {
                    if (!(entity instanceof Player enemy)) continue;
                    if (enemy.getUniqueId().equals(p.getUniqueId())) continue;
                    if (enemy.getLocation().distanceSquared(p.getLocation()) > 6.25) continue; // 2.5^2
                    if (enemy.getGameMode() == GameMode.SPECTATOR) continue;
                    if (!enemy.isOnline() || !enemy.isValid() || enemy.getHealth() <= 0) continue;
                    if (deadPlayers.contains(enemy.getUniqueId()) || pendingCardJoiners.contains(enemy.getUniqueId())) continue;
                    GameTeam enemyTeam = plugin.getTeamManager().getPlayerTeam(enemy.getUniqueId());
                    if (myTeam != null && enemyTeam != null && myTeam != enemyTeam) {
                        var victimAttr = enemy.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                        double victimMaxHp = victimAttr != null ? victimAttr.getValue() : enemy.getMaxHealth();
                        double steal = victimMaxHp * 0.05 * data.lifestealAura;
                        enemy.setNoDamageTicks(0);
                        enemy.damage(steal);
                        p.setHealth(Math.min(p.getHealth() + steal, p.getMaxHealth()));
                        break;
                    }
                }
            }
            if (data.silenceAura > 0) {
                long now = System.currentTimeMillis();
                for (Entity entity : nearby) {
                    if (!(entity instanceof LivingEntity enemy) || enemy.getUniqueId().equals(p.getUniqueId())) continue;
                    if (enemy.getLocation().distanceSquared(p.getLocation()) > 25.0) continue; // 5^2
                    if (entity instanceof Player silenceTarget) {
                        if (silenceTarget.getGameMode() == GameMode.SPECTATOR) continue;
                        GameTeam enemyTeam = plugin.getTeamManager().getPlayerTeam(silenceTarget.getUniqueId());
                        if (myTeam != null && enemyTeam != null && myTeam != enemyTeam) {
                            Long lastSilence = lastSilenceAuraTime.get(silenceTarget.getUniqueId());
                            if (lastSilence == null || now - lastSilence >= 2500) {
                                GunItem.silencePlayer(silenceTarget.getUniqueId());
                                GunItem.cancelReload(silenceTarget.getUniqueId());
                                GunItem.resetShieldActive(silenceTarget.getUniqueId());
                                lastSilenceAuraTime.put(silenceTarget.getUniqueId(), now);
                                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                                    GunItem.unsilencePlayer(silenceTarget.getUniqueId());
                                }, 60L);
                            }
                        }
                    }
                }
            }
            if (data.abyssal > 0) {
                Location lastLoc = abyssalLastLocations.get(p.getUniqueId());
                Location currentLoc = p.getLocation();
                if (lastLoc != null && lastLoc.distanceSquared(currentLoc) < 0.25) {
                    data.abyssalTicks++;
                    int remaining = 10 - data.abyssalTicks;
                    if (remaining > 0) {
                        p.sendTitle("", ChatColor.DARK_PURPLE + "Призыв... " + remaining + "с", 0, 25, 0);
                    }
                    if (data.abyssalTicks >= 10) {
                        data.abyssalTicks = 0;
                        spawnAbyssalPhantom(p, data);
                    }
                } else {
                    if (data.abyssalTicks > 0) {
                        p.sendTitle("", ChatColor.RED + "Призыв сброшен!", 0, 20, 0);
                    }
                    data.abyssalTicks = 0;
                }
                abyssalLastLocations.put(p.getUniqueId(), currentLoc.clone());
            }
            if (data.pristinePerseverance > 0) {
                var attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (attr == null) continue;

                double baseMaxHP = data.getMaxHealth();
                if (data.grow > 0) {
                    baseMaxHP += data.grow * 0.2 * 10;
                }
                double enhancedMaxHP = com.rounds.player.PlayerData.clampMaxHealth(baseMaxHP * (1.0 + data.pristinePerseverance));
                double currentMaxHP = attr.getValue();
                double currentHP = p.getHealth();
                boolean pristineActive = currentMaxHP > baseMaxHP + 0.5;

                if (currentHP >= 0.9 * currentMaxHP) {
                    if (!pristineActive && currentMaxHP < enhancedMaxHP - 0.5) {
                        attr.setBaseValue(enhancedMaxHP);
                        p.setHealth(Math.min(p.getHealth(), attr.getValue()));
                    }
                } else {
                    if (pristineActive) {
                        attr.setBaseValue(baseMaxHP);
                        p.setHealth(Math.min(p.getHealth(), attr.getValue()));
                    }
                }
            }
            if (data.chameleon > 0) {
                Location cur = p.getLocation();
                Location last = chameleonLastLoc.get(p.getUniqueId());
                if (last != null && last.distanceSquared(cur) < 0.01) {
                    int still = chameleonStillTicks.getOrDefault(p.getUniqueId(), 0) + 1;
                    if (still >= 5) {
                        still = 0;
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            PotionEffectType.INVISIBILITY, 140, 0, false, false, false));
                    }
                    chameleonStillTicks.put(p.getUniqueId(), still);
                } else {
                    chameleonStillTicks.remove(p.getUniqueId());
                }
                chameleonLastLoc.put(p.getUniqueId(), cur.clone());
            }
            if (data.speed > 0) {
                double speedRangeSq = Math.pow(5.0 + data.speed * 3.0, 2);
                for (Entity entity : nearby) {
                    if (entity instanceof LivingEntity enemy && !enemy.getUniqueId().equals(p.getUniqueId())) {
                        if (enemy.getLocation().distanceSquared(p.getLocation()) > speedRangeSq) continue;
                        GameTeam enemyTeam = plugin.getTeamManager().getPlayerTeam(enemy.getUniqueId());
                        if (myTeam != null && enemyTeam != null && myTeam != enemyTeam) {
                            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                PotionEffectType.SPEED, 40, 0, true, false, false));
                            break;
                        }
                    }
                }
            }
        }
    }

    private void spawnAbyssalPhantom(Player summoner, PlayerData data) {
        GameTeam summonerTeam = plugin.getTeamManager().getPlayerTeam(summoner.getUniqueId());
        int phantomCount = Math.max((int) Math.round(data.abyssal), 1);

        Location baseLoc = summoner.getLocation().add(0, 3, 0);
        summoner.sendTitle("", ChatColor.DARK_PURPLE + (phantomCount > 1 ? "Призвано фантомов: " + phantomCount + "!" : "Фантом призван!"), 10, 40, 10);
        summoner.getWorld().playSound(baseLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);

        for (int i = 0; i < phantomCount; i++) {
            double angle = phantomCount > 1 ? (2 * Math.PI * i / phantomCount) : 0;
            Location spawnLoc = baseLoc.clone().add(Math.cos(angle) * 1.5, 0, Math.sin(angle) * 1.5);

            Phantom phantom = summoner.getWorld().spawn(spawnLoc, Phantom.class, p -> {
                p.setCustomName(ChatColor.DARK_PURPLE + "Тёмный фантом");
                p.setCustomNameVisible(true);
                var attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (attr != null) attr.setBaseValue(10);
                p.setHealth(10);
                var dmgAttr = p.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
                if (dmgAttr != null) dmgAttr.setBaseValue(12.0);
                p.getPersistentDataContainer().set(RoundsKeys.SUMMONED_PHANTOM,
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            });

            BukkitRunnable phantomTask = new BukkitRunnable() {
                int ticks = 0;
                boolean hasDamaged = false;
                int deathCountdown = -1;
                LivingEntity target = null;

                @Override
                public void run() {
                    if (!phantom.isValid() || phantom.isDead() || ticks >= 400) {
                        if (phantom.isValid()) phantom.remove();
                        phantomTaskIds.remove(getTaskId());
                        cancel();
                        return;
                    }

                    if (deathCountdown >= 0) {
                        deathCountdown++;
                        if (deathCountdown >= 100) {
                            if (phantom.isValid()) phantom.remove();
                            phantomTaskIds.remove(getTaskId());
                            cancel();
                            return;
                        }
                    }

                    boolean targetInvalid = target == null || !target.isValid() || target.isDead()
                            || (target instanceof Player tp && (!tp.isOnline() || tp.getGameMode() == GameMode.SPECTATOR))
                            || deadPlayers.contains(target.getUniqueId());
                    // Цель ищем раз в 10 тиков или когда кэш стал невалиден — не каждый тик.
                    if (targetInvalid || ticks % 10 == 0) {
                        target = findNearestEnemyForPhantom(phantom, summoner, summonerTeam);
                    }

                    if (target != null) {
                        phantom.setTarget(target);
                        Vector direction = target.getLocation().add(0, 1, 0).toVector()
                            .subtract(phantom.getLocation().toVector());
                        if (direction.length() > 0) {
                            phantom.setVelocity(direction.normalize().multiply(0.4));
                        }
                        double dist = phantom.getLocation().distance(target.getLocation());
                        if (dist < 3.0 && ticks % 20 == 0) {
                            target.damage(12.0);
                            RoundsEntities.applyHitRedEffects(target, phantom.getLocation(), target.getLocation());
                            if (!hasDamaged) {
                                hasDamaged = true;
                                deathCountdown = 0;
                            }
                        }
                    } else {
                        phantom.setTarget(null);
                    }

                    ticks++;
                }
            };
            phantomTask.runTaskTimer(plugin, 0L, 1L);
            phantomTaskIds.add(phantomTask.getTaskId());
        }
    }

    public void removeAllPhantoms() {
        for (int id : phantomTaskIds) {
            Bukkit.getScheduler().cancelTask(id);
        }
        phantomTaskIds.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Phantom phantom : world.getEntitiesByClass(Phantom.class)) {
                if (phantom.getPersistentDataContainer().has(RoundsKeys.SUMMONED_PHANTOM,
                        org.bukkit.persistence.PersistentDataType.BYTE)) {
                    phantom.remove();
                }
            }
        }
    }

    private static final double PHANTOM_TARGET_RADIUS = 100.0;

    private LivingEntity findNearestEnemyForPhantom(Phantom phantom, Player summoner, GameTeam summonerTeam) {
        double closest = Double.MAX_VALUE;
        LivingEntity nearest = null;
        for (Entity entity : phantom.getNearbyEntities(PHANTOM_TARGET_RADIUS, PHANTOM_TARGET_RADIUS, PHANTOM_TARGET_RADIUS)) {
            if (!(entity instanceof Player p)) continue;
            if (p.getUniqueId().equals(summoner.getUniqueId())) continue;
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (!p.isOnline() || !p.isValid() || p.getHealth() <= 0) continue;
            if (deadPlayers.contains(p.getUniqueId()) || pendingCardJoiners.contains(p.getUniqueId())) continue;
            if (!isParticipant(p.getUniqueId())) continue;
            GameTeam targetTeam = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (summonerTeam != null && targetTeam != null && summonerTeam == targetTeam) continue;
            double dist = p.getLocation().distanceSquared(phantom.getLocation());
            if (dist < closest) {
                closest = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    private void startGameTick() {
        if (tickTaskId != -1) Bukkit.getScheduler().cancelTask(tickTaskId);
        tickTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (state == GameState.WAITING || state == GameState.GAME_END) return;
            // Авто-финиш: кто-то в сети, но продолжать не может меньше двух участников
            // (например, после ничьей вышли оба, а затем зашёл только один игрок).
            if (!plugin.getServer().getOnlinePlayers().isEmpty() && countContinuablePlayers() < 2) {
                autoStopInsufficientPlayers();
                return;
            }
            if (state == GameState.PLAYING) gameTick();
            else if (state == GameState.CARDS) cardTick();
        }, 0L, 1L);
    }

    /**
     * Сколько игроков в сети могут продолжать игру: состоят в команде,
     * выбирают карты или ожидают выбора карт после переподключения.
     */
    private int countContinuablePlayers() {
        var cm = plugin.getCardManager();
        int count = 0;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getTeamManager().getPlayerTeam(p.getUniqueId()) != null
                    || cm.isPendingPick(p.getUniqueId())
                    || pendingCardJoiners.contains(p.getUniqueId())) {
                count++;
            }
        }
        return count;
    }

    private void autoStopInsufficientPlayers() {
        if (com.rounds.stats.MatchStatsManager.hasActiveMatchData()) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
            com.rounds.stats.MatchStatsManager.registerParticipant(p.getUniqueId(), p.getName());
        }
        com.rounds.stats.MatchStatsManager.onGameEnd(plugin.getPlayerDataManager());
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getTeamManager().getPlayerTeam(p.getUniqueId()) != null) {
                p.sendTitle(Messages.get("title.game-won"), "", 10, 100, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
            p.sendMessage(ChatColor.GREEN + Messages.get("game.auto-stop-single"));
        }
        stopGame();
        plugin.getTeamManager().clearAll();
        getStateManager().clear();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            plugin.getPlayerDataManager().resetPlayerToLobby(p);
        }
    }

    private void gameTick() {
        musicTick++;
        if (autoDeathEnabled) checkAutoDeath();
        if (musicTick % 5 == 0) {
            updateSpectatorGlow();
        }
        if (musicTick % 10 == 0) {
            checkRoundEnd();
            updateAllPlayerNametags();
            tickWrongMapCheck();
            tickHighlightGlowExpires();
        }
        if (musicTick % 20 == 0) {
            applyPeriodicEffects();
        }
        if (musicTick % 40 == 0) {
            sendSpectatorActionbar();
        }
    }

    private void tickHighlightGlowExpires() {
        if (highlightGlowUntil.isEmpty()) return;
        Iterator<Map.Entry<UUID, Long>> it = highlightGlowUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (musicTick >= entry.getValue()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    p.removePotionEffect(PotionEffectType.GLOWING);
                }
                it.remove();
            }
        }
    }

    private void checkAutoDeath() {
        if (!autoDeathEnabled || (plugin != null && !plugin.getRoundsConfig().isAutoDeathEnabled())) {
            hideAutoDeathBossBar();
            return;
        }
        long now = System.currentTimeMillis();
        long intervalMs = (plugin != null) ? plugin.getRoundsConfig().getAutoDeathIntervalSeconds() * 1000L : AUTO_DEATH_INTERVAL_MS;
        int guard = 0;
        while (now >= autoDeathNextEvent && guard++ < 10) {
            if (autoDeathNextIsKill) {
                killRandomPlayer();
                autoDeathNextIsKill = false;
            } else {
                Bukkit.broadcastMessage(ChatColor.DARK_RED + Messages.get("game.auto-death-warning"));
                autoDeathNextIsKill = true;
            }
            autoDeathInitialPhase = false;
            autoDeathPhaseDurationMs = intervalMs;
            autoDeathNextEvent += intervalMs;
            if (autoDeathBossBar != null) {
                autoDeathBossBar.setColor(BarColor.RED);
                autoDeathBossBar.setTitle(Messages.get("game.bossbar-auto-death"));
                autoDeathBossBar.setProgress(1.0);
            }
        }
        if (now - autoDeathNextEvent > intervalMs * 10) autoDeathNextEvent = now;
        updateAutoDeathBossBar(now);
    }

    private void updateAutoDeathBossBar(long now) {
        if (plugin == null || !plugin.getRoundsConfig().isAutoDeathBossBarEnabled()) {
            hideAutoDeathBossBar();
            return;
        }
        if (state != GameState.PLAYING) {
            hideAutoDeathBossBar();
            return;
        }
        String title = autoDeathInitialPhase
                ? Messages.get("game.round-start", currentRound)
                : Messages.get("game.bossbar-auto-death");
        BarColor color = autoDeathInitialPhase ? BarColor.YELLOW : BarColor.RED;

        if (autoDeathBossBar == null) {
            autoDeathBossBar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
        } else {
            autoDeathBossBar.setTitle(title);
            autoDeathBossBar.setColor(color);
        }

        double remainingMs = Math.max(0, autoDeathNextEvent - now);
        double durationMs = Math.max(1000L, autoDeathPhaseDurationMs);
        double progress = Math.max(0.0, Math.min(1.0, remainingMs / durationMs));
        autoDeathBossBar.setProgress(progress);
        autoDeathBossBar.setVisible(true);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getBotManager() != null && plugin.getBotManager().isBot(p)) continue;
            if (!autoDeathBossBar.getPlayers().contains(p)) {
                autoDeathBossBar.addPlayer(p);
            }
        }
        for (Player p : autoDeathBossBar.getPlayers()) {
            if (!p.isOnline()) {
                autoDeathBossBar.removePlayer(p);
            }
        }
    }

    public void hideAutoDeathBossBar() {
        if (autoDeathBossBar != null) {
            autoDeathBossBar.setVisible(false);
            autoDeathBossBar.removeAll();
        }
    }

    private void killRandomPlayer() {
        List<Player> alive = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            if (plugin.getTeamManager().getPlayerTeam(uuid) == null) continue;
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (deadPlayers.contains(uuid) || pendingCardJoiners.contains(uuid)) continue;
            if (!p.isValid() || p.isDead() || p.getHealth() <= 0) continue;
            alive.add(p);
        }
        if (alive.size() <= 1) return;
        Player victim = alive.get(RANDOM.nextInt(alive.size()));
        Bukkit.broadcastMessage(ChatColor.DARK_RED + Messages.get("game.auto-death-killed", victim.getName()));
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.2f);
        com.rounds.entity.RoundsEntities.safeSetHealth(victim, 0.0);
    }

    private void cardTick() {
        musicTick++;
        if (wheelEnabled && musicTick % 120 == 0) {
            plugin.getCardGUI().rotateAllCards();
        }

        // Anti-fall and flight enforcement for Spectators during card selection
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) {
                if (!p.getAllowFlight()) p.setAllowFlight(true);
                if (!p.isFlying()) p.setFlying(true);

                GameTeam t = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
                if (t == null) continue;
                Location safe = teamSpawns.get(t);
                if (safe != null && safe.getWorld() != null && safe.getWorld().equals(p.getWorld())) {
                    if (p.getLocation().getY() < safe.getY() - 1.5) {
                        p.teleport(safe.clone().add(0, 1.2, 0));
                        p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    }
                }
            }
        }

        if (musicTick % 40 == 0) {
            sendSpectatorActionbar();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (!plugin.getCardManager().isPendingPick(p.getUniqueId())) {
                    sendEnemyPickingCardsTitle(p, 60);
                }
            }
        }
    }

    private void sendSpectatorActionbar() {
        String msg = Messages.get("game.spectator-hint");
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getTeamManager().getPlayerTeam(p.getUniqueId()) == null
                    && !deadPlayers.contains(p.getUniqueId())) {
                p.sendActionBar(ChatColor.GREEN + msg);
            }
        }
    }

    public void sendEnemyPickingCardsTitle(Player p, int stay) {
        String full = Messages.get("game.enemy-picking-cards");
        String[] parts = full.split("\n", 2);
        p.sendTitle(ChatColor.YELLOW + parts[0], parts.length > 1 ? ChatColor.YELLOW + parts[1] : "", 10, stay, 20);
    }

    public void onAllCardsPicked() {
        if (state != GameState.CARDS) return;
        startRound();
    }

    private void startRound() {
        currentRound++;
        state = GameState.PLAYING;
        deadPlayers.clear();
        pendingCardJoiners.clear();
        returningJoiners.clear();
        saveState();

        // Fire RoundsRoundStartEvent
        com.rounds.api.event.RoundsRoundStartEvent startEvent =
            new com.rounds.api.event.RoundsRoundStartEvent(currentRound, Bukkit.getOnlinePlayers());
        Bukkit.getPluginManager().callEvent(startEvent);

        // Call onRoundStart for custom cards
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData pData = plugin.getPlayerDataManager().getData(p);
            if (pData != null && !pData.getOwnedCards().isEmpty() && plugin.getCustomDataManager() != null) {
                var container = plugin.getCustomDataManager().getOrCreate(p.getUniqueId());
                var context = new com.rounds.api.impl.CardContextImpl(plugin, p, container);
                for (int cid : pData.getOwnedCards()) {
                    com.rounds.cards.Card c = plugin.getCardManager().getRegistry().getCard(cid);
                    if (c instanceof com.rounds.api.impl.CustomCardAdapter cca) {
                        try { cca.getCustomCard().onRoundStart(context); } catch (Throwable ignored) {}
                    }
                }
            }
        }
        GunItem.resetRoundState();
        com.rounds.listener.LegendaryEffects.clearRoundState();
        chameleonStillTicks.clear();
        long roundTimeMs = (plugin != null) ? plugin.getRoundsConfig().getRoundTimeSeconds() * 1000L : 180_000L;
        autoDeathPhaseDurationMs = Math.max(1000L, roundTimeMs);
        autoDeathNextEvent = System.currentTimeMillis() + autoDeathPhaseDurationMs;
        autoDeathNextIsKill = false;
        autoDeathInitialPhase = true;
        if (autoDeathBossBar != null) {
            autoDeathBossBar.setColor(BarColor.YELLOW);
            autoDeathBossBar.setTitle(Messages.get("game.round-start", currentRound));
            autoDeathBossBar.setProgress(1.0);
        }

        plugin.getServer().broadcastMessage(ChatColor.YELLOW + Messages.get("game.round-start", currentRound));

        if (plugin.getBotManager() != null) {
            plugin.getBotManager().randomizeBotLevels();
        }

        Map<GameTeam, Integer> roundTeamMemberCount = new HashMap<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (team != null) {
                Location baseSpawn = teamSpawns.get(team);
                if (baseSpawn == null) {
                    baseSpawn = getGroundLocation(p.getLocation());
                }
                int memberIdx = roundTeamMemberCount.getOrDefault(team, 0);
                roundTeamMemberCount.put(team, memberIdx + 1);
                Location spawn = getTeammateSpawnLocation(baseSpawn, memberIdx);
                p.teleport(spawn);
                com.rounds.bot.FakeBot bot = (plugin.getBotManager() != null) ? plugin.getBotManager().getBot(p.getName()) : null;
                if (bot != null) {
                    bot.resetPhysics();
                }
                p.setAllowFlight(false);
                p.setFlying(false);
                p.getInventory().clear();
                giveGun(p);
                PlayerData roundData = plugin.getPlayerDataManager().getData(p);
                if (roundData != null) {
                    roundData.ammo = roundData.maxAmmo;
                    applySnowballSpeed(p, roundData);
                }
                clearCardEffects(p);
                applyPlayerHP(p);
                applyRoundEffects(p);
                applyTeamColor(p, false);
                p.setGameMode(GameMode.ADVENTURE);
                p.setFoodLevel(20);
                p.setSaturation(5.0f);
                p.setExhaustion(0f);
                p.setInvulnerable(false);
                p.setNoDamageTicks(0);
            } else {
                p.setPlayerListName(p.getName());
                p.setGameMode(GameMode.SPECTATOR);
                p.setAllowFlight(true);
                p.setFlying(true);
                p.setInvulnerable(true);
                if (currentZoneCenter != null && currentZoneCenter.getWorld() != null) {
                    if (p.getWorld() == null || !p.getWorld().equals(currentZoneCenter.getWorld())
                            || p.getLocation().distanceSquared(currentZoneCenter) > 80.0 * 80.0) {
                        p.teleport(currentZoneCenter.clone().add(0, 3.0, 0));
                    }
                }
            }
        }
        clearAllSpectatorGlow();
        updateSpectatorGlow();
        musicTick = 0;
        showNameTagsInGame();
        updateScoreboard();
        if (plugin.getBotManager() != null) {
            plugin.getBotManager().onRoundStart();
        }
    }

    public Location getTeammateSpawnLocation(Location baseSpawn, int memberIndex) {
        if (baseSpawn == null || memberIndex <= 0) return baseSpawn != null ? baseSpawn.clone() : null;
        World world = baseSpawn.getWorld();
        if (world == null) return baseSpawn.clone();

        // Distribute teammates in rings around the team spawn point:
        // Ring 1 (indices 1-6): 6 slots at 1.4m radius
        // Ring 2 (indices 7-18): 12 slots at 2.6m radius
        double radius;
        double angle;
        if (memberIndex <= 6) {
            radius = 1.4;
            angle = (memberIndex - 1) * (2.0 * Math.PI / 6.0);
        } else {
            radius = 2.6;
            angle = (memberIndex - 7) * (2.0 * Math.PI / 12.0);
        }

        double ox = Math.cos(angle) * radius;
        double oz = Math.sin(angle) * radius;

        Location candidate = baseSpawn.clone().add(ox, 0, oz);
        Block feet = candidate.getBlock();
        Block head = candidate.clone().add(0, 1, 0).getBlock();
        Block floor = candidate.clone().subtract(0, 1, 0).getBlock();

        if (!feet.getType().isSolid() && !head.getType().isSolid() && floor.getType().isSolid()) {
            return candidate;
        }

        // Try Y+1 if on a step
        Block floorUp = feet;
        Block feetUp = head;
        Block headUp = candidate.clone().add(0, 2, 0).getBlock();
        if (floorUp.getType().isSolid() && !feetUp.getType().isSolid() && !headUp.getType().isSolid()) {
            return candidate.add(0, 1, 0);
        }

        // Try Y-1 if on a slope
        Block floorDown = candidate.clone().subtract(0, 2, 0).getBlock();
        if (floorDown.getType().isSolid() && !floor.getType().isSolid() && !feet.getType().isSolid()) {
            return candidate.subtract(0, 1, 0);
        }

        return baseSpawn.clone();
    }

    private void pickRandomZoneAndAssignSpawns() {
        BlockStorage bs = plugin.getBlockListener().getBlockStorage();
        List<BlockStorage.MapBlock> zones = bs.getMapBlocks();
        Set<GameTeam> teams = new HashSet<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam t = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (t != null) teams.add(t);
        }
        teamSpawns.clear();

        if (!zones.isEmpty()) {
            int chosen = RANDOM.nextInt(zones.size());
            currentZoneCenter = zones.get(chosen).center.clone();

            List<Location> spawns = bs.getSpawnBlocksInZone(zones.get(chosen));
            if (!spawns.isEmpty()) {
                teamSpawns.putAll(SpawnManager.assignSpawns(teams, spawns));
                return;
            }
        }

        // Fallback when no map zones/spawns are configured
        Location base = bs.getLobbyBlock();
        if (base == null && !plugin.getServer().getOnlinePlayers().isEmpty()) {
            base = plugin.getServer().getOnlinePlayers().iterator().next().getLocation();
        }
        if (base != null) {
            Location ground = getGroundLocation(base);
            currentZoneCenter = ground.clone();
            teamSpawns.put(GameTeam.RED, ground.clone().add(-2.0, 0, 0));
            teamSpawns.put(GameTeam.BLUE, ground.clone().add(2.0, 0, 0));
        }
    }

    private void checkRoundEnd() {
        if (state != GameState.PLAYING) return;
        Set<GameTeam> aliveTeams = new HashSet<>();
        int totalAlive = 0;
        for (GameTeam team : GameTeam.values()) {
            int alive = 0;
            for (UUID uuid : plugin.getTeamManager().getTeamPlayers(team)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline() && p.isValid()
                        && (p.getGameMode() != GameMode.SPECTATOR || plugin.getCardManager().isPendingPick(uuid) || isPendingCardJoiner(uuid))
                        && p.getHealth() > 0
                        && !p.isDead()
                        && !deadPlayers.contains(uuid)) {
                    alive++;
                }
            }
            if (alive > 0) {
                aliveTeams.add(team);
            }
            totalAlive += alive;
        }

        if (totalAlive == 0) {
            drawRound();
            return;
        }

        if (aliveTeams.size() == 1) {
            Set<UUID> participants = new HashSet<>(plugin.getPlayerDataManager().getActivePlayers());
            for (GameTeam team : GameTeam.values()) {
                participants.addAll(plugin.getTeamManager().getTeamPlayers(team));
            }
            if (participants.size() > 1) {
                GameTeam winner = aliveTeams.iterator().next();
                endRound(winner);
            }
        }
    }

    /**
     * Мгновенная очистка боевого состояния: пули, кольца/облака, фантомы,
     * таймеры (пилы, перезарядка, hpBoost), зелья-эффекты.
     * Вызывается при конце раунда, конце игры и /rdebug stop.
     */
    public void cleanupRoundCombatState() {
        cancelAllWrongMapTasks(state == GameState.WAITING || state == GameState.GAME_END ? "game-ended" : "round-ended");
        lastWrongMapCheckTime.clear();
        lastWrongMapResult.clear();
        cancelScheduledTasks();
        removeAllPhantoms();
        RoundsEntities.clearAllState();
        GunItem.resetRoundState();
        com.rounds.listener.LegendaryEffects.clearRoundState();
        highlightGlowUntil.clear();
        hideAutoDeathBossBar();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            clearCardEffects(p);
            p.setNoDamageTicks(0);
        }
    }

    private void endRound(GameTeam winner) {
        try {
            int cur = (int) getCurrentRound();
            if (com.rounds.debug.DebugSession.active) {
                com.rounds.debug.DebugLog.log("GAME", "END round=" + cur + " winner=" + (winner == null ? "NONE" : winner.name()));
                com.rounds.debug.DebugLog.flushRoundEnd(cur);
                com.rounds.debug.DebugLog.log("CLEAN", "Beginning cleanup for R#" + cur + " ...");
            }
            if (com.rounds.debug.DebugSession.active && com.rounds.debug.DebugSession.stopAt != -1 && com.rounds.debug.DebugSession.stopAt == cur) {
                com.rounds.debug.DebugLog.flushFinal("AUTO-STOP at end of R#" + cur);
                com.rounds.debug.DebugSession.clear();
                // will continue cleanup but debug stopped
            }
        } catch (Throwable ignored) {}
        plugin.getTeamManager().addWin(winner);
        state = GameState.ROUND_END;
        losingTeams.clear();
        saveState();
        cleanupRoundCombatState();

        List<Player> winners = new ArrayList<>();
        if (winner != null) {
            for (UUID u : plugin.getTeamManager().getTeamPlayers(winner)) {
                Player wp = Bukkit.getPlayer(u);
                if (wp != null && wp.isOnline()) winners.add(wp);
            }
        }
        Map<String, Integer> teamScores = new HashMap<>();
        for (GameTeam t : GameTeam.values()) {
            teamScores.put(t.name(), plugin.getTeamManager().getWins(t));
        }
        com.rounds.api.event.RoundsRoundEndEvent endEvent =
            new com.rounds.api.event.RoundsRoundEndEvent(currentRound, winner != null ? winner.name() : null, winners, teamScores);
        Bukkit.getPluginManager().callEvent(endEvent);

        for (Player p : Bukkit.getOnlinePlayers()) {
            GameTeam pTeam = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            boolean teamWon = (winner != null && winner == pTeam);
            PlayerData pData = plugin.getPlayerDataManager().getData(p);
            if (pData != null && !pData.getOwnedCards().isEmpty() && plugin.getCustomDataManager() != null) {
                var container = plugin.getCustomDataManager().getOrCreate(p.getUniqueId());
                var context = new com.rounds.api.impl.CardContextImpl(plugin, p, container);
                for (int cid : pData.getOwnedCards()) {
                    com.rounds.cards.Card c = plugin.getCardManager().getRegistry().getCard(cid);
                    if (c instanceof com.rounds.api.impl.CustomCardAdapter cca) {
                        try { cca.getCustomCard().onRoundEnd(context, teamWon); } catch (Throwable ignored) {}
                    }
                }
            }
        }

        for (UUID uuid : plugin.getTeamManager().getTeamPlayers(winner)) {
            Player wp = Bukkit.getPlayer(uuid);
            if (wp == null || !wp.isOnline() || !wp.isValid()) continue;
            PlayerData wd = plugin.getPlayerDataManager().getData(wp);
            if (wd.snowball > 0) {
                wd.snowballWins++;
                applySnowballSpeed(wp, wd);
            }
        }

        for (GameTeam team : GameTeam.values()) {
            if (team != winner && (plugin.getTeamManager().getPlayerCount(team) > 0 || !plugin.getTeamManager().getTeamPlayers(team).isEmpty())) {
                losingTeams.add(team);
            }
        }
        if (losingTeams.isEmpty()) {
            for (UUID uuid : plugin.getPlayerDataManager().getActivePlayers()) {
                GameTeam t = plugin.getTeamManager().getPlayerTeam(uuid);
                if (t != null && t != winner) {
                    losingTeams.add(t);
                }
            }
        }

        String teamName = Messages.get("team." + winner.name().toLowerCase());
        plugin.getServer().broadcastMessage(ChatColor.GREEN + winner.getColor().toString() + teamName + " " + Messages.get("game.round-won"));
        plugin.getServer().broadcastMessage(ChatColor.GOLD + Messages.get("game.score", getScoreDisplay()));

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam pTeam = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (pTeam == winner) {
                p.sendTitle(Messages.get("title.won"), "", 10, 60, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            } else if (losingTeams.contains(pTeam)) {
                p.sendTitle(Messages.get("title.lost"), "", 10, 60, 20);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            } else {
                String specTitle = Messages.get("title.spectator-won", winner.getColor() + "§l", teamName.toLowerCase());
                if (specTitle.equals("title.spectator-won")) {
                    specTitle = winner.getColor() + "§lПобедили " + teamName.toLowerCase();
                }
                p.sendTitle(specTitle, "", 10, 60, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }

        for (GameTeam team : GameTeam.values()) {
            if (plugin.getTeamManager().getWins(team) >= roundsToWin) {
                endGame(team);
                return;
            }
        }

        scheduleDelayed(() -> {
            state = GameState.CARDS;
            saveState();
            musicTick = 0;
            plugin.getCardManager().clearPendingPicks();
            pickRandomZoneAndAssignSpawns();
            applyDarkness();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
                if (team != null) {
                    Location spawn = teamSpawns.get(team);
                    if (spawn != null) p.teleport(spawn);
                    p.setFireTicks(0);
                    p.setHealth(p.getMaxHealth());
                    p.setInvulnerable(true);
                    p.setVelocity(new Vector(0, 0, 0));
                    p.setFoodLevel(20);
                    p.setSaturation(5.0f);
                    p.setExhaustion(0f);
                } else {
                    p.setGameMode(GameMode.SPECTATOR);
                    p.setAllowFlight(true);
                    p.setFlying(true);
                    p.setInvulnerable(true);
                    if (currentZoneCenter != null && currentZoneCenter.getWorld() != null) {
                        p.teleport(currentZoneCenter.clone().add(0, 3.0, 0));
                    }
                }
            }
            openCardGUIs();
            updateScoreboard();
        }, 60L);
    }

    private void drawRound() {
        state = GameState.ROUND_END;
        losingTeams.clear();
        saveState();
        cleanupRoundCombatState();
        plugin.getServer().broadcastMessage(ChatColor.YELLOW + Messages.get("game.draw"));

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendTitle(Messages.get("title.draw"), "", 10, 60, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        scheduleDelayed(() -> {
            state = GameState.CARDS;
            saveState();
            musicTick = 0;
            plugin.getCardManager().clearPendingPicks();
            pickRandomZoneAndAssignSpawns();
            applyDarkness();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
                if (team != null) {
                    Location spawn = teamSpawns.get(team);
                    if (spawn != null) p.teleport(spawn);
                    p.setFireTicks(0);
                    p.setHealth(p.getMaxHealth());
                    p.setInvulnerable(true);
                    p.setVelocity(new Vector(0, 0, 0));
                    p.setFoodLevel(20);
                    p.setSaturation(5.0f);
                    p.setExhaustion(0f);
                } else {
                    p.setGameMode(GameMode.SPECTATOR);
                    p.setAllowFlight(true);
                    p.setFlying(true);
                    p.setInvulnerable(true);
                    if (currentZoneCenter != null && currentZoneCenter.getWorld() != null) {
                        p.teleport(currentZoneCenter.clone().add(0, 3.0, 0));
                    }
                }
            }
            openCardGUIs(true);
            updateScoreboard();
        }, 60L);
    }

    private void endGame(GameTeam winner) {
        state = GameState.GAME_END;
        saveState();
        stopGameTick();
        cleanupRoundCombatState();

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            com.rounds.stats.MatchStatsManager.registerParticipant(p.getUniqueId(), p.getName());
        }
        com.rounds.stats.MatchStatsManager.onGameEnd(plugin.getPlayerDataManager());

        if (plugin.getBotManager() != null) {
            plugin.getBotManager().despawnAll();
        }

        String teamName = Messages.get("team." + winner.name().toLowerCase());
        plugin.getServer().broadcastMessage(ChatColor.GREEN + Messages.get("game.game-over"));
        plugin.getServer().broadcastMessage(ChatColor.GREEN + winner.getColor().toString() + teamName + " " + Messages.get("game.game-won"));
        plugin.getServer().broadcastMessage(ChatColor.GOLD + Messages.get("game.final-score", getScoreDisplay()));

        scheduleDelayed(() -> {
            String notice = Messages.get("debug.statsend-notice");
            if (notice != null && !notice.isEmpty()) {
                plugin.getServer().broadcastMessage(ChatColor.GOLD + notice);
            }
        }, 60L);

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam pTeam = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (pTeam == winner) {
                p.sendTitle(Messages.get("title.game-won"), "", 10, 100, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            } else if (pTeam != null) {
                p.sendTitle(Messages.get("title.game-lost"), "", 10, 100, 20);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            } else {
                String specTitle = Messages.get("title.spectator-won", winner.getColor() + "§l", teamName.toLowerCase());
                if (specTitle.equals("title.spectator-won")) {
                    specTitle = winner.getColor() + "§lПобедили " + teamName.toLowerCase();
                }
                p.sendTitle(specTitle, "", 10, 100, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }

        scheduleDelayed(() -> {
            state = GameState.WAITING;
            gameActive = false;
            clearAllSpectatorGlow();
            saveState();
            removeScoreboard();
        RoundsEntities.clearAllDamageVignettes();
            if (plugin.getBotManager() != null) {
                plugin.getBotManager().despawnAll();
            }
            plugin.getTeamManager().clearAll();
            plugin.getPlayerDataManager().clearActivePlayers();
            resetAllNameColors();
            plugin.getCardManager().resetAllCards();
            GunItem.resetRoundState();
            RoundsEntities.clearAllState();
            restoreGameRules();
            teamSpawns.clear();
            resetWins();
            deadPlayers.clear();
            abyssalLastLocations.clear();
            lastSilenceAuraTime.clear();
            syncedHearts.clear();
            chameleonLastLoc.clear();
            chameleonStillTicks.clear();
            com.rounds.listener.LegendaryEffects.resetAll();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                p.getInventory().clear();
                var attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (attr != null) attr.setBaseValue(20);
                p.setHealth(Math.min(p.getHealth(), 20));
                p.setFoodLevel(20);
                p.setInvulnerable(false);
                p.setNoDamageTicks(0);
                p.setGameMode(GameMode.ADVENTURE);
                p.setAllowFlight(false);
                p.setFlying(false);
                clearCardEffects(p);
                removeSnowballModifiers(p);
                Location lobby = plugin.getBlockListener().getBlockStorage().getLobbyBlock();
                if (lobby != null) p.teleport(lobby.clone().add(0, 1, 0));
            }
        }, 100L);
    }

    private void openCardGUIs() {
        openCardGUIs(false);
    }

    private void openCardGUIs(boolean allTeams) {
        removeDarkness();
        Set<UUID> pickers = new HashSet<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            boolean shouldPick = team != null && (allTeams || losingTeams.contains(team) || pendingCardJoiners.contains(p.getUniqueId()));
            if (shouldPick) {
                pickers.add(p.getUniqueId());
            }
        }

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (pickers.contains(p.getUniqueId())) {
                p.setGameMode(GameMode.ADVENTURE);
                p.setAllowFlight(false);
                p.setFlying(false);
                p.setInvulnerable(true);
                GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
                if (!p.isDead()) {
                    plugin.getCardManager().openCardSelection(p, team);
                }
            } else {
                // Not picking this round: team members wait in Adventure mode at spawn, true spectators stay in Spectator
                GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
                if (team != null) {
                    p.setGameMode(GameMode.ADVENTURE);
                    p.setAllowFlight(false);
                    p.setFlying(false);
                    p.setInvulnerable(true);
                    p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    Location safe = teamSpawns.get(team);
                    if (safe != null && safe.getWorld() != null && safe.getWorld().equals(p.getWorld())) {
                        p.teleport(safe.clone().add(0, 1.2, 0));
                    }
                    sendEnemyPickingCardsTitle(p, 100);
                } else {
                    p.setGameMode(GameMode.SPECTATOR);
                    p.setAllowFlight(true);
                    p.setFlying(true);
                    p.setInvulnerable(true);
                }
            }
        }
        pendingCardJoiners.clear();
        returningJoiners.clear();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    private void openCardGUIsDraw() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (team == null) {
                p.setGameMode(GameMode.SPECTATOR);
                p.setAllowFlight(true);
                p.setFlying(true);
                p.setInvulnerable(true);
            } else {
                p.setGameMode(GameMode.ADVENTURE);
                p.setAllowFlight(false);
                p.setFlying(false);
                p.setInvulnerable(true);
            }
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (team != null || pendingCardJoiners.contains(p.getUniqueId())) {
                if (p.isDead()) continue;
                plugin.getCardManager().openCardSelection(p, team);
            }
        }
        pendingCardJoiners.clear();
        returningJoiners.clear();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    public void clearCardEffects(Player player) {
        for (PotionEffectType type : PotionEffectType.values()) {
            player.removePotionEffect(type);
        }
    }

    private void applySnowballSpeed(Player player, PlayerData data) {
        var attr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr == null) return;
        removeSnowballModifiers(player);
        if (data.snowballWins > 0) {
            AttributeModifier mod = new AttributeModifier(SNOWBALL_MOD_ID, "rounds_snowball",
                0.05 * data.snowballWins * Math.max(data.snowball, 1.0), AttributeModifier.Operation.ADD_SCALAR);
            try {
                attr.addModifier(mod);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void removeSnowballModifiers(Player player) {
        var attr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr == null) return;
        for (AttributeModifier mod : new ArrayList<>(attr.getModifiers())) {
            if (!isSnowballModifier(mod)) continue;
            try {
                attr.removeModifier(mod);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private boolean isSnowballModifier(AttributeModifier mod) {
        try {
            if (SNOWBALL_MOD_ID.equals(mod.getUniqueId())) return true;
        } catch (Throwable ignored) {
        }
        String name = mod.getName();
        return name != null && name.toLowerCase(Locale.ROOT).contains("rounds_snowball");
    }

    private void applyDarkness() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                PotionEffectType.DARKNESS, 200, 0, false, false, false));
        }
    }

    private void removeDarkness() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!isGameStarted()) return;
        int oldLevel = ((Player) event.getEntity()).getFoodLevel();
        if (event.getFoodLevel() < oldLevel) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (state == GameState.CARDS && event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                GameTeam team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
                Location spawn = (team != null) ? teamSpawns.get(team) : null;
                if (spawn != null) {
                    player.teleport(spawn);
                } else if (currentZoneCenter != null) {
                    player.teleport(currentZoneCenter);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (state != GameState.CARDS) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (!isParticipant(player.getUniqueId()) && plugin.getTeamManager().getPlayerTeam(player.getUniqueId()) == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Freeze position (X, Y, Z) while cards are being picked, allow looking around (yaw, pitch)
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location locked = from.clone();
            locked.setYaw(to.getYaw());
            locked.setPitch(to.getPitch());
            event.setTo(locked);
            player.setVelocity(new Vector(0, 0, 0));
        }
    }

    private void resetWins() {         plugin.getTeamManager().resetWins();
        currentRound = 0; }
    private void resetAllCards() { plugin.getCardManager().resetAllCards(); }

    public String getScoreDisplay() {
        StringBuilder sb = new StringBuilder();
        for (GameTeam gt : GameTeam.values()) {
            String teamName = Messages.get("team." + gt.name().toLowerCase());
            sb.append(gt.getColor().toString()).append(teamName).append(": ").append((int) plugin.getTeamManager().getWins(gt)).append(" ");
        }
        return sb.toString();
    }

    private List<Player> getReadyPlayers() {
        List<Player> result = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getTeamManager().getPlayerTeam(p.getUniqueId()) != null) {
                result.add(p);
            }
        }
        return result;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (state != GameState.PLAYING) return;
        Player dead = event.getEntity();
        GameTeam deadTeam = plugin.getTeamManager().getPlayerTeam(dead.getUniqueId());
        if (deadTeam == null) return;
        event.getDrops().clear();
        event.setDroppedExp(0);

        PlayerData deadData = plugin.getPlayerDataManager().getData(dead);
        if (deadData.phoenixUses > 0) {
            deadData.phoenixUses--;
            event.setCancelled(true);
            dead.setFallDistance(0);
            dead.setFireTicks(0);
            dead.setHealth(dead.getMaxHealth() / 2);
            dead.setNoDamageTicks(20);
            dead.addPotionEffect(new org.bukkit.potion.PotionEffect(
                PotionEffectType.REGENERATION, 100, 2));
            dead.getWorld().playSound(dead.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
            dead.sendMessage(ChatColor.RED + Messages.get("game.phoenix-revived"));
            return;
        }

        if (deadData.implode > 0) {
            Location deathLoc = dead.getLocation();
            deathLoc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, deathLoc, 1, 0, 0, 0, 0);
            deathLoc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, deathLoc, 3, 0.5, 0.5, 0.5, 0.05);
            for (Entity entity : dead.getNearbyEntities(4.0, 4.0, 4.0)) {
                if (entity instanceof LivingEntity target && !target.getUniqueId().equals(dead.getUniqueId())) {
                    if (target instanceof Player tp) {
                        if (tp.getGameMode() == GameMode.SPECTATOR) continue;
                        GameTeam targetTeam = plugin.getTeamManager().getPlayerTeam(tp.getUniqueId());
                        if (deadTeam != null && targetTeam != null && deadTeam == targetTeam) continue;
                    }
                    try {
                        double dist = target.getLocation().distance(deathLoc);
                        double dmg = Math.max(0, 8.0 * Math.max(deadData.implode, 1.0) * (1.0 - dist / 4.0));
                        if (dmg > 0) target.damage(dmg);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            dead.getWorld().playSound(deathLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
        }

        deadPlayers.add(dead.getUniqueId());
        com.rounds.stats.MatchStatsManager.recordDeath(dead.getUniqueId());
        highlightGlowUntil.remove(dead.getUniqueId());
        updateTeamNametagVisibility();
        refreshTeamScoreboards();

        Player killer = dead.getKiller();
        if (killer != null) {
            GameTeam killerTeam = plugin.getTeamManager().getPlayerTeam(killer.getUniqueId());
            PlayerData killerData = plugin.getPlayerDataManager().getData(killer);
            if (killerTeam != null && killerTeam != deadTeam) {
                com.rounds.stats.MatchStatsManager.recordKill(killer.getUniqueId());
                killer.sendMessage(ChatColor.GREEN + Messages.get("game.eliminated", dead.getName()));
                if (killerData.refresh > 0) {
                    killerData.ammo = killerData.maxAmmo;
                    GunItem.cancelReload(killer.getUniqueId());
                    killer.sendMessage(ChatColor.GREEN + Messages.get("game.refresh-ammo"));
                }
                if (killerData.tacticalReload > 0) {
                    killerData.ammo = killerData.maxAmmo;
                    GunItem.cancelReload(killer.getUniqueId());
                }
            }
        }
        dead.sendMessage(ChatColor.RED + Messages.get("game.you-eliminated"));
        checkRoundEnd();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (state != GameState.PLAYING && state != GameState.ROUND_END && state != GameState.CARDS) return;
        Player player = event.getPlayer();

        if (state == GameState.PLAYING && deadPlayers.contains(player.getUniqueId())) {
            event.setRespawnLocation(player.getWorld().getSpawnLocation());
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                player.setInvulnerable(true);
                GameTeam deadTeam = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
                Location spawn = deadTeam != null ? teamSpawns.get(deadTeam) : null;
                if (spawn == null) spawn = currentZoneCenter;
                if (spawn != null) player.teleport(spawn);
            }, 1L);
            return;
        }

        event.setRespawnLocation(player.getWorld().getSpawnLocation());

        GameTeam team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                player.setInvulnerable(true);
                Player target = findRandomAlivePlayer();
                if (target != null) {
                    player.teleport(target.getLocation());
                }
            }, plugin.getRoundsConfig().getRespawnDelayTicks());
            return;
        }

        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            if (state == GameState.PLAYING) {
                giveGun(player);
                applyPlayerHP(player);
            } else if (state == GameState.CARDS) {
                if (team != null && (losingTeams.contains(team) || plugin.getCardManager().isPendingPick(player.getUniqueId()))) {
                    plugin.getCardManager().openCardSelection(player, team);
                }
                player.setInvulnerable(true);
            } else if (state == GameState.ROUND_END) {
                player.setInvulnerable(true);
            }
            player.setGameMode(GameMode.ADVENTURE);
            player.setFoodLevel(20);
            player.setSaturation(5.0f);
            player.setExhaustion(0f);
        }, plugin.getRoundsConfig().getRespawnDelayTicks());
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (state != GameState.PLAYING) return;
        Player p = event.getPlayer();
        GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
        if (team == null || p.getGameMode() == GameMode.SPECTATOR || deadPlayers.contains(p.getUniqueId()) || p.isDead()) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (state == GameState.PLAYING && isPlayerOnWrongMap(p)) {
                triggerWrongMapTask(p);
            }
        });
    }

    public boolean isPlayerOnWrongMap(Player p) {
        if (p == null || currentZoneCenter == null || currentZoneCenter.getWorld() == null) {
            return false;
        }
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTime = lastWrongMapCheckTime.get(uuid);
        if (lastTime != null && (now - lastTime) < 5000L) {
            return lastWrongMapResult.getOrDefault(uuid, false);
        }
        lastWrongMapCheckTime.put(uuid, now);

        boolean result = computeIsPlayerOnWrongMap(p);
        lastWrongMapResult.put(uuid, result);
        return result;
    }

    private boolean computeIsPlayerOnWrongMap(Player p) {
        Location loc = p.getLocation();
        if (loc.getWorld() == null) return false;

        BlockStorage bs = plugin.getBlockListener().getBlockStorage();
        if (bs == null) return false;

        // 1. If player is inside current map zone, they are safe
        BlockStorage.MapBlock currentZone = null;
        for (BlockStorage.MapBlock mb : bs.getMapBlocks()) {
            if (mb.centerEquals(currentZoneCenter)) {
                currentZone = mb;
                break;
            }
        }
        if (currentZone != null && currentZone.center.getWorld() != null && currentZone.center.getWorld().equals(loc.getWorld())) {
            int half = currentZone.size / 2;
            if (Math.abs(loc.getBlockX() - currentZone.center.getBlockX()) <= half
                    && Math.abs(loc.getBlockZ() - currentZone.center.getBlockZ()) <= half) {
                return false;
            }
        } else if (currentZone == null && loc.getWorld().equals(currentZoneCenter.getWorld())) {
            if (loc.distanceSquared(currentZoneCenter) <= 2500) return false;
        }

        // 2. Check if player is near another map's center or inside another map zone
        for (BlockStorage.MapBlock mb : bs.getMapBlocks()) {
            if (mb.centerEquals(currentZoneCenter)) continue;
            if (mb.center.getWorld() == null || !mb.center.getWorld().equals(loc.getWorld())) continue;

            if (mb.center.distanceSquared(loc) <= 36.0) {
                return true;
            }
            int half = mb.size / 2;
            if (Math.abs(loc.getBlockX() - mb.center.getBlockX()) <= half
                    && Math.abs(loc.getBlockZ() - mb.center.getBlockZ()) <= half) {
                return true;
            }
        }

        return false;
    }

    private String checkCancellationReason(WrongMapTeleportTask task, Player p) {
        if (task.startGeneration != gameGeneration || !gameActive || state == GameState.WAITING || state == GameState.GAME_END) {
            return "game-ended";
        }
        if (state != GameState.PLAYING || currentRound != task.startRound) {
            return "round-ended";
        }
        if (currentZoneCenter == null || task.startZoneCenter == null
                || !task.startZoneCenter.getWorld().equals(currentZoneCenter.getWorld())
                || task.startZoneCenter.getBlockX() != currentZoneCenter.getBlockX()
                || task.startZoneCenter.getBlockY() != currentZoneCenter.getBlockY()
                || task.startZoneCenter.getBlockZ() != currentZoneCenter.getBlockZ()) {
            return "map-changed";
        }
        if (p == null || !p.isOnline() || !p.isValid()) {
            return "player-offline";
        }
        if (p.isDead() || p.getHealth() <= 0 || deadPlayers.contains(p.getUniqueId())) {
            return "player-died";
        }
        GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
        if (team == null || p.getGameMode() == GameMode.SPECTATOR) {
            return "player-spectator";
        }
        if (!isPlayerOnWrongMap(p)) {
            return "player-returned";
        }
        return null;
    }

    private Location getWrongMapTeleportTarget(Player p) {
        if (p == null) return null;
        GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
        if (team == null) return null;

        Location teamSpawn = teamSpawns.get(team);
        if (teamSpawn != null && teamSpawn.getWorld() != null) {
            return teamSpawn.clone();
        }

        BlockStorage bs = plugin.getBlockListener().getBlockStorage();
        if (bs != null && currentZoneCenter != null) {
            BlockStorage.MapBlock currentZone = null;
            for (BlockStorage.MapBlock mb : bs.getMapBlocks()) {
                if (mb.centerEquals(currentZoneCenter)) {
                    currentZone = mb;
                    break;
                }
            }
            if (currentZone != null) {
                List<Location> spawns = bs.getSpawnBlocksInZone(currentZone);
                if (!spawns.isEmpty()) {
                    Location chosen = spawns.get(RANDOM.nextInt(spawns.size())).clone();
                    return chosen.add(0.5, 1.0, 0.5);
                }
            }
        }

        if (currentZoneCenter != null && currentZoneCenter.getWorld() != null) {
            return getGroundLocation(currentZoneCenter.clone().add(0.5, 0, 0.5));
        }

        return null;
    }

    private void triggerWrongMapTask(Player p) {
        if (p == null || wrongMapTasks.containsKey(p.getUniqueId())) return;
        WrongMapTeleportTask task = new WrongMapTeleportTask(p.getUniqueId(), currentRound, gameGeneration, currentZoneCenter);
        wrongMapTasks.put(p.getUniqueId(), task);
        p.sendMessage(Messages.get("game.wrong-map-warning", task.lastAnnouncedSecond));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    private void cancelAllWrongMapTasks(String reasonKey) {
        if (wrongMapTasks.isEmpty()) return;
        String reasonText = Messages.get("game.cancel-reason." + reasonKey);
        String cancelMsg = Messages.get("game.wrong-map-cancelled", reasonText);
        for (UUID uuid : wrongMapTasks.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(cancelMsg);
            }
        }
        wrongMapTasks.clear();
    }

    private void tickWrongMapCheck() {
        if (state != GameState.PLAYING) {
            cancelAllWrongMapTasks("round-ended");
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, WrongMapTeleportTask>> it = wrongMapTasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, WrongMapTeleportTask> entry = it.next();
            UUID uuid = entry.getKey();
            WrongMapTeleportTask task = entry.getValue();
            Player p = Bukkit.getPlayer(uuid);

            String cancelReason = checkCancellationReason(task, p);
            if (cancelReason != null) {
                it.remove();
                if (p != null && p.isOnline()) {
                    String reasonText = Messages.get("game.cancel-reason." + cancelReason);
                    p.sendMessage(Messages.get("game.wrong-map-cancelled", reasonText));
                }
                continue;
            }

            long remainingMs = task.finishTimeMs - now;
            int remainingSec = (int) Math.max(0, Math.ceil(remainingMs / 1000.0));
            if (remainingMs <= 0) {
                it.remove();
                if (p != null && p.isOnline()) {
                    Location target = getWrongMapTeleportTarget(p);
                    if (target == null) {
                        String reasonText = Messages.get("game.cancel-reason.no-spawns");
                        p.sendMessage(Messages.get("game.wrong-map-cancelled", reasonText));
                    } else {
                        p.teleport(target);
                        lastWrongMapCheckTime.remove(p.getUniqueId());
                        lastWrongMapResult.remove(p.getUniqueId());
                        p.sendMessage(Messages.get("game.wrong-map-teleported"));
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    }
                }
            } else if (remainingSec < task.lastAnnouncedSecond) {
                task.lastAnnouncedSecond = remainingSec;
                if (p != null && p.isOnline()) {
                    p.sendMessage(Messages.get("game.wrong-map-warning", remainingSec));
                }
            }
        }

        // Periodic wrong map scan: evaluated every 5 seconds (100 ticks), not earlier
        if (musicTick % 100 == 0) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (wrongMapTasks.containsKey(p.getUniqueId())) continue;
                GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
                if (team == null) continue;
                if (p.getGameMode() == GameMode.SPECTATOR || deadPlayers.contains(p.getUniqueId()) || p.isDead()) continue;

                if (isPlayerOnWrongMap(p)) {
                    triggerWrongMapTask(p);
                }
            }
        }
    }

    public void shutdown() {
        stopScoreboardTask();
        removeScoreboard();
        stopGameTick();
        cancelScheduledTasks();
        removeAllPhantoms();
        restoreGameRules();
        cleanupRoundCombatState();
    }

    @SuppressWarnings("unchecked")
    private <T> void saveAndSet(World world, GameRule<T> rule, T value) {
        T original = world.getGameRuleValue(rule);
        if (!savedGameRules.containsKey(rule)) {
            savedGameRules.put(rule, original);
        }
        world.setGameRule(rule, value);
    }

    private void applyGameRules() {
        RoundsConfig config = plugin.getRoundsConfig();
        if (!config.isGameRulesEnabled()) return;
        for (World world : Bukkit.getWorlds()) {
            if (config.isGrInstantRespawn()) saveAndSet(world, GameRule.DO_IMMEDIATE_RESPAWN, true);
            if (config.isGrKeepInventory()) saveAndSet(world, GameRule.KEEP_INVENTORY, true);
            if (config.isGrFreezeTime()) {
                saveAndSet(world, GameRule.DO_DAYLIGHT_CYCLE, false);
                try { world.setTime(1000); } catch (IllegalArgumentException ignored) {}
            }
            if (config.isGrDisableWeather()) {
                saveAndSet(world, GameRule.DO_WEATHER_CYCLE, false);
                try { world.setClearWeatherDuration(6000); } catch (IllegalArgumentException ignored) {}
            }
            if (config.isGrDisableMobSpawning()) saveAndSet(world, GameRule.DO_MOB_SPAWNING, false);
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreGameRules() {
        for (World world : Bukkit.getWorlds()) {
            for (Map.Entry<GameRule<?>, Object> entry : savedGameRules.entrySet()) {
                GameRule<Object> rule = (GameRule<Object>) entry.getKey();
                world.setGameRule(rule, entry.getValue());
            }
        }
        savedGameRules.clear();
    }

    public void stopGame() {
        if (plugin.getBotManager() != null) {
            plugin.getBotManager().despawnAll();
        }
        if (state == GameState.WAITING) return;
        if (com.rounds.stats.MatchStatsManager.hasActiveMatchData()) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
            com.rounds.stats.MatchStatsManager.registerParticipant(p.getUniqueId(), p.getName());
        }
        com.rounds.stats.MatchStatsManager.onGameEnd(plugin.getPlayerDataManager());
        }
        gameActive = false;
        gameGeneration++;
        stopGameTick();
        cancelScheduledTasks();
        plugin.getCardManager().clearPendingPicks();
        plugin.getCardManager().resetAllCards();
        cleanupRoundCombatState();
        restoreGameRules();
        removeDarkness();
        teamSpawns.clear();
        resetWins();
        deadPlayers.clear();
        abyssalLastLocations.clear();
        lastSilenceAuraTime.clear();
        syncedHearts.clear();
        chameleonLastLoc.clear();
        chameleonStillTicks.clear();
        com.rounds.listener.LegendaryEffects.resetAll();
        state = GameState.WAITING;
        clearAllSpectatorGlow();
        if (plugin.getCustomDataManager() != null) {
            plugin.getCustomDataManager().clearAll();
        }
        saveState();
        removeScoreboard();
        RoundsEntities.clearAllDamageVignettes();
        plugin.getTeamManager().clearAll();
        plugin.getPlayerDataManager().clearActivePlayers();
        resetAllNameColors();
        Location lobbyLoc = plugin.getBlockListener().getBlockStorage().getLobbyBlock();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerDataManager().getData(p.getUniqueId());
            if (data != null) data.resetStats();
            GunItem.cancelReload(p.getUniqueId());
            p.getInventory().clear();
            var attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) attr.setBaseValue(20);
            p.setHealth(Math.min(p.getHealth(), 20));
            p.setFoodLevel(20);
            p.setSaturation(5.0f);
            p.setExhaustion(0f);
            p.setInvulnerable(false);
            p.setNoDamageTicks(0);
            p.setGameMode(GameMode.ADVENTURE);
            p.setAllowFlight(false);
            p.setFlying(false);
            clearCardEffects(p);
            removeSnowballModifiers(p);
            if (lobbyLoc != null) {
                p.teleport(getGroundLocation(lobbyLoc));
            }
        }
        plugin.getPlayerDataManager().clearActivePlayers();
    }

    private void stopGameTick() {
        if (tickTaskId != -1) {
            Bukkit.getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }
    }

    public GameState getState() { return state; }
    public boolean isGameActive() { return gameActive || state != GameState.WAITING; }
    public Map<GameTeam, Location> getTeamSpawns() { return teamSpawns; }
    public int getRounds() { return roundsToWin; }
    public void setRounds(int rounds) { this.roundsToWin = rounds; }
    public double getCurrentRound() { return currentRound; }
    public boolean isGameStarted() { return state != GameState.WAITING; }
    public Location getCurrentZoneCenter() { return currentZoneCenter != null ? currentZoneCenter.clone() : null; }
    public void addDeadPlayer(UUID uuid) { deadPlayers.add(uuid); }
    public void removeDeadPlayer(UUID uuid) { deadPlayers.remove(uuid); }
    public Set<GameTeam> getLosingTeams() { return Collections.unmodifiableSet(losingTeams); }
    public Set<UUID> getDeadPlayers() { return Collections.unmodifiableSet(deadPlayers); }
    public GameStateManager getStateManager() { return stateManager; }

    public boolean isParticipant(UUID uuid) {
        return plugin.getPlayerDataManager().isActive(uuid);
    }

    public boolean isTargetable(Player player) {
        if (player == null || !player.isOnline() || !player.isValid() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (plugin.getTeamManager().getPlayerTeam(player.getUniqueId()) != null) {
            return true;
        }
        return isParticipant(player.getUniqueId()) || (plugin.getBotManager() != null && plugin.getBotManager().isBot(player));
    }

    public Player findRandomAlivePlayer() {
        List<Player> alive = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (team != null && p.isOnline() && p.isValid()
                    && p.getGameMode() != GameMode.SPECTATOR
                    && p.getHealth() > 0
                    && !p.isDead()
                    && !deadPlayers.contains(p.getUniqueId())) {
                alive.add(p);
            }
        }
        if (alive.isEmpty()) return null;
        return alive.get(RANDOM.nextInt(alive.size()));
    }

    public Set<GameTeam> getAvailableTeams() {
        if (plugin.getBlockListener() != null) {
            Set<GameTeam> placed = plugin.getBlockListener().getJoinBlockTeams();
            if (!placed.isEmpty()) {
                return placed;
            }
        }
        Set<GameTeam> available = new HashSet<>(plugin.getRoundsConfig().getEnabledTeams());
        if (available.isEmpty()) {
            available.addAll(Arrays.asList(GameTeam.values()));
        }
        return available;
    }

    public GameTeam findSmallestTeam() {
        Set<GameTeam> available = getAvailableTeams();
        int minCount = Integer.MAX_VALUE;
        List<GameTeam> candidates = new ArrayList<>();
        for (GameTeam team : available) {
            int count = plugin.getTeamManager().getPlayerCount(team);
            if (count < minCount) {
                minCount = count;
                candidates.clear();
                candidates.add(team);
            } else if (count == minCount) {
                candidates.add(team);
            }
        }
        if (candidates.isEmpty()) return GameTeam.BLUE;
        return candidates.get(RANDOM.nextInt(candidates.size()));
    }

    public void markPendingCardJoiner(UUID uuid, boolean returning) {
        pendingCardJoiners.add(uuid);
        if (returning) returningJoiners.add(uuid); else returningJoiners.remove(uuid);
    }

    public void removePendingCardJoiner(UUID uuid) {
        pendingCardJoiners.remove(uuid);
        returningJoiners.remove(uuid);
    }

    public boolean isPendingCardJoiner(UUID uuid) {
        return pendingCardJoiners.contains(uuid);
    }

    public boolean isReturningJoiner(UUID uuid) {
        return returningJoiners.contains(uuid);
    }

    public void showNameTagsInGame() {
        refreshTeamScoreboards();
        if (tabCompat.isActive()) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
                boolean isBot = (plugin.getBotManager() != null && plugin.getBotManager().getBot(p.getName()) != null);
                if ((team != null && plugin.getRoundsConfig().isColorNicknames()) || isBot) {
                    updateColoredName(p, team);
                } else {
                    tabCompat.resetName(p);
                }
            }
            updateTeamNametagVisibility();
        } else {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                updateColoredName(p, plugin.getTeamManager().getPlayerTeam(p.getUniqueId()));
            }
        }
    }

    public void updateAllPlayerNametags() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            boolean isBot = (plugin.getBotManager() != null && plugin.getBotManager().getBot(p.getName()) != null);
            if (team != null || isBot) {
                updateColoredName(p, team);
            }
        }
    }

    public void updateTeamNametagVisibility() {
        if (!isGameStarted() && !isGameActive()) {
            if (tabCompat.isActive()) {
                tabCompat.resetAllNametagVisibilities();
            }
            return;
        }
        if (!tabCompat.isActive()) return;

        List<? extends Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player target : players) {
            for (Player viewer : players) {
                if (target.equals(viewer)) continue;
                // Only send nametag visibility packets to real players who actually have client viewports!
                if (plugin.getBotManager() != null && plugin.getBotManager().getBot(viewer.getName()) != null) continue;

                boolean isSpectator = (viewer.getGameMode() == GameMode.SPECTATOR || deadPlayers.contains(viewer.getUniqueId()));
                boolean isTeammate = RoundsEntities.isTeammate(target.getUniqueId(), viewer.getUniqueId());

                if (isSpectator || isTeammate) {
                    tabCompat.showNametagTo(target, viewer);
                } else {
                    tabCompat.hideNametagFrom(target, viewer);
                }
            }
        }
    }

    public static int getHeartCount(Player p) {
        return Math.max(0, (int) Math.ceil(p.getHealth() / 2.0));
    }

    private String heartsPrefix(ChatColor color, Player p) {
        return color + "" + getHeartCount(p) + "\u2665 " + color;
    }

    private void updateColoredName(Player p, GameTeam team) {
        FakeBot bot = (plugin.getBotManager() != null) ? plugin.getBotManager().getBot(p.getName()) : null;
        ChatColor c = (team != null) ? team.getColor() : (bot != null && bot.getAssignedTeam() != null ? bot.getAssignedTeam().getColor() : ChatColor.GRAY);
        String prefix = (team != null || bot != null) ? heartsPrefix(c, p) : "";
        String suffix = (bot != null) ? " " + c + "§l[L" + bot.getLevel() + "]" : "";

        if (tabCompat.isActive()) {
            int ping = 0;
            try { ping = p.getPing(); } catch (Throwable ignored) {}
            if (team != null || bot != null) {
                tabCompat.setColoredName(p, c, prefix, suffix);
                tabCompat.setPlayerPing(p, ping, c + String.valueOf(ping) + " ms");
            } else {
                tabCompat.resetName(p);
                tabCompat.setPlayerPing(p, ping, String.valueOf(ping) + " ms");
            }
        }
        if (team != null || bot != null) {
            p.setPlayerListName(prefix + p.getName() + suffix);
        } else {
            p.setPlayerListName(p.getName());
        }

        Scoreboard mainSb = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team t = mainSb.getEntryTeam(p.getName());
        if (t != null) {
            t.setPrefix(prefix);
            t.setSuffix(suffix);
        }
        for (Scoreboard psb : playerScoreboards.values()) {
            org.bukkit.scoreboard.Team pt = psb.getEntryTeam(p.getName());
            if (pt != null) {
                pt.setPrefix(prefix);
                pt.setSuffix(suffix);
            }
        }
    }

    public void restoreNameTags() {
        refreshTeamScoreboards();
        if (tabCompat.isActive()) {
            tabCompat.resetAllNametagVisibilities();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                tabCompat.showNametag(p);
                updateColoredName(p, plugin.getTeamManager().getPlayerTeam(p.getUniqueId()));
            }
            return;
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            updateColoredName(p, plugin.getTeamManager().getPlayerTeam(p.getUniqueId()));
        }
    }

    public void applyTeamColor(Player player) {
        applyTeamColor(player, true);
    }

    public void applyTeamColor(Player player, boolean refreshScoreboard) {
        if (!plugin.getRoundsConfig().isColorNicknames()) return;
        updateColoredName(player, plugin.getTeamManager().getPlayerTeam(player.getUniqueId()));
        if (refreshScoreboard) {
            refreshTeamScoreboards();
        }
    }

    public void resetAllNameColors() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.setPlayerListName(p.getName());
            if (tabCompat.isActive()) {
                tabCompat.resetName(p);
                tabCompat.showNametag(p);
            }
        }
        Scoreboard mainSb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (org.bukkit.scoreboard.Team t : new ArrayList<>(mainSb.getTeams())) {
            if (t.getName().startsWith("rounds_") || t.getName().startsWith("rb_") || t.getName().startsWith("rp_")) {
                t.unregister();
            }
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            Scoreboard pSb = p.getScoreboard();
            if (pSb != null && !pSb.equals(mainSb)) {
                for (org.bukkit.scoreboard.Team t : new ArrayList<>(pSb.getTeams())) {
                    if (t.getName().startsWith("rounds_") || t.getName().startsWith("rb_") || t.getName().startsWith("rp_")) {
                        t.unregister();
                    }
                }
            }
        }
    }

    public void setupTeamsOnScoreboard(Scoreboard sb) {
        setupTeamsOnScoreboard(sb, null);
    }

    public void setupTeamsOnScoreboard(Scoreboard sb, Player viewer) {
        if (sb == null) return;
        boolean gameRunning = isGameStarted() || isGameActive();
        boolean isViewerSpectator = viewer != null && (viewer.getGameMode() == GameMode.SPECTATOR || deadPlayers.contains(viewer.getUniqueId()));

        for (GameTeam gt : GameTeam.values()) {
            String teamName = "rounds_" + gt.name().toLowerCase();
            org.bukkit.scoreboard.Team team = sb.getTeam(teamName);
            if (team == null) {
                team = sb.registerNewTeam(teamName);
            }
            team.setColor(gt.getColor());
            if (plugin.getRoundsConfig().isColorNicknames()) {
                team.setPrefix(gt.getColor().toString());
            }
            team.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, gameRunning
                    ? org.bukkit.scoreboard.Team.OptionStatus.FOR_OWN_TEAM
                    : org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
        }
        org.bukkit.scoreboard.Team noTeam = sb.getTeam("rounds_noteam");
        if (noTeam != null) noTeam.unregister();

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            FakeBot bot = (plugin.getBotManager() != null) ? plugin.getBotManager().getBot(p.getName()) : null;
            GameTeam pTeam = (bot != null) ? bot.getAssignedTeam() : plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (pTeam == null && bot == null) continue;

            String targetTeamName = (bot != null) ? "rb_" + p.getName().toLowerCase() : "rp_" + p.getName().toLowerCase();
            org.bukkit.scoreboard.Team targetTeam = sb.getTeam(targetTeamName);
            if (targetTeam == null) {
                targetTeam = sb.registerNewTeam(targetTeamName);
            }

            ChatColor c = (pTeam != null) ? pTeam.getColor() : ChatColor.GRAY;
            int hearts = getHeartCount(p);
            String prefix = c + "" + hearts + "\u2665 " + c;
            String suffix = (bot != null) ? " " + c + "§l[L" + bot.getLevel() + "]" : "";

            org.bukkit.scoreboard.Team.OptionStatus targetVis;
            if (!gameRunning) {
                targetVis = org.bukkit.scoreboard.Team.OptionStatus.ALWAYS;
            } else if (viewer == null) {
                targetVis = org.bukkit.scoreboard.Team.OptionStatus.FOR_OWN_TEAM;
            } else if (isViewerSpectator || RoundsEntities.isTeammate(p.getUniqueId(), viewer.getUniqueId())) {
                targetVis = org.bukkit.scoreboard.Team.OptionStatus.ALWAYS;
            } else {
                targetVis = org.bukkit.scoreboard.Team.OptionStatus.NEVER;
            }

            targetTeam.setColor(c);
            targetTeam.setPrefix(prefix);
            targetTeam.setSuffix(suffix);
            targetTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, targetVis);
            targetTeam.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
            targetTeam.setAllowFriendlyFire(false);

            if (!targetTeam.hasEntry(p.getName())) {
                org.bukkit.scoreboard.Team current = sb.getEntryTeam(p.getName());
                if (current != null && !current.equals(targetTeam)) {
                    current.removeEntry(p.getName());
                }
                targetTeam.addEntry(p.getName());
            }
        }
    }

    public void refreshTeamScoreboards() {
        Scoreboard mainSb = Bukkit.getScoreboardManager().getMainScoreboard();
        setupTeamsOnScoreboard(mainSb, null);
        for (Map.Entry<UUID, Scoreboard> entry : playerScoreboards.entrySet()) {
            Scoreboard sb = entry.getValue();
            if (sb != null && !sb.equals(mainSb)) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    setupTeamsOnScoreboard(sb, p);
                }
            }
        }
    }

    private final Set<UUID> playersWithSpectatorGlow = new HashSet<>();

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                updateSpectatorGlow();
                updateTeamNametagVisibility();
                refreshTeamScoreboards();
            });
        } else {
            clearSpectatorGlow(event.getPlayer());
            playersWithSpectatorGlow.remove(event.getPlayer().getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                updateTeamNametagVisibility();
                refreshTeamScoreboards();
            });
        }
    }

    public void updateSpectatorGlow() {
        if (!isGameStarted() && !isGameActive()) return;

        // Auto-clear glow for anyone who is no longer a spectator
        Iterator<UUID> it = playersWithSpectatorGlow.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline() || p.getGameMode() != GameMode.SPECTATOR) {
                if (p != null && p.isOnline()) {
                    clearSpectatorGlow(p);
                }
                it.remove();
            }
        }

        List<Player> spectators = new ArrayList<>();
        List<Player> livingPlayers = new ArrayList<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) {
                spectators.add(p);
            } else if (!p.isDead() && plugin.getTeamManager().getPlayerTeam(p.getUniqueId()) != null) {
                livingPlayers.add(p);
            }
        }

        if (spectators.isEmpty() || livingPlayers.isEmpty()) return;

        MinecraftServer nmsServer = ((CraftServer) Bukkit.getServer()).getServer();

        for (Player target : livingPlayers) {
            byte flags = getEntitySharedFlags(target, true);

            SynchedEntityData.DataValue<Byte> dataValue = new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, flags);
            ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(target.getEntityId(), List.of(dataValue));

            for (Player spectator : spectators) {
                ServerPlayer sp = nmsServer.getPlayerList().getPlayer(spectator.getUniqueId());
                if (sp != null && sp.connection != null) {
                    sp.connection.send(packet);
                    playersWithSpectatorGlow.add(spectator.getUniqueId());
                }
            }
        }
    }

    public void clearSpectatorGlow(Player formerSpectator) {
        if (formerSpectator == null) return;
        MinecraftServer nmsServer = ((CraftServer) Bukkit.getServer()).getServer();
        ServerPlayer sp = nmsServer.getPlayerList().getPlayer(formerSpectator.getUniqueId());
        if (sp == null || sp.connection == null) return;

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(formerSpectator)) continue;
            boolean hasCardGlow = target.hasPotionEffect(PotionEffectType.GLOWING);
            byte flags = getEntitySharedFlags(target, hasCardGlow);

            SynchedEntityData.DataValue<Byte> dataValue = new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, flags);
            ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(target.getEntityId(), List.of(dataValue));
            sp.connection.send(packet);
        }
    }

    private byte getEntitySharedFlags(Player target, boolean glow) {
        byte flags = 0;
        if (target.getFireTicks() > 0) flags |= 0x01;
        if (target.isSneaking()) flags |= 0x02;
        if (target.isSprinting()) flags |= 0x08;
        if (target.isSwimming()) flags |= 0x10;
        if (target.isInvisible()) flags |= 0x20;
        if (glow) flags |= 0x40;
        if (target.isGliding()) flags |= 0x80;
        return flags;
    }

    public void clearAllSpectatorGlow() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            clearSpectatorGlow(p);
        }
        playersWithSpectatorGlow.clear();
    }

    private void saveState() {
        if (state == GameState.WAITING) {
            stateManager.clear();
        } else {
            stateManager.save(this);
        }
    }

    public void restoreGame() {
        SavedState saved = stateManager.load();
        if (saved == null) return;

        state = saved.state;
        gameActive = state != GameState.WAITING;
        currentRound = saved.currentRound;
        roundsToWin = saved.roundsToWin;
        losingTeams.addAll(saved.lastLosers);
        deadPlayers.addAll(saved.deadPlayers);

        for (Map.Entry<GameTeam, Integer> entry : saved.wins.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                plugin.getTeamManager().addWin(entry.getKey());
            }
        }

        plugin.getLogger().info("Restored game state: " + state.name() + " round " + currentRound);

        updateScoreboard();
        startGameTick();

        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            for (UUID uuid : plugin.getPlayerDataManager().getActivePlayers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;

                PlayerDataManager.SavedPlayerData playerSaved =
                    plugin.getPlayerDataManager().loadPlayerFullData(uuid);
                if (playerSaved == null) continue;

                if (playerSaved.team != null) {
                    plugin.getTeamManager().joinTeam(uuid, playerSaved.team);
                }

                plugin.getPlayerDataManager().applySavedData(uuid, playerSaved);
                plugin.getCardManager().restorePendingPick(uuid, playerSaved.pendingCardIds);

                if (state == GameState.PLAYING) {
                    giveGun(player);
                    applyPlayerHP(player);
                    player.setGameMode(GameMode.ADVENTURE);
                } else if (state == GameState.CARDS) {
                    player.setGameMode(GameMode.ADVENTURE);
                }
            }
            updateScoreboard();
        }, 5L);
    }

    // ===== Built-in Scoreboard =====
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final Map<UUID, String> lastScoreboardRender = new HashMap<>();
    private final Map<UUID, String> playerObjTracker = new HashMap<>();
    private int scoreboardTaskId = -1;
    private boolean forceScoreboardPreview = false;

    public void startScoreboardTask() {
        if (scoreboardTaskId != -1) {
            Bukkit.getScheduler().cancelTask(scoreboardTaskId);
        }
        scoreboardTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::scoreboardTick, 20L, 20L);
    }

    public void stopScoreboardTask() {
        if (scoreboardTaskId != -1) {
            Bukkit.getScheduler().cancelTask(scoreboardTaskId);
            scoreboardTaskId = -1;
        }
    }

    public boolean shouldShowBuiltinScoreboard() {
        if (!plugin.getRoundsConfig().isBuiltinScoreboard()) return false;
        org.bukkit.plugin.Plugin tab = Bukkit.getPluginManager().getPlugin("TAB");
        if (tab != null && tab.isEnabled()) return false;
        return true;
    }

    public boolean isScoreboardActive() {
        return shouldShowBuiltinScoreboard() && (isGameStarted() || isGameActive() || forceScoreboardPreview);
    }

    public void setForceScoreboardPreview(boolean force) {
        this.forceScoreboardPreview = force;
    }

    public void scoreboardTick() {
        if (!isScoreboardActive()) {
            if (!playerScoreboards.isEmpty()) {
                removeScoreboard();
            }
            return;
        }
        updateScoreboard();
    }

    public void updateScoreboard() {
        if (!isScoreboardActive()) return;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getBotManager() != null && plugin.getBotManager().getBot(p.getName()) != null) continue;
            buildScoreboard(p);
        }
    }

    public void buildScoreboard(Player player) {
        if (!isScoreboardActive()) return;
        if (player == null || !player.isOnline()) return;
        if (plugin.getBotManager() != null && plugin.getBotManager().getBot(player.getName()) != null) return;

        Scoreboard sb = playerScoreboards.computeIfAbsent(player.getUniqueId(),
                k -> Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard());

        long sec = System.currentTimeMillis() / 1000L;
        int page = (int) ((sec / 5) % 2); // 0 = Game, 1 = Stats

        String title;
        List<String> lines = new ArrayList<>();

        if (page == 0) {
            String cfgTitle = plugin.getRoundsConfig().getBuiltinScoreboardTitle();
            title = (cfgTitle != null && !cfgTitle.isEmpty() && !cfgTitle.equalsIgnoreCase("&6&lROUNDS"))
                    ? ChatColor.translateAlternateColorCodes('&', cfgTitle)
                    : Messages.get("scoreboard.game-title");
            lines.add("\u00A77\u00A7m════════");

            GameTeam myTeam = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
            String teamPart = (myTeam != null)
                    ? myTeam.getColor() + Messages.raw("team." + myTeam.name().toLowerCase())
                    : Messages.raw("team.none");
            lines.add(" \u00A7f" + Messages.raw("scoreboard.you") + ": " + teamPart);
            lines.add(" ");
            lines.add("     \u00A7f" + Messages.raw("scoreboard.wins") + "  ");
            lines.add(" \u00A7b" + Messages.raw("team.blue") + ": \u00A7b" + (int) plugin.getTeamManager().getWins(GameTeam.BLUE));
            lines.add(" \u00A7c" + Messages.raw("team.red") + ": \u00A7c" + (int) plugin.getTeamManager().getWins(GameTeam.RED));
            lines.add(" \u00A7e" + Messages.raw("team.yellow") + ": \u00A7e" + (int) plugin.getTeamManager().getWins(GameTeam.YELLOW));
            lines.add(" \u00A7a" + Messages.raw("team.green") + ": \u00A7a" + (int) plugin.getTeamManager().getWins(GameTeam.GREEN));
            lines.add("\u00A77\u00A7m════════\u00A7r");
        } else {
            String cfgStatsTitle = plugin.getRoundsConfig().getBuiltinScoreboardStatsTitle();
            title = (cfgStatsTitle != null && !cfgStatsTitle.isEmpty() && !cfgStatsTitle.equalsIgnoreCase("&b&lМои статы"))
                    ? ChatColor.translateAlternateColorCodes('&', cfgStatsTitle)
                    : Messages.get("scoreboard.stats-title");
            lines.add("\u00A77\u00A7m════════");

            PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
            String statHp = (data != null) ? formatStat(data.hp) : "100";
            String statDmg = (data != null) ? formatStat(data.dmg) : "1";
            String statCards = (data != null && data.getOwnedCards() != null)
                    ? String.valueOf(data.getOwnedCards().size()) : "0";
            String statBullets = (data != null) ? formatStat(data.bullets) : "1";
            String statMaxAmmo = (data != null) ? formatStat(data.maxAmmo) : "3";

            lines.add(" \u00A7f" + Messages.raw("scoreboard.stat-hp") + ": \u00A7a" + statHp);
            lines.add(" \u00A7f" + Messages.raw("scoreboard.stat-dmg") + ": \u00A7c" + statDmg);
            lines.add(" \u00A7f" + Messages.raw("scoreboard.stat-cards") + ": \u00A7d" + statCards);
            lines.add(" \u00A7f" + Messages.raw("scoreboard.stat-bullets") + ": \u00A76" + statBullets);
            lines.add(" \u00A7f" + Messages.raw("scoreboard.stat-max-ammo") + ": \u00A7e" + statMaxAmmo);
            lines.add("\u00A77\u00A7m════════\u00A7r");
        }

        // PlaceholderAPI expansion support if installed (graceful native fallback if absent)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                java.lang.reflect.Method setPlaceholders = papiClass.getMethod("setPlaceholders", Player.class, String.class);
                for (int i = 0; i < lines.size(); i++) {
                    lines.set(i, (String) setPlaceholders.invoke(null, player, lines.get(i)));
                }
            } catch (Throwable ignored) {}
        }

        String contentHash = title + "::" + String.join(";", lines);
        if (contentHash.equals(lastScoreboardRender.get(player.getUniqueId()))) {
            if (player.getScoreboard() != sb) {
                player.setScoreboard(sb);
            }
            return;
        }
        lastScoreboardRender.put(player.getUniqueId(), contentHash);

        String curObj = playerObjTracker.getOrDefault(player.getUniqueId(), "r_obj_b");
        String nextObjName = curObj.equals("r_obj_a") ? "r_obj_b" : "r_obj_a";

        Objective nextObj = sb.getObjective(nextObjName);
        if (nextObj != null) nextObj.unregister();

        nextObj = sb.registerNewObjective(nextObjName, Criteria.DUMMY, title);

        // Reflection for Paper 1.20.5+ blank number format
        try {
            Class<?> nfClass = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            java.lang.reflect.Method blankMethod = nfClass.getMethod("blank");
            Object blank = blankMethod.invoke(null);
            java.lang.reflect.Method setNf = nextObj.getClass().getMethod("numberFormat", nfClass);
            setNf.invoke(nextObj, blank);
        } catch (Throwable ignored) {}

        int score = lines.size();
        for (String line : lines) {
            nextObj.getScore(line).setScore(score--);
        }

        nextObj.setDisplaySlot(DisplaySlot.SIDEBAR);

        Objective oldObj = sb.getObjective(curObj);
        if (oldObj != null) oldObj.unregister();

        playerObjTracker.put(player.getUniqueId(), nextObjName);

        setupTeamsOnScoreboard(sb, player);
        if (player.getScoreboard() != sb) {
            player.setScoreboard(sb);
        }
    }

    private static String formatStat(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.US, "%.1f", v);
    }

    public void onPlayerQuit(UUID uuid) {
        playerScoreboards.remove(uuid);
        lastScoreboardRender.remove(uuid);
        playerObjTracker.remove(uuid);
        deadPlayers.remove(uuid);
        syncedHearts.remove(uuid);
        playersWithSpectatorGlow.remove(uuid);
        abyssalLastLocations.remove(uuid);
        lastSilenceAuraTime.remove(uuid);
        highlightGlowUntil.remove(uuid);
        wrongMapTasks.remove(uuid);
        lastWrongMapCheckTime.remove(uuid);
        lastWrongMapResult.remove(uuid);
        chameleonLastLoc.remove(uuid);
        chameleonStillTicks.remove(uuid);
        pendingCardJoiners.remove(uuid);
        returningJoiners.remove(uuid);
        if (autoDeathBossBar != null) {
            Player qp = Bukkit.getPlayer(uuid);
            if (qp != null) autoDeathBossBar.removePlayer(qp);
        }
    }

    public void removeScoreboard() {
        for (Scoreboard sb : playerScoreboards.values()) {
            Scoreboard empty = Bukkit.getScoreboardManager().getNewScoreboard();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getScoreboard() == sb) p.setScoreboard(empty);
            }
        }
        playerScoreboards.clear();
        lastScoreboardRender.clear();
        playerObjTracker.clear();
    }
}

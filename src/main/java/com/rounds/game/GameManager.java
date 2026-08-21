package com.rounds.game;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import org.bukkit.util.Vector;
import com.rounds.RoundsConfig;
import com.rounds.RoundsPlugin;
import com.rounds.blocks.BlockStorage;
import com.rounds.blocks.SpawnManager;
import com.rounds.entity.RoundsEntities;
import com.rounds.item.GunItem;
import com.rounds.player.PlayerData;
import com.rounds.player.PlayerDataManager;
import com.rounds.teams.TeamManager;
import com.rounds.game.GameStateManager.SavedState;
import com.rounds.teams.TeamManager.GameTeam;
import com.rounds.util.Messages;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

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
    private final Set<GameTeam> losingTeams = new HashSet<>();
    private final GameStateManager stateManager;
    private final com.rounds.util.TabCompat tabCompat;
    private boolean wheelEnabled = false;
    private final Set<Integer> scheduledTaskIds = new HashSet<>();
    private final Set<UUID> pendingCardJoiners = new HashSet<>();
    private final Set<UUID> returningJoiners = new HashSet<>();
    private final Map<GameRule<?>, Object> savedGameRules = new HashMap<>();
    private final Map<GameTeam, Location> teamSpawns = new HashMap<>();
    private Location currentZoneCenter = null;

    public GameManager(RoundsPlugin plugin) {
        this.plugin = plugin;
        this.roundsToWin = plugin.getRoundsConfig().getDefaultRounds();
        this.stateManager = new GameStateManager(plugin);
        this.tabCompat = new com.rounds.util.TabCompat();
    }

    public boolean isWheelEnabled() { return wheelEnabled; }
    public void setWheelEnabled(boolean enabled) { this.wheelEnabled = enabled; }

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

        resetWins();
        resetAllCards();
        musicTick = 0;
        currentRound = 0;
        deadPlayers.clear();
        abyssalLastLocations.clear();
        lastSilenceAuraTime.clear();
        teamSpawns.clear();

        saveState();
        updateScoreboard();
        hideNameTagsInGame();

        plugin.getCardManager().clearPendingPicks();
        plugin.getPlayerDataManager().clearActivePlayers();
        applyGameRules();

        for (Player p : readyPlayers) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            plugin.getPlayerDataManager().trackPlayer(p.getUniqueId());
            plugin.getPlayerDataManager().savePlayerFullData(p.getUniqueId(), team, null);
        }

        BlockStorage bs = plugin.getBlockListener().getBlockStorage();
        Location lobby = bs.getLobbyBlock();

        if (lobby != null) {
            Location lobbyTp = lobby.clone().add(0, 1, 0);
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
        }
        teamSpawns.clear();
        teamSpawns.putAll(spawnAssignment);

        applyDarkness();

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getTeamManager().getPlayerTeam(p.getUniqueId()) == null) {
                p.setGameMode(GameMode.SPECTATOR);
                p.setInvulnerable(true);
                if (currentZoneCenter != null) p.teleport(currentZoneCenter);
            }
        }

        new BukkitRunnable() {
            int count = 3;

            @Override
            public void run() {
                if (count <= 0) {
                    cancel();
                    finishStart(readyPlayers, teamsWithPlayers);
                    return;
                }

                plugin.getServer().broadcastMessage(ChatColor.YELLOW + "" + count);
                applyDarkness();

                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    p.setGameMode(GameMode.SPECTATOR);
                    p.setInvulnerable(true);
                }
                count--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
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
            p.setGameMode(GameMode.SPECTATOR);
            p.setInvulnerable(true);
        }

        for (Player p : readyPlayers) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (team != null) {
                Location spawn = teamSpawns.get(team);
                if (spawn != null) p.teleport(spawn);
                p.setFoodLevel(20);
                p.setSaturation(5.0f);
                p.setExhaustion(0f);
                p.setInvulnerable(true);
                p.setNoDamageTicks(0);
                plugin.getCardManager().openCardSelection(p, team);
            }
        }
        saveState();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    private void giveGun(Player player) {
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
            player.setHealth(Math.min(maxHP, com.rounds.player.PlayerData.MAX_HEALTH));
        }
    }

    private void applyRoundEffects(Player player) {
    }

    private void applyPeriodicEffects() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!p.isValid()) continue;
            if (plugin.getTeamManager().getPlayerTeam(p.getUniqueId()) == null) continue;
            if (deadPlayers.contains(p.getUniqueId())) continue;
            PlayerData data = plugin.getPlayerDataManager().getData(p);
            if (data.radiance > 0) {
                for (Entity entity : p.getNearbyEntities(8.0, 8.0, 8.0)) {
                    if (entity instanceof Player radTarget && !radTarget.equals(p)) {
                        if (radTarget.getGameMode() == GameMode.SPECTATOR) continue;
                        GameTeam myTeam = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
                        GameTeam targetTeam = plugin.getTeamManager().getPlayerTeam(radTarget.getUniqueId());
                        if (myTeam != targetTeam) {
                            radTarget.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                PotionEffectType.GLOWING, 30, 0));
                        }
                    }
                }
            }
            if (data.autoReload > 0 && p.getGameMode() == GameMode.SURVIVAL) {
                if (data.ammo < data.maxAmmo && !GunItem.isReloading(p.getUniqueId())) {
                    data.ammo = Math.min(data.ammo + data.autoReload * 0.1, data.maxAmmo);
                }
                data.ammo = Math.min(data.ammo, data.maxAmmo);
            }
            if (data.lifestealAura > 0) {
                for (Entity entity : p.getNearbyEntities(2.5, 2.5, 2.5)) {
                    if (!(entity instanceof Player enemy)) continue;
                    if (enemy.getUniqueId().equals(p.getUniqueId())) continue;
                    if (enemy.getGameMode() == GameMode.SPECTATOR) continue;
                    if (!enemy.isOnline() || !enemy.isValid() || enemy.getHealth() <= 0) continue;
                    if (deadPlayers.contains(enemy.getUniqueId()) || pendingCardJoiners.contains(enemy.getUniqueId())) continue;
                    GameTeam myTeam = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
                    GameTeam enemyTeam = plugin.getTeamManager().getPlayerTeam(enemy.getUniqueId());
                    if (myTeam != null && enemyTeam != null && myTeam != enemyTeam) {
                        double steal = Math.max(Math.ceil(data.lifestealAura * 0.5), 1);
                        enemy.setNoDamageTicks(0);
                        enemy.damage(steal);
                        p.setHealth(Math.min(p.getHealth() + steal, p.getMaxHealth()));
                        break;
                    }
                }
            }
            if (data.silenceAura > 0) {
                long now = System.currentTimeMillis();
                for (Entity entity : p.getNearbyEntities(5.0, 5.0, 5.0)) {
                    if (entity instanceof LivingEntity enemy && !enemy.getUniqueId().equals(p.getUniqueId())) {
                        if (entity instanceof Player silenceTarget) {
                            if (silenceTarget.getGameMode() == GameMode.SPECTATOR) continue;
                            GameTeam myTeam = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
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
            }
            if (data.abyssal > 0) {
                Location lastLoc = abyssalLastLocations.get(p.getUniqueId());
                Location currentLoc = p.getLocation();
                if (lastLoc != null && lastLoc.distanceSquared(currentLoc) < 0.25) {
                    data.abyssalTicks++;
                    int remaining = 30 - data.abyssalTicks;
                    if (remaining > 0 && remaining % 5 == 0) {
                        p.sendTitle("", ChatColor.DARK_PURPLE + "Призыв... " + remaining + "с", 0, 25, 0);
                    }
                    if (data.abyssalTicks >= 30) {
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
                        p.setHealth(Math.min(p.getHealth(), enhancedMaxHP));
                    }
                } else {
                    if (pristineActive) {
                        attr.setBaseValue(baseMaxHP);
                        p.setHealth(Math.min(p.getHealth(), baseMaxHP));
                    }
                }
            }
            if (data.speed > 0) {
                double speedRange = 5.0 + data.speed * 3.0;
                for (Entity entity : p.getNearbyEntities(speedRange, speedRange, speedRange)) {
                    if (entity instanceof LivingEntity enemy && !enemy.getUniqueId().equals(p.getUniqueId())) {
                        GameTeam myTeam = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
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
        Location spawnLoc = summoner.getLocation().add(0, 3, 0);

        Phantom phantom = summoner.getWorld().spawn(spawnLoc, Phantom.class, p -> {
            p.setCustomName(ChatColor.DARK_PURPLE + "Тёмный фантом");
            p.setCustomNameVisible(true);
            var attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) attr.setBaseValue(10);
            p.setHealth(10);
        });

        UUID summonerUUID = summoner.getUniqueId();
        summoner.sendTitle("", ChatColor.DARK_PURPLE + "Фантом призван!", 10, 40, 10);
        summoner.getWorld().playSound(spawnLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            boolean hasDamaged = false;
            int deathCountdown = -1;

            @Override
            public void run() {
                if (!phantom.isValid() || phantom.isDead() || ticks >= 400) {
                    if (phantom.isValid()) phantom.remove();
                    cancel();
                    return;
                }

                if (deathCountdown >= 0) {
                    deathCountdown++;
                    if (deathCountdown >= 100) {
                        if (phantom.isValid()) phantom.remove();
                        cancel();
                        return;
                    }
                }

                LivingEntity target = findNearestEnemyForPhantom(phantom, summoner, summonerTeam);
                if (target != null) {
                    phantom.setTarget(target);
                    Vector direction = target.getLocation().add(0, 1, 0).toVector()
                        .subtract(phantom.getLocation().toVector());
                    if (direction.length() > 0) {
                        phantom.setVelocity(direction.normalize().multiply(0.4));
                    }
                    double dist = phantom.getLocation().distance(target.getLocation());
                    if (dist < 3.0 && ticks % 20 == 0) {
                        target.damage(6.0);
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
        }.runTaskTimer(plugin, 0L, 1L);
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
            if (state == GameState.PLAYING) gameTick();
            else if (state == GameState.CARDS) cardTick();
        }, 0L, 1L);
    }

    private void gameTick() {
        musicTick++;
        checkRoundEnd();
        if (musicTick % 20 == 0) {
            applyPeriodicEffects();
        }
        if (musicTick % 40 == 0) {
            sendSpectatorActionbar();
        }
    }

    private void cardTick() {
        musicTick++;
        if (wheelEnabled && musicTick % 120 == 0) {
            plugin.getCardGUI().rotateAllCards();
        }
        if (musicTick % 40 == 0) {
            sendSpectatorActionbar();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (plugin.getTeamManager().getPlayerTeam(p.getUniqueId()) == null
                        && !deadPlayers.contains(p.getUniqueId())) {
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

    private void sendEnemyPickingCardsTitle(Player p, int stay) {
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
        saveState();
        GunItem.resetRoundState();

        plugin.getServer().broadcastMessage(ChatColor.YELLOW + Messages.get("game.round-start", currentRound));

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (team != null) {
                Location spawn = teamSpawns.get(team);
                if (spawn != null) p.teleport(spawn);
                p.getInventory().clear();
                giveGun(p);
                PlayerData roundData = plugin.getPlayerDataManager().getData(p);
                if (roundData != null) roundData.ammo = roundData.maxAmmo;
                clearCardEffects(p);
                applyPlayerHP(p);
                applyRoundEffects(p);
                applyTeamColor(p);
                p.setGameMode(GameMode.SURVIVAL);
                p.setFoodLevel(20);
                p.setSaturation(5.0f);
                p.setExhaustion(0f);
                p.setInvulnerable(false);
                p.setNoDamageTicks(0);
            } else {
                if (currentZoneCenter != null) p.teleport(currentZoneCenter);
                p.setPlayerListName(p.getName());
                p.setGameMode(GameMode.SPECTATOR);
                p.setInvulnerable(true);
            }
        }
        musicTick = 0;
        updateScoreboard();
    }

    private void pickRandomZoneAndAssignSpawns() {
        BlockStorage bs = plugin.getBlockListener().getBlockStorage();
        List<BlockStorage.MapBlock> zones = bs.getMapBlocks();
        if (zones.isEmpty()) return;

        int chosen = new Random().nextInt(zones.size());
        currentZoneCenter = zones.get(chosen).center.clone();

        List<Location> spawns = bs.getSpawnBlocksInZone(zones.get(chosen));
        Set<GameTeam> teams = new HashSet<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam t = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (t != null) teams.add(t);
        }
        teamSpawns.clear();
        if (!spawns.isEmpty()) {
            teamSpawns.putAll(SpawnManager.assignSpawns(teams, spawns));
        }
    }

    private void checkRoundEnd() {
        Set<GameTeam> aliveTeams = new HashSet<>();
        int totalAlive = 0;
        for (GameTeam team : GameTeam.values()) {
            int alive = 0;
            for (UUID uuid : plugin.getTeamManager().getTeamPlayers(team)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline() && p.isValid() && p.getHealth() > 0 && !deadPlayers.contains(uuid) && !pendingCardJoiners.contains(uuid)) {
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

    private void endRound(GameTeam winner) {
        plugin.getTeamManager().addWin(winner);
        state = GameState.ROUND_END;
        losingTeams.clear();
        saveState();
        for (GameTeam team : GameTeam.values()) {
            if (team != winner && plugin.getTeamManager().getTeamPlayers(team).size() > 0) {
                losingTeams.add(team);
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
                    p.setInvulnerable(true);
                    p.setFoodLevel(20);
                    p.setSaturation(5.0f);
                    p.setExhaustion(0f);
                } else {
                    if (currentZoneCenter != null) p.teleport(currentZoneCenter);
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
                    p.setInvulnerable(true);
                    p.setFoodLevel(20);
                    p.setSaturation(5.0f);
                    p.setExhaustion(0f);
                } else {
                    if (currentZoneCenter != null) p.teleport(currentZoneCenter);
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

        String teamName = Messages.get("team." + winner.name().toLowerCase());
        plugin.getServer().broadcastMessage(ChatColor.GREEN + Messages.get("game.game-over"));
        plugin.getServer().broadcastMessage(ChatColor.GREEN + winner.getColor().toString() + teamName + " " + Messages.get("game.game-won"));
        plugin.getServer().broadcastMessage(ChatColor.GOLD + Messages.get("game.final-score", getScoreDisplay()));

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam pTeam = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (pTeam == winner) {
                p.sendTitle(Messages.get("title.game-won"), "", 10, 100, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            } else {
                p.sendTitle(Messages.get("title.game-lost"), "", 10, 100, 20);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            }
        }

        scheduleDelayed(() -> {
            state = GameState.WAITING;
            saveState();
            removeScoreboard();
            resetAllNameColors();
            restoreNameTags();
            plugin.getCardManager().resetAllCards();
            GunItem.resetRoundState();
            RoundsEntities.clearAllState();
            restoreGameRules();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                p.getInventory().clear();
                var attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (attr != null) attr.setBaseValue(20);
                p.setHealth(Math.min(p.getHealth(), 20));
                p.setFoodLevel(20);
                p.setInvulnerable(false);
                p.setNoDamageTicks(0);
                p.setGameMode(GameMode.ADVENTURE);
                clearCardEffects(p);
                Location lobby = plugin.getBlockListener().getBlockStorage().getLobbyBlock();
                if (lobby != null) p.teleport(lobby.clone().add(0, 1, 0));
            }
        }, 200L);
    }

    private void openCardGUIs() {
        openCardGUIs(false);
    }

    private void openCardGUIs(boolean allTeams) {
        removeDarkness();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.setGameMode(GameMode.SPECTATOR);
            p.setInvulnerable(true);
        }
        Set<UUID> pickers = new HashSet<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (team != null && (allTeams || losingTeams.contains(team) || pendingCardJoiners.contains(p.getUniqueId()))) {
                if (p.isDead()) continue;
                pickers.add(p.getUniqueId());
                plugin.getCardManager().openCardSelection(p, team);
            }
        }
        pendingCardJoiners.clear();
        returningJoiners.clear();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            if (!pickers.contains(p.getUniqueId())) {
                sendEnemyPickingCardsTitle(p, 100);
            }
        }
    }

    private void openCardGUIsDraw() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.setGameMode(GameMode.SPECTATOR);
            p.setInvulnerable(true);
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
            dead.sendMessage(ChatColor.RED + "Phoenix revived you!");
            return;
        }

        if (deadData.implode > 0) {
            dead.getWorld().createExplosion(dead.getLocation(), 3.0f, false, false);
            for (Entity entity : dead.getNearbyEntities(4.0, 4.0, 4.0)) {
                if (entity instanceof LivingEntity target && !target.getUniqueId().equals(dead.getUniqueId())) {
                    if (target instanceof Player tp && tp.getGameMode() == GameMode.SPECTATOR) continue;
                    try {
                        double dist = target.getLocation().distance(dead.getLocation());
                        double dmg = Math.max(0, 8.0 * (1.0 - dist / 4.0));
                        if (dmg > 0) target.damage(dmg);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            dead.getWorld().playSound(dead.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
        }

        deadPlayers.add(dead.getUniqueId());

        Player killer = dead.getKiller();
        if (killer != null) {
            GameTeam killerTeam = plugin.getTeamManager().getPlayerTeam(killer.getUniqueId());
            PlayerData killerData = plugin.getPlayerDataManager().getData(killer);
            if (killerTeam != null && killerTeam != deadTeam) {
                killer.sendMessage(ChatColor.GREEN + Messages.get("game.eliminated", dead.getName()));
                if (killerData.refresh > 0) {
                    killerData.ammo = killerData.maxAmmo;
                    GunItem.cancelReload(killer.getUniqueId());
                    killer.sendMessage(ChatColor.GREEN + "Refresh! Ammo refilled!");
                }
                if (killerData.tacticalReload > 0) {
                    killerData.ammo = killerData.maxAmmo;
                    GunItem.cancelReload(killer.getUniqueId());
                }
            }
        }
        dead.sendMessage(ChatColor.RED + Messages.get("game.you-eliminated"));
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
                if (team != null && (losingTeams.isEmpty() || losingTeams.contains(team))) {
                    plugin.getCardManager().openCardSelection(player, team);
                }
                player.setInvulnerable(true);
            } else if (state == GameState.ROUND_END) {
                player.setInvulnerable(true);
            }
            player.setGameMode(GameMode.SURVIVAL);
            player.setFoodLevel(20);
            player.setSaturation(5.0f);
            player.setExhaustion(0f);
        }, plugin.getRoundsConfig().getRespawnDelayTicks());
    }

    public void shutdown() { stopGameTick(); }

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
        if (state == GameState.WAITING) return;
        stopGameTick();
        cancelScheduledTasks();
        plugin.getCardManager().clearPendingPicks();
        plugin.getCardManager().resetAllCards();
        GunItem.resetRoundState();
        RoundsEntities.clearAllState();
        restoreGameRules();
        removeDarkness();
        teamSpawns.clear();
        resetWins();
        deadPlayers.clear();
        abyssalLastLocations.clear();
        lastSilenceAuraTime.clear();
        state = GameState.WAITING;
        saveState();
        removeScoreboard();
        resetAllNameColors();
        restoreNameTags();
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
            clearCardEffects(p);
            if (lobbyLoc != null) {
                p.teleport(lobbyLoc.clone().add(0, 1, 0));
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
    public Map<GameTeam, Location> getTeamSpawns() { return teamSpawns; }
    public int getRounds() { return roundsToWin; }
    public void setRounds(int rounds) { this.roundsToWin = rounds; }
    public double getCurrentRound() { return currentRound; }
    public boolean isGameStarted() { return state != GameState.WAITING; }
    public void addDeadPlayer(UUID uuid) { deadPlayers.add(uuid); }
    public Set<GameTeam> getLosingTeams() { return Collections.unmodifiableSet(losingTeams); }
    public Set<UUID> getDeadPlayers() { return Collections.unmodifiableSet(deadPlayers); }
    public GameStateManager getStateManager() { return stateManager; }

    public boolean isParticipant(UUID uuid) {
        return plugin.getPlayerDataManager().isActive(uuid);
    }

    public boolean isTargetable(Player player) {
        return player != null && player.isOnline() && player.isValid()
            && player.getGameMode() != GameMode.SPECTATOR
            && isParticipant(player.getUniqueId());
    }

    public Player findRandomAlivePlayer() {
        List<Player> alive = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            if (team != null && p.isOnline() && p.isValid() && p.getHealth() > 0
                    && !deadPlayers.contains(p.getUniqueId())) {
                alive.add(p);
            }
        }
        if (alive.isEmpty()) return null;
        return alive.get(new Random().nextInt(alive.size()));
    }

    public GameTeam findSmallestTeam() {
        int minCount = Integer.MAX_VALUE;
        List<GameTeam> candidates = new ArrayList<>();
        for (GameTeam team : GameTeam.values()) {
            int count = plugin.getTeamManager().getPlayerCount(team);
            if (count < minCount) {
                minCount = count;
                candidates.clear();
                candidates.add(team);
            } else if (count == minCount) {
                candidates.add(team);
            }
        }
        return candidates.get(new Random().nextInt(candidates.size()));
    }

    public void markPendingCardJoiner(UUID uuid, boolean returning) {
        pendingCardJoiners.add(uuid);
        if (returning) returningJoiners.add(uuid); else returningJoiners.remove(uuid);
    }

    public boolean isPendingCardJoiner(UUID uuid) {
        return pendingCardJoiners.contains(uuid);
    }

    public boolean isReturningJoiner(UUID uuid) {
        return returningJoiners.contains(uuid);
    }

    public void hideNameTagsInGame() {
        if (tabCompat.isActive()) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                tabCompat.hideNametag(p);
            }
            return;
        }
        Scoreboard mainSb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team hidden = mainSb.getTeam("rounds_hidden");
        if (hidden == null) {
            hidden = mainSb.registerNewTeam("rounds_hidden");
            hidden.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            for (Team t : new ArrayList<>(mainSb.getTeams())) {
                if (t.getName().startsWith("rounds_") && !t.getName().equals("rounds_hidden")) {
                    t.removePlayer(p);
                }
            }
            hidden.addPlayer(p);
        }
    }

    public void restoreNameTags() {
        if (tabCompat.isActive()) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                tabCompat.showNametag(p);
                GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
                if (team != null) {
                    tabCompat.setColoredName(p, team.getColor());
                } else {
                    tabCompat.resetName(p);
                }
            }
            return;
        }
        Scoreboard mainSb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team hidden = mainSb.getTeam("rounds_hidden");
        if (hidden != null) hidden.unregister();
        if (!plugin.getRoundsConfig().isColorNicknames()) return;
        refreshTeamScoreboards();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            p.setPlayerListName(team != null ? team.getColor() + p.getName() : p.getName());
        }
    }

    public void applyTeamColor(Player player) {
        if (!plugin.getRoundsConfig().isColorNicknames()) return;
        GameTeam team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (tabCompat.isActive()) {
            if (team != null) {
                tabCompat.setColoredName(player, team.getColor());
            } else {
                tabCompat.resetName(player);
            }
        } else if (team != null) {
            player.setPlayerListName(team.getColor() + player.getName());
        } else {
            player.setPlayerListName(player.getName());
        }
        refreshTeamScoreboards();
    }

    public void resetAllNameColors() {
        if (!plugin.getRoundsConfig().isColorNicknames()) return;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.setPlayerListName(p.getName());
        }
        Scoreboard mainSb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (GameTeam gt : GameTeam.values()) {
            org.bukkit.scoreboard.Team team = mainSb.getTeam("rounds_" + gt.name().toLowerCase());
            if (team != null) team.unregister();
        }
    }

    private void refreshTeamScoreboards() {
        if (!plugin.getRoundsConfig().isColorNicknames()) return;
        Scoreboard mainSb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (GameTeam gt : GameTeam.values()) {
            String teamName = "rounds_" + gt.name().toLowerCase();
            org.bukkit.scoreboard.Team team = mainSb.getTeam(teamName);
            if (team == null) {
                team = mainSb.registerNewTeam(teamName);
            }
            team.setColor(gt.getColor());
            team.setPrefix(gt.getColor().toString());
            team.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
        }
        org.bukkit.scoreboard.Team noTeam = mainSb.getTeam("rounds_noteam");
        if (noTeam != null) noTeam.unregister();

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            GameTeam pTeam = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
            String teamName = pTeam != null ? "rounds_" + pTeam.name().toLowerCase() : null;
            for (org.bukkit.scoreboard.Team t : mainSb.getTeams()) {
                if (t.getName().startsWith("rounds_")) {
                    t.removePlayer(p);
                }
            }
            if (teamName != null) {
                org.bukkit.scoreboard.Team target = mainSb.getTeam(teamName);
                if (target != null) target.addPlayer(p);
            }
        }
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
                    player.setGameMode(GameMode.SURVIVAL);
                } else if (state == GameState.CARDS) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
            updateScoreboard();
        }, 5L);
    }

    // ===== Built-in Scoreboard =====
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();

    private static final String DIV = "\u00A7b\u00A7m                          ";

    public void updateScoreboard() {
        if (!plugin.getRoundsConfig().isBuiltinScoreboard()) return;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            buildScoreboard(p);
        }
    }

    public void buildScoreboard(Player player) {
        if (!plugin.getRoundsConfig().isBuiltinScoreboard()) return;
        Scoreboard sb = playerScoreboards.computeIfAbsent(player.getUniqueId(),
                k -> Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard());

        for (Objective obj : sb.getObjectives()) obj.unregister();

        Objective obj = sb.registerNewObjective("rounds", Criteria.DUMMY,
                ChatColor.translateAlternateColorCodes('&',
                        plugin.getRoundsConfig().getBuiltinScoreboardTitle()));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<GameTeam> activeTeams = new ArrayList<>();
        for (GameTeam gt : GameTeam.values()) {
            if (!plugin.getTeamManager().getTeamPlayers(gt).isEmpty()) activeTeams.add(gt);
        }

        int score = 10;

        obj.getScore(DIV).setScore(score--);
        obj.getScore("\u00A7f\u0420\u0430\u0443\u043D\u0434: \u00A7e" + currentRound + "/" + roundsToWin).setScore(score--);

        GameTeam myTeam = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        String teamPart = myTeam != null
                ? myTeam.getColor() + Messages.get("team." + myTeam.name().toLowerCase()) : "";
        obj.getScore("\u00A7f\u041A\u043E\u043C\u0430\u043D\u0434\u0430: " + teamPart).setScore(score--);

        obj.getScore(DIV).setScore(score--);

        for (GameTeam gt : activeTeams) {
            String name = Messages.get("team." + gt.name().toLowerCase());
            int pad = Math.max(1, 7 - name.length());
            String entry = "\u00A7f" + name + ":" + " ".repeat(pad) + "\u00A77\u2502 "
                    + "\u00A7b" + (int) plugin.getTeamManager().getWins(gt);
            obj.getScore(entry).setScore(score--);
        }

        obj.getScore(DIV).setScore(score);

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.setScoreboard(sb);
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
    }
}

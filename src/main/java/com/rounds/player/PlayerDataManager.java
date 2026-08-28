package com.rounds.player;

import com.rounds.DefaultStats;
import com.rounds.RoundsKeys;
import com.rounds.RoundsPlugin;
import com.rounds.cards.Card;
import com.rounds.game.GameManager;
import com.rounds.teams.TeamManager.GameTeam;
import com.rounds.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlayerDataManager implements Listener {

    private final RoundsPlugin plugin;
    private final Map<UUID, PlayerData> cache = new HashMap<>();
    private final Set<UUID> activePlayers = new HashSet<>();
    private final File activeFile;
    private final Map<UUID, SavedPlayerData> savedData = new HashMap<>();

    private static final Map<String, NamespacedKey> STAT_KEYS = new HashMap<>();

    static {
        String[] stats = {
            "dmg", "atks", "atk-speed", "atkr", "bounce", "ammo", "max-ammo", "bullets",
            "cold", "poison", "toxic_cloud", "leech", "tg_bounce", "homing",
            "homing_on_block",
            "poison_lvl", "cold_lvl", "parazit_lvl", "parazit",
            "hp", "shield_cooldown", "bomb_bullet", "bomb_on_block", "explode_bullets",
            "bullet_speed", "empower", "empower_charge", "dark_strength",
            "barage", "big_bullet", "grow", "truster_lvl", "jump_height", "dark",
            "card_select_1", "card_select_2", "card_select_3",
            "card_select_4", "card_select_5", "card_uses", "rare_card",
            "player_use", "atks_reload", "pristine_perseverance"
        };
        for (String s : stats) {
            STAT_KEYS.put(s, new NamespacedKey("rounds", s));
        }
    }

    public PlayerDataManager(RoundsPlugin plugin) {
        this.plugin = plugin;
        this.activeFile = new File(plugin.getDataFolder(), "active-players.yml");
        loadActivePlayers();
    }

    private List<Integer> getRegisteredCardIds() {
        List<Integer> ids = new ArrayList<>();
        for (Card card : plugin.getCardManager().getRegistry().getAllCards()) {
            ids.add(card.getId());
        }
        return ids;
    }

    // ==================== Active player tracking ====================

    public void trackPlayer(UUID uuid) {
        activePlayers.add(uuid);
        saveActivePlayers();
    }

    public void trackPlayer(UUID uuid, GameTeam team, List<Integer> pendingCardIds) {
        activePlayers.add(uuid);
        savePlayerFullData(uuid, team, pendingCardIds);
    }

    public Set<UUID> getActivePlayers() {
        return new HashSet<>(activePlayers);
    }

    public boolean isActive(UUID uuid) {
        return activePlayers.contains(uuid);
    }

    public void removeActivePlayer(UUID uuid) {
        activePlayers.remove(uuid);
        saveActivePlayers();
    }

    public void clearActivePlayers() {
        activePlayers.clear();
        savedData.clear();
        playerStore = new YamlConfiguration();
        clearActiveFile();
        playerStore = null;
    }

    // ==================== Full player data save/load (per-UUID) ====================

    public void savePlayerFullData(UUID uuid, GameTeam team, List<Integer> pendingCardIds) {
        PlayerData data = cache.get(uuid);
        if (data == null) data = new PlayerData();

        YamlConfiguration yml = store();
        String path = "players." + uuid.toString();

        yml.set(path + ".team", team != null ? team.name() : null);
        yml.set(path + ".name", data.playerName);
        yml.set(path + ".pending-cards", pendingCardIds);

        yml.set(path + ".stats.dmg", data.dmg);
        yml.set(path + ".stats.atks", data.atks);
        yml.set(path + ".stats.atk-speed", data.atkSpeed);
        yml.set(path + ".stats.atkr", data.atkr);
        yml.set(path + ".stats.bounce", data.bouncePl);
        yml.set(path + ".stats.ammo", data.ammo);
        yml.set(path + ".stats.max-ammo", data.maxAmmo);
        yml.set(path + ".stats.bullets", data.bullets);
        yml.set(path + ".stats.cold", data.cold);
        yml.set(path + ".stats.poison", data.poison);
        yml.set(path + ".stats.toxic-cloud", data.toxicCloud);
        yml.set(path + ".stats.leech", data.leech);
        yml.set(path + ".stats.tg-bounce", data.tgBounce);
        yml.set(path + ".stats.homing", data.homing);
        yml.set(path + ".stats.homing-on-block", data.homingOnBlock);
        yml.set(path + ".stats.poison-lvl", data.poisonLvl);
        yml.set(path + ".stats.cold-lvl", data.coldLvl);
        yml.set(path + ".stats.parazit-lvl", data.parazitLvl);
        yml.set(path + ".stats.parazit", data.parazit);
        yml.set(path + ".stats.hp", data.hp);
        yml.set(path + ".stats.shield-cooldown", data.shieldCooldown);
                yml.set(path + ".stats.bomb-bullet", data.bombBullet);
        yml.set(path + ".stats.bomb-on-block", data.bombOnBlock);

        yml.set(path + ".stats.bullet-speed", data.bulletSpeed);
        yml.set(path + ".stats.empower", data.empower);
        yml.set(path + ".stats.empower-charge", data.empowerCharge);
        yml.set(path + ".stats.dark-strength", data.darkStrength);
        yml.set(path + ".stats.big-bullet", data.bigBullet);
        yml.set(path + ".stats.grow", data.grow);
        yml.set(path + ".stats.truster-lvl", data.trusterLvl);
        yml.set(path + ".stats.jump-height", data.jumpHeight);
        yml.set(path + ".stats.dark", data.dark);
        yml.set(path + ".stats.atks-reload", data.atksReload);
        yml.set(path + ".stats.pristine-perseverance", data.pristinePerseverance);

        yml.set(path + ".cards", new ArrayList<>(data.getOwnedCards()));

        flushStore();
    }

    public void saveAllFullData(List<Integer> pendingCardIds) {
        for (UUID uuid : activePlayers) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(uuid);
            savePlayerFullData(uuid, team, pendingCardIds);
        }
    }

    public SavedPlayerData loadPlayerFullData(UUID uuid) {
        YamlConfiguration yml = store();
        String path = "players." + uuid.toString();
        if (!yml.contains(path)) return null;

        String teamStr = yml.getString(path + ".team");
        GameTeam team = null;
        if (teamStr != null) {
            try { team = GameTeam.valueOf(teamStr); } catch (IllegalArgumentException ignored) {}
        }

        List<Integer> pendingCards = yml.getIntegerList(path + ".pending-cards");

        List<Integer> ownedCards = yml.getIntegerList(path + ".cards");

        Map<String, Double> stats = new HashMap<>();
        YamlConfiguration sec = yml;
        for (String key : STAT_KEYS.keySet()) {
            String ymlKey = key.replace('_', '-');
            stats.put(key, sec.getDouble(path + ".stats." + ymlKey, 0));
        }

        return new SavedPlayerData(team, pendingCards, ownedCards, stats);
    }

    public void applySavedData(UUID uuid, SavedPlayerData saved) {
        if (saved == null) return;
        PlayerData data = cache.computeIfAbsent(uuid, u -> new PlayerData());
        data.resetStats();
        data.resetAllCards();

        Player player = plugin.getServer().getPlayer(uuid);

        if (saved.ownedCards != null && !saved.ownedCards.isEmpty()) {
            for (int cardId : saved.ownedCards) {
                data.setCard(cardId, true);
                Card card = plugin.getCardManager().getRegistry().getCard(cardId);
                if (card != null) {
                    card.apply(player, data);
                }
            }
        } else if (saved.stats != null && !saved.stats.isEmpty()) {
            data.dmg = saved.stats.getOrDefault("dmg", 1.0);
            data.atks = saved.stats.getOrDefault("atks", 20.0);
            data.atkSpeed = saved.stats.getOrDefault("atk-speed", 0.0);
            data.atkr = saved.stats.getOrDefault("atkr", 0.0);
            data.bouncePl = saved.stats.getOrDefault("bounce", 0.0);
            data.ammo = saved.stats.getOrDefault("ammo", DefaultStats.get().ammo);
            double restoredMaxAmmo = saved.stats.getOrDefault("max-ammo", 0.0);
            data.maxAmmo = restoredMaxAmmo >= 1 ? restoredMaxAmmo : DefaultStats.get().maxAmmo;
            data.ammo = Math.min(Math.max(data.ammo, 1), data.maxAmmo);
            data.bullets = saved.stats.getOrDefault("bullets", 1.0);
            data.cold = saved.stats.getOrDefault("cold", 0.0);
            data.poison = saved.stats.getOrDefault("poison", 0.0);
            data.toxicCloud = saved.stats.getOrDefault("toxic_cloud", 0.0);
            data.leech = saved.stats.getOrDefault("leech", 0.0);
            data.tgBounce = saved.stats.getOrDefault("tg_bounce", 0.0);
            data.homing = saved.stats.getOrDefault("homing", 0.0);
            data.homingOnBlock = saved.stats.getOrDefault("homing_on_block", 0.0);
            data.poisonLvl = saved.stats.getOrDefault("poison_lvl", 0.0);
            data.coldLvl = saved.stats.getOrDefault("cold_lvl", 0.0);
            data.parazitLvl = saved.stats.getOrDefault("parazit_lvl", 0.0);
            data.parazit = saved.stats.getOrDefault("parazit", 0.0);
            data.hp = saved.stats.getOrDefault("hp", 20.0);
            data.shieldCooldown = saved.stats.getOrDefault("shield_cooldown", 0.0);
            data.bombBullet = saved.stats.getOrDefault("bomb_bullet", 0.0);
            data.bombOnBlock = saved.stats.getOrDefault("bomb_on_block", 0.0);
            data.bulletSpeed = saved.stats.getOrDefault("bullet_speed", 1.0);
            data.empower = saved.stats.getOrDefault("empower", 0.0);
            data.empowerCharge = saved.stats.getOrDefault("empower_charge", 0.0);
            data.darkStrength = saved.stats.getOrDefault("dark_strength", 0.0);
            data.bigBullet = saved.stats.getOrDefault("big_bullet", 0.0);
            data.grow = saved.stats.getOrDefault("grow", 0.0);
            data.trusterLvl = saved.stats.getOrDefault("truster_lvl", 0.0);
            data.jumpHeight = saved.stats.getOrDefault("jump_height", 0.0);
            data.dark = saved.stats.getOrDefault("dark", 0.0);
            data.atksReload = saved.stats.getOrDefault("atks_reload", 0.0);
            data.pristinePerseverance = saved.stats.getOrDefault("pristine_perseverance", 0.0);
        }
    }

    // ==================== Per-player save on quit ====================

    public void savePlayerDataOnQuit(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;
        GameTeam team = plugin.getTeamManager().getPlayerTeam(uuid);

        List<Integer> pendingCardIds = plugin.getCardManager().getPendingCardIds(uuid);

        YamlConfiguration yml = store();
        String path = "players." + uuid.toString();
        yml.set(path + ".team", team != null ? team.name() : null);
        yml.set(path + ".name", data.playerName);
        yml.set(path + ".pending-cards", pendingCardIds);
        yml.set(path + ".was-active", activePlayers.contains(uuid));

        yml.set(path + ".stats.dmg", data.dmg);
        yml.set(path + ".stats.atks", data.atks);
        yml.set(path + ".stats.atk-speed", data.atkSpeed);
        yml.set(path + ".stats.atkr", data.atkr);
        yml.set(path + ".stats.bounce", data.bouncePl);
        yml.set(path + ".stats.ammo", data.ammo);
        yml.set(path + ".stats.max-ammo", data.maxAmmo);
        yml.set(path + ".stats.bullets", data.bullets);
        yml.set(path + ".stats.cold", data.cold);
        yml.set(path + ".stats.poison", data.poison);
        yml.set(path + ".stats.toxic-cloud", data.toxicCloud);
        yml.set(path + ".stats.leech", data.leech);
        yml.set(path + ".stats.tg-bounce", data.tgBounce);
        yml.set(path + ".stats.homing", data.homing);
        yml.set(path + ".stats.homing-on-block", data.homingOnBlock);
        yml.set(path + ".stats.poison-lvl", data.poisonLvl);
        yml.set(path + ".stats.cold-lvl", data.coldLvl);
        yml.set(path + ".stats.parazit-lvl", data.parazitLvl);
        yml.set(path + ".stats.parazit", data.parazit);
        yml.set(path + ".stats.hp", data.hp);
        yml.set(path + ".stats.shield-cooldown", data.shieldCooldown);
                yml.set(path + ".stats.bomb-bullet", data.bombBullet);
        yml.set(path + ".stats.bomb-on-block", data.bombOnBlock);

        yml.set(path + ".stats.bullet-speed", data.bulletSpeed);
        yml.set(path + ".stats.empower", data.empower);
        yml.set(path + ".stats.empower-charge", data.empowerCharge);
        yml.set(path + ".stats.dark-strength", data.darkStrength);
        yml.set(path + ".stats.big-bullet", data.bigBullet);
        yml.set(path + ".stats.grow", data.grow);
        yml.set(path + ".stats.truster-lvl", data.trusterLvl);
        yml.set(path + ".stats.jump-height", data.jumpHeight);
        yml.set(path + ".stats.dark", data.dark);
        yml.set(path + ".stats.atks-reload", data.atksReload);
        yml.set(path + ".stats.pristine-perseverance", data.pristinePerseverance);

        yml.set(path + ".cards", new ArrayList<>(data.getOwnedCards()));

        flushStore();
    }

    // ==================== Legacy active-players.yml tracking ====================

    private void saveActivePlayers() {
        // Пишем в общий кэш, а не в новый конфиг — иначе терялись бы секции players.*.
        YamlConfiguration config = store();
        config.set("version", 2);
        List<String> uuids = new ArrayList<>();
        for (UUID uuid : activePlayers) {
            uuids.add(uuid.toString());
        }
        config.set("active-list", uuids);
        flushStore();
    }

    private void loadActivePlayers() {
        if (!activeFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(activeFile);

        if (config.contains("active-list")) {
            List<String> uuids = config.getStringList("active-list");
            for (String s : uuids) {
                try { activePlayers.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
            }
            return;
        }

        var section = config.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String uuidStr = section.getString(key);
            if (uuidStr != null) {
                try { activePlayers.add(UUID.fromString(uuidStr)); } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void clearActiveFile() {
        if (activeFile.exists()) activeFile.delete();
    }

    // ==================== Internal YAML helpers ====================

    // Кэш конфига в памяти: чтение файла один раз, запись — синхронная (crash-safe).
    private YamlConfiguration playerStore;

    private YamlConfiguration store() {
        if (playerStore == null) {
            playerStore = activeFile.exists()
                    ? YamlConfiguration.loadConfiguration(activeFile)
                    : new YamlConfiguration();
        }
        return playerStore;
    }

    private void flushStore() {
        try {
            store().save(activeFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save active players YAML: " + e.getMessage());
        }
    }

    public void removePlayerData(UUID uuid) {
        YamlConfiguration yml = store();
        yml.set("players." + uuid.toString(), null);
        flushStore();
    }

    // ==================== Standard cache + PDC ====================

    public PlayerData getData(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), uuid -> new PlayerData());
    }

    public PlayerData getData(UUID uuid) {
        return cache.computeIfAbsent(uuid, u -> new PlayerData());
    }

    public void clearPDC(Player player) {
        if (player == null) return;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        for (NamespacedKey key : STAT_KEYS.values()) {
            pdc.remove(key);
        }
        for (int id : getRegisteredCardIds()) {
            NamespacedKey cardKey = new NamespacedKey("rounds", "card_" + id);
            pdc.remove(cardKey);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        try {
            boolean isCurrentGameActive = plugin.getGameManager().isGameStarted();
            boolean wasInCurrentGame = isCurrentGameActive && activePlayers.contains(uuid);

            plugin.getLogger().info("[Join] " + player.getName() + " joined, gameStarted=" + isCurrentGameActive + " wasActive=" + wasInCurrentGame);

            if (wasInCurrentGame) {
                // Reconnecting player to the currently active match
                PlayerData data = loadFromPDC(player);
                cache.put(uuid, data);
                SavedPlayerData playerSaved = loadPlayerFullData(uuid);
                if (playerSaved != null) {
                    applySavedData(uuid, playerSaved);
                }
            } else {
                // Joining lobby or joining as spectator with a clean slate
                PlayerData data = new PlayerData();
                data.playerName = player.getName();
                cache.put(uuid, data);
                clearPDC(player);
                removePlayerData(uuid);

                if (isCurrentGameActive) {
                    if (plugin.getTeamManager().getPlayerTeam(uuid) != null) {
                        plugin.getTeamManager().leaveTeam(uuid);
                    }
                    plugin.getGameManager().showNameTagsInGame();
                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                        player.setGameMode(GameMode.SPECTATOR);
                        player.setInvulnerable(true);
                        Player target = plugin.getGameManager().findRandomAlivePlayer();
                        if (target != null) {
                            player.teleport(target.getLocation());
                        }
                    }, 5L);
                } else {
                    plugin.getLogger().info("[Join] " + player.getName() + " -> WAITING, scheduling reset");
                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                        try {
                            plugin.getLogger().info("[Join] " + player.getName() + " -> executing reset now");
                            PlayerData pdata = getData(uuid);
                            if (pdata != null) {
                                pdata.resetStats();
                                pdata.resetAllCards();
                            }
                            clearPDC(player);
                            removePlayerData(uuid);
                            plugin.getGameManager().clearCardEffects(player);
                            com.rounds.item.GunItem.cancelReload(uuid);
                            com.rounds.item.GunItem.clearPlayer(uuid);
                            player.getInventory().clear();
                            var attr = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
                            if (attr != null) attr.setBaseValue(20);
                            player.setHealth(20);
                            player.setFoodLevel(20);
                            player.setGameMode(GameMode.ADVENTURE);
                            player.setInvulnerable(false);
                            player.setNoDamageTicks(0);
                            org.bukkit.Location lobby = plugin.getBlockListener().getBlockStorage().getLobbyBlock();
                            if (lobby != null) {
                                player.teleport(lobby.clone().add(0, 1, 0));
                            } else {
                                plugin.getLogger().warning("[Join] Lobby is null!");
                            }
                        } catch (Exception e) {
                            plugin.getLogger().severe("[Join] Error resetting " + player.getName() + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }, 5L);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[Join] Error in onJoin for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerData data = cache.get(uuid);
        if (data != null && activePlayers.contains(uuid) && plugin.getGameManager().isGameStarted()) {
            saveToPDC(player, data);
            savePlayerDataOnQuit(uuid);
        } else {
            clearPDC(player);
            removePlayerData(uuid);
        }
        com.rounds.item.GunItem.cancelReload(uuid);
        if (plugin.getGameManager().getState() == GameManager.GameState.CARDS) {
            plugin.getCardManager().removePendingPick(uuid);
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                GameManager checkGm = plugin.getGameManager();
                if (checkGm.getState() == GameManager.GameState.CARDS
                        && plugin.getCardManager().allPicksDone()) {
                    checkGm.onAllCardsPicked();
                }
            }, 10L);
        }
        cache.remove(uuid);
        GunCooldowns.clear(uuid);

        GameManager gm = plugin.getGameManager();
        if (gm.isGameStarted() && gm.getState() != GameManager.GameState.GAME_END) {
            int alive = 0;
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getUniqueId().equals(uuid)) continue;
                if (plugin.getTeamManager().getPlayerTeam(p.getUniqueId()) != null) {
                    alive++;
                }
            }
            if (alive < 2) {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.getUniqueId().equals(uuid)) continue;
                    if (plugin.getTeamManager().getPlayerTeam(p.getUniqueId()) != null) {
                        p.sendTitle(Messages.get("title.game-won"), "", 10, 100, 20);
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        p.sendMessage(ChatColor.GREEN + Messages.get("game.auto-stop-single"));
                    }
                }
                gm.stopGame();
                plugin.getTeamManager().clearAll();
                gm.getStateManager().clear();
            }
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, PlayerData> entry : cache.entrySet()) {
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p != null) {
                saveToPDC(p, entry.getValue());
            }
        }
    }

    private void saveToPDC(Player player, PlayerData data) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(RoundsKeys.PLAYER_NAME, PersistentDataType.STRING, data.playerName);

        setStat(pdc, "dmg", data.dmg);
        setStat(pdc, "atks", data.atks);
        setStat(pdc, "atk-speed", data.atkSpeed);
        setStat(pdc, "atkr", data.atkr);
        setStat(pdc, "bounce", data.bouncePl);
        setStat(pdc, "ammo", data.ammo);
        setStat(pdc, "max-ammo", data.maxAmmo);
        setStat(pdc, "bullets", data.bullets);
        setStat(pdc, "cold", data.cold);
        setStat(pdc, "poison", data.poison);
        setStat(pdc, "toxic_cloud", data.toxicCloud);
        setStat(pdc, "leech", data.leech);
        setStat(pdc, "tg_bounce", data.tgBounce);
        setStat(pdc, "homing", data.homing);
        setStat(pdc, "homing_on_block", data.homingOnBlock);
        setStat(pdc, "poison_lvl", data.poisonLvl);
        setStat(pdc, "cold_lvl", data.coldLvl);
        setStat(pdc, "parazit_lvl", data.parazitLvl);
        setStat(pdc, "parazit", data.parazit);
        setStat(pdc, "hp", data.hp);
        setStat(pdc, "shield_cooldown", data.shieldCooldown);
        setStat(pdc, "bomb_bullet", data.bombBullet);
        setStat(pdc, "bomb_on_block", data.bombOnBlock);
        setStat(pdc, "bullet_speed", data.bulletSpeed);
        setStat(pdc, "empower", data.empower);
        setStat(pdc, "empower_charge", data.empowerCharge);
        setStat(pdc, "dark_strength", data.darkStrength);
        setStat(pdc, "big_bullet", data.bigBullet);
        setStat(pdc, "grow", data.grow);
        setStat(pdc, "truster_lvl", data.trusterLvl);
        setStat(pdc, "jump_height", data.jumpHeight);
        setStat(pdc, "dark", data.dark);
        setStat(pdc, "pristine_perseverance", data.pristinePerseverance);
        setStat(pdc, "card_select_1", data.cardSelect1);
        setStat(pdc, "card_select_2", data.cardSelect2);
        setStat(pdc, "card_select_3", data.cardSelect3);
        setStat(pdc, "card_select_4", data.cardSelect4);
        setStat(pdc, "card_select_5", data.cardSelect5);
        setStat(pdc, "card_uses", data.cardUses);
        setStat(pdc, "rare_card", data.rareCard);
        setStat(pdc, "player_use", data.playerUse);
        setStat(pdc, "atks_reload", data.atksReload);

        for (int id : getRegisteredCardIds()) {
            NamespacedKey cardKey = new NamespacedKey("rounds", "card_" + id);
            if (data.getCard(id)) {
                pdc.set(cardKey, PersistentDataType.DOUBLE, 1.0);
            } else if (pdc.has(cardKey, PersistentDataType.DOUBLE)) {
                pdc.remove(cardKey);
            }
        }
    }

    private PlayerData loadFromPDC(Player player) {
        PlayerData data = new PlayerData();
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        String name = pdc.get(RoundsKeys.PLAYER_NAME, PersistentDataType.STRING);
        if (name != null) data.playerName = name;

        data.dmg = getStat(pdc, "dmg", 1.0);
        data.atks = getStat(pdc, "atks", 20);
        data.atkSpeed = getStat(pdc, "atk-speed", 0);
        data.atkr = getStat(pdc, "atkr", 0);
        data.bouncePl = getStat(pdc, "bounce", 0);
        data.ammo = getStat(pdc, "ammo", DefaultStats.get().ammo);
        data.maxAmmo = getStat(pdc, "max-ammo", DefaultStats.get().maxAmmo);
        data.bullets = getStat(pdc, "bullets", 1);
        data.cold = getStat(pdc, "cold", 0);
        data.poison = getStat(pdc, "poison", 0);
        data.toxicCloud = getStat(pdc, "toxic_cloud", 0);
        data.leech = getStat(pdc, "leech", 0);
        data.tgBounce = getStat(pdc, "tg_bounce", 0);
        data.homing = getStat(pdc, "homing", 0);
        data.homingOnBlock = getStat(pdc, "homing_on_block", 0);
        data.poisonLvl = getStat(pdc, "poison_lvl", 0);
        data.coldLvl = getStat(pdc, "cold_lvl", 0);
        data.parazitLvl = getStat(pdc, "parazit_lvl", 0);
        data.parazit = getStat(pdc, "parazit", 0);
        data.hp = getStat(pdc, "hp", 20);
        data.shieldCooldown = getStat(pdc, "shield_cooldown", 0);
        data.bombBullet = getStat(pdc, "bomb_bullet", 0);
        data.bombOnBlock = getStat(pdc, "bomb_on_block", 0);
        data.bulletSpeed = getStat(pdc, "bullet_speed", 1.0);
        data.empower = getStat(pdc, "empower", 0);
        data.empowerCharge = getStat(pdc, "empower_charge", 0);
        data.darkStrength = getStat(pdc, "dark_strength", 0);
        data.bigBullet = getStat(pdc, "big_bullet", 0);
        data.grow = getStat(pdc, "grow", 0);
        data.trusterLvl = getStat(pdc, "truster_lvl", 0);
        data.jumpHeight = getStat(pdc, "jump_height", 0);
        data.dark = getStat(pdc, "dark", 0);
        data.pristinePerseverance = getStat(pdc, "pristine_perseverance", 0);
        data.cardSelect1 = getStat(pdc, "card_select_1", 0);
        data.cardSelect2 = getStat(pdc, "card_select_2", 0);
        data.cardSelect3 = getStat(pdc, "card_select_3", 0);
        data.cardSelect4 = getStat(pdc, "card_select_4", 0);
        data.cardSelect5 = getStat(pdc, "card_select_5", 0);
        data.cardUses = getStat(pdc, "card_uses", 0);
        data.rareCard = getStat(pdc, "rare_card", 0);
        data.playerUse = getStat(pdc, "player_use", 0);
        data.atksReload = getStat(pdc, "atks_reload", 0);

        for (int id : getRegisteredCardIds()) {
            NamespacedKey cardKey = new NamespacedKey("rounds", "card_" + id);
            Double cardVal = pdc.get(cardKey, PersistentDataType.DOUBLE);
            data.setCard(id, cardVal != null && cardVal > 0);
        }

        return data;
    }

    private void setStat(PersistentDataContainer pdc, String name, double value) {
        NamespacedKey key = STAT_KEYS.get(name);
        if (key == null) return;
        pdc.set(key, PersistentDataType.DOUBLE, value);
    }

    private double getStat(PersistentDataContainer pdc, String name, double def) {
        Double val = pdc.get(STAT_KEYS.get(name), PersistentDataType.DOUBLE);
        return val != null ? val : def;
    }

    public static final class GunCooldowns {
        private static final Map<UUID, Long> LAST_SHOT = new HashMap<>();
        private static final Map<UUID, Long> LAST_RELOAD = new HashMap<>();

        public static boolean canShoot(UUID uuid, double cooldownTicks) {
            long now = System.currentTimeMillis();
            long last = LAST_SHOT.getOrDefault(uuid, 0L);
            long cooldownMs = (long) (cooldownTicks * 50);
            return now - last >= cooldownMs;
        }

        public static void recordShot(UUID uuid) {
            LAST_SHOT.put(uuid, System.currentTimeMillis());
        }

        public static boolean canReload(UUID uuid, double reloadTicks) {
            long now = System.currentTimeMillis();
            long last = LAST_RELOAD.getOrDefault(uuid, 0L);
            long cooldownMs = (long) (reloadTicks * 50);
            return now - last >= cooldownMs;
        }

        public static void recordReload(UUID uuid) {
            LAST_RELOAD.put(uuid, System.currentTimeMillis());
        }

        public static void clear(UUID uuid) {
            LAST_SHOT.remove(uuid);
            LAST_RELOAD.remove(uuid);
        }
    }

    public static class SavedPlayerData {
        public final GameTeam team;
        public final List<Integer> pendingCardIds;
        public final List<Integer> ownedCards;
        public final Map<String, Double> stats;

        public SavedPlayerData(GameTeam team, List<Integer> pendingCardIds,
                               List<Integer> ownedCards, Map<String, Double> stats) {
            this.team = team;
            this.pendingCardIds = pendingCardIds;
            this.ownedCards = ownedCards;
            this.stats = stats;
        }
    }
}

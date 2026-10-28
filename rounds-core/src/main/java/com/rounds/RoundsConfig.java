package com.rounds;

import com.rounds.teams.TeamManager.GameTeam;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public final class RoundsConfig {

    private final RoundsPlugin plugin;

    private List<GameTeam> enabledTeams = new ArrayList<>();
    private int defaultRounds;
    private int maxRounds;
    private int cardChoiceTimeSeconds = 30;
    private int cardSelectionTicks;
    private int respawnDelayTicks;

    private double baseGunCooldown;
    private String gunMaterial;

    private int cardSelectionCount;
    private boolean weightedRarity;

    private boolean builtinScoreboard;
    private String builtinScoreboardTitle;
    private String builtinScoreboardStatsTitle;

    private boolean freeplay;

    private boolean colorNicknames;

    private boolean gameRulesEnabled;
    private boolean grInstantRespawn;
    private boolean grKeepInventory;
    private boolean grFreezeTime;
    private boolean grDisableWeather;
    private boolean grDisableMobSpawning;

    private double jumpBlockDamagePercent;
    private double jumpBlockLaunchHeight;

    private double upBlockLiftSpeed;
    private int upBlockDurationTicks;

    private int shieldDurationTicks;
    private long shieldCooldownMs;
    private long noPartyCooldownMs;
    private double noPartyRadius;

    private int countdownSeconds;
    private int roundTimeSeconds;
    private boolean autoDeathEnabled;
    private int autoDeathDelaySeconds;
    private int autoDeathIntervalSeconds;
    private boolean autoDeathBossBar;

    private int botBulkLimit;
    private double botVisionFov;
    private double botMaxVisionDistance;
    private double botProximityHearingDistance;
    private double botJumpPadParryChance;
    private int botJumpPadParryMinLevel;

    private boolean musicEnabled;
    private double musicVolume;

    public RoundsConfig(RoundsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        List<String> teamList = c.getStringList("teams.enabled");
        enabledTeams.clear();
        if (teamList != null && !teamList.isEmpty()) {
            for (String s : teamList) {
                try {
                    enabledTeams.add(GameTeam.valueOf(s.toUpperCase()));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (enabledTeams.isEmpty()) {
            enabledTeams.addAll(Arrays.asList(GameTeam.values()));
        }

        defaultRounds = c.getInt("game.default-rounds", 5);
        maxRounds = c.getInt("game.max-rounds", 20);
        cardChoiceTimeSeconds = c.getInt("game.card-choice-time", 30);
        if (cardChoiceTimeSeconds < 10) cardChoiceTimeSeconds = 30;
        cardSelectionTicks = c.getInt("game.card-selection-time", 200);
        respawnDelayTicks = c.getInt("game.respawn-delay", 5);

        baseGunCooldown = c.contains("gun.base-cooldown")
                ? c.getDouble("gun.base-cooldown")
                : c.getDouble("defaults.attack-speed", 20);
        gunMaterial = c.getString("gun.material", "CROSSBOW");

        cardSelectionCount = c.getInt("cards.selection-count", 5);
        weightedRarity = c.getBoolean("cards.weighted-rarity", true);

        builtinScoreboard = c.getBoolean("builtin-scoreboard.enabled", true);
        builtinScoreboardTitle = c.getString("builtin-scoreboard.title", "&6&lROUNDS");
        builtinScoreboardStatsTitle = c.getString("builtin-scoreboard.stats-title", "&b&lМои статы");

        freeplay = c.getBoolean("freeplay.enabled", false);

        colorNicknames = c.getBoolean("color-nicknames", true);

        String gr = "game-rules.";
        gameRulesEnabled = c.getBoolean(gr + "enabled", true);
        grInstantRespawn = c.getBoolean(gr + "instant-respawn", true);
        grKeepInventory = c.getBoolean(gr + "keep-inventory", true);
        grFreezeTime = c.getBoolean(gr + "freeze-time", true);
        grDisableWeather = c.getBoolean(gr + "disable-weather", true);
        grDisableMobSpawning = c.getBoolean(gr + "disable-mob-spawning", true);

        String jb = "jump-block.";
        jumpBlockDamagePercent = c.getDouble(jb + "damage-percent", 20.0);
        jumpBlockLaunchHeight = c.getDouble(jb + "launch-height", 10.0);

        String ub = "up-block.";
        upBlockLiftSpeed = c.getDouble(ub + "lift-speed", 0.3);
        upBlockDurationTicks = c.getInt(ub + "duration-ticks", 40);

        String sh = "shield.";
        shieldDurationTicks = c.getInt(sh + "duration-ticks", 20);
        shieldCooldownMs = c.getLong(sh + "cooldown-ms", 10000L);
        noPartyCooldownMs = c.getLong(sh + "no-party-cooldown-ms", 30000L);
        noPartyRadius = c.getDouble(sh + "no-party-radius", 5.0);

        countdownSeconds = c.getInt("game.countdown-seconds", 3);
        if (c.contains("game.round-time-seconds")) {
            roundTimeSeconds = c.getInt("game.round-time-seconds", 180);
        } else {
            int delay = c.getInt("game.auto-death.delay-seconds", 240);
            int interval = c.getInt("game.auto-death.interval-seconds", 60);
            roundTimeSeconds = Math.max(10, delay > interval ? (delay - interval) : delay);
        }
        autoDeathEnabled = c.getBoolean("game.auto-death.enabled", true);
        autoDeathDelaySeconds = c.getInt("game.auto-death.delay-seconds", 240);
        autoDeathIntervalSeconds = c.getInt("game.auto-death.interval-seconds", 60);
        autoDeathBossBar = c.getBoolean("game.auto-death.bossbar", true);

        botBulkLimit = c.getInt("bots.bulk-limit", 500);
        botVisionFov = c.getDouble("bots.vision.fov-degrees", 140.0);
        botMaxVisionDistance = c.getDouble("bots.vision.max-distance", 60.0);
        botProximityHearingDistance = c.getDouble("bots.vision.proximity-hearing", 2.5);
        botJumpPadParryChance = c.getDouble("bots.jump-pad-parry.chance", 0.50);
        botJumpPadParryMinLevel = c.getInt("bots.jump-pad-parry.min-level", 5);

        musicEnabled = c.getBoolean("music.enabled", true);
        musicVolume = c.getDouble("music.volume", 0.4);

        DefaultStats ds = new DefaultStats();
        ds.dmg = c.getDouble("defaults.damage", 3.0);
        ds.atks = c.getDouble("defaults.attack-speed", baseGunCooldown);
        ds.atkSpeed = c.getDouble("defaults.attack-speed-modifier", 0);
        ds.atkr = c.getDouble("defaults.attack-range", 0);
        ds.ammo = c.getDouble("defaults.ammo", 3);
        ds.maxAmmo = c.getDouble("defaults.max-ammo", 3);
        ds.bullets = c.getDouble("defaults.bullets", 1);
        ds.hp = c.getDouble("defaults.hp", 20);
        ds.bulletSpeed = c.getDouble("defaults.bullet-speed", 1.0);
        ds.reloadSpeed = c.getDouble("defaults.reload-speed", 0);
        DefaultStats.set(ds);
    }

    public int getDefaultRounds() { return defaultRounds; }
    public int getMaxRounds() { return maxRounds; }
    public int getCardChoiceTimeSeconds() { return cardChoiceTimeSeconds; }
    public void setCardChoiceTimeSeconds(int seconds) {
        this.cardChoiceTimeSeconds = Math.max(10, seconds);
        plugin.getConfig().set("game.card-choice-time", this.cardChoiceTimeSeconds);
        plugin.saveConfig();
    }
    public int getCardSelectionTicks() { return cardSelectionTicks; }
    public int getRespawnDelayTicks() { return respawnDelayTicks; }
    public double getBaseGunCooldown() { return baseGunCooldown; }
    public String getGunMaterial() { return gunMaterial; }
    public int getCardSelectionCount() { return cardSelectionCount; }
    public boolean isWeightedRarity() { return weightedRarity; }
    public boolean isBuiltinScoreboard() { return builtinScoreboard; }
    public String getBuiltinScoreboardTitle() { return builtinScoreboardTitle; }
    public String getBuiltinScoreboardStatsTitle() { return builtinScoreboardStatsTitle; }
    public boolean isColorNicknames() { return colorNicknames; }
    public boolean isGameRulesEnabled() { return gameRulesEnabled; }
    public boolean isGrInstantRespawn() { return grInstantRespawn; }
    public boolean isGrKeepInventory() { return grKeepInventory; }
    public boolean isGrFreezeTime() { return grFreezeTime; }
    public boolean isGrDisableWeather() { return grDisableWeather; }
    public boolean isGrDisableMobSpawning() { return grDisableMobSpawning; }
    public double getJumpBlockDamagePercent() { return jumpBlockDamagePercent; }
    public double getJumpBlockLaunchHeight() { return jumpBlockLaunchHeight; }
    public double getUpBlockLiftSpeed() { return upBlockLiftSpeed; }
    public int getUpBlockDurationTicks() { return upBlockDurationTicks; }

    public int getShieldDurationTicks() { return shieldDurationTicks; }
    public long getShieldCooldownMs() { return shieldCooldownMs; }
    public long getNoPartyCooldownMs() { return noPartyCooldownMs; }
    public double getNoPartyRadius() { return noPartyRadius; }

    public int getCountdownSeconds() { return countdownSeconds; }
    public int getRoundTimeSeconds() { return roundTimeSeconds; }
    public boolean isAutoDeathEnabled() { return autoDeathEnabled; }
    public int getAutoDeathDelaySeconds() { return autoDeathDelaySeconds; }
    public int getAutoDeathIntervalSeconds() { return autoDeathIntervalSeconds; }
    public boolean isAutoDeathBossBarEnabled() { return autoDeathBossBar; }
    public void setAutoDeathBossBarEnabled(boolean enabled) {
        this.autoDeathBossBar = enabled;
        plugin.getConfig().set("game.auto-death.bossbar", enabled);
        plugin.saveConfig();
    }

    public int getBotBulkLimit() { return botBulkLimit; }
    public double getBotVisionFov() { return botVisionFov; }
    public double getBotMaxVisionDistance() { return botMaxVisionDistance; }
    public double getBotProximityHearingDistance() { return botProximityHearingDistance; }
    public double getBotJumpPadParryChance() { return botJumpPadParryChance; }
    public int getBotJumpPadParryMinLevel() { return botJumpPadParryMinLevel; }

    public boolean isFreeplay() { return freeplay; }

    public void setFreeplay(boolean enabled) {
        this.freeplay = enabled;
        plugin.getConfig().set("freeplay.enabled", enabled);
        plugin.saveConfig();
    }

    public void setBuiltinScoreboard(boolean enabled) {
        this.builtinScoreboard = enabled;
        plugin.getConfig().set("builtin-scoreboard.enabled", enabled);
        plugin.saveConfig();
    }

    public void setBuiltinScoreboardTitle(String title) {
        this.builtinScoreboardTitle = title;
        plugin.getConfig().set("builtin-scoreboard.title", title);
        plugin.saveConfig();
    }

    public List<GameTeam> getEnabledTeams() {
        return Collections.unmodifiableList(enabledTeams);
    }

    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    public void setMusicEnabled(boolean enabled) {
        this.musicEnabled = enabled;
        plugin.getConfig().set("music.enabled", enabled);
        plugin.saveConfig();
    }

    public double getMusicVolume() {
        return musicVolume;
    }

    public void setMusicVolume(double volume) {
        this.musicVolume = Math.max(0.0, Math.min(1.0, volume));
        plugin.getConfig().set("music.volume", this.musicVolume);
        plugin.saveConfig();
    }
}

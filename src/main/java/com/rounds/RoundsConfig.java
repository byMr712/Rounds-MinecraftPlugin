package com.rounds;

import org.bukkit.configuration.file.FileConfiguration;

public final class RoundsConfig {

    private final RoundsPlugin plugin;

    private int defaultRounds;
    private int maxRounds;
    private int cardSelectionTicks;
    private int respawnDelayTicks;

    private double baseGunCooldown;
    private String gunMaterial;

    private int cardSelectionCount;
    private boolean weightedRarity;

    private boolean builtinScoreboard;
    private String builtinScoreboardTitle;

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

    public RoundsConfig(RoundsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        defaultRounds = c.getInt("game.default-rounds", 5);
        maxRounds = c.getInt("game.max-rounds", 20);
        cardSelectionTicks = c.getInt("game.card-selection-time", 200);
        respawnDelayTicks = c.getInt("game.respawn-delay", 5);

        baseGunCooldown = c.getDouble("gun.base-cooldown", 20);
        gunMaterial = c.getString("gun.material", "CROSSBOW");

        cardSelectionCount = c.getInt("cards.selection-count", 5);
        weightedRarity = c.getBoolean("cards.weighted-rarity", true);

        builtinScoreboard = c.getBoolean("builtin-scoreboard.enabled", false);
        builtinScoreboardTitle = c.getString("builtin-scoreboard.title", "&6&lROUNDS");

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

        DefaultStats ds = new DefaultStats();
        ds.dmg = c.getDouble("defaults.damage", 3.0);
        ds.atks = c.getDouble("defaults.attack-speed", 20);
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
    public int getCardSelectionTicks() { return cardSelectionTicks; }
    public int getRespawnDelayTicks() { return respawnDelayTicks; }
    public double getBaseGunCooldown() { return baseGunCooldown; }
    public String getGunMaterial() { return gunMaterial; }
    public int getCardSelectionCount() { return cardSelectionCount; }
    public boolean isWeightedRarity() { return weightedRarity; }
    public boolean isBuiltinScoreboard() { return builtinScoreboard; }
    public String getBuiltinScoreboardTitle() { return builtinScoreboardTitle; }
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
}

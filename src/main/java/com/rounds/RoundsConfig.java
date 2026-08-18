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
    private int gunCustomModelData;

    private boolean resourcePackAutoSend;
    private String resourcePackUrl;
    private String resourcePackHash;
    private String resourcePackPrompt;

    private int cardSelectionCount;
    private boolean weightedRarity;

    private boolean builtinScoreboard;
    private String builtinScoreboardTitle;

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
        gunCustomModelData = c.getInt("gun.custom-model-data", 9999);

        resourcePackAutoSend = c.getBoolean("resource-pack.auto-send", false);
        resourcePackUrl = c.getString("resource-pack.url", "");
        resourcePackHash = c.getString("resource-pack.hash", "");
        resourcePackPrompt = c.getString("resource-pack.prompt", "Download Rounds resource pack for custom textures?");

        cardSelectionCount = c.getInt("cards.selection-count", 5);
        weightedRarity = c.getBoolean("cards.weighted-rarity", true);

        builtinScoreboard = c.getBoolean("builtin-scoreboard.enabled", false);
        builtinScoreboardTitle = c.getString("builtin-scoreboard.title", "&6&lROUNDS");
    }

    public int getDefaultRounds() { return defaultRounds; }
    public int getMaxRounds() { return maxRounds; }
    public int getCardSelectionTicks() { return cardSelectionTicks; }
    public int getRespawnDelayTicks() { return respawnDelayTicks; }
    public double getBaseGunCooldown() { return baseGunCooldown; }
    public String getGunMaterial() { return gunMaterial; }
    public int getGunCustomModelData() { return gunCustomModelData; }
    public boolean isResourcePackAutoSend() { return resourcePackAutoSend; }
    public String getResourcePackUrl() { return resourcePackUrl; }
    public String getResourcePackHash() { return resourcePackHash; }
    public String getResourcePackPrompt() { return resourcePackPrompt; }
    public int getCardSelectionCount() { return cardSelectionCount; }
    public boolean isWeightedRarity() { return weightedRarity; }
    public boolean isBuiltinScoreboard() { return builtinScoreboard; }
    public String getBuiltinScoreboardTitle() { return builtinScoreboardTitle; }
}

package com.rounds;

import com.rounds.blocks.BlockListener;
import com.rounds.cards.CardManager;
import com.rounds.command.DebugCommands;
import com.rounds.entity.RoundsEntities;
import com.rounds.game.GameManager;
import com.rounds.gui.CardGUIListener;
import com.rounds.item.GunItem;
import com.rounds.listener.JumpListener;
import com.rounds.placeholder.RoundsPlaceholders;
import com.rounds.player.PlayerDataManager;
import com.rounds.teams.TeamManager;
import com.rounds.util.Messages;
import org.bukkit.plugin.java.JavaPlugin;

public class RoundsPlugin extends JavaPlugin {

    private static RoundsPlugin instance;
    private RoundsConfig roundsConfig;
    private GameManager gameManager;
    private TeamManager teamManager;
    private PlayerDataManager playerDataManager;
    private CardManager cardManager;
    private CardGUIListener cardGUIListener;
    private BlockListener blockListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Messages.init(this);
        roundsConfig = new RoundsConfig(this);

        teamManager = new TeamManager(this);
        playerDataManager = new PlayerDataManager(this);
        cardManager = new CardManager(this);
        gameManager = new GameManager(this);
        blockListener = new BlockListener(this);
        cardGUIListener = new CardGUIListener(this);

        DebugCommands.register(this);
        GunItem.register(this);
        RoundsEntities.register(this);

        getServer().getPluginManager().registerEvents(blockListener, this);
        getServer().getPluginManager().registerEvents(cardGUIListener, this);
        getServer().getPluginManager().registerEvents(gameManager, this);
        getServer().getPluginManager().registerEvents(playerDataManager, this);
        GunItem gunItem = new GunItem();
        GunItem.setInstance(gunItem);
        getServer().getPluginManager().registerEvents(gunItem, this);
        getServer().getPluginManager().registerEvents(new RoundsEntities(), this);
        getServer().getPluginManager().registerEvents(new JumpListener(this), this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new RoundsPlaceholders(this).register();
            getLogger().info("PlaceholderAPI found - placeholders registered (%rounds_%)");
        } else {
            getLogger().info("PlaceholderAPI not found - builtin scoreboard or TAB integration unavailable");
        }

        gameManager.restoreGame();

        getLogger().info("RoundsPlugin v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            if (gameManager.isGameStarted()) {
                playerDataManager.saveAllFullData(null);
                gameManager.getStateManager().save(gameManager);
            }
            gameManager.shutdown();
        }
        if (playerDataManager != null) playerDataManager.saveAll();
        instance = null;
    }

    public static RoundsPlugin getInstance() { return instance; }
    public RoundsConfig getRoundsConfig() { return roundsConfig; }
    public GameManager getGameManager() { return gameManager; }
    public TeamManager getTeamManager() { return teamManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public CardManager getCardManager() { return cardManager; }
    public CardGUIListener getCardGUI() { return cardGUIListener; }
    public BlockListener getBlockListener() { return blockListener; }
}

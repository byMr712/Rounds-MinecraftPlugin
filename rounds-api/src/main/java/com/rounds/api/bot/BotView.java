package com.rounds.api.bot;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface BotView {
    /**
     * Возвращает Bukkit-сущность игрока, представляющую бота.
     * Метод никогда не возвращает null; для NMS-ботов возвращается серверная обертка Player.
     */
    Player getBukkitPlayer();
    
    BotArchetype getArchetype();
    
    // Гарантированный диапазон: [1, 10]
    int getDifficultyLevel();
    
    // Гарантированный диапазон: [0.0, 1.0]
    double getHealthPercent();
    
    boolean hasLineOfSightToTarget();
    double getDistanceToTarget();
    Location getLastKnownTargetLocation();
}

package com.rounds.bot;

public enum BotStrategy {
    AGGRESSIVE, // RUSH in C++ combat_arena
    DEFENSIVE,  // RETREAT in C++ combat_arena
    BALANCED;   // STRAFE / SNIPE / heuristic in C++ combat_arena

    public static BotStrategy fromString(String str) {
        if (str == null) return BALANCED;
        return switch (str.trim().toLowerCase()) {
            case "aggressive", "rush", "attack" -> AGGRESSIVE;
            case "defensive", "retreat", "defense", "evade" -> DEFENSIVE;
            default -> BALANCED;
        };
    }
}

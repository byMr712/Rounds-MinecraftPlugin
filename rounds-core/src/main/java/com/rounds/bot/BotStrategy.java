package com.rounds.bot;

public enum BotStrategy {
    AGGRESSIVE_FLANKER,
    TACTICAL_SNIPER,
    DEFENSIVE_TURTLE,
    ERRATIC_BRAWLER,
    // Legacy compatibility aliases
    AGGRESSIVE,
    DEFENSIVE,
    BALANCED;

    public static BotAI.BotPlaystyle toPlaystyle(String str) {
        if (str == null) return BotAI.BotPlaystyle.AGGRESSIVE_FLANKER;
        String s = str.trim().toLowerCase().replace("-", "_").replace(" ", "_");
        return switch (s) {
            case "sniper", "tactical", "tactical_sniper", "snipe" -> BotAI.BotPlaystyle.TACTICAL_SNIPER;
            case "turtle", "defensive", "defensive_turtle", "defense", "shield" -> BotAI.BotPlaystyle.DEFENSIVE_TURTLE;
            case "brawler", "erratic", "erratic_brawler", "brawl" -> BotAI.BotPlaystyle.ERRATIC_BRAWLER;
            case "flanker", "aggressive", "aggressive_flanker", "rush", "attack" -> BotAI.BotPlaystyle.AGGRESSIVE_FLANKER;
            default -> BotAI.BotPlaystyle.AGGRESSIVE_FLANKER;
        };
    }

    public static BotStrategy fromString(String str) {
        if (str == null) return BALANCED;
        BotAI.BotPlaystyle ps = toPlaystyle(str);
        return switch (ps) {
            case TACTICAL_SNIPER -> TACTICAL_SNIPER;
            case DEFENSIVE_TURTLE -> DEFENSIVE_TURTLE;
            case ERRATIC_BRAWLER -> ERRATIC_BRAWLER;
            case AGGRESSIVE_FLANKER -> AGGRESSIVE_FLANKER;
        };
    }
}

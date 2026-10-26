package com.rounds.api.card;

import org.bukkit.ChatColor;

public enum CardRarity {
    COMMON(ChatColor.WHITE, 40),
    UNCOMMON(ChatColor.GREEN, 30),
    RARE(ChatColor.AQUA, 18),
    EPIC(ChatColor.LIGHT_PURPLE, 9),
    LEGENDARY(ChatColor.GOLD, 3);

    private final ChatColor color;
    private final int weight;

    CardRarity(ChatColor color, int weight) {
        this.color = color;
        this.weight = weight;
    }

    public ChatColor getColor() { return color; }
    public int getWeight() { return weight; }
}

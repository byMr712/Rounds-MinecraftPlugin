package com.rounds.api.impl;

import com.rounds.api.bot.BotArchetype;
import com.rounds.api.bot.BotView;
import com.rounds.bot.FakeBot;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class BotViewImpl implements BotView {

    private final FakeBot bot;

    public BotViewImpl(FakeBot bot) {
        this.bot = bot;
    }

    @Override
    public Player getBukkitPlayer() {
        return bot != null ? bot.getBukkitPlayer() : null;
    }

    @Override
    public BotArchetype getArchetype() {
        if (bot == null || bot.getPlaystyle() == null) {
            return BotArchetype.AGGRESSIVE_FLANKER;
        }
        try {
            return BotArchetype.valueOf(bot.getPlaystyle().name());
        } catch (IllegalArgumentException e) {
            return BotArchetype.AGGRESSIVE_FLANKER;
        }
    }

    @Override
    public int getDifficultyLevel() {
        if (bot == null) return 1;
        return Math.max(1, Math.min(10, bot.getLevel()));
    }

    @Override
    public double getHealthPercent() {
        Player p = getBukkitPlayer();
        if (p == null || p.getMaxHealth() <= 0) return 1.0;
        return Math.max(0.0, Math.min(1.0, p.getHealth() / p.getMaxHealth()));
    }

    @Override
    public boolean hasLineOfSightToTarget() {
        return bot != null && bot.getAi() != null && bot.getAi().canSeeTarget();
    }

    @Override
    public double getDistanceToTarget() {
        if (bot == null || bot.getAi() == null) return -1.0;
        return bot.getAi().getDistanceToTarget();
    }

    @Override
    public Location getLastKnownTargetLocation() {
        if (bot == null || bot.getAi() == null) return null;
        return bot.getAi().getLastKnownTargetLoc();
    }
}

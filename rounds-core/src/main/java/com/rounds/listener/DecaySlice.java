package com.rounds.listener;

import java.util.UUID;

public class DecaySlice {
    public final double damagePerSecond;
    public final UUID attackerId;
    public int secondsRemaining;
    public int ticksUntilNextSecond;

    public DecaySlice(double totalDamage, int totalSeconds, UUID attackerId) {
        this.damagePerSecond = totalDamage / (double) Math.max(1, totalSeconds);
        this.secondsRemaining = Math.max(1, totalSeconds);
        this.ticksUntilNextSecond = 20; // 1 second interval
        this.attackerId = attackerId;
    }
}

package com.rounds.cards;

/**
 * Pre-compiled, ultra-compact action types for data-driven card triggers.
 * Designed for zero string parsing and minimal overhead on tick execution.
 */
public enum CardActionType {
    /**
     * Restores ammunition to the player's magazine (value = count).
     */
    RESTORE_AMMO,

    /**
     * Heals the executing player (value = health points).
     */
    HEAL,

    /**
     * Deals damage to the opposing target entity (value = damage points).
     */
    DAMAGE_VICTIM,

    /**
     * Applies a directional velocity impulse to the shooter (value = multiplier).
     */
    PUSH_SHOOTER,

    /**
     * Applies a knockback impulse to the victim away from shooter (value = multiplier).
     */
    PUSH_VICTIM,

    /**
     * Plays a sound effect at the player location (param = sound name, value = pitch).
     */
    PLAY_SOUND,

    /**
     * Spawns particle effects (param = particle name, value = count).
     */
    SPAWN_PARTICLE,

    /**
     * Applies a potion effect to the target (param = effect name, value = duration in seconds).
     */
    APPLY_POTION
}

package com.rounds.cards;

/**
 * Combat event triggers for data-driven card abilities.
 */
public enum CardTrigger {
    /**
     * Triggered when the player fires a bullet.
     */
    ON_SHOOT,

    /**
     * Triggered when the player's bullet hits an enemy living entity.
     */
    ON_BULLET_HIT,

    /**
     * Triggered when the player receives damage from an attack.
     */
    ON_DAMAGE_TAKEN,

    /**
     * Triggered when the player successfully blocks an attack or projectile with the shield.
     */
    ON_SHIELD_BLOCK,

    /**
     * Triggered when the player begins or finishes a gun reload.
     */
    ON_RELOAD
}

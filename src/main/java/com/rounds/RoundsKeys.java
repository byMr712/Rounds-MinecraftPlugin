package com.rounds;

import org.bukkit.NamespacedKey;

public final class RoundsKeys {

    private RoundsKeys() {}

    public static final NamespacedKey PLAYER_NAME = key("player_name");
    public static final NamespacedKey PLAYER_USE = key("player_use");

    public static final NamespacedKey GUN = key("gun");
    public static final NamespacedKey IS_BULLET = key("is_bullet");
    public static final NamespacedKey BULLET_OWNER = key("bullet_owner");
    public static final NamespacedKey BULLET_DAMAGE = key("bullet_damage");
    public static final NamespacedKey BULLET_BOUNCE = key("bullet_bounce");
    public static final NamespacedKey BULLET_SCALE = key("bullet_scale");
    public static final NamespacedKey BULLET_HOMING = key("bullet_homing");
    public static final NamespacedKey BULLET_DRILL = key("bullet_drill");
    public static final NamespacedKey BULLET_SPAWN_LOC = key("bullet_spawn_loc");
    public static final NamespacedKey BULLET_TG_BOUNCE = key("bullet_tg_bounce");
    public static final NamespacedKey BULLET_SNEAKY = key("bullet_sneaky");
    public static final NamespacedKey BULLET_DISPLAY = key("bullet_display");

    public static final NamespacedKey IS_BOMB = key("is_bomb");
    public static final NamespacedKey IS_HEAL_RING = key("is_heal_ring");
    public static final NamespacedKey IS_TOXIC_RING = key("is_toxic_ring");
    public static final NamespacedKey IS_SHIELD_BOMB = key("is_shield_bomb");

    public static final NamespacedKey JOIN_BLOCK = key("join_block");
    public static final NamespacedKey CDSHOOT_BLOCK = key("cdshoot_block");
    public static final NamespacedKey LOBBY_BLOCK = key("lobby_block");
    public static final NamespacedKey MAP_BLOCK = key("map_block");
    public static final NamespacedKey SPAWN_BLOCK = key("spawn_block");
    public static final NamespacedKey JUMP_BLOCK = key("jump_block");
    public static final NamespacedKey UP_BLOCK = key("up_block");
    public static final NamespacedKey SHIELD = key("shield");
    public static final NamespacedKey SHIELD_OWNER = key("shield_owner");

    public static final NamespacedKey CARD_SELECT = key("card_select");

    private static NamespacedKey key(String value) {
        return new NamespacedKey("rounds", value);
    }
}

package com.rounds.placeholder;

import com.rounds.RoundsPlugin;
import com.rounds.game.GameManager;
import com.rounds.player.PlayerData;
import com.rounds.player.PlayerDataManager;
import com.rounds.teams.TeamManager;
import com.rounds.teams.TeamManager.GameTeam;
import com.rounds.util.Messages;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RoundsPlaceholders extends PlaceholderExpansion {

    private final RoundsPlugin plugin;

    public RoundsPlaceholders(RoundsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "rounds";
    }

    @Override
    public @NotNull String getAuthor() {
        return "RoundsPlugin";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    public record PlaceholderEntry(String key, String descriptionKey) {}

    public static List<PlaceholderEntry> getGamePlaceholders() {
        return List.of(
            new PlaceholderEntry("round",              "ph-desc.round"),
            new PlaceholderEntry("rounds_to_win",      "ph-desc.rounds-to-win"),
            new PlaceholderEntry("round_display",      "ph-desc.round-display"),
            new PlaceholderEntry("state",              "ph-desc.state"),
            new PlaceholderEntry("team",               "ph-desc.team"),
            new PlaceholderEntry("team_color",         "ph-desc.team-color"),
            new PlaceholderEntry("team_adjective",     "ph-desc.team-adjective"),
            new PlaceholderEntry("team_wins",          "ph-desc.team-wins"),
            new PlaceholderEntry("blue_wins",          "ph-desc.blue-wins"),
            new PlaceholderEntry("red_wins",           "ph-desc.red-wins"),
            new PlaceholderEntry("yellow_wins",        "ph-desc.yellow-wins"),
            new PlaceholderEntry("green_wins",         "ph-desc.green-wins"),
            new PlaceholderEntry("blue_name",          "ph-desc.blue-name"),
            new PlaceholderEntry("red_name",           "ph-desc.red-name"),
            new PlaceholderEntry("yellow_name",        "ph-desc.yellow-name"),
            new PlaceholderEntry("green_name",         "ph-desc.green-name")
        );
    }

    public static List<PlaceholderEntry> getStatPlaceholders() {
        return List.of(
            new PlaceholderEntry("stat_hp",            "ph-desc.stat-hp"),
            new PlaceholderEntry("stat_dmg",           "ph-desc.stat-dmg"),
            new PlaceholderEntry("stat_atk_speed",     "ph-desc.stat-atk-speed"),
            new PlaceholderEntry("stat_atkr",          "ph-desc.stat-atkr"),
            new PlaceholderEntry("stat_ammo",          "ph-desc.stat-ammo"),
            new PlaceholderEntry("stat_max_ammo",      "ph-desc.stat-max-ammo"),
            new PlaceholderEntry("stat_bullets",       "ph-desc.stat-bullets"),
            new PlaceholderEntry("stat_bullet_speed",  "ph-desc.stat-bullet-speed"),
            new PlaceholderEntry("stat_bounce",        "ph-desc.stat-bounce"),
            new PlaceholderEntry("stat_homing",        "ph-desc.stat-homing"),
            new PlaceholderEntry("stat_big_bullet",    "ph-desc.stat-big-bullet"),
            new PlaceholderEntry("stat_cold",          "ph-desc.stat-cold"),
            new PlaceholderEntry("stat_cold_lvl",      "ph-desc.stat-cold-lvl"),
            new PlaceholderEntry("stat_poison",        "ph-desc.stat-poison"),
            new PlaceholderEntry("stat_poison_lvl",    "ph-desc.stat-poison-lvl"),
            new PlaceholderEntry("stat_parazit",       "ph-desc.stat-parazit"),
            new PlaceholderEntry("stat_parazit_lvl",   "ph-desc.stat-parazit-lvl"),
            new PlaceholderEntry("stat_leech",         "ph-desc.stat-leech"),
            new PlaceholderEntry("stat_truster",       "ph-desc.stat-truster"),
            new PlaceholderEntry("stat_empower",       "ph-desc.stat-empower"),
            new PlaceholderEntry("stat_empower_charge","ph-desc.stat-empower-charge"),
            new PlaceholderEntry("stat_dark_strength", "ph-desc.stat-dark-strength"),
            new PlaceholderEntry("stat_dark",          "ph-desc.stat-dark"),
            new PlaceholderEntry("stat_grow",          "ph-desc.stat-grow"),
            new PlaceholderEntry("stat_bomb_bullet",   "ph-desc.stat-bomb-bullet"),
            new PlaceholderEntry("stat_bomb_on_block", "ph-desc.stat-bomb-on-block"),
            new PlaceholderEntry("stat_shield_active", "ph-desc.stat-shield-active"),
            new PlaceholderEntry("stat_shield_hp",     "ph-desc.stat-shield-hp"),
            new PlaceholderEntry("stat_shield_cd",     "ph-desc.stat-shield-cd"),
            new PlaceholderEntry("stat_speed",         "ph-desc.stat-speed"),
            new PlaceholderEntry("stat_stun",          "ph-desc.stat-stun"),
            new PlaceholderEntry("stat_saw",           "ph-desc.stat-saw"),
            new PlaceholderEntry("stat_silence",       "ph-desc.stat-silence"),
            new PlaceholderEntry("stat_emp",           "ph-desc.stat-emp"),
            new PlaceholderEntry("stat_sneaky",        "ph-desc.stat-sneaky"),
            new PlaceholderEntry("stat_phoenix",       "ph-desc.stat-phoenix"),
            new PlaceholderEntry("stat_abyssal",       "ph-desc.stat-abyssal"),
            new PlaceholderEntry("stat_cards",         "ph-desc.stat-cards")
        );
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        GameManager gm = plugin.getGameManager();
        TeamManager tm = plugin.getTeamManager();
        GameTeam team = tm.getPlayerTeam(player.getUniqueId());

        if (params.equalsIgnoreCase("round")) {
            return String.valueOf((int) gm.getCurrentRound());
        }
        if (params.equalsIgnoreCase("rounds_to_win")) {
            return String.valueOf((int) gm.getRounds());
        }
        if (params.equalsIgnoreCase("round_display")) {
            return (int) gm.getCurrentRound() + "/" + (int) gm.getRounds();
        }
        if (params.equalsIgnoreCase("state")) {
            return gm.getState().name();
        }
        if (params.equalsIgnoreCase("team")) {
            if (team == null) return Messages.raw("team.none");
            return Messages.raw("team." + team.name().toLowerCase());
        }
        if (params.equalsIgnoreCase("team_color")) {
            if (team == null) return "";
            return team.getColor().toString();
        }
        if (params.equalsIgnoreCase("team_adjective")) {
            if (team == null) return "";
            return Messages.raw("team.adj-" + team.name().toLowerCase());
        }
        if (params.equalsIgnoreCase("team_wins")) {
            if (team == null) return "0";
            return String.valueOf((int) tm.getWins(team));
        }
        if (params.equalsIgnoreCase("blue_wins")) return String.valueOf((int) tm.getWins(GameTeam.BLUE));
        if (params.equalsIgnoreCase("red_wins")) return String.valueOf((int) tm.getWins(GameTeam.RED));
        if (params.equalsIgnoreCase("yellow_wins")) return String.valueOf((int) tm.getWins(GameTeam.YELLOW));
        if (params.equalsIgnoreCase("green_wins")) return String.valueOf((int) tm.getWins(GameTeam.GREEN));
        if (params.equalsIgnoreCase("blue_name")) return Messages.raw("team.blue");
        if (params.equalsIgnoreCase("red_name")) return Messages.raw("team.red");
        if (params.equalsIgnoreCase("yellow_name")) return Messages.raw("team.yellow");
        if (params.equalsIgnoreCase("green_name")) return Messages.raw("team.green");

        if (params.startsWith("stat_")) {
            PlayerDataManager pdm = plugin.getPlayerDataManager();
            PlayerData data = pdm.getData(player.getUniqueId());
            if (data == null) return "0";
            double v = getStatValue(data, params.substring(5));
            if (v == Math.floor(v) && !Double.isInfinite(v)) {
                return String.valueOf((long) v);
            }
            return String.valueOf(v);
        }

        return null;
    }

    private static double getStatValue(PlayerData d, String stat) {
        return switch (stat) {
            case "hp"             -> d.hp;
            case "dmg"            -> d.dmg;
            case "atk_speed"      -> d.atkSpeed;
            case "atkr"           -> d.atkr;
            case "ammo"           -> d.ammo;
            case "max_ammo"       -> d.maxAmmo;
            case "bullets"        -> d.bullets;
            case "bullet_speed"   -> d.bulletSpeed;
            case "bounce"         -> d.bouncePl;
            case "homing"         -> d.homing;
            case "big_bullet"     -> d.bigBullet;
            case "cold"           -> d.cold;
            case "cold_lvl"       -> d.coldLvl;
            case "poison"         -> d.poison;
            case "poison_lvl"     -> d.poisonLvl;
            case "parazit"        -> d.parazit;
            case "parazit_lvl"    -> d.parazitLvl;
            case "leech"          -> d.leech;
            case "truster"        -> d.trusterLvl;
            case "empower"        -> d.empower;
            case "empower_charge" -> d.empowerCharge;
            case "dark_strength"  -> d.darkStrength;
            case "dark"           -> d.dark;
            case "grow"           -> d.grow;
            case "bomb_bullet"    -> d.bombBullet;
            case "bomb_on_block"  -> d.bombOnBlock;
            case "shield_active"  -> d.shieldActive ? 1.0 : 0.0;
            case "shield_hp"      -> d.shieldHp;
            case "shield_cd"      -> d.shieldCooldown;
            case "speed"          -> d.speed;
            case "stun"           -> d.stun;
            case "saw"            -> d.saw;
            case "silence"        -> d.silence;
            case "emp"            -> d.emp;
            case "sneaky"         -> d.sneaky;
            case "phoenix"        -> d.phoenix;
            case "abyssal"        -> d.abyssal;
            case "cards"          -> { int c = 0; for (boolean b : d.cards) if (b) c++; yield c; }
            default               -> 0.0;
        };
    }
}

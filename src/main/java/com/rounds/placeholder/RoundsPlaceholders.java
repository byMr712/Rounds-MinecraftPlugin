package com.rounds.placeholder;

import com.rounds.RoundsPlugin;
import com.rounds.game.GameManager;
import com.rounds.player.PlayerData;
import com.rounds.player.PlayerDataManager;
import com.rounds.teams.TeamManager;
import com.rounds.teams.TeamManager.GameTeam;
import com.rounds.util.Messages;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
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
            new PlaceholderEntry("green_name",         "ph-desc.green-name"),
            new PlaceholderEntry("card_choice_time",   "ph-desc.card-choice-time"),
            new PlaceholderEntry("bots_count",         "ph-desc.bots-count"),
            new PlaceholderEntry("alive_players",      "ph-desc.alive-players"),
            new PlaceholderEntry("ping",               "ph-desc.ping")
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
            new PlaceholderEntry("stat_reload_time",   "ph-desc.stat-reload-time"),
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
            new PlaceholderEntry("stat_jump_height",   "ph-desc.stat-jump-height"),
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
            new PlaceholderEntry("stat_cards",         "ph-desc.stat-cards"),
            new PlaceholderEntry("stat_drill",         "ph-desc.stat-drill"),
            new PlaceholderEntry("stat_heal",          "ph-desc.stat-heal"),
            new PlaceholderEntry("stat_shockwave",     "ph-desc.stat-shockwave"),
            new PlaceholderEntry("stat_implode",       "ph-desc.stat-implode"),
            new PlaceholderEntry("stat_teleport",      "ph-desc.stat-teleport"),
            new PlaceholderEntry("stat_tactical_reload","ph-desc.stat-tactical-reload"),
            new PlaceholderEntry("stat_evasion",       "ph-desc.stat-evasion"),
            new PlaceholderEntry("stat_chameleon",     "ph-desc.stat-chameleon"),
            new PlaceholderEntry("stat_berserk",       "ph-desc.stat-berserk"),
            new PlaceholderEntry("stat_overheat",      "ph-desc.stat-overheat"),
            new PlaceholderEntry("stat_spikes",        "ph-desc.stat-spikes"),
            new PlaceholderEntry("stat_bullet_rain",   "ph-desc.stat-bullet-rain"),
            new PlaceholderEntry("stat_frost_armor",   "ph-desc.stat-frost-armor"),
            new PlaceholderEntry("stat_no_party",      "ph-desc.stat-no-party"),
            new PlaceholderEntry("stat_auto_reload",   "ph-desc.stat-auto-reload"),
            new PlaceholderEntry("stat_shields_up",    "ph-desc.stat-shields-up"),
            new PlaceholderEntry("stat_shield_charge", "ph-desc.stat-shield-charge"),
            new PlaceholderEntry("stat_double_block",  "ph-desc.stat-double-block"),
            new PlaceholderEntry("stat_overpower",     "ph-desc.stat-overpower"),
            new PlaceholderEntry("stat_lifesteal_aura","ph-desc.stat-lifesteal-aura"),
            new PlaceholderEntry("stat_target_bounce", "ph-desc.stat-target-bounce"),
            new PlaceholderEntry("stat_damage_per_bounce","ph-desc.stat-damage-per-bounce"),
            new PlaceholderEntry("stat_toxic_cloud",   "ph-desc.stat-toxic-cloud"),
            new PlaceholderEntry("stat_snowball",      "ph-desc.stat-snowball"),
            new PlaceholderEntry("stat_skyfall",       "ph-desc.stat-skyfall"),
            new PlaceholderEntry("stat_storm_caller",  "ph-desc.stat-storm-caller"),
            new PlaceholderEntry("stat_executioner",   "ph-desc.stat-executioner"),
            new PlaceholderEntry("stat_blood_furry",   "ph-desc.stat-blood-furry"),
            new PlaceholderEntry("stat_pristine_perseverance","ph-desc.stat-pristine-perseverance"),
            new PlaceholderEntry("stat_second_wind",   "ph-desc.stat-second-wind"),
            new PlaceholderEntry("stat_hp_cost",       "ph-desc.stat-hp-cost"),
            new PlaceholderEntry("stat_hp_boost_on_hit","ph-desc.stat-hp-boost-on-hit"),
            new PlaceholderEntry("stat_ammo_per_hit",  "ph-desc.stat-ammo-per-hit")
        );
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        return handlePlaceholder(player, params);
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        return handlePlaceholder(player, params);
    }

    private @Nullable String handlePlaceholder(@Nullable OfflinePlayer player, @NotNull String params) {
        GameManager gm = plugin.getGameManager();
        TeamManager tm = plugin.getTeamManager();

        // 1. Global game placeholders (work without player context: e.g. TAB header/footer, scoreboard)
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
        if (params.equalsIgnoreCase("blue_wins")) return String.valueOf((int) tm.getWins(GameTeam.BLUE));
        if (params.equalsIgnoreCase("red_wins")) return String.valueOf((int) tm.getWins(GameTeam.RED));
        if (params.equalsIgnoreCase("yellow_wins")) return String.valueOf((int) tm.getWins(GameTeam.YELLOW));
        if (params.equalsIgnoreCase("green_wins")) return String.valueOf((int) tm.getWins(GameTeam.GREEN));
        if (params.equalsIgnoreCase("blue_name")) return Messages.raw("team.blue");
        if (params.equalsIgnoreCase("red_name")) return Messages.raw("team.red");
        if (params.equalsIgnoreCase("yellow_name")) return Messages.raw("team.yellow");
        if (params.equalsIgnoreCase("green_name")) return Messages.raw("team.green");
        if (params.equalsIgnoreCase("card_choice_time")) {
            return String.valueOf(plugin.getRoundsConfig().getCardChoiceTimeSeconds());
        }
        if (params.equalsIgnoreCase("bots_count")) {
            return String.valueOf(plugin.getBotManager() != null ? plugin.getBotManager().getBots().size() : 0);
        }
        if (params.equalsIgnoreCase("alive_players")) {
            int alive = 0;
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() != org.bukkit.GameMode.SPECTATOR && !gm.getDeadPlayers().contains(p.getUniqueId())) {
                    alive++;
                }
            }
            return String.valueOf(alive);
        }

        // 2. Player-specific placeholders (require player/UUID)
        if (player == null) return "";

        UUID uuid = player.getUniqueId();
        GameTeam team = tm.getPlayerTeam(uuid);

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
        if (params.equalsIgnoreCase("ping")) {
            if (player == null) return "0";
            GameTeam t = plugin.getTeamManager().getPlayerTeam(uuid);
            com.rounds.bot.FakeBot bot = (plugin.getBotManager() != null && player.getName() != null) ? plugin.getBotManager().getBot(player.getName()) : null;
            org.bukkit.ChatColor c = (t != null) ? t.getColor() : (bot != null && bot.getAssignedTeam() != null ? bot.getAssignedTeam().getColor() : org.bukkit.ChatColor.GRAY);
            int ping = (player instanceof Player online) ? online.getPing() : 0;
            return c + String.valueOf(ping);
        }

        if (params.equalsIgnoreCase("team_wins")) {
            if (team == null) return "0";
            return String.valueOf((int) tm.getWins(team));
        }

        if (params.startsWith("stat_")) {
            PlayerDataManager pdm = plugin.getPlayerDataManager();
            PlayerData data = pdm.getData(uuid);
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
            case "reload_time"    -> Math.max(100 * (1.0 - Math.min(d.reloadSpeed, 0.95)) * (1.0 + d.atksReload * 0.1), 4) / 20.0;
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
            case "jump_height"    -> d.jumpHeight;
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
            case "cards"          -> d.getOwnedCards().size();
            case "drill"                 -> d.drill;
            case "heal"                  -> d.heal;
            case "shockwave"             -> d.shockwave;
            case "implode"               -> d.implode;
            case "teleport"              -> d.teleport;
            case "tactical_reload"       -> d.tacticalReload;
            case "evasion"               -> d.evasion;
            case "chameleon"             -> d.chameleon;
            case "berserk"               -> d.berserk;
            case "overheat"              -> d.overheat;
            case "spikes"                -> d.spikes;
            case "bullet_rain"           -> d.bulletRain;
            case "frost_armor"           -> d.frostArmor;
            case "no_party"              -> d.noParty;
            case "auto_reload"           -> d.autoReload;
            case "shields_up"            -> d.shieldsUp;
            case "shield_charge"         -> d.shieldCharge;
            case "double_block"          -> d.doubleBlock;
            case "overpower"             -> d.overpower;
            case "lifesteal_aura"        -> d.lifestealAura;
            case "target_bounce"         -> d.tgBounce;
            case "damage_per_bounce"     -> d.damagePerBounce;
            case "toxic_cloud"           -> d.toxicCloud;
            case "snowball"              -> d.snowball;
            case "skyfall"               -> d.skyfall;
            case "storm_caller"          -> d.stormCaller;
            case "executioner"           -> d.executioner;
            case "blood_furry"           -> d.bloodFurry;
            case "pristine_perseverance" -> d.pristinePerseverance;
            case "second_wind"           -> d.secondWind;
            case "hp_cost"               -> d.hpCost;
            case "hp_boost_on_hit"       -> d.hpBoostOnHit;
            case "ammo_per_hit"          -> d.ammoPerHit;
            default               -> 0.0;
        };
    }
}

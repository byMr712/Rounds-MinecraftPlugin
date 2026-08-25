package com.rounds.cards;

import com.rounds.player.PlayerData;
import com.rounds.util.Messages;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Card {

    private static final Pattern COLOR_PATTERN = Pattern.compile("&([0-9a-fk-or])");

    private final int id;
    private final int familyId;
    private final Map<String, String> names;
    private final Map<String, String> descriptions;
    private final Material material;
    private final CardRegistry.Rarity rarity;
    private final boolean enabled;
    private final Map<String, Double> effects;
    private final List<String> commands;
    private final String customScript;

    public Card(int id, int familyId, Map<String, String> names, Map<String, String> descriptions,
                Material material,
                CardRegistry.Rarity rarity, boolean enabled, Map<String, Double> effects,
                List<String> commands, String customScript) {
        this.id = id;
        this.familyId = familyId;
        this.names = names;
        this.descriptions = descriptions;
        this.material = material;
        this.rarity = rarity;
        this.enabled = enabled;
        this.effects = effects;
        this.commands = commands;
        this.customScript = customScript;
    }

    public void apply(Player player, PlayerData data) {
        for (Map.Entry<String, Double> entry : effects.entrySet()) {
            applyEffect(data, entry.getKey().replace('_', '-'), entry.getValue());
        }

        if (player != null) {
            for (String cmd : commands) {
                String resolved = cmd.replace("%player%", player.getName());
                player.getServer().dispatchCommand(player.getServer().getConsoleSender(), resolved);
            }
        }
    }

    private void applyEffect(PlayerData data, String key, double value) {
        switch (key) {
            case "damage": data.dmg = PlayerData.round2(data.dmg * (1.0 + value)); break;
            case "attack-speed": data.atkSpeed = PlayerData.round2(Math.max(data.atkSpeed + value, -0.99)); break;
            case "attack-range": data.atkr = PlayerData.round2(Math.max(data.atkr + value, 0)); break;
            case "bullets": data.bullets = PlayerData.round2(Math.max(data.bullets + value, 1)); break;
            case "ammo": data.ammo = Math.max(data.ammo + value, 1); data.maxAmmo = Math.max(data.maxAmmo + value, 1); break;
            case "bullet-speed": data.bulletSpeed = PlayerData.round2(Math.max(data.bulletSpeed + value, 0.1)); break;
            case "bounce": data.bouncePl = PlayerData.round2(Math.max(data.bouncePl + value, 0)); break;
            case "hp": data.hp = PlayerData.round2(Math.max(data.hp * (1.0 + value), 2)); break;
            case "hp-cost": data.hpCost = PlayerData.round2(Math.max(data.hpCost + value, 0)); break;
            case "cold": data.cold = PlayerData.round2(Math.max(data.cold + value, 0)); break;
            case "cold-level", "cold-lvl": data.coldLvl = PlayerData.round2(Math.max(data.coldLvl + value, 0)); break;
            case "poison": data.poison = PlayerData.round2(Math.max(data.poison + value, 0)); break;
            case "toxic-cloud": data.toxicCloud = PlayerData.round2(Math.max(data.toxicCloud + value, 0)); break;
            case "poison-level", "poison-lvl": data.poisonLvl = PlayerData.round2(Math.max(data.poisonLvl + value, 0)); break;
            case "homing": data.homing = PlayerData.round2(Math.max(data.homing + value, 0)); break;
            case "homing-on-block": data.homingOnBlock = PlayerData.round2(Math.max(data.homingOnBlock + value, 0)); break;
            case "leech": data.leech = PlayerData.round2(Math.max(data.leech + value, 0)); break;
            case "empower": data.empower = PlayerData.round2(Math.max(data.empower + value, 0)); break;
            case "empower-charge": data.empowerCharge = PlayerData.round2(Math.max(data.empowerCharge + value, 0)); break;
            case "dark-strength": data.darkStrength = PlayerData.round2(Math.max(data.darkStrength + value, 0)); break;
            case "big-bullet": data.bigBullet = PlayerData.round2(Math.max(data.bigBullet + value, 0)); break;
            case "bomb-bullet": data.bombBullet = PlayerData.round2(Math.max(data.bombBullet + value, 0)); break;
            case "bomb-on-block": data.bombOnBlock = PlayerData.round2(Math.max(data.bombOnBlock + value, 0)); break;
            case "target-bounce", "tg-bounce": data.tgBounce = PlayerData.round2(Math.max(data.tgBounce + value, 0)); break;
            case "shield": data.shieldCooldown = PlayerData.round2(Math.max(data.shieldCooldown + value, 0)); break;
            case "truster": data.trusterLvl = PlayerData.round2(Math.max(data.trusterLvl + value, 0)); break;
            case "jump-height": data.jumpHeight = PlayerData.round2(Math.max(data.jumpHeight + value, 0)); break;
            case "grow": data.grow = PlayerData.round2(Math.max(data.grow + value, 0)); break;
            case "attack-speed-reload", "reload": data.atksReload = PlayerData.round2(Math.max(data.atksReload + value, 0)); break;
            case "parazit": data.parazit = PlayerData.round2(Math.max(data.parazit + value, 0)); break;
            case "parazit-level", "parazit-lvl": data.parazitLvl = PlayerData.round2(Math.max(data.parazitLvl + value, 0)); break;
            case "speed": data.speed = PlayerData.round2(Math.max(data.speed + value, 0)); break;
            case "speed-boost": data.speedBoost = PlayerData.round2(Math.max(data.speedBoost + value, 0)); break;
            case "stun": data.stun = PlayerData.round2(Math.max(data.stun + value, 0)); break;
            case "block-cd": data.blockCd = PlayerData.round2(data.blockCd + value); break;
            case "reload-speed": data.reloadSpeed = PlayerData.round2(Math.max(Math.min(data.reloadSpeed + value, 0.95), 0)); break;
            case "heal": data.heal = PlayerData.round2(Math.max(data.heal + value, 0)); break;
            case "damage-per-bounce": data.damagePerBounce = PlayerData.round2(data.damagePerBounce + value); break;
            case "double-block": data.doubleBlock = PlayerData.round2(Math.max(data.doubleBlock + value, 0)); break;
            case "shields-up": data.shieldsUp = PlayerData.round2(Math.max(data.shieldsUp + value, 0)); break;
            case "shield-charge": data.shieldCharge = PlayerData.round2(Math.max(data.shieldCharge + value, 0)); break;
            case "auto-reload": data.autoReload = PlayerData.round2(Math.max(data.autoReload + value, 0)); break;
            case "saw": data.saw = PlayerData.round2(Math.max(data.saw + value, 0)); break;
            case "shockwave": data.shockwave = PlayerData.round2(Math.max(data.shockwave + value, 0)); break;
            case "silence": data.silence = PlayerData.round2(Math.max(data.silence + value, 0)); break;
            case "sneaky": data.sneaky = PlayerData.round2(Math.max(data.sneaky + value, 0)); break;
            case "emp": data.emp = PlayerData.round2(Math.max(data.emp + value, 0)); break;
            case "overpower": data.overpower = PlayerData.round2(Math.max(data.overpower + value, 0)); break;
            case "refresh": data.refresh = PlayerData.round2(Math.max(data.refresh + value, 0)); break;
            case "radiance": data.radiance = PlayerData.round2(Math.max(data.radiance + value, 0)); break;
            case "highlight": data.highlight = PlayerData.round2(Math.max(data.highlight + value, 0)); break;
            case "lifesteal-aura", "lifesteal_aura": data.lifestealAura = PlayerData.round2(Math.max(data.lifestealAura + value, 0)); break;
            case "phoenix": data.phoenix = PlayerData.round2(Math.max(data.phoenix + value, 0));
                data.phoenixUses = (int) data.phoenix; break;
            case "abyssal": data.abyssal = PlayerData.round2(Math.max(data.abyssal + value, 0)); break;
            case "implode": data.implode = PlayerData.round2(Math.max(data.implode + value, 0)); break;
            case "drill": data.drill = PlayerData.round2(Math.max(data.drill + value, 0)); break;
            case "teleport": data.teleport = PlayerData.round2(Math.max(data.teleport + value, 0)); break;
            case "tactical-reload", "tactical_reload": data.tacticalReload = PlayerData.round2(Math.max(data.tacticalReload + value, 0)); break;
            case "ammo-per-hit", "ammo_per_hit": data.ammoPerHit = PlayerData.round2(Math.max(data.ammoPerHit + value, 0)); break;
            case "hp-boost-on-hit", "hp_boost_on_hit": data.hpBoostOnHit = PlayerData.round2(Math.max(data.hpBoostOnHit + value, 0)); break;
            case "pristine-perseverance", "pristine_perseverance": data.pristinePerseverance = PlayerData.round2(Math.max(data.pristinePerseverance + value, 0)); break;
            case "blood-furry", "blood_furry": data.bloodFurry = PlayerData.round2(Math.max(data.bloodFurry + value, 0)); break;
            case "executioner": data.executioner = PlayerData.round2(Math.max(data.executioner + value, 0)); break;
            case "storm-caller", "storm_caller": data.stormCaller = PlayerData.round2(Math.max(data.stormCaller + value, 0)); break;
            case "evasion": data.evasion = PlayerData.round2(Math.max(data.evasion + value, 0)); break;
            case "chameleon": data.chameleon = PlayerData.round2(Math.max(data.chameleon + value, 0)); break;
            case "snowball": data.snowball = PlayerData.round2(Math.max(data.snowball + value, 0)); break;
            case "skyfall": data.skyfall = PlayerData.round2(Math.max(data.skyfall + value, 0)); break;
            case "berserk": data.berserk = PlayerData.round2(Math.max(data.berserk + value, 0)); break;
            case "overheat": data.overheat = PlayerData.round2(Math.max(data.overheat + value, 0)); break;
            case "second-wind", "second_wind": data.secondWind = PlayerData.round2(Math.max(data.secondWind + value, 0)); break;
            case "spikes": data.spikes = PlayerData.round2(Math.max(data.spikes + value, 0)); break;
            case "chikibamboni": data.chikibamboni = PlayerData.round2(Math.max(data.chikibamboni + value, 0)); break;
            case "bullet-rain", "bullet_rain": data.bulletRain = PlayerData.round2(Math.max(data.bulletRain + value, 0)); data.ammo = Math.max(data.ammo + value, 1); data.maxAmmo = Math.max(data.maxAmmo + value, 1); break;
            case "frost-armor", "frost_armor": data.frostArmor = PlayerData.round2(Math.max(data.frostArmor + value, 0)); break;
            case "no-party", "no_party": data.noParty = PlayerData.round2(Math.max(data.noParty + value, 0)); break;
        }
    }

    public ItemStack createItemStack() {
        String lang = Messages.getLanguageCode();
        return createItemStack(lang);
    }

    public ItemStack createItemStack(String lang) {
        return createItemStack(lang, true);
    }

    public ItemStack createItemStack(String lang, boolean withSelectHint) {
        ItemStack item;
        try {
            item = new ItemStack(material);
        } catch (IllegalArgumentException e) {
            item = new ItemStack(Material.PAPER);
        }
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(colorize(getName(lang)));

        List<String> lore = new ArrayList<>();
        lore.add(rarity.getColor() + Messages.get("rarity." + rarity.name().toLowerCase()));
        String desc = getDescription(lang);
        if (!desc.isEmpty()) {
            lore.add(ChatColor.GRAY + desc);
        }
        if (withSelectHint) {
            lore.add("");
            lore.add(ChatColor.YELLOW + Messages.get("card.click-to-select"));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static String colorize(String text) {
        Matcher matcher = COLOR_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.COLOR_CHAR + matcher.group(1));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public String getName(String lang) {
        String name = names.get(lang);
        if (name == null) name = names.get("en");
        if (name == null && !names.isEmpty()) name = names.values().iterator().next();
        return name != null ? name : "Card #" + id;
    }

    public String getColoredName(String lang) {
        return colorize(getName(lang));
    }

    public String getDescription(String lang) {
        String desc = descriptions.get(lang);
        if (desc == null) desc = descriptions.get("en");
        if (desc == null && !descriptions.isEmpty()) desc = descriptions.values().iterator().next();
        return desc != null ? desc : "";
    }

    public int getId() { return id; }
    public int getFamilyId() { return familyId; }
    public Map<String, String> getNames() { return Collections.unmodifiableMap(names); }
    public Map<String, String> getDescriptions() { return Collections.unmodifiableMap(descriptions); }
    public Material getMaterial() { return material; }
    public CardRegistry.Rarity getRarity() { return rarity; }
    public boolean isEnabled() { return enabled; }
    public Map<String, Double> getEffects() { return Collections.unmodifiableMap(effects); }
    public List<String> getCommands() { return Collections.unmodifiableList(commands); }
    public String getCustomScript() { return customScript; }
}

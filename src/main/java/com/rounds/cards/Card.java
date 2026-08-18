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
    private final Map<String, String> names;
    private final Map<String, String> descriptions;
    private final Material material;
    private final int customModelData;
    private final CardRegistry.Rarity rarity;
    private final boolean enabled;
    private final Map<String, Double> effects;
    private final List<String> commands;
    private final String customScript;

    public Card(int id, Map<String, String> names, Map<String, String> descriptions,
                Material material, int customModelData,
                CardRegistry.Rarity rarity, boolean enabled, Map<String, Double> effects,
                List<String> commands, String customScript) {
        this.id = id;
        this.names = names;
        this.descriptions = descriptions;
        this.material = material;
        this.customModelData = customModelData;
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

        for (String cmd : commands) {
            String resolved = cmd.replace("%player%", player.getName());
            player.getServer().dispatchCommand(player.getServer().getConsoleSender(), resolved);
        }
    }

    private void applyEffect(PlayerData data, String key, double value) {
        switch (key) {
            case "damage": data.dmg *= (1.0 + value); break;
            case "attack-speed": data.atkSpeed = 1.0 - (1.0 - data.atkSpeed) * (1.0 - value); break;
            case "attack-range": data.atkr += value; break;
            case "bullets": data.bullets += value; break;
            case "ammo": data.ammo = Math.max(data.ammo + value, 1); data.maxAmmo = Math.max(data.maxAmmo + value, 1); break;
            case "bullet-speed": data.bulletSpeed += value; break;
            case "bounce": data.bouncePl += value; break;
            case "hp": data.hp = Math.max(data.hp * (1.0 + value), 2); break;
            case "cold": data.cold += value; break;
            case "cold-level": data.coldLvl += value; break;
            case "poison": data.poison += value; break;
            case "poison-level": data.poisonLvl += value; break;
            case "homing": data.homing += value; break;
            case "leech": data.leech += value; break;
            case "empower": data.empower += value; break;
            case "empower-charge": data.empowerCharge += value; break;
            case "dark-strength": data.darkStrength += value; break;
            case "big-bullet": data.bigBullet += value; break;
            case "bomb-bullet": data.bombBullet += value; break;
            case "bomb-on-block": data.bombOnBlock += value; break;
            case "target-bounce": data.tgBounce += value; break;
            case "shield": data.shieldCooldown += value; break;
            case "truster": data.trusterLvl += value; break;
            case "grow": data.grow += value; break;
            case "attack-speed-reload": data.atksReload += value; break;
            case "reload": data.atksReload += value; break;
            case "parazit": data.parazit += value; break;
            case "parazit-level": data.parazitLvl += value; break;
            case "speed": data.speed += value; break;
            case "speed-boost": data.speedBoost += value; break;
            case "stun": data.stun += value; break;
            case "block-cd": data.blockCd += value; break;
            case "reload-speed": data.reloadSpeed += value; break;
            case "heal": data.heal += value; break;
            case "damage-per-bounce": data.damagePerBounce += value; break;
            case "double-block": data.doubleBlock += value; break;
            case "shields-up": data.shieldsUp += value; break;
            case "shield-charge": data.shieldCharge += value; break;
            case "auto-reload": data.autoReload += value; break;
            case "saw": data.saw += value; break;
            case "shockwave": data.shockwave += value; break;
            case "silence": data.silence += value; break;
            case "sneaky": data.sneaky += value; break;
            case "emp": data.emp += value; break;
            case "overpower": data.overpower += value; break;
            case "refresh": data.refresh += value; break;
            case "radiance": data.radiance += value; break;
            case "lifesteal-aura": data.lifestealAura += value; break;
            case "phoenix": data.phoenix += value; break;
            case "abyssal": data.abyssal += value; break;
            case "implode": data.implode += value; break;
            case "echo": data.echo += value; break;
            case "drill": data.drill += value; break;
            case "remote": data.remote += value; break;
            case "splash": data.splash += value; break;
            case "teleport": data.teleport += value; break;
            case "tactical-reload": data.tacticalReload += value; break;
            case "ammo-per-hit": data.ammoPerHit += value; break;
            case "hp-boost-on-hit": data.hpBoostOnHit += value; break;
        }
    }

    public ItemStack createItemStack() {
        String lang = Messages.getLanguage();
        return createItemStack(lang);
    }

    public ItemStack createItemStack(String lang) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(colorize(getName(lang)));

        List<String> lore = new ArrayList<>();
        lore.add(rarity.getColor() + rarity.name());
        String desc = getDescription(lang);
        if (!desc.isEmpty()) {
            lore.add(ChatColor.GRAY + desc);
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + Messages.get("card.click-to-select"));

        meta.setLore(lore);
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
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
    public Map<String, String> getNames() { return Collections.unmodifiableMap(names); }
    public Map<String, String> getDescriptions() { return Collections.unmodifiableMap(descriptions); }
    public Material getMaterial() { return material; }
    public int getCustomModelData() { return customModelData; }
    public CardRegistry.Rarity getRarity() { return rarity; }
    public boolean isEnabled() { return enabled; }
    public Map<String, Double> getEffects() { return Collections.unmodifiableMap(effects); }
    public List<String> getCommands() { return Collections.unmodifiableList(commands); }
    public String getCustomScript() { return customScript; }
}

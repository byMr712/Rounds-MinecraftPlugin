package com.rounds.api.card;

import com.rounds.api.bot.BotView;
import com.rounds.api.event.RoundsBulletHitEvent;
import com.rounds.api.event.RoundsShieldBlockEvent;
import com.rounds.api.event.RoundsWeaponShootEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.regex.Pattern;

public abstract class CustomCard {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9._-]+$");

    private final Plugin ownerPlugin;
    private final NamespacedKey key;
    private final CardRarity rarity;
    private final Material material;
    private final CardRole role;
    private int customModelData = 0; // 0 означает стандартную ванильную модель

    protected CustomCard(Plugin plugin, String id, CardRarity rarity, Material material, CardRole role) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin не может быть null");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Card ID не может быть пустым");
        }
        String cleanId = id.toLowerCase(Locale.ROOT);
        if (!ID_PATTERN.matcher(cleanId).matches()) {
            throw new IllegalArgumentException("Card ID '" + id + "' содержит недопустимые символы. Разрешены только: [a-z0-9._-]");
        }

        this.ownerPlugin = plugin;
        this.key = new NamespacedKey(plugin, cleanId);
        this.rarity = rarity != null ? rarity : CardRarity.COMMON;
        this.material = material != null ? material : Material.PAPER;
        this.role = role != null ? role : CardRole.OFFENSIVE;
    }

    public abstract String getName(String langCode);
    public abstract String getDescription(String langCode);

    public void onApply(CardContext context) {}
    public void onRemove(CardContext context) {}
    public void onRoundStart(CardContext context) {}
    public void onRoundEnd(CardContext context, boolean teamWon) {}

    public void onShoot(RoundsWeaponShootEvent event) {}
    public void onBulletHit(RoundsBulletHitEvent event) {}
    public void onShieldBlock(RoundsShieldBlockEvent event) {}

    /**
     * Оценка привлекательности карты для ИИ-бота при драфте.
     * Контракт:
     * - Базовое значение: 1.0 (нейтральная оценка).
     * - Допустимый диапазон: [0.0, 2.0].
     * - Если bot == null или возвращено NaN/бесконечность, ядро принимает значение 1.0.
     * - Любые значения вне диапазона автоматически клампятся ядром: Math.max(0.0, Math.min(2.0, value)).
     */
    public double evaluateForBot(BotView bot) {
        return 1.0;
    }

    public final NamespacedKey getKey() { return key; }
    public final Plugin getOwnerPlugin() { return ownerPlugin; }
    public final CardRarity getRarity() { return rarity; }
    public final Material getMaterial() { return material; }
    public final CardRole getRole() { return role; }
    public final int getCustomModelData() { return customModelData; }
    public final void setCustomModelData(int customModelData) { this.customModelData = customModelData; }
}

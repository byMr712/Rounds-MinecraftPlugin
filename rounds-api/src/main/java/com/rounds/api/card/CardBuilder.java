package com.rounds.api.card;

import com.rounds.api.RoundsAPI;
import com.rounds.api.bot.BotView;
import com.rounds.api.event.RoundsBulletHitEvent;
import com.rounds.api.event.RoundsShieldBlockEvent;
import com.rounds.api.event.RoundsWeaponShootEvent;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public final class CardBuilder {

    private final Plugin plugin;
    private final String id;
    private CardRarity rarity = CardRarity.COMMON;
    private Material material = Material.PAPER;
    private CardRole role = CardRole.OFFENSIVE;
    private int customModelData = 0;

    private final Map<String, String> names = new HashMap<>();
    private final Map<String, String> descriptions = new HashMap<>();
    private Function<String, String> nameSupplier;
    private Function<String, String> descriptionSupplier;

    private Consumer<CardContext> onApplyAction;
    private Consumer<CardContext> onRemoveAction;
    private Consumer<CardContext> onRoundStartAction;
    private BiConsumer<CardContext, Boolean> onRoundEndAction;
    private Consumer<RoundsWeaponShootEvent> onShootAction;
    private Consumer<RoundsBulletHitEvent> onBulletHitAction;
    private Consumer<RoundsShieldBlockEvent> onShieldBlockAction;
    private Function<BotView, Double> botEvaluator;

    private CardBuilder(Plugin plugin, String id) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.id = Objects.requireNonNull(id, "Card ID cannot be null");
    }

    public static CardBuilder create(Plugin plugin, String id) {
        return new CardBuilder(plugin, id);
    }

    public CardBuilder rarity(CardRarity rarity) {
        if (rarity != null) this.rarity = rarity;
        return this;
    }

    public CardBuilder material(Material material) {
        if (material != null) this.material = material;
        return this;
    }

    public CardBuilder role(CardRole role) {
        if (role != null) this.role = role;
        return this;
    }

    public CardBuilder customModelData(int customModelData) {
        this.customModelData = customModelData;
        return this;
    }

    public CardBuilder name(String langCode, String name) {
        if (langCode != null && name != null) {
            this.names.put(langCode.toLowerCase(), name);
        }
        return this;
    }

    public CardBuilder name(String defaultName) {
        if (defaultName != null) {
            this.names.put("en", defaultName);
            this.names.put("ru", defaultName);
        }
        return this;
    }

    public CardBuilder name(Function<String, String> nameSupplier) {
        this.nameSupplier = nameSupplier;
        return this;
    }

    public CardBuilder description(String langCode, String description) {
        if (langCode != null && description != null) {
            this.descriptions.put(langCode.toLowerCase(), description);
        }
        return this;
    }

    public CardBuilder description(String defaultDescription) {
        if (defaultDescription != null) {
            this.descriptions.put("en", defaultDescription);
            this.descriptions.put("ru", defaultDescription);
        }
        return this;
    }

    public CardBuilder description(Function<String, String> descriptionSupplier) {
        this.descriptionSupplier = descriptionSupplier;
        return this;
    }

    public CardBuilder onApply(Consumer<CardContext> action) {
        this.onApplyAction = action;
        return this;
    }

    public CardBuilder onRemove(Consumer<CardContext> action) {
        this.onRemoveAction = action;
        return this;
    }

    public CardBuilder onRoundStart(Consumer<CardContext> action) {
        this.onRoundStartAction = action;
        return this;
    }

    public CardBuilder onRoundEnd(BiConsumer<CardContext, Boolean> action) {
        this.onRoundEndAction = action;
        return this;
    }

    public CardBuilder onShoot(Consumer<RoundsWeaponShootEvent> action) {
        this.onShootAction = action;
        return this;
    }

    public CardBuilder onBulletHit(Consumer<RoundsBulletHitEvent> action) {
        this.onBulletHitAction = action;
        return this;
    }

    public CardBuilder onShieldBlock(Consumer<RoundsShieldBlockEvent> action) {
        this.onShieldBlockAction = action;
        return this;
    }

    public CardBuilder evaluateForBot(Function<BotView, Double> evaluator) {
        this.botEvaluator = evaluator;
        return this;
    }

    public CustomCard build() {
        final Map<String, String> finalNames = new HashMap<>(this.names);
        final Map<String, String> finalDescs = new HashMap<>(this.descriptions);
        final Function<String, String> fnName = this.nameSupplier;
        final Function<String, String> fnDesc = this.descriptionSupplier;
        final Consumer<CardContext> applyAct = this.onApplyAction;
        final Consumer<CardContext> removeAct = this.onRemoveAction;
        final Consumer<CardContext> rStartAct = this.onRoundStartAction;
        final BiConsumer<CardContext, Boolean> rEndAct = this.onRoundEndAction;
        final Consumer<RoundsWeaponShootEvent> shootAct = this.onShootAction;
        final Consumer<RoundsBulletHitEvent> hitAct = this.onBulletHitAction;
        final Consumer<RoundsShieldBlockEvent> blockAct = this.onShieldBlockAction;
        final Function<BotView, Double> evalAct = this.botEvaluator;

        CustomCard card = new CustomCard(plugin, id, rarity, material, role) {
            @Override
            public String getName(String langCode) {
                if (fnName != null) {
                    String res = fnName.apply(langCode);
                    if (res != null) return res;
                }
                String code = langCode != null ? langCode.toLowerCase() : "en";
                String val = finalNames.get(code);
                if (val != null) return val;
                val = finalNames.get("en");
                return val != null ? val : getKey().getKey();
            }

            @Override
            public String getDescription(String langCode) {
                if (fnDesc != null) {
                    String res = fnDesc.apply(langCode);
                    if (res != null) return res;
                }
                String code = langCode != null ? langCode.toLowerCase() : "en";
                String val = finalDescs.get(code);
                if (val != null) return val;
                val = finalDescs.get("en");
                return val != null ? val : "";
            }

            @Override
            public void onApply(CardContext context) {
                if (applyAct != null) applyAct.accept(context);
            }

            @Override
            public void onRemove(CardContext context) {
                if (removeAct != null) removeAct.accept(context);
            }

            @Override
            public void onRoundStart(CardContext context) {
                if (rStartAct != null) rStartAct.accept(context);
            }

            @Override
            public void onRoundEnd(CardContext context, boolean teamWon) {
                if (rEndAct != null) rEndAct.accept(context, teamWon);
            }

            @Override
            public void onShoot(RoundsWeaponShootEvent event) {
                if (shootAct != null) shootAct.accept(event);
            }

            @Override
            public void onBulletHit(RoundsBulletHitEvent event) {
                if (hitAct != null) hitAct.accept(event);
            }

            @Override
            public void onShieldBlock(RoundsShieldBlockEvent event) {
                if (blockAct != null) blockAct.accept(event);
            }

            @Override
            public double evaluateForBot(BotView bot) {
                if (evalAct != null) {
                    Double score = evalAct.apply(bot);
                    if (score != null) return score;
                }
                return super.evaluateForBot(bot);
            }
        };

        card.setCustomModelData(customModelData);
        return card;
    }

    public CustomCard register() {
        CustomCard card = build();
        RoundsAPI.registerCard(card);
        return card;
    }
}

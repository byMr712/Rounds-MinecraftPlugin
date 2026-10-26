package com.rounds.api.impl;

import com.rounds.RoundsPlugin;
import com.rounds.api.ApiVersion;
import com.rounds.api.RoundsService;
import com.rounds.api.card.CustomCard;
import com.rounds.api.data.CustomDataContainer;
import com.rounds.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RoundsServiceImpl implements RoundsService {

    private final RoundsPlugin plugin;
    private final ApiVersion version = new ApiVersion(1, 0, 0);
    private final IdMappingRegistry idMappingRegistry;
    private final Map<NamespacedKey, CustomCard> registeredCardsByKey = new ConcurrentHashMap<>();
    private final List<CustomCard> registeredCardsOrder = Collections.synchronizedList(new ArrayList<>());

    public RoundsServiceImpl(RoundsPlugin plugin) {
        this.plugin = plugin;
        File registryFile = new File(plugin.getDataFolder(), "cards/id_registry.yml");
        this.idMappingRegistry = new IdMappingRegistry(registryFile, plugin.getLogger());
    }

    public IdMappingRegistry getIdMappingRegistry() {
        return idMappingRegistry;
    }

    @Override
    public ApiVersion getVersion() {
        return version;
    }

    @Override
    public synchronized void registerCard(CustomCard card) {
        Objects.requireNonNull(card, "card cannot be null");
        Plugin owner = card.getOwnerPlugin();
        if (owner == null || !owner.isEnabled()) {
            throw new IllegalStateException("Owner plugin is disabled or null for card " + card.getKey());
        }
        if (registeredCardsByKey.containsKey(card.getKey())) {
            throw new IllegalStateException("Card with key " + card.getKey() + " is already registered");
        }

        int id = idMappingRegistry.getOrAssignId(card.getKey());
        CustomCardAdapter adapter = new CustomCardAdapter(id, card, plugin, null);

        if (plugin.getCardManager() != null && plugin.getCardManager().getRegistry() != null) {
            plugin.getCardManager().getRegistry().registerCustomCard(adapter);
        }

        registeredCardsByKey.put(card.getKey(), card);
        registeredCardsOrder.remove(card);
        registeredCardsOrder.add(card);
    }

    @Override
    public synchronized void unregisterCard(Plugin plugin, NamespacedKey key) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        CustomCard card = registeredCardsByKey.get(key);
        if (card == null) return;

        if (!card.getOwnerPlugin().equals(plugin)) {
            throw new SecurityException("Plugin " + plugin.getName() + " is not the owner of card " + key);
        }

        registeredCardsByKey.remove(key);
        registeredCardsOrder.remove(card);
        idMappingRegistry.retire(key);

        OptionalInt optId = idMappingRegistry.getId(key);
        int cardId = optId.isPresent() ? optId.getAsInt() : -1;

        if (this.plugin.getCardManager() != null && this.plugin.getCardManager().getRegistry() != null) {
            this.plugin.getCardManager().getRegistry().unregisterCustomCard(cardId, key.getKey());
        }

        if (cardId > 0 && this.plugin.getPlayerDataManager() != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerData data = this.plugin.getPlayerDataManager().getData(p);
                if (data != null && data.getOwnedCards().contains(cardId)) {
                    data.removeOneCard(cardId);
                    var dataContainer = this.plugin.getCustomDataManager().getOrCreate(p.getUniqueId());
                    card.onRemove(new CardContextImpl(this.plugin, p, dataContainer));
                    this.plugin.getCardManager().recalculatePlayerStats(p);
                }
            }
        }
    }

    @Override
    public synchronized void unregisterAllCards(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        List<NamespacedKey> toRemove = new ArrayList<>();
        for (CustomCard card : registeredCardsByKey.values()) {
            if (card.getOwnerPlugin().equals(plugin)) {
                toRemove.add(card.getKey());
            }
        }
        for (NamespacedKey k : toRemove) {
            unregisterCard(plugin, k);
        }
    }

    @Override
    public Optional<CustomCard> getCard(NamespacedKey key) {
        if (key == null) return Optional.empty();
        CustomCard card = registeredCardsByKey.get(key);
        if (card != null) {
            return Optional.of(card);
        }
        if (key.getNamespace().equalsIgnoreCase("rounds") && plugin.getCardManager() != null) {
            com.rounds.cards.Card c = plugin.getCardManager().getRegistry().findCardByName(key.getKey());
            if (c != null) {
                String slug = plugin.getCardManager().getRegistry().getCardSlug(c);
                return Optional.of(new BuiltinCardWrapper(plugin, c, slug));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<CustomCard> getRegisteredCards() {
        synchronized (registeredCardsOrder) {
            return Collections.unmodifiableList(new ArrayList<>(registeredCardsOrder));
        }
    }

    @Override
    public Optional<CustomDataContainer> getCustomData(Player player) {
        if (plugin.getCustomDataManager() == null) return Optional.empty();
        return plugin.getCustomDataManager().get(player).map(c -> (CustomDataContainer) c);
    }

    @Override
    public Optional<CustomDataContainer> getCustomData(UUID playerUuid) {
        if (plugin.getCustomDataManager() == null) return Optional.empty();
        return plugin.getCustomDataManager().get(playerUuid).map(c -> (CustomDataContainer) c);
    }

    @Override
    public int getCurrentRound() {
        if (plugin.getGameManager() == null || !plugin.getGameManager().isGameStarted()) {
            return 0;
        }
        return (int) plugin.getGameManager().getCurrentRound();
    }

    @Override
    public boolean isMatchRunning() {
        return plugin.getGameManager() != null && plugin.getGameManager().isGameStarted();
    }

    @Override
    public boolean runSync(Runnable task) {
        Objects.requireNonNull(task, "Task cannot be null");
        if (!plugin.isEnabled()) return false;
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return true;
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
            return true;
        }
    }
}

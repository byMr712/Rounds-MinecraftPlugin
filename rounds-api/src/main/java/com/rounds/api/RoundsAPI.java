package com.rounds.api;

import com.rounds.api.card.CustomCard;
import com.rounds.api.data.CustomDataContainer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RoundsAPI {
    private RoundsAPI() {}

    public static RoundsService getService() {
        RegisteredServiceProvider<RoundsService> rsp = 
            Bukkit.getServicesManager().getRegistration(RoundsService.class);
        if (rsp == null) {
            throw new IllegalStateException("RoundsService не зарегистрирован! Убедитесь, что Rounds включен.");
        }
        return rsp.getProvider();
    }

    public static boolean isAvailable() {
        return Bukkit.getServicesManager().getRegistration(RoundsService.class) != null;
    }

    public static ApiVersion getVersion() {
        return getService().getVersion();
    }

    public static void registerCard(CustomCard card) {
        getService().registerCard(card);
    }

    public static void unregisterCard(Plugin plugin, NamespacedKey key) {
        getService().unregisterCard(plugin, key);
    }

    public static void unregisterAllCards(Plugin plugin) {
        getService().unregisterAllCards(plugin);
    }

    public static Optional<CustomCard> getCard(NamespacedKey key) {
        return getService().getCard(key);
    }

    public static List<CustomCard> getRegisteredCards() {
        return getService().getRegisteredCards();
    }

    public static Optional<CustomDataContainer> getCustomData(Player player) {
        return getService().getCustomData(player);
    }

    public static Optional<CustomDataContainer> getCustomData(UUID playerUuid) {
        return getService().getCustomData(playerUuid);
    }

    public static int getCurrentRound() {
        return getService().getCurrentRound();
    }

    public static boolean isMatchRunning() {
        return getService().isMatchRunning();
    }

    public static boolean runSync(Runnable task) {
        Objects.requireNonNull(task, "Task не может быть null");
        return getService().runSync(task);
    }
}

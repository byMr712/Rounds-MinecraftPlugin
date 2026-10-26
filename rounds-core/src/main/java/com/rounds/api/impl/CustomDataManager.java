package com.rounds.api.impl;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomDataManager {

    private final ConcurrentHashMap<UUID, CustomDataContainerImpl> containers = new ConcurrentHashMap<>();

    public CustomDataContainerImpl getOrCreate(UUID uuid) {
        if (uuid == null) return null;
        return containers.computeIfAbsent(uuid, u -> new CustomDataContainerImpl());
    }

    public Optional<CustomDataContainerImpl> get(Player player) {
        if (player == null || !player.isOnline()) return Optional.empty();
        CustomDataContainerImpl container = containers.get(player.getUniqueId());
        return Optional.ofNullable(container);
    }

    public Optional<CustomDataContainerImpl> get(UUID uuid) {
        if (uuid == null) return Optional.empty();
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) return Optional.empty();
        return Optional.ofNullable(containers.get(uuid));
    }

    public void onPlayerQuit(UUID uuid, boolean isMatchRunning) {
        if (!isMatchRunning) {
            containers.remove(uuid);
        }
        // If match is running, container is kept in memory for reconnect progress
    }

    public void clearAll() {
        for (CustomDataContainerImpl c : containers.values()) {
            c.clear();
        }
        containers.clear();
    }

    public void remove(UUID uuid) {
        if (uuid != null) {
            CustomDataContainerImpl c = containers.remove(uuid);
            if (c != null) c.clear();
        }
    }
}

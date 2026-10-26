package com.rounds.stats;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MatchStatsManager {

    public static class PlayerMatchStats {
        public final UUID uuid;
        public String playerName;
        public int cardsCount;
        public int kills;
        public int deaths;
        public int shots;
        public double damageDealt;
        public double damageReceived;
        public double maxShotDamage;

        public PlayerMatchStats(UUID uuid, String playerName) {
            this.uuid = uuid;
            this.playerName = playerName != null ? playerName : uuid.toString();
        }
    }

    private static final Map<UUID, PlayerMatchStats> activeStats = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerMatchStats> lastMatchStats = new ConcurrentHashMap<>();
    private static final Map<String, UUID> nameToUuid = new ConcurrentHashMap<>();

    public static void registerParticipant(UUID uuid, String name) {
        if (uuid == null) return;
        if (name != null) {
            nameToUuid.put(name.toLowerCase(Locale.ROOT), uuid);
        }
        activeStats.computeIfAbsent(uuid, u -> new PlayerMatchStats(u, name));
    }

    public static void recordShot(UUID uuid) {
        if (uuid == null) return;
        PlayerMatchStats s = activeStats.get(uuid);
        if (s != null) s.shots++;
    }

    public static void recordKill(UUID uuid) {
        if (uuid == null) return;
        PlayerMatchStats s = activeStats.get(uuid);
        if (s != null) s.kills++;
    }

    public static void recordDeath(UUID uuid) {
        if (uuid == null) return;
        PlayerMatchStats s = activeStats.get(uuid);
        if (s != null) s.deaths++;
    }

    public static void recordDamageDealt(UUID attackerUuid, UUID victimUuid, double damage) {
        if (attackerUuid != null && damage > 0) {
            PlayerMatchStats s = activeStats.get(attackerUuid);
            if (s != null) {
                s.damageDealt += damage;
                if (damage > s.maxShotDamage) {
                    s.maxShotDamage = damage;
                }
            }
        }
    }

    public static void recordDamageReceived(UUID victimUuid, double damage) {
        if (victimUuid != null && damage > 0) {
            PlayerMatchStats s = activeStats.get(victimUuid);
            if (s != null) {
                s.damageReceived += damage;
            }
        }
    }

    public static void setCardsCount(UUID uuid, int cardsCount) {
        if (uuid == null) return;
        PlayerMatchStats s = activeStats.get(uuid);
        if (s != null) s.cardsCount = cardsCount;
    }

    public static void onGameStart() {
        activeStats.clear();
    }

    public static void onGameEnd(com.rounds.player.PlayerDataManager pdm) {
        lastMatchStats.clear();
        for (Map.Entry<UUID, PlayerMatchStats> entry : activeStats.entrySet()) {
            PlayerMatchStats s = entry.getValue();
            PlayerMatchStats copy = new PlayerMatchStats(s.uuid, s.playerName);
            if (pdm != null) {
                com.rounds.player.PlayerData data = pdm.getData(s.uuid);
                copy.cardsCount = (data != null && data.getOwnedCards() != null) ? data.getOwnedCards().size() : s.cardsCount;
            } else {
                copy.cardsCount = s.cardsCount;
            }
            copy.kills = s.kills;
            copy.deaths = s.deaths;
            copy.shots = s.shots;
            copy.damageDealt = s.damageDealt;
            copy.damageReceived = s.damageReceived;
            copy.maxShotDamage = s.maxShotDamage;
            lastMatchStats.put(entry.getKey(), copy);
        }
        activeStats.clear();
    }

    public static PlayerMatchStats getLastMatchStats(UUID uuid) {
        if (uuid == null) return null;
        return lastMatchStats.get(uuid);
    }

    public static PlayerMatchStats getLastMatchStatsByName(String name) {
        if (name == null || name.isBlank()) return null;
        for (PlayerMatchStats s : lastMatchStats.values()) {
            if (s.playerName.equalsIgnoreCase(name.trim())) {
                return s;
            }
        }
        UUID u = nameToUuid.get(name.trim().toLowerCase(Locale.ROOT));
        if (u != null) return lastMatchStats.get(u);
        return null;
    }

    public static Collection<PlayerMatchStats> getAllLastMatchStats() {
        return Collections.unmodifiableCollection(lastMatchStats.values());
    }

    public static boolean hasFinishedGameData() {
        return !lastMatchStats.isEmpty();
    }
}

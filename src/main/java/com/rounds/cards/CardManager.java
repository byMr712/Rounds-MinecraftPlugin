package com.rounds.cards;

import com.rounds.RoundsPlugin;
import com.rounds.player.PlayerData;
import com.rounds.teams.TeamManager.GameTeam;
import com.rounds.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CardManager {

    private final RoundsPlugin plugin;
    private final CardRegistry registry;
    private final Map<UUID, List<Card>> pendingCards = new HashMap<>();
    private final Set<UUID> pendingPicks = new HashSet<>();
    private final Map<UUID, BukkitTask> choiceTimers = new HashMap<>();

    public CardManager(RoundsPlugin plugin) {
        this.plugin = plugin;
        this.registry = new CardRegistry(plugin);
        registry.loadCards();
    }

    public void openCardSelection(Player player, GameTeam team) {
        List<Card> cards = registry.getRandomCards(5);
        pendingCards.put(player.getUniqueId(), cards);
        pendingPicks.add(player.getUniqueId());
        plugin.getCardGUI().open(player, cards);
        startChoiceTimer(player);

        List<Integer> cardIds = new java.util.ArrayList<>();
        for (Card c : cards) cardIds.add(c.getId());
        plugin.getPlayerDataManager().savePlayerFullData(player.getUniqueId(), team, cardIds);
    }

    public void selectCard(Player player, int slotIndex) {
        cancelChoiceTimer(player.getUniqueId());
        List<Card> cards = pendingCards.remove(player.getUniqueId());
        if (cards == null || slotIndex < 0 || slotIndex >= cards.size()) return;

        Card card = cards.get(slotIndex);
        PlayerData data = plugin.getPlayerDataManager().getData(player);
        card.apply(player, data);
        data.setCard(card.getId(), true);
        syncPlayerHP(player, data);
        pendingPicks.remove(player.getUniqueId());

        GameTeam team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        plugin.getPlayerDataManager().savePlayerFullData(player.getUniqueId(), team, null);

        boolean revealed = grantChainedCards(player, data, card);

        if (!revealed) {
            String lang = Messages.getLanguageCode();
            String msg = Messages.get("card.picked", player.getName(), card.getColoredName(lang), card.getDescription(lang));
            plugin.getServer().broadcastMessage(msg);
        }

        if (allPicksDone()) {
            plugin.getGameManager().onAllCardsPicked();
        } else {
            // Player has chosen, but other players/bots are still selecting: stay in Adventure mode at spawn
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setInvulnerable(true);
            player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            plugin.getGameManager().sendEnemyPickingCardsTitle(player, 100);
        }
    }

    public boolean applyCardToPlayer(Player player, Card card) {
        if (player == null || card == null) return false;
        PlayerData data = plugin.getPlayerDataManager().getData(player);
        card.apply(player, data);
        data.setCard(card.getId(), true);
        syncPlayerHP(player, data);
        GameTeam team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        plugin.getPlayerDataManager().savePlayerFullData(player.getUniqueId(), team, null);
        grantChainedCards(player, data, card);
        return true;
    }

    public boolean removeCardFromPlayer(Player player, int cardIndex) {
        if (player == null) return false;
        PlayerData data = plugin.getPlayerDataManager().getData(player);
        if (!data.removeCardAtIndex(cardIndex)) return false;

        recalculatePlayerStats(player);
        return true;
    }

    public void recalculatePlayerStats(Player player) {
        if (player == null) return;
        PlayerData data = plugin.getPlayerDataManager().getData(player);
        data.resetStats();

        // Re-apply remaining cards
        for (int cardId : data.getOwnedCards()) {
            Card c = registry.getCard(cardId);
            if (c != null) {
                c.apply(player, data, false);
            }
        }

        syncPlayerHP(player, data);
        GameTeam team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
        plugin.getPlayerDataManager().savePlayerFullData(player.getUniqueId(), team, null);
    }

    private boolean grantChainedCards(Player player, PlayerData data, Card source) {
        boolean revealedAny = false;
        for (Map.Entry<String, Double> entry : source.getEffects().entrySet()) {
            String key = entry.getKey().replace('_', '-');
            int count = (int) Math.round(entry.getValue());
            if (count <= 0) continue;

            List<Card> granted;
            if (key.equals("card-bonus")) {
                granted = registry.getRandomCardsByRarity(CardRegistry.Rarity.COMMON, count, null);
            } else if (key.equals("random-legendary")) {
                granted = registry.getRandomCardsByRarity(CardRegistry.Rarity.LEGENDARY, count, source.getId());
            } else {
                continue;
            }

            String lang = Messages.getLanguageCode();
            for (Card bonus : granted) {
                bonus.apply(player, data);
                data.setCard(bonus.getId(), true);
                syncPlayerHP(player, data);
                String msg = Messages.get("card.picked", player.getName(), bonus.getColoredName(lang), bonus.getDescription(lang));
                plugin.getServer().broadcastMessage(msg);
                revealedAny = true;
            }
        }
        return revealedAny;
    }

    public void resetAllCards() {
        cancelAllChoiceTimers();
        pendingPicks.clear();
        pendingCards.clear();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerDataManager().getData(p);
            data.resetAllCards();
            data.resetStats();
            plugin.getPlayerDataManager().clearPDC(p);
            plugin.getPlayerDataManager().removePlayerData(p.getUniqueId());
        }
    }

    public boolean allPicksDone() {
        return pendingPicks.isEmpty();
    }

    public boolean isPendingPick(UUID uuid) {
        return pendingPicks.contains(uuid);
    }

    public List<Card> getPendingCards(UUID uuid) {
        return pendingCards.get(uuid);
    }

    public List<Integer> getPendingCardIds(UUID uuid) {
        List<Card> cards = pendingCards.get(uuid);
        if (cards == null) return java.util.Collections.emptyList();
        List<Integer> ids = new java.util.ArrayList<>();
        for (Card c : cards) ids.add(c.getId());
        return ids;
    }

    public void restorePendingPick(UUID uuid, List<Integer> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) return;
        List<Card> cards = new java.util.ArrayList<>();
        for (int id : cardIds) {
            Card card = registry.getCard(id);
            if (card != null) cards.add(card);
        }
        if (cards.isEmpty()) return;
        pendingCards.put(uuid, cards);
        pendingPicks.add(uuid);
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline()) {
            plugin.getCardGUI().open(player, cards);
            startChoiceTimer(player);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    public void clearPendingPicks() {
        cancelAllChoiceTimers();
        pendingPicks.clear();
        pendingCards.clear();
    }

    public void removePendingPick(UUID uuid) {
        cancelChoiceTimer(uuid);
        pendingPicks.remove(uuid);
        pendingCards.remove(uuid);
    }

    public void startChoiceTimer(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        cancelChoiceTimer(uuid);
        if (plugin.getBotManager() != null && plugin.getBotManager().isBot(player)) {
            return;
        }

        int limitSec = plugin.getRoundsConfig().getCardChoiceTimeSeconds();
        if (limitSec < 10) limitSec = 30;
        final int totalSec = limitSec;

        BukkitTask task = new BukkitRunnable() {
            int remaining = totalSec;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline() || !isPendingPick(uuid)) {
                    cancel();
                    choiceTimers.remove(uuid);
                    return;
                }

                remaining--;

                if (remaining <= 5 && remaining >= 2) {
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                } else if (remaining == 1) {
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
                } else if (remaining <= 0) {
                    cancel();
                    choiceTimers.remove(uuid);
                    removePendingPick(uuid);
                    p.kickPlayer(Messages.get("card.timeout-kick", totalSec));
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        choiceTimers.put(uuid, task);
    }

    public void cancelChoiceTimer(UUID uuid) {
        BukkitTask task = choiceTimers.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void cancelAllChoiceTimers() {
        for (BukkitTask task : choiceTimers.values()) {
            if (task != null) task.cancel();
        }
        choiceTimers.clear();
    }

    public void replacePendingCards(UUID uuid, List<Card> newCards) {
        pendingCards.put(uuid, newCards);
    }

    public CardRegistry getRegistry() {
        return registry;
    }

    private void syncPlayerHP(Player player, PlayerData data) {
        double maxHP = data.getMaxHealth();
        var attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(maxHP);
            player.setHealth(Math.min(player.getHealth(), attr.getValue()));
        }
    }

    public void reload() {
        registry.reload();
    }
}

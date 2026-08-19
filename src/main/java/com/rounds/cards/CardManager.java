package com.rounds.cards;

import com.rounds.RoundsPlugin;
import com.rounds.player.PlayerData;
import com.rounds.teams.TeamManager.GameTeam;
import com.rounds.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

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

        List<Integer> cardIds = new java.util.ArrayList<>();
        for (Card c : cards) cardIds.add(c.getId());
        plugin.getPlayerDataManager().savePlayerFullData(player.getUniqueId(), team, cardIds);
    }

    public void selectCard(Player player, int slotIndex) {
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

        String lang = Messages.getLanguage();
        String msg = Messages.get("card.picked", player.getName(), card.getColoredName(lang), card.getDescription(lang));
        plugin.getServer().broadcastMessage(msg);

        if (allPicksDone()) {
            plugin.getGameManager().onAllCardsPicked();
        }
    }

    public void resetAllCards() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            pendingCards.remove(p.getUniqueId());
            PlayerData data = plugin.getPlayerDataManager().getData(p);
            data.resetAllCards();
            data.resetStats();
        }
        pendingPicks.clear();
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
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    public void clearPendingPicks() {
        pendingPicks.clear();
    }

    public void removePendingPick(UUID uuid) {
        pendingPicks.remove(uuid);
        pendingCards.remove(uuid);
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
        }
        player.setHealth(maxHP);
    }

    public void reload() {
        registry.reload();
    }
}

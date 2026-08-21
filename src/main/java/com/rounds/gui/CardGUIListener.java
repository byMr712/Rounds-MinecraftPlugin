package com.rounds.gui;

import com.rounds.RoundsPlugin;
import com.rounds.cards.Card;
import com.rounds.cards.CardManager;
import com.rounds.game.GameManager;
import com.rounds.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CardGUIListener implements Listener {

    private final RoundsPlugin plugin;
    private final Map<UUID, List<Card>> openGUIs = new ConcurrentHashMap<>();

    private static final int SIZE = 27;
    private static final int[] CARD_SLOTS = {11, 12, 13, 14, 15};

    public CardGUIListener(RoundsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, List<Card> cards) {
        String title = "\u00A78[\u00A76Rounds\u00A78] " + Messages.get("card.gui-title");
        Inventory inv = Bukkit.createInventory(new CardGUIHolder(), SIZE, title);

        for (int i = 0; i < cards.size() && i < CARD_SLOTS.length; i++) {
            inv.setItem(CARD_SLOTS[i], cards.get(i).createItemStack());
        }

        openGUIs.put(player.getUniqueId(), cards);
        player.openInventory(inv);
    }

    public void openShow(Player viewer, Player target) {
        String title = "\u00A78[\u00A76Rounds\u00A78] " + Messages.get("card.show-title", target.getName());
        Inventory inv = Bukkit.createInventory(new CardsShowHolder(), 54, title);

        List<Card> owned = new ArrayList<>();
        for (int id : plugin.getPlayerDataManager().getData(target).getOwnedCards()) {
            Card card = plugin.getCardManager().getRegistry().getCard(id);
            if (card != null) owned.add(card);
        }
        owned.sort(Comparator.comparingInt(Card::getFamilyId).thenComparingInt(Card::getId));

        if (owned.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.setDisplayName(ChatColor.RED + Messages.get("card.show-empty"));
            empty.setItemMeta(meta);
            inv.setItem(13, empty);
        } else {
            String lang = Messages.getLanguageCode();
            for (int i = 0; i < owned.size() && i < 54; i++) {
                inv.setItem(i, owned.get(i).createItemStack(lang, false));
            }
        }

        viewer.openInventory(inv);
    }

    public void rotateAllCards() {
        for (Map.Entry<UUID, List<Card>> entry : openGUIs.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof CardGUIHolder)) continue;

            List<Card> newCards = plugin.getCardManager().getRegistry().getRandomCards(CARD_SLOTS.length);
            entry.setValue(newCards);
            plugin.getCardManager().replacePendingCards(entry.getKey(), newCards);

            Inventory inv = player.getOpenInventory().getTopInventory();
            for (int i = 0; i < newCards.size() && i < CARD_SLOTS.length; i++) {
                inv.setItem(CARD_SLOTS[i], newCards.get(i).createItemStack());
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        boolean isShowGui = event.getInventory().getHolder() instanceof CardsShowHolder;
        if (!isShowGui && !(event.getInventory().getHolder() instanceof CardGUIHolder)) return;
        event.setCancelled(true);
        if (isShowGui) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= SIZE) return;

        boolean isCardSlot = false;
        int cardIndex = -1;
        for (int i = 0; i < CARD_SLOTS.length; i++) {
            if (rawSlot == CARD_SLOTS[i]) {
                isCardSlot = true;
                cardIndex = i;
                break;
            }
        }
        if (!isCardSlot) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE
                || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        List<Card> cards = openGUIs.get(player.getUniqueId());
        if (cards == null || cardIndex >= cards.size()) return;

        plugin.getCardManager().selectCard(player, cardIndex);
        openGUIs.remove(player.getUniqueId());
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof CardGUIHolder)
                && !(event.getInventory().getHolder() instanceof CardsShowHolder)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof CardGUIHolder)) return;

        openGUIs.remove(player.getUniqueId());

        CardManager cm = plugin.getCardManager();
        if (!cm.isPendingPick(player.getUniqueId())) return;

        GameManager gm = plugin.getGameManager();
        if (gm.getState() != GameManager.GameState.CARDS) return;

        List<Card> cards = cm.getPendingCards(player.getUniqueId());
        if (cards == null) return;

        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && cm.isPendingPick(uuid)) {
                open(p, cm.getPendingCards(uuid));
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        CardManager cm = plugin.getCardManager();
        if (!cm.isPendingPick(player.getUniqueId())) return;

        GameManager gm = plugin.getGameManager();
        if (gm.getState() != GameManager.GameState.CARDS) return;

        List<Card> cards = cm.getPendingCards(player.getUniqueId());
        if (cards == null) return;

        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && cm.isPendingPick(uuid)) {
                open(p, cm.getPendingCards(uuid));
            }
        }, 10L);
    }

    public static class CardGUIHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class CardsShowHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}

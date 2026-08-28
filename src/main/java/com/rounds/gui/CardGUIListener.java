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
        openShow(viewer, target, 1);
    }

    public void openShow(Player viewer, Player target, int page) {
        List<Card> owned = new ArrayList<>();
        // Chronological order: preserves the exact order cards were obtained (oldest first, newest last)
        for (int id : plugin.getPlayerDataManager().getData(target).getOwnedCards()) {
            Card card = plugin.getCardManager().getRegistry().getCard(id);
            if (card != null) owned.add(card);
        }

        int pageSize = 45;
        int maxPage = Math.max(1, (int) Math.ceil((double) owned.size() / pageSize));
        int curPage = Math.min(Math.max(1, page), maxPage);

        String title = "\u00A78[\u00A76Rounds\u00A78] " + Messages.get("card.show-title", target.getName());
        Inventory inv = Bukkit.createInventory(new CardsShowHolder(target.getUniqueId(), curPage, maxPage), 54, title);

        if (owned.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.setDisplayName(ChatColor.RED + Messages.get("card.show-empty"));
            empty.setItemMeta(meta);
            inv.setItem(22, empty);
        } else {
            String lang = Messages.getLanguageCode();
            int startIndex = (curPage - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, owned.size());
            for (int i = startIndex; i < endIndex; i++) {
                ItemStack item = owned.get(i).createItemStack(lang, false);
                if (viewer.hasPermission("rounds.admin")) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                        lore.add("");
                        lore.add(Messages.get("card.show-admin-hint"));
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    }
                }
                inv.setItem(i - startIndex, item);
            }
        }

        // Bottom control row (slots 45..53)
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);

        for (int slot = 45; slot < 54; slot++) {
            inv.setItem(slot, filler);
        }

        if (curPage > 1) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.setDisplayName(ChatColor.GREEN + Messages.get("card.show-prev", curPage - 1, maxPage));
            prev.setItemMeta(prevMeta);
            inv.setItem(45, prev);
        } else {
            ItemStack prevInactive = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta prevMeta = prevInactive.getItemMeta();
            prevMeta.setDisplayName(ChatColor.GRAY + Messages.get("card.show-no-prev"));
            prevInactive.setItemMeta(prevMeta);
            inv.setItem(45, prevInactive);
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + Messages.get("card.show-page", curPage, maxPage));
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.YELLOW + Messages.get("card.show-total-cards", owned.size()));
        if (viewer.hasPermission("rounds.admin")) {
            infoLore.add("");
            infoLore.add(ChatColor.GRAY + Messages.get("card.show-admin-hint"));
        }
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        if (curPage < maxPage) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.setDisplayName(ChatColor.GREEN + Messages.get("card.show-next", curPage + 1, maxPage));
            next.setItemMeta(nextMeta);
            inv.setItem(53, next);
        } else {
            ItemStack nextInactive = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta nextMeta = nextInactive.getItemMeta();
            nextMeta.setDisplayName(ChatColor.GRAY + Messages.get("card.show-no-next"));
            nextInactive.setItemMeta(nextMeta);
            inv.setItem(53, nextInactive);
        }

        viewer.openInventory(inv);
    }

    public void openCardActionMenu(Player admin, Player target, Card card, int cardIndex, int returnPage) {
        String lang = Messages.getLanguageCode();
        String title = "\u00A78[\u00A76Rounds\u00A78] " + Messages.get("card.action-menu-title");
        Inventory inv = Bukkit.createInventory(new CardActionHolder(target.getUniqueId(), card.getId(), cardIndex, returnPage), 27, title);

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Slot 13: Card info
        inv.setItem(13, card.createItemStack(lang, false));

        // Slot 11: Duplicate button
        ItemStack dupItem = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta dupMeta = dupItem.getItemMeta();
        dupMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + Messages.get("card.action-duplicate-title"));
        List<String> dupLore = new ArrayList<>();
        dupLore.add(ChatColor.GRAY + Messages.get("card.action-duplicate-desc", target.getName()));
        dupLore.add("");
        dupLore.add(ChatColor.YELLOW + Messages.get("card.action-click-duplicate"));
        dupMeta.setLore(dupLore);
        dupItem.setItemMeta(dupMeta);
        inv.setItem(11, dupItem);

        // Slot 15: Delete button
        ItemStack delItem = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta delMeta = delItem.getItemMeta();
        delMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + Messages.get("card.action-delete-title"));
        List<String> delLore = new ArrayList<>();
        delLore.add(ChatColor.GRAY + Messages.get("card.action-delete-desc", target.getName()));
        delLore.add("");
        delLore.add(ChatColor.RED + Messages.get("card.action-click-delete"));
        delMeta.setLore(delLore);
        delItem.setItemMeta(delMeta);
        inv.setItem(15, delItem);

        // Slot 22: Back button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + Messages.get("card.action-back"));
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);

        admin.openInventory(inv);
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

        if (event.getInventory().getHolder() instanceof CardsShowHolder showHolder) {
            event.setCancelled(true);
            int rawSlot = event.getRawSlot();
            Player target = Bukkit.getPlayer(showHolder.getTargetId());

            if (rawSlot == 45 && showHolder.getPage() > 1) {
                if (target != null) {
                    openShow(player, target, showHolder.getPage() - 1);
                    player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
                }
                return;
            } else if (rawSlot == 53 && showHolder.getPage() < showHolder.getMaxPage()) {
                if (target != null) {
                    openShow(player, target, showHolder.getPage() + 1);
                    player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
                }
                return;
            }

            // Check if admin clicked a card (slots 0..44)
            if (rawSlot >= 0 && rawSlot < 45 && player.hasPermission("rounds.admin")) {
                if (target == null) return;
                var data = plugin.getPlayerDataManager().getData(target);
                int cardIndex = (showHolder.getPage() - 1) * 45 + rawSlot;
                List<Integer> owned = data.getOwnedCards();
                if (cardIndex < 0 || cardIndex >= owned.size()) return;

                int cardId = owned.get(cardIndex);
                Card card = plugin.getCardManager().getRegistry().getCard(cardId);
                if (card != null) {
                    openCardActionMenu(player, target, card, cardIndex, showHolder.getPage());
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
                }
            }
            return;
        }

        if (event.getInventory().getHolder() instanceof CardActionHolder actionHolder) {
            event.setCancelled(true);
            int rawSlot = event.getRawSlot();
            Player target = Bukkit.getPlayer(actionHolder.getTargetId());
            Card card = plugin.getCardManager().getRegistry().getCard(actionHolder.getCardId());
            String lang = Messages.getLanguageCode();

            if (rawSlot == 11) { // Duplicate card
                if (target != null && card != null) {
                    plugin.getCardManager().applyCardToPlayer(target, card);
                    player.sendMessage(ChatColor.GREEN + Messages.get("card.action-duplicated-msg", card.getColoredName(lang), target.getName()));
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
                    openShow(player, target, actionHolder.getReturnPage());
                }
            } else if (rawSlot == 15) { // Delete card
                if (target != null && card != null) {
                    plugin.getCardManager().removeCardFromPlayer(target, actionHolder.getCardIndex());
                    player.sendMessage(ChatColor.RED + Messages.get("card.action-deleted-msg", card.getColoredName(lang), target.getName()));
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_BREAK, 0.8f, 1.0f);
                    openShow(player, target, actionHolder.getReturnPage());
                }
            } else if (rawSlot == 22) { // Back
                if (target != null) {
                    openShow(player, target, actionHolder.getReturnPage());
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
                }
            }
            return;
        }

        if (!(event.getInventory().getHolder() instanceof CardGUIHolder)) return;
        event.setCancelled(true);

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
        if (event.getInventory().getHolder() instanceof CardGUIHolder
                || event.getInventory().getHolder() instanceof CardsShowHolder
                || event.getInventory().getHolder() instanceof CardActionHolder) {
            event.setCancelled(true);
        }
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
        private final UUID targetId;
        private final int page;
        private final int maxPage;

        public CardsShowHolder(UUID targetId, int page, int maxPage) {
            this.targetId = targetId;
            this.page = page;
            this.maxPage = maxPage;
        }

        public UUID getTargetId() { return targetId; }
        public int getPage() { return page; }
        public int getMaxPage() { return maxPage; }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class CardActionHolder implements InventoryHolder {
        private final UUID targetId;
        private final int cardId;
        private final int cardIndex;
        private final int returnPage;

        public CardActionHolder(UUID targetId, int cardId, int cardIndex, int returnPage) {
            this.targetId = targetId;
            this.cardId = cardId;
            this.cardIndex = cardIndex;
            this.returnPage = returnPage;
        }

        public UUID getTargetId() { return targetId; }
        public int getCardId() { return cardId; }
        public int getCardIndex() { return cardIndex; }
        public int getReturnPage() { return returnPage; }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}

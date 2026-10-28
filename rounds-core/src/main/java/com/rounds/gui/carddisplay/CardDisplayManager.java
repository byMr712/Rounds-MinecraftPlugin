package com.rounds.gui.carddisplay;

import com.rounds.RoundsPlugin;
import com.rounds.cards.Card;
import com.rounds.cards.CardRegistry;
import com.rounds.util.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * World-space card draft UI using custom-font glyphs: five card images hang in the air in a
 * half-circle around the player (each card = a TextDisplay rendering a private-use codepoint
 * that the resource pack maps to the card texture). The player turns their head to look at
 * them, hover shows the card's own description on the action bar, left-click
 * (or hotkeys 1..5) picks. Rarity drives the glowing outline, EPIC/LEGENDARY cards are
 * wrapped in colored fire particles.
 */
public class CardDisplayManager implements Listener {

    private final RoundsPlugin plugin;
    private final Map<UUID, Scene> scenes = new ConcurrentHashMap<>();

    private static final int SLOT_COUNT = 5;
    private static final double RADIUS = 2.2;
    private static final double FAN_TOTAL_DEG = 160.0;
    private static final double CARD_CENTER_OFFSET = 1.2;
    private static final int DESC_WRAP = 30;
    // Card glyph is 88 font units tall (bitmap provider) rendered at ~0.025 blocks/unit,
    // source images are 160x250 (width = 0.64 * height). A small margin is added so the
    // edges of the card are comfortably selectable.
    private static final double CARD_HALF_WIDTH = 0.76;
    private static final double CARD_HALF_HEIGHT = 1.16;
    private static final int GLYPH_BASE = 0xE000;
    private static final Key CARD_FONT = Key.key("rounds", "default");

    public CardDisplayManager(RoundsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, List<Card> cards) {
        close(player);
        Scene scene = new Scene(player, cards);
        scenes.put(player.getUniqueId(), scene);
        scene.begin();
        hideOtherPlayers(player);
    }

    public void close(Player player) {
        if (player == null) return;
        Scene scene = scenes.remove(player.getUniqueId());
        if (scene != null) scene.dispose();
        showOtherPlayers(player);
    }

    public void closeAll() {
        for (UUID uuid : new ArrayList<>(scenes.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            Scene s = scenes.remove(uuid);
            if (s != null) s.dispose();
            if (p != null) showOtherPlayers(p);
        }
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid == null) return;
        Scene s = scenes.remove(uuid);
        if (s != null) s.dispose();
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) showOtherPlayers(p);
    }

    private void hideOtherPlayers(Player viewer) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(viewer.getUniqueId())) continue;
            viewer.hidePlayer(plugin, other);
        }
    }

    private void showOtherPlayers(Player viewer) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(viewer.getUniqueId())) continue;
            viewer.showPlayer(plugin, other);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onLeftClick(PlayerInteractEvent event) {
        Scene scene = scenes.get(event.getPlayer().getUniqueId());
        if (scene == null) return;
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        event.setCancelled(true);
        if (scene.hovered >= 0) {
            scene.select(scene.hovered);
        }
    }

    @EventHandler
    public void onHeldSlot(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Scene scene = scenes.get(player.getUniqueId());
        if (scene == null) return;
        int slot = event.getNewSlot();
        if (slot >= 0 && slot < scene.cards.size()) {
            event.setCancelled(true);
            player.getInventory().setHeldItemSlot(event.getPreviousSlot());
            scene.select(slot);
        }
    }

    private Color rarityColor(CardRegistry.Rarity rarity) {
        return switch (rarity) {
            case COMMON -> Color.WHITE;
            case UNCOMMON -> Color.fromRGB(85, 255, 85);
            case RARE -> Color.fromRGB(85, 255, 255);
            case EPIC -> Color.fromRGB(255, 85, 255);
            case LEGENDARY -> Color.fromRGB(255, 215, 0);
        };
    }

    private boolean hasFire(CardRegistry.Rarity rarity) {
        return rarity == CardRegistry.Rarity.EPIC || rarity == CardRegistry.Rarity.LEGENDARY;
    }

    private Component cardGlyph(Card card) {
        return Component.text(String.valueOf((char) (GLYPH_BASE + card.getFamilyId()))).font(CARD_FONT);
    }

    private final class Scene {
        final Player player;
        List<Card> cards;
        final UUID playerId;

        TextDisplay[] displays = new TextDisplay[SLOT_COUNT];
        Location[] targets = new Location[SLOT_COUNT];
        TextDisplay description;
        int hovered = -1;

        BukkitTask tickTask;
        BukkitTask carouselTask;

        Scene(Player player, List<Card> cards) {
            this.player = player;
            this.playerId = player.getUniqueId();
            this.cards = new ArrayList<>(cards);
        }

        void begin() {
            Location eye = player.getEyeLocation();
            float yaw = player.getLocation().getYaw();

            for (int i = 0; i < SLOT_COUNT && i < cards.size(); i++) {
                double deg = (i - (SLOT_COUNT - 1) / 2.0) * (FAN_TOTAL_DEG / (SLOT_COUNT - 1));
                Location loc = fanPosition(eye, yaw, deg, RADIUS);
                targets[i] = loc;
                spawnCard(i, loc);
            }

            description = player.getWorld().spawn(eye.clone(), TextDisplay.class);
            if (description != null) {
                description.setBillboard(Display.Billboard.VERTICAL);
                description.setSeeThrough(true);
                description.setBackgroundColor(Color.fromARGB(170, 8, 8, 14));
                // Wide enough for manually wrapped lines (~30 chars), so the display's own
                // auto-wrap (default 200px) never re-wraps them.
                description.setLineWidth(220);
                description.setAlignment(TextDisplay.TextAlignment.CENTER);
                description.setShadowed(true);
                // Description scale (user-tuned: 3x -> 1.2x -> ~1.0x of the default text size).
                description.setTransformation(new org.bukkit.util.Transformation(
                        new org.joml.Vector3f(0f, 0f, 0f),
                        new org.joml.AxisAngle4f(),
                        new org.joml.Vector3f(1.02f, 1.02f, 1.02f),
                        new org.joml.AxisAngle4f()));
                description.text(Component.empty());
            }

            tickTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || !scenes.containsKey(playerId)) { cancel(); return; }
                    updateHover();
                    spawnFireParticles();
                }
            }.runTaskTimer(plugin, 1L, 2L);

            carouselTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || !scenes.containsKey(playerId)) { cancel(); return; }
                    if (plugin.getGameManager() != null && plugin.getGameManager().isWheelEnabled()) {
                        rotateCarousel();
                    }
                }
            }.runTaskTimer(plugin, 140L, 140L); // 7 seconds
        }

        Location fanPosition(Location eye, float yaw, double deg, double radius) {
            double rad = Math.toRadians(yaw + deg);
            double x = eye.getX() - Math.sin(rad) * radius;
            double z = eye.getZ() + Math.cos(rad) * radius;
            float facing = (float) ((yaw + deg + 180.0) % 360.0);
            return new Location(eye.getWorld(), x, eye.getY() - CARD_CENTER_OFFSET, z, facing, 0f);
        }

        void spawnCard(int i, Location loc) {
            Card card = cards.get(i);
            TextDisplay display = player.getWorld().spawn(loc, TextDisplay.class);
            if (display != null) {
                display.text(cardGlyph(card));
                display.setBillboard(Display.Billboard.FIXED);
                display.setViewRange(0.6f);
                display.setGlowing(true);
                display.setGlowColorOverride(rarityColor(card.getRarity()));
                displays[i] = display;
            }
        }

        void updateHover() {
            Location eye = player.getEyeLocation();
            org.bukkit.util.Vector dir = eye.getDirection();
            int best = -1;
            double bestT = Double.MAX_VALUE;
            for (int i = 0; i < SLOT_COUNT && i < cards.size(); i++) {
                if (displays[i] == null) continue;
                // Ray-rectangle intersection against the card's visible plane, so the whole
                // card (including its edges) is selectable, not just its center.
                org.bukkit.util.Vector center = displays[i].getLocation().toVector()
                        .add(new org.bukkit.util.Vector(0, CARD_CENTER_OFFSET, 0));
                org.bukkit.util.Vector distVec = center.clone().subtract(eye.toVector());
                if (distVec.lengthSquared() < 0.01) continue;
                org.bukkit.util.Vector normal = distVec.clone().normalize(); // eye -> card center
                double denom = normal.dot(dir);
                if (Math.abs(denom) < 1e-6) continue; // card edge-on / behind
                double t = distVec.dot(normal) / denom;
                if (t < 0) continue;
                org.bukkit.util.Vector hit = eye.toVector().add(dir.clone().multiply(t));
                org.bukkit.util.Vector pc = hit.clone().subtract(center);
                // dot(pc, right) where right = (normal.z, 0, -normal.x)
                double x = pc.getX() * normal.getZ() - pc.getZ() * normal.getX();
                double y = pc.getY();
                if (Math.abs(x) <= CARD_HALF_WIDTH && Math.abs(y) <= CARD_HALF_HEIGHT) {
                    if (t < bestT) { bestT = t; best = i; }
                }
            }

            if (description != null) {
                if (best >= 0) {
                    if (best != hovered) {
                        Card c = cards.get(best);
                        String lang = Messages.getLanguageCode();
                        List<String> lines = Card.wrapText(c.getDescription(lang), DESC_WRAP);
                        // Newline inside the text component is a real line break for a
                        // TextDisplay (Component.join was removed in newer Adventure).
                        description.text(LegacyComponentSerializer.legacySection()
                                .deserialize(String.join("\n", lines)));
                    }
                    description.teleport(descriptionPos(eye, player.getLocation().getYaw()));
                } else {
                    if (hovered >= 0) description.text(Component.empty());
                }
            }
            hovered = best;
        }

        Location descriptionPos(Location eye, float yaw) {
            double rad = Math.toRadians(yaw);
            double x = eye.getX() - Math.sin(rad) * 1.8;
            double z = eye.getZ() + Math.cos(rad) * 1.8;
            // Ground is at eye - 1.62 for a standing player; keep the block above it.
            return new Location(eye.getWorld(), x, eye.getY() - 1.45, z);
        }

        void spawnFireParticles() {
            for (int i = 0; i < SLOT_COUNT && i < cards.size(); i++) {
                if (displays[i] == null) continue;
                if (!hasFire(cards.get(i).getRarity())) continue;
                Location c = displays[i].getLocation().clone().add(0, CARD_CENTER_OFFSET, 0);
                Color col = rarityColor(cards.get(i).getRarity());
                Particle.DustOptions dust = new Particle.DustOptions(col, 1.2f);
                for (int p = 0; p < 6; p++) {
                    double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                    double r = 0.55 + ThreadLocalRandom.current().nextDouble() * 0.3;
                    double y = -0.7 + ThreadLocalRandom.current().nextDouble() * 1.4;
                    c.getWorld().spawnParticle(Particle.REDSTONE,
                            c.getX() + Math.cos(a) * r, c.getY() + y, c.getZ() + Math.sin(a) * r,
                            1, 0, 0, 0, 0, dust);
                }
            }
        }

        void rotateCarousel() {
            if (cards.isEmpty()) return;
            List<Card> current = cards;
            List<Card> next = new ArrayList<>(current.subList(1, current.size()));
            Card fresh = plugin.getCardManager().getRegistry().getRandomCards(1, player).get(0);
            next.add(fresh);
            cards = next;
            plugin.getCardManager().replacePendingCards(playerId, cards);

            for (int i = 1; i < SLOT_COUNT && i < current.size(); i++) {
                if (displays[i] != null && targets[i - 1] != null) {
                    displays[i].setTeleportDuration(10);
                    displays[i].teleport(targets[i - 1]);
                }
            }
            fadeOut(displays[0]);
            for (int i = 0; i < SLOT_COUNT - 1; i++) {
                displays[i] = displays[i + 1];
            }
            displays[SLOT_COUNT - 1] = null;

            int last = SLOT_COUNT - 1;
            Location enter = targets[last].clone().add(1.6, 0, 0);
            spawnCard(last, enter);
            if (displays[last] != null && targets[last] != null) {
                displays[last].setTeleportDuration(10);
                displays[last].teleport(targets[last]);
                displays[last].setBrightness(new Display.Brightness(0, 0));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (displays[last] != null && displays[last].isValid()) {
                        displays[last].setBrightness(new Display.Brightness(15, 15));
                    }
                }, 1L);
            }
        }

        void fadeOut(TextDisplay display) {
            if (display == null) return;
            display.setBrightness(new Display.Brightness(0, 0));
            Bukkit.getScheduler().runTaskLater(plugin, display::remove, 2L);
        }

        void select(int index) {
            if (index < 0 || index >= cards.size()) return;
            plugin.getCardManager().selectCard(player, index);
        }

        void dispose() {
            if (tickTask != null) tickTask.cancel();
            if (carouselTask != null) carouselTask.cancel();
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (displays[i] != null) displays[i].remove();
            }
            if (description != null) description.remove();
            tickTask = null;
            carouselTask = null;
            displays = new TextDisplay[SLOT_COUNT];
        }
    }
}

package com.rounds.gui.carddisplay;

import com.rounds.RoundsPlugin;
import com.rounds.cards.Card;
import com.rounds.cards.CardRegistry;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * World-space card draft UI using custom-font glyphs: five card images hang in the air in a
 * half-circle around the player (each card = a TextDisplay rendering a private-use codepoint
 * that the resource pack maps to the card texture). The player turns their head to look at
 * them and left-clicks (or presses hotkeys 1..5) to pick; no hover text is shown. Rarity
 * drives the glowing outline, EPIC/LEGENDARY cards are wrapped in colored fire particles.
 */
public class CardDisplayManager implements Listener {

    private final RoundsPlugin plugin;
    private final Map<UUID, Scene> scenes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCardPickMs = new ConcurrentHashMap<>();

    private static final int SLOT_COUNT = 5;
    private static final double RADIUS = 2.2;
    private static final double FAN_TOTAL_DEG = 105.3;
    private static final float CARD_SCALE = 0.66f;
    private static final Transformation CARD_TRANSFORMATION = new Transformation(
            new Vector3f(0f, 0f, 0f),
            new AxisAngle4f(),
            new Vector3f(CARD_SCALE, CARD_SCALE, CARD_SCALE),
            new AxisAngle4f()
    );
    private static final double CARD_CENTER_OFFSET = 1.2 * CARD_SCALE;
    // Card glyph is 88 font units tall (bitmap provider) rendered at ~0.025 blocks/unit,
    // source images are 160x250 (width = 0.64 * height). Scaled to 0.66 size (-20% from 0.825).
    private static final double CARD_HALF_WIDTH = 0.484;
    private static final double CARD_HALF_HEIGHT = 1.16 * CARD_SCALE;
    private static final Key CARD_FONT = Key.key("rounds", "default");

    public CardDisplayManager(RoundsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, List<Card> cards) {
        if (player == null || !player.isOnline()) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        close(player.getUniqueId());
        Scene scene = new Scene(player, cards);
        scenes.put(player.getUniqueId(), scene);
        scene.begin();
        hideOtherPlayers(player);
    }

    public void close(Player player) {
        if (player == null) return;
        close(player.getUniqueId());
    }

    public void close(UUID playerId) {
        if (playerId == null) return;
        cleanupPlayer(playerId);
    }

    public boolean hasActiveScene(UUID uuid) {
        return uuid != null && scenes.containsKey(uuid);
    }

    public Location getSceneOrigin(UUID uuid) {
        if (uuid == null) return null;
        Scene s = scenes.get(uuid);
        return s != null ? s.origin : null;
    }

    public boolean isCardClickRecent(UUID uuid) {
        if (uuid == null) return false;
        Long t = lastCardPickMs.get(uuid);
        if (t == null) return false;
        if (System.currentTimeMillis() - t < 500) {
            return true;
        }
        lastCardPickMs.remove(uuid);
        return false;
    }

    public boolean isWithinSelectionBounds(Player player, Location to) {
        if (player == null || to == null) return false;
        Scene scene = scenes.get(player.getUniqueId());
        return scene != null && scene.isWithinBounds(to);
    }

    public void closeAll() {
        for (UUID uuid : new ArrayList<>(scenes.keySet())) {
            cleanupPlayer(uuid);
        }
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            for (TextDisplay td : w.getEntitiesByClass(TextDisplay.class)) {
                if (td.getScoreboardTags().contains("rounds_card_display")) {
                    td.remove();
                }
            }
        }
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid == null) return;
        Scene s = scenes.remove(uuid);
        if (s != null) {
            s.dispose();
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (s.origin != null && s.origin.getWorld() != null && s.origin.getWorld().equals(p.getWorld())) {
                    Location returnLoc = s.origin.clone();
                    returnLoc.setYaw(p.getLocation().getYaw());
                    returnLoc.setPitch(p.getLocation().getPitch());
                    p.teleport(returnLoc);
                }
                p.setVelocity(new Vector(0, 0, 0));
                p.setFlying(false);
                p.setAllowFlight(false);
            }
        }
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            showOtherPlayers(p);
            p.sendActionBar(Component.empty());
        }
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            for (TextDisplay td : w.getEntitiesByClass(TextDisplay.class)) {
                if (td.getScoreboardTags().contains("owner_" + uuid)) {
                    td.remove();
                }
            }
        }
    }

    private void hideOtherPlayers(Player viewer) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(viewer.getUniqueId())) continue;
            try {
                viewer.hideEntity(plugin, other);
            } catch (Throwable t) {
                viewer.hidePlayer(plugin, other);
            }
        }
    }

    private void showOtherPlayers(Player viewer) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(viewer.getUniqueId())) continue;
            try {
                viewer.showEntity(plugin, other);
            } catch (Throwable ignored) {}
            try {
                viewer.showPlayer(plugin, other);
            } catch (Throwable ignored) {}
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastCardPickMs.remove(event.getPlayer().getUniqueId());
        cleanupPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR
                || plugin.getGameManager() == null
                || !plugin.getGameManager().isGameStarted()
                || plugin.getGameManager().getState() != com.rounds.game.GameManager.GameState.CARDS) {
            cleanupPlayer(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeftClick(PlayerInteractEvent event) {
        Scene scene = scenes.get(event.getPlayer().getUniqueId());
        if (scene == null) return;
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        event.setCancelled(true);
        lastCardPickMs.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
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
            lastCardPickMs.put(player.getUniqueId(), System.currentTimeMillis());
            scene.select(slot);
        }
    }

    private TextColor rarityTextColor(CardRegistry.Rarity rarity) {
        return switch (rarity) {
            case COMMON -> NamedTextColor.WHITE;
            case UNCOMMON -> NamedTextColor.GREEN;
            case RARE -> NamedTextColor.AQUA;
            case EPIC -> NamedTextColor.LIGHT_PURPLE;
            case LEGENDARY -> NamedTextColor.GOLD;
        };
    }

    private Component starComponent(Card card) {
        int count = card.getRarity().ordinal() + 1;
        String stars = String.join(" ", Collections.nCopies(Math.max(1, Math.min(5, count)), "★"));
        return Component.text(stars, rarityTextColor(card.getRarity()))
                .decorate(TextDecoration.BOLD);
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
        if (card instanceof com.rounds.api.impl.CustomCardAdapter adapter) {
            Character customGlyph = adapter.getCustomCard().getGlyph();
            if (customGlyph != null) {
                return Component.text(String.valueOf(customGlyph)).font(CARD_FONT);
            }
        }
        return Component.text(String.valueOf((char) card.getGlyphCodepoint())).font(CARD_FONT);
    }

    private final class Scene {
        final Player player;
        List<Card> cards;
        final UUID playerId;
        final Location origin;
        final double fx, fz;
        final double rx, rz;

        TextDisplay[] displays = new TextDisplay[SLOT_COUNT];
        TextDisplay[] starDisplays = new TextDisplay[SLOT_COUNT];
        Location[] targets = new Location[SLOT_COUNT];
        Location[] starTargets = new Location[SLOT_COUNT];
        final Set<Location> fakeBarriers = new HashSet<>();
        int hovered = -1;

        BukkitTask tickTask;
        BukkitTask carouselTask;

        Scene(Player player, List<Card> cards) {
            this.player = player;
            this.playerId = player.getUniqueId();
            this.cards = new ArrayList<>(cards);
            this.origin = player.getLocation().clone();
            float yaw = origin.getYaw();
            double rad = Math.toRadians(yaw);
            this.fx = -Math.sin(rad);
            this.fz = Math.cos(rad);
            this.rx = -fz;
            this.rz = fx;
        }

        boolean isWithinBounds(Location loc) {
            if (loc == null || loc.getWorld() == null || origin.getWorld() == null) return false;
            if (!loc.getWorld().equals(origin.getWorld())) return false;
            if (Math.abs(loc.getY() - origin.getY()) > 0.85) return false;

            double dx = loc.getX() - origin.getX();
            double dz = loc.getZ() - origin.getZ();
            double fwd = dx * fx + dz * fz;
            double side = dx * rx + dz * rz;

            // Allow approaching cards closer (up to 2.00m forward, cards are at 2.2m),
            // and stepping backward with extra room (up to 1.50m backward). Lateral strafe limit 1.50m.
            if (fwd < -1.50 || fwd > 2.00) return false;
            if (Math.abs(side) > 1.50) return false;
            return true;
        }

        void setupWalkingEnclosure() {
            if (origin.getWorld() == null) return;
            World world = origin.getWorld();
            int floorY = (int) Math.floor(origin.getY() - 0.1);
            int bx = origin.getBlockX();
            int bz = origin.getBlockZ();

            // Check 9x9 grid around origin (radius 4 blocks)
            for (int x = bx - 4; x <= bx + 4; x++) {
                for (int z = bz - 4; z <= bz + 4; z++) {
                    double cx = x + 0.5;
                    double cz = z + 0.5;
                    double dx = cx - origin.getX();
                    double dz = cz - origin.getZ();
                    double fwd = dx * fx + dz * fz;
                    double side = dx * rx + dz * rz;

                    boolean inWalkZone = (fwd >= -1.65 && fwd <= 2.15 && Math.abs(side) <= 1.65);
                    if (inWalkZone) {
                        // Protect floor: if non-solid (slope, drop-off, jump-pad abyss), place barrier floor
                        Location floorLoc = new Location(world, x, floorY, z);
                        if (!floorLoc.getBlock().getType().isSolid()) {
                            fakeBarriers.add(floorLoc);
                            player.sendBlockChange(floorLoc, Material.BARRIER.createBlockData());
                        }
                    } else if (fwd >= -2.4 && fwd <= 2.9 && Math.abs(side) <= 2.4) {
                        // Outer boundary perimeter: invisible barrier walls (2 blocks high) to prevent stepping off
                        for (int y = floorY + 1; y <= floorY + 2; y++) {
                            Location wallLoc = new Location(world, x, y, z);
                            if (!wallLoc.getBlock().getType().isSolid()) {
                                fakeBarriers.add(wallLoc);
                                player.sendBlockChange(wallLoc, Material.BARRIER.createBlockData());
                            }
                        }
                    }
                }
            }
        }

        void begin() {
            setupWalkingEnclosure();

            Location eye = player.getEyeLocation();
            float yaw = player.getLocation().getYaw();

            for (int i = 0; i < SLOT_COUNT && i < cards.size(); i++) {
                double deg = (i - (SLOT_COUNT - 1) / 2.0) * (FAN_TOTAL_DEG / (SLOT_COUNT - 1));
                Location loc = fanPosition(eye, yaw, deg, RADIUS);
                targets[i] = loc;
                starTargets[i] = starPosition(loc);
                spawnCard(i, loc);
            }

            // Warm-up: the client does not interpolate the very first teleport of a freshly
            // spawned Display entity (it snaps). A tiny no-op teleport right after spawning
            // initializes the client lerp state, so the first wheel rotation slides smoothly
            // just like all following ones.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < SLOT_COUNT; i++) {
                    if (displays[i] != null && displays[i].isValid()) {
                        displays[i].setTeleportDuration(2);
                        displays[i].teleport(displays[i].getLocation());
                    }
                    if (starDisplays[i] != null && starDisplays[i].isValid()) {
                        starDisplays[i].setTeleportDuration(2);
                        starDisplays[i].teleport(starDisplays[i].getLocation());
                    }
                }
            }, 2L);

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
            // Fixed billboard: face the player once, do not track the camera afterwards.
            float facing = (float) ((yaw + deg + 180.0) % 360.0);
            return new Location(eye.getWorld(), x, eye.getY() - CARD_CENTER_OFFSET, z, facing, 0f);
        }

        Location starPosition(Location cardLoc) {
            return cardLoc.clone().add(0, CARD_CENTER_OFFSET + CARD_HALF_HEIGHT + 0.15, 0);
        }

        void spawnCard(int i, Location loc) {
            Card card = cards.get(i);
            TextDisplay display = player.getWorld().spawn(loc, TextDisplay.class);
            if (display != null) {
                display.text(cardGlyph(card));
                display.setTransformation(CARD_TRANSFORMATION);
                display.setBillboard(Display.Billboard.FIXED);
                display.setViewRange(0.6f);
                display.setGlowing(true);
                display.setGlowColorOverride(rarityColor(card.getRarity()));
                display.setSeeThrough(true);
                display.addScoreboardTag("rounds_card_display");
                display.addScoreboardTag("owner_" + playerId);
                displays[i] = display;
            }

            Location starLoc = starPosition(loc);
            TextDisplay starDisplay = player.getWorld().spawn(starLoc, TextDisplay.class);
            if (starDisplay != null) {
                starDisplay.text(starComponent(card));
                starDisplay.setTransformation(CARD_TRANSFORMATION);
                starDisplay.setBillboard(Display.Billboard.FIXED);
                starDisplay.setViewRange(0.6f);
                starDisplay.setSeeThrough(true);
                starDisplay.setShadowed(true);
                starDisplay.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                starDisplay.addScoreboardTag("rounds_card_display");
                starDisplay.addScoreboardTag("owner_" + playerId);
                starDisplays[i] = starDisplay;
            }
        }

        void updateHover() {
            Location eye = player.getEyeLocation();
            org.bukkit.util.Vector dir = eye.getDirection();
            int best = -1;
            double bestT = Double.MAX_VALUE;
            for (int i = 0; i < SLOT_COUNT && i < cards.size(); i++) {
                if (displays[i] == null) continue;
                org.bukkit.util.Vector center = displays[i].getLocation().toVector()
                        .add(new org.bukkit.util.Vector(0, CARD_CENTER_OFFSET, 0));
                org.bukkit.util.Vector distVec = center.clone().subtract(eye.toVector());
                if (distVec.lengthSquared() < 0.01) continue;
                org.bukkit.util.Vector normal = distVec.clone().normalize();
                double denom = normal.dot(dir);
                if (Math.abs(denom) < 1e-6) continue;
                double t = distVec.dot(normal) / denom;
                if (t < 0) continue;
                org.bukkit.util.Vector hit = eye.toVector().add(dir.clone().multiply(t));
                org.bukkit.util.Vector pc = hit.clone().subtract(center);
                double x = pc.getX() * normal.getZ() - pc.getZ() * normal.getX();
                double y = pc.getY();
                if (Math.abs(x) <= CARD_HALF_WIDTH && Math.abs(y) <= CARD_HALF_HEIGHT) {
                    if (t < bestT) { bestT = t; best = i; }
                }
            }
            hovered = best;
        }

        void spawnFireParticles() {
            for (int i = 0; i < SLOT_COUNT && i < cards.size(); i++) {
                if (displays[i] == null) continue;
                CardRegistry.Rarity rarity = cards.get(i).getRarity();
                if (!hasFire(rarity)) continue;
                Location c = displays[i].getLocation().clone().add(0, CARD_CENTER_OFFSET, 0);
                Color col = rarityColor(rarity);
                Particle.DustOptions dust = new Particle.DustOptions(col, 0.6f);

                double cardYawRad = Math.toRadians(displays[i].getLocation().getYaw());
                double nx = -Math.sin(cardYawRad);
                double nz = Math.cos(cardYawRad);
                double rightX = -nz;
                double rightZ = nx;

                for (int p = 0; p < 2; p++) {
                    ThreadLocalRandom rng = ThreadLocalRandom.current();
                    // Position particles slightly behind the card (back-glow) and along outer edges
                    // so card text and description remain 100% crisp and readable
                    boolean sideEdge = rng.nextBoolean();
                    double edgeX = sideEdge ? (rng.nextBoolean() ? 1 : -1) * (CARD_HALF_WIDTH + rng.nextDouble() * 0.10)
                                            : (rng.nextDouble() - 0.5) * 2 * CARD_HALF_WIDTH;
                    double edgeY = sideEdge ? (rng.nextDouble() - 0.5) * 2 * CARD_HALF_HEIGHT
                                            : (rng.nextBoolean() ? 1 : -1) * (CARD_HALF_HEIGHT + rng.nextDouble() * 0.10);
                    double depthOffset = -0.10 - rng.nextDouble() * 0.15; // strictly behind the card plane

                    double px = c.getX() + rightX * edgeX + nx * depthOffset;
                    double py = c.getY() + edgeY;
                    double pz = c.getZ() + rightZ * edgeX + nz * depthOffset;

                    c.getWorld().spawnParticle(Particle.REDSTONE, px, py, pz, 1, 0, 0, 0, 0, dust);
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
                if (starDisplays[i] != null && starTargets[i - 1] != null) {
                    starDisplays[i].setTeleportDuration(10);
                    starDisplays[i].teleport(starTargets[i - 1]);
                }
            }
            fadeOut(displays[0]);
            fadeOut(starDisplays[0]);
            for (int i = 0; i < SLOT_COUNT - 1; i++) {
                displays[i] = displays[i + 1];
                starDisplays[i] = starDisplays[i + 1];
            }
            displays[SLOT_COUNT - 1] = null;
            starDisplays[SLOT_COUNT - 1] = null;

            int last = SLOT_COUNT - 1;
            Location enter = targets[last].clone().add(1.6, 0, 0);
            spawnCard(last, enter);
            // Let the client register the freshly spawned entity for one tick before the
            // interpolated slide into the slot begins; otherwise the entry pops instantly.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (displays[last] != null && displays[last].isValid() && targets[last] != null) {
                    displays[last].setTeleportDuration(10);
                    displays[last].teleport(targets[last]);
                }
                if (starDisplays[last] != null && starDisplays[last].isValid() && starTargets[last] != null) {
                    starDisplays[last].setTeleportDuration(10);
                    starDisplays[last].teleport(starTargets[last]);
                }
            }, 1L);
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
                if (displays[i] != null) {
                    displays[i].remove();
                    displays[i] = null;
                }
                if (starDisplays[i] != null) {
                    starDisplays[i].remove();
                    starDisplays[i] = null;
                }
            }
            if (player.isOnline()) {
                player.sendActionBar(Component.empty());
                for (Location loc : fakeBarriers) {
                    player.sendBlockChange(loc, loc.getBlock().getBlockData());
                }
            }
            fakeBarriers.clear();
            tickTask = null;
            carouselTask = null;
            displays = new TextDisplay[SLOT_COUNT];
            starDisplays = new TextDisplay[SLOT_COUNT];
        }
    }
}

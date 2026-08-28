package com.rounds.cards;

import com.rounds.RoundsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class CardRegistry {

    private final Map<Integer, Card> cards = new LinkedHashMap<>();
    private int loadedFiles = 0;
    private final Random random = new Random();
    private final File dataFolder;
    private final File originalDir;
    private final File customDir;
    private final RoundsPlugin plugin;

    public enum Rarity {
        COMMON(ChatColor.WHITE, 40),
        UNCOMMON(ChatColor.GREEN, 30),
        RARE(ChatColor.AQUA, 18),
        EPIC(ChatColor.LIGHT_PURPLE, 9),
        LEGENDARY(ChatColor.GOLD, 3);

        private final ChatColor color;
        private final int weight;

        Rarity(ChatColor color, int weight) {
            this.color = color;
            this.weight = weight;
        }

        public ChatColor getColor() { return color; }
        public int getWeight() { return weight; }
    }

    public CardRegistry(RoundsPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
        this.originalDir = new File(dataFolder, "cards/original");
        this.customDir = new File(dataFolder, "cards/custom");
    }

    public void loadCards() {
        cards.clear();
        loadedFiles = 0;
        File cardsRoot = new File(dataFolder, "cards");
        cardsRoot.mkdirs();
        originalDir.mkdirs();
        customDir.mkdirs();

        extractBuiltinCards();
        removeDeletedBuiltinCards();
        migrateLegacyIfNeeded();
        loadFromDir(cardsRoot);

        long legendaries = cards.values().stream().filter(c -> c.isEnabled()
                && c.getRarity() == Rarity.LEGENDARY).count();
        plugin.getLogger().info("Loaded " + cards.size() + " cards from " + loadedFiles
                + " files (" + legendaries + " enabled legendaries)");
    }

    private void extractBuiltinCards() {
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(
                new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()))) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("cards/") || !name.endsWith(".yml")) continue;
                File target = new File(dataFolder, name);
                byte[] jarBytes;
                try (java.io.InputStream in = jar.getInputStream(entry)) {
                    jarBytes = in.readAllBytes();
                }
                if (!needsUpdate(target, jarBytes)) continue;
                target.getParentFile().mkdirs();
                try (java.io.OutputStream out = new java.io.FileOutputStream(target)) {
                    out.write(jarBytes);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to extract builtin cards: " + e.getMessage());
        }
    }

    private static boolean needsUpdate(File target, byte[] jarBytes) {
        if (!target.exists()) return true;
        try {
            byte[] diskBytes = java.nio.file.Files.readAllBytes(target.toPath());
            return !java.util.Arrays.equals(diskBytes, jarBytes);
        } catch (IOException e) {
            return true;
        }
    }

    private static final Set<String> REMOVED_BUILTIN_CARDS = new HashSet<>(List.of("remote.yml"));

    private void removeDeletedBuiltinCards() {
        for (String name : REMOVED_BUILTIN_CARDS) {
            File stale = new File(originalDir, name);
            if (stale.isFile() && stale.delete()) {
                plugin.getLogger().info("Removed removed builtin card: " + name);
            }
        }
    }

    private void migrateLegacyIfNeeded() {
        File legacyFile = new File(dataFolder, "cards.yml");
        if (!legacyFile.exists()) return;
        if (hasCardFiles(originalDir)) {
            legacyFile.renameTo(new File(dataFolder, "cards.yml.legacy-backup"));
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(legacyFile);
        ConfigurationSection cardsSection = config.getConfigurationSection("cards");
        if (cardsSection == null) return;

        for (String key : cardsSection.getKeys(false)) {
            ConfigurationSection cardSection = cardsSection.getConfigurationSection(key);
            if (cardSection == null) continue;

            try {
                int id = Integer.parseInt(key);
                String nameRaw = cardSection.getString("name", "Card #" + id);
                String cleanName = nameRaw.replaceAll("&[0-9a-fk-or]", "").trim();
                String slug = cleanName.replaceAll("[^a-zA-Z0-9]+", "-").replaceAll("^-|-$", "").toLowerCase();

                YamlConfiguration cardFile = new YamlConfiguration();
                cardFile.set("id", id);
                cardFile.set("name.en", nameRaw);
                cardFile.set("description.en", cardSection.getString("description", ""));
                cardFile.set("material", cardSection.getString("material", "PAPER"));
                cardFile.set("rarity", cardSection.getString("rarity", "COMMON"));
                cardFile.set("enabled", cardSection.getBoolean("enabled", true));

                ConfigurationSection effectsSection = cardSection.getConfigurationSection("effects");
                if (effectsSection != null) {
                    for (String effectKey : effectsSection.getKeys(false)) {
                        cardFile.set("effects." + effectKey, effectsSection.getDouble(effectKey));
                    }
                }

                List<String> commands = cardSection.getStringList("commands");
                if (!commands.isEmpty()) cardFile.set("commands", commands);

                String script = cardSection.getString("script", null);
                if (script != null) cardFile.set("script", script);

                cardFile.save(new File(originalDir, slug + ".yml"));
            } catch (Exception e) {
                // skip
            }
        }

        legacyFile.renameTo(new File(dataFolder, "cards.yml.legacy-backup"));
    }

    private boolean hasCardFiles(File dir) {
        if (!dir.exists()) return false;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        return files != null && files.length > 0;
    }

    private void loadFromDir(File dir) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        Arrays.sort(files, Comparator.comparing(File::getName));

        for (File file : files) {
            if (file.isDirectory()) {
                loadFromDir(file);
                continue;
            }
            if (!file.getName().endsWith(".yml")) continue;
            loadedFiles++;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            try {
                int baseId = config.getInt("id");
                if (baseId <= 0) continue;

                List<Map<?, ?>> variations = config.getMapList("variations");
                if (variations.isEmpty()) {
                    registerCard(parseSingleCard(config, baseId), file);
                } else {
                    for (int i = 0; i < variations.size(); i++) {
                        int fallbackId = (i == 0) ? baseId : baseId * 100 + i + 1;
                        Card card = parseVariationCard(config, variations.get(i), fallbackId, baseId);
                        if (card != null) registerCard(card, file);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load card '" + file.getName() + "': " + e.getMessage());
            }
        }
    }

    // One file = one card family. No "variations" list -> single card (legacy format).
    // Each variation entry: rarity, description {ru/en}, effects; optional: id, material.
    // Default ids: first variation inherits the file id, the rest get id*100+index (e.g. 30 -> 3002, 3003...).
    private Card parseSingleCard(YamlConfiguration config, int id) {
        Map<String, String> names = loadLocalizedMap(config, "name");
        if (names.isEmpty()) names.put("en", "Card #" + id);

        return new Card(id, id, names, loadLocalizedMap(config, "description"),
                parseMaterial(config.getString("material", "PAPER")),
                parseRarity(config.getString("rarity", "COMMON")),
                config.getBoolean("enabled", true),
                loadEffects(config),
                config.getStringList("commands"), config.getString("script", null));
    }

    private Card parseVariationCard(YamlConfiguration config, Map<?, ?> variation, int fallbackId, int familyId) {
        int id = fallbackId;
        if (variation.get("id") instanceof Number explicit) id = explicit.intValue();

        String materialStr = variation.get("material") != null
                ? String.valueOf(variation.get("material"))
                : config.getString("material", "PAPER");
        String rarityStr = variation.get("rarity") != null
                ? String.valueOf(variation.get("rarity"))
                : config.getString("rarity", "COMMON");

        Map<String, String> names = loadLocalizedMap(config, "name");
        if (names.isEmpty()) names.put("en", "Card #" + id);

        Map<String, String> descriptions = resolveVariationDescriptions(config, variation);

        Map<String, Double> effects = new LinkedHashMap<>();
        if (variation.get("effects") instanceof Map<?, ?> varEffects) {
            for (Map.Entry<?, ?> e : varEffects.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                double val = 0;
                if (e.getValue() instanceof Number n) val = n.doubleValue();
                else {
                    try { val = Double.parseDouble(e.getValue().toString()); } catch (NumberFormatException ignored) {}
                }
                effects.put(String.valueOf(e.getKey()).toLowerCase(), val);
            }
        }

        return new Card(id, familyId, names, descriptions, parseMaterial(materialStr), parseRarity(rarityStr),
                config.getBoolean("enabled", true), effects,
                config.getStringList("commands"), config.getString("script", null));
    }

    // Description is declared once at the top as a template with {v} / {0},{1},... placeholders.
    // Each variation supplies "value: ..." or "values: [...]"; a variation may also fully
    // override the description with its own description {ru/en} block.
    private Map<String, String> resolveVariationDescriptions(YamlConfiguration config, Map<?, ?> variation) {
        Map<String, String> descriptions = loadLocalizedMap(config, "description");

        if (variation.get("description") instanceof Map<?, ?> varDesc) {
            Map<String, String> overridden = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : varDesc.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    overridden.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
            return overridden;
        }

        List<String> values = new ArrayList<>();
        if (variation.get("values") instanceof List<?> list) {
            for (Object o : list) values.add(String.valueOf(o));
        } else if (variation.get("value") != null) {
            values.add(String.valueOf(variation.get("value")));
        }
        if (values.isEmpty()) return descriptions;

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : descriptions.entrySet()) {
            String text = e.getValue();
            for (int i = 0; i < values.size(); i++) {
                text = text.replace("{" + i + "}", values.get(i));
            }
            text = text.replace("{v}", values.get(0));
            result.put(e.getKey(), text);
        }
        return result;
    }

    private Map<String, Double> loadEffects(YamlConfiguration config) {
        Map<String, Double> effects = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("effects");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                effects.put(key.toLowerCase(), section.getDouble(key));
            }
        }
        return effects;
    }

    private Material parseMaterial(String str) {
        try {
            return Material.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.PAPER;
        }
    }

    private Rarity parseRarity(String str) {
        try {
            return Rarity.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Rarity.COMMON;
        }
    }

    private void registerCard(Card card, File file) {
        Card previous = cards.put(card.getId(), card);
        if (previous != null) {
            cards.put(previous.getId(), previous);
            plugin.getLogger().warning("Duplicate card id " + card.getId()
                    + " in '" + file.getName() + "', keeping earlier definition");
        }
    }

    private Map<String, String> loadLocalizedMap(YamlConfiguration config, String path) {
        Map<String, String> map = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section != null) {
            for (String lang : section.getKeys(false)) {
                String val = section.getString(lang, "");
                if (!val.isEmpty()) map.put(lang, val);
            }
        } else {
            String fallback = config.getString(path, null);
            if (fallback != null && !fallback.isEmpty()) {
                map.put("en", fallback);
            }
        }
        return map;
    }

    public void reload() {
        loadCards();
    }

    public Card getCard(int id) {
        return cards.get(id);
    }

    public Card findCardByName(String name) {
        if (name == null || name.isBlank()) return null;
        String query = name.trim();

        // 1. Try numeric ID lookup
        try {
            int id = Integer.parseInt(query);
            Card byId = cards.get(id);
            if (byId != null) return byId;
        } catch (NumberFormatException ignored) {}

        String cleanQuery = stripAllFormatting(query).toLowerCase();
        if (cleanQuery.isEmpty()) return null;

        // 2. Exact match across all localized names
        for (Card c : cards.values()) {
            for (String n : c.getNames().values()) {
                String cleanName = stripAllFormatting(n).toLowerCase();
                if (cleanName.equals(cleanQuery)) return c;
            }
        }

        // 3. Substring match across all localized names
        for (Card c : cards.values()) {
            for (String n : c.getNames().values()) {
                String cleanName = stripAllFormatting(n).toLowerCase();
                if (cleanName.contains(cleanQuery)) return c;
            }
        }
        return null;
    }

    private static String stripAllFormatting(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(text.replaceAll("&[0-9a-fk-orA-FK-OR]", "")).trim();
    }

    public List<String> getCardNameSuggestions() {
        List<String> list = new ArrayList<>();
        String lang = "en";
        try { lang = com.rounds.util.Messages.getLanguageCode(); } catch (Exception ignored) {}
        for (Card c : cards.values()) {
            String name = c.getName(lang);
            name = stripAllFormatting(name);
            if (!list.contains(name)) {
                list.add(name);
            }
        }
        return list;
    }

    public Collection<Card> getAllCards() {
        return Collections.unmodifiableCollection(cards.values());
    }

    public int getLoadedFileCount() {
        return loadedFiles;
    }

    public List<Card> getEnabledCards() {
        List<Card> enabled = new ArrayList<>();
        for (Card c : cards.values()) {
            if (c.isEnabled()) enabled.add(c);
        }
        return enabled;
    }

    public List<Card> getRandomCards(int count) {
        List<Card> pool = getEnabledCards();
        List<Card> result = new ArrayList<>();

        int totalWeight = 0;
        for (Card c : pool) totalWeight += c.getRarity().getWeight();

        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            int roll = random.nextInt(totalWeight);
            int cumulative = 0;
            Card chosen = pool.get(0);
            for (Card c : pool) {
                cumulative += c.getRarity().getWeight();
                if (roll < cumulative) {
                    chosen = c;
                    break;
                }
            }
            result.add(chosen);
            int family = chosen.getFamilyId();
            pool.removeIf(c -> c.getFamilyId() == family);
            totalWeight = 0;
            for (Card c : pool) totalWeight += c.getRarity().getWeight();
        }
        return result;
    }

    public List<Card> getRandomCardsByRarity(Rarity rarity, int count, Integer excludeId) {
        List<Card> pool = new ArrayList<>();
        int excludeFamily = (excludeId != null && getCard(excludeId) != null)
                ? getCard(excludeId).getFamilyId() : Integer.MIN_VALUE;
        for (Card c : getEnabledCards()) {
            if (c.getRarity() != rarity) continue;
            if (c.getFamilyId() == excludeFamily) continue;
            pool.add(c);
        }
        Collections.shuffle(pool, random);

        List<Card> result = new ArrayList<>();
        Set<Integer> usedFamilies = new HashSet<>();
        for (Card c : pool) {
            if (result.size() >= count) break;
            if (!usedFamilies.add(c.getFamilyId())) continue;
            result.add(c);
        }
        return result;
    }
}

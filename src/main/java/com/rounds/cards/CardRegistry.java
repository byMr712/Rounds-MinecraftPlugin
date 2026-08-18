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
        originalDir.mkdirs();
        customDir.mkdirs();

        extractBuiltinCards();
        migrateLegacyIfNeeded();
        loadFromDir(originalDir);
        loadFromDir(customDir);
    }

    private void extractBuiltinCards() {
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(
                new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()))) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("cards/original/") && name.endsWith(".yml")) {
                    File target = new File(dataFolder, name);
                    if (!target.exists()) {
                        target.getParentFile().mkdirs();
                        try (java.io.InputStream in = jar.getInputStream(entry);
                             java.io.OutputStream out = new java.io.FileOutputStream(target)) {
                            byte[] buf = new byte[4096];
                            int len;
                            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to extract builtin cards: " + e.getMessage());
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
                cardFile.set("custom-model-data", cardSection.getInt("custom-model-data", 0));
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
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) return;

        Arrays.sort(files, Comparator.comparing(File::getName));

        for (File file : files) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            try {
                int id = config.getInt("id");
                if (id <= 0) continue;

                String materialStr = config.getString("material", "PAPER");
                int customModelData = config.getInt("custom-model-data", 0);
                String rarityStr = config.getString("rarity", "COMMON");
                boolean enabled = config.getBoolean("enabled", true);

                Material material;
                try {
                    material = Material.valueOf(materialStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    material = Material.PAPER;
                }

                Rarity rarity;
                try {
                    rarity = Rarity.valueOf(rarityStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    rarity = Rarity.COMMON;
                }

                Map<String, String> names = loadLocalizedMap(config, "name");
                if (names.isEmpty()) names.put("en", "Card #" + id);

                Map<String, String> descriptions = loadLocalizedMap(config, "description");

                Map<String, Double> effects = new LinkedHashMap<>();
                ConfigurationSection effectsSection = config.getConfigurationSection("effects");
                if (effectsSection != null) {
                    for (String effectKey : effectsSection.getKeys(false)) {
                        effects.put(effectKey.toLowerCase(), effectsSection.getDouble(effectKey));
                    }
                }

                List<String> commands = config.getStringList("commands");
                String customScript = config.getString("script", null);

                Card card = new Card(id, names, descriptions, material, customModelData,
                        rarity, enabled, effects, commands, customScript);
                cards.put(id, card);
            } catch (Exception e) {
                // skip broken card file
            }
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
        String lower = name.toLowerCase();
        for (Card c : cards.values()) {
            for (String n : c.getNames().values()) {
                String stripped = ChatColor.stripColor(n).toLowerCase();
                if (stripped.contains(lower)) return c;
            }
        }
        return null;
    }

    public List<String> getCardNameSuggestions() {
        List<String> list = new ArrayList<>();
        String lang = "en";
        try { lang = com.rounds.util.Messages.getLanguage(); } catch (Exception ignored) {}
        for (Card c : cards.values()) {
            list.add(ChatColor.stripColor(c.getName(lang)));
        }
        return list;
    }

    public Collection<Card> getAllCards() {
        return Collections.unmodifiableCollection(cards.values());
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
            pool.remove(chosen);
            totalWeight = 0;
            for (Card c : pool) totalWeight += c.getRarity().getWeight();
        }
        return result;
    }
}

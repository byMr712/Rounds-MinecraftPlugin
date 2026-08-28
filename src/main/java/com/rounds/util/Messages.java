package com.rounds.util;

import com.rounds.RoundsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class Messages {

    // Язык по умолчанию и «легасные» значения конфига до переезда на папку lang/.
    private static final String DEFAULT_LANG = "EN_en";
    private static final Map<String, String> LEGACY_IDS = Map.of("ru", "RU_ru", "en", "EN_en");

    private static RoundsPlugin plugin;
    private static String language = DEFAULT_LANG;
    private static Map<String, String> messages = new HashMap<>();
    // Доступные языки: каждый *.txt в папке lang/ — отдельный язык, имя файла = id.
    private static Set<String> available = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public static void init(RoundsPlugin pl) {
        plugin = pl;
        reload();
    }

    public static void reload() {
        scanLanguages();
        language = resolveLanguage(plugin.getConfig().getString("language", DEFAULT_LANG));
        if (language == null) language = DEFAULT_LANG;
        loadMessages();
    }

    public static void reload(String newLanguage) {
        scanLanguages();
        String resolved = resolveLanguage(newLanguage);
        if (resolved == null) resolved = DEFAULT_LANG;
        language = resolved;
        plugin.getConfig().set("language", resolved);
        plugin.saveConfig();
        loadMessages();
    }

    /**
     * Сканирует папку lang/: любой *.txt внутри считается языковым пакетом,
     * поэтому сторонний файл, добавленный после запуска, подхватывается
     * следующим /rdebug reload или рестартом сервера.
     */
    private static void scanLanguages() {
        available.clear();
        File dir = new File(plugin.getDataFolder(), "lang");
        if (!dir.exists() && !dir.mkdirs()) return;
        saveDefaultLang("lang/" + DEFAULT_LANG + ".txt");
        saveDefaultLang("lang/RU_ru.txt");
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".txt"));
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            String id = name.substring(0, name.length() - 4);
            if (!id.isEmpty()) available.add(id);
        }
    }

    private static void saveDefaultLang(String resourcePath) {
        if (plugin.getResource(resourcePath) != null
                && !new File(plugin.getDataFolder(), resourcePath).exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    /** Канонический id языка по вводу пользователя или null, если такого файла нет. */
    public static String resolveLanguage(String input) {
        if (input == null || input.isEmpty()) return null;
        if (available.contains(input)) return input; // точное совпадение с именем файла
        String legacy = LEGACY_IDS.get(input.toLowerCase(Locale.ROOT)); // старые "ru"/"en"
        if (legacy != null && available.contains(legacy)) return legacy;
        for (String id : available) { // регистронезависимо
            if (id.equalsIgnoreCase(input)) return id;
        }
        return null;
    }

    public static Set<String> getAvailableLanguages() {
        return Collections.unmodifiableSet(available);
    }

    private static void loadMessages() {
        messages.clear();

        File file = new File(plugin.getDataFolder(), "lang" + File.separatorChar + language + ".txt");
        if (!file.exists()) file = fallbackFile();
        if (file == null || !file.exists()) return;

        InputStream defStream = plugin.getResource("lang/" + file.getName());
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream, StandardCharsets.UTF_8));
            flattenSection("", defConfig, messages);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        flattenSection("", config, messages);
    }

    /** Резервный языковой пакет, если файл текущего языка удалён. */
    private static File fallbackFile() {
        File def = new File(plugin.getDataFolder(), "lang" + File.separatorChar + DEFAULT_LANG + ".txt");
        if (def.exists()) return def;
        for (String id : available) {
            if (id.equals(DEFAULT_LANG)) continue;
            File f = new File(plugin.getDataFolder(), "lang" + File.separatorChar + id + ".txt");
            if (f.exists()) return f;
        }
        return null;
    }

    private static void flattenSection(String prefix, ConfigurationSection section, Map<String, String> map) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection sub) {
                flattenSection(prefix + key + ".", sub, map);
            } else {
                map.put(prefix + key, ChatColor.translateAlternateColorCodes('&', String.valueOf(value)));
            }
        }
    }

    public static String get(String key) {
        return messages.getOrDefault(key, key);
    }

    public static String get(String key, Object... args) {
        String msg = get(key);
        for (int i = 0; i < args.length; i++) {
            msg = msg.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return msg;
    }

    public static String raw(String key) {
        String val = messages.getOrDefault(key, key);
        return ChatColor.stripColor(val);
    }

    public static String getLanguage() {
        return language;
    }

    /**
     * Короткий код для внешних данных, где языки ключованы как "ru"/"en"
     * (карточки: name.ru / name.en). RU_ru → "ru", DE_de → "de".
     */
    public static String getLanguageCode() {
        int u = language.indexOf('_');
        String code = u > 0 ? language.substring(0, u) : language;
        return code.toLowerCase(Locale.ROOT);
    }
}

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
import java.util.HashMap;
import java.util.Map;

public class Messages {

    private static RoundsPlugin plugin;
    private static String language;
    private static Map<String, String> messages = new HashMap<>();

    public static void init(RoundsPlugin pl) {
        plugin = pl;
        language = pl.getConfig().getString("language", "en");
        loadMessages();
    }

    public static void reload() {
        language = plugin.getConfig().getString("language", "en");
        loadMessages();
    }

    public static void reload(String newLanguage) {
        language = newLanguage;
        loadMessages();
    }

    private static void loadMessages() {
        messages.clear();

        File file = new File(plugin.getDataFolder(), "messages.yml");
        FileConfiguration config;

        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);

        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream, StandardCharsets.UTF_8));
            config.setDefaults(defConfig);
        }

        ConfigurationSection section = config.getConfigurationSection(language);
        if (section == null) {
            section = config.getConfigurationSection("ru");
        }
        if (section == null) return;

        flattenSection("", section, messages);
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
}

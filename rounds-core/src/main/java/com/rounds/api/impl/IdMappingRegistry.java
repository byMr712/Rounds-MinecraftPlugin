package com.rounds.api.impl;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class IdMappingRegistry {

    public static final int BUILTIN_MAX_ID = 99_999;
    public static final int CUSTOM_MIN_ID = 100_000;

    private static class Entry {
        final NamespacedKey key;
        final int id;
        boolean retired;

        Entry(NamespacedKey key, int id, boolean retired) {
            this.key = key;
            this.id = id;
            this.retired = retired;
        }
    }

    private final File file;
    private final Logger logger;
    private final Map<NamespacedKey, Entry> byKey = new LinkedHashMap<>();
    private final Map<Integer, Entry> byId = new LinkedHashMap<>();
    private int nextId = CUSTOM_MIN_ID;

    public IdMappingRegistry(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    public synchronized void load() {
        byKey.clear();
        byId.clear();
        nextId = CUSTOM_MIN_ID;

        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("mappings");
        if (section != null) {
            for (String strKey : section.getKeys(false)) {
                ConfigurationSection entrySec = section.getConfigurationSection(strKey);
                if (entrySec == null) continue;
                String namespace = entrySec.getString("namespace");
                String name = entrySec.getString("key");
                int id = entrySec.getInt("id");
                boolean retired = entrySec.getBoolean("retired", false);

                if (namespace != null && name != null && id >= CUSTOM_MIN_ID) {
                    NamespacedKey key = new NamespacedKey(namespace, name);
                    Entry entry = new Entry(key, id, retired);
                    byKey.put(key, entry);
                    byId.put(id, entry);
                    if (id >= nextId) {
                        nextId = id + 1;
                    }
                }
            }
        }
    }

    public synchronized void save() {
        try {
            YamlConfiguration config = new YamlConfiguration();
            ConfigurationSection sec = config.createSection("mappings");
            for (Entry entry : byKey.values()) {
                String entryKey = entry.key.getNamespace() + "_" + entry.key.getKey();
                ConfigurationSection item = sec.createSection(entryKey);
                item.set("namespace", entry.key.getNamespace());
                item.set("key", entry.key.getKey());
                item.set("id", entry.id);
                item.set("retired", entry.retired);
            }
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            logger.warning("Failed to save id_registry.yml: " + e.getMessage());
        }
    }

    public synchronized int getOrAssignId(NamespacedKey key) {
        Entry existing = byKey.get(key);
        if (existing != null) {
            if (existing.retired) {
                existing.retired = false;
                save();
            }
            return existing.id;
        }

        int id = nextId++;
        Entry entry = new Entry(key, id, false);
        byKey.put(key, entry);
        byId.put(id, entry);
        save();
        return id;
    }

    public synchronized Optional<NamespacedKey> getKey(int id) {
        Entry entry = byId.get(id);
        if (entry != null && !entry.retired) {
            return Optional.of(entry.key);
        }
        return Optional.empty();
    }

    public synchronized OptionalInt getId(NamespacedKey key) {
        Entry entry = byKey.get(key);
        if (entry != null && !entry.retired) {
            return OptionalInt.of(entry.id);
        }
        return OptionalInt.empty();
    }

    public synchronized void retire(NamespacedKey key) {
        Entry entry = byKey.get(key);
        if (entry != null && !entry.retired) {
            entry.retired = true;
            save();
        }
    }

    public synchronized boolean isRegistered(NamespacedKey key) {
        Entry entry = byKey.get(key);
        return entry != null && !entry.retired;
    }
}

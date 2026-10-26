package com.rounds.api.impl;

import com.rounds.api.data.CustomDataContainer;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CustomDataContainerImpl implements CustomDataContainer {

    private final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();
    private final Set<String> tags = ConcurrentHashMap.newKeySet();

    @Override
    public void set(String key, Object value) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        if (key.startsWith("rounds:")) {
            throw new SecurityException("Ключи с зарезервированным префиксом 'rounds:' запрещены: " + key);
        }
        data.put(key, value);
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        if (key == null || type == null) return Optional.empty();
        Object val = data.get(key);
        if (val != null && type.isInstance(val)) {
            return Optional.of(type.cast(val));
        }
        return Optional.empty();
    }

    @Override
    public int getInt(String key, int def) {
        if (key == null) return def;
        Object val = data.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    @Override
    public double getDouble(String key, double def) {
        if (key == null) return def;
        Object val = data.get(key);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        if (key == null) return def;
        Object val = data.get(key);
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return def;
    }

    @Override
    public String getString(String key, String def) {
        if (key == null) return def;
        Object val = data.get(key);
        return val != null ? String.valueOf(val) : def;
    }

    @Override
    public boolean hasTag(String tag) {
        return tag != null && tags.contains(tag);
    }

    @Override
    public void addTag(String tag) {
        if (tag != null) tags.add(tag);
    }

    @Override
    public void removeTag(String tag) {
        if (tag != null) tags.remove(tag);
    }

    @Override
    public void remove(String key) {
        if (key != null) data.remove(key);
    }

    public void clear() {
        data.clear();
        tags.clear();
    }
}

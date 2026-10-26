package com.rounds.api.data;

import java.util.Optional;

public interface CustomDataContainer {
    /**
     * Сохраняет значение в контейнере.
     * @throws SecurityException если key начинается с зарезервированного префикса "rounds:".
     * @throws NullPointerException если key == null или value == null.
     */
    void set(String key, Object value);

    <T> Optional<T> get(String key, Class<T> type);
    int getInt(String key, int def);
    double getDouble(String key, double def);
    boolean getBoolean(String key, boolean def);
    String getString(String key, String def);

    boolean hasTag(String tag);
    void addTag(String tag);
    void removeTag(String tag);

    void remove(String key);
}

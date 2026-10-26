package com.rounds.api;

import java.util.Objects;

public final class ApiVersion implements Comparable<ApiVersion> {
    private final int major;
    private final int minor;
    private final int patch;

    public ApiVersion(int major, int minor, int patch) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Компоненты версии не могут быть отрицательными: " 
                    + major + "." + minor + "." + patch);
        }
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /**
     * Проверяет совместимость текущей версии сервера с требуемой аддоном (target).
     * Правило:
     * 1. Мажорные версии обязаны совпадать строго.
     * 2. При несовпадении минорной версии: текущая должна быть строго выше целевой.
     * 3. При совпадении минорной версии: текущий патч должен быть не ниже целевого.
     */
    public boolean isCompatibleWith(ApiVersion target) {
        if (target == null) return false;
        if (this.major != target.major) return false;
        if (this.minor != target.minor) return this.minor > target.minor;
        return this.patch >= target.patch;
    }

    public int getMajor() { return major; }
    public int getMinor() { return minor; }
    public int getPatch() { return patch; }

    @Override
    public int compareTo(ApiVersion o) {
        Objects.requireNonNull(o, "ApiVersion для сравнения не может быть null");
        if (this.major != o.major) return Integer.compare(this.major, o.major);
        if (this.minor != o.minor) return Integer.compare(this.minor, o.minor);
        return Integer.compare(this.patch, o.patch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiVersion that)) return false;
        return major == that.major && minor == that.minor && patch == that.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}

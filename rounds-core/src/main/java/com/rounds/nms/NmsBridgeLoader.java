package com.rounds.nms;

/**
 * Locates the single {@link NmsBridge} implementation shipped inside the current artifact.
 * Each rounds-nms* module exposes exactly one class under this fixed name, so the core
 * loads it by name and degrades gracefully when the artifact targets a different server version.
 */
public final class NmsBridgeLoader {

    public static final String IMPL_CLASS_NAME = "com.rounds.nms.impl.NmsBridgeImpl";

    private NmsBridgeLoader() {
    }

    public static NmsBridge load() {
        try {
            Class<?> implClass = Class.forName(IMPL_CLASS_NAME);
            return (NmsBridge) implClass.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            return null;
        }
    }
}

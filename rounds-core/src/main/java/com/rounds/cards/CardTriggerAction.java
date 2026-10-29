package com.rounds.cards;

import java.util.Map;

/**
 * Immutable, compiled card trigger-action binding.
 * Parsed once at startup so runtime execution is a direct switch-dispatch.
 */
public record CardTriggerAction(
        CardTrigger trigger,
        CardActionType action,
        double value,
        String param
) {
    public static CardTriggerAction fromMap(Map<?, ?> map) {
        if (map == null) return null;
        Object onObj = map.get("on");
        Object actionObj = map.get("action");
        if (onObj == null || actionObj == null) return null;

        CardTrigger trigger;
        CardActionType action;
        try {
            trigger = CardTrigger.valueOf(String.valueOf(onObj).trim().toUpperCase());
            action = CardActionType.valueOf(String.valueOf(actionObj).trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        double val = 0.0;
        Object valObj = map.get("value");
        if (valObj instanceof Number n) {
            val = n.doubleValue();
        } else if (valObj != null) {
            try {
                val = Double.parseDouble(String.valueOf(valObj));
            } catch (NumberFormatException ignored) {}
        }

        String param = map.get("param") != null ? String.valueOf(map.get("param")).trim() : "";
        return new CardTriggerAction(trigger, action, val, param);
    }
}

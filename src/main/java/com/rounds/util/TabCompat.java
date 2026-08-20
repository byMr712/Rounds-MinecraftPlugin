package com.rounds.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public class TabCompat {

    private boolean active = false;
    private Object api;
    private Class<?> tabPlayerClass;
    private Method mGetTabListFormatManager;
    private Method mGetNameTagManager;
    private Method mGetPlayer;
    private Method mSetName;
    private Method mSetPrefix;
    private Method mHideNametag;
    private Method mShowNametag;

    public TabCompat() {
        try {
            if (Bukkit.getPluginManager().getPlugin("TAB") == null) return;
            Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) return;
            mGetTabListFormatManager = apiClass.getMethod("getTabListFormatManager");
            mGetNameTagManager = apiClass.getMethod("getNameTagManager");
            mGetPlayer = apiClass.getMethod("getPlayer", UUID.class);
            tabPlayerClass = mGetPlayer.getReturnType();
            Object tlf = mGetTabListFormatManager.invoke(api);
            Object ntm = mGetNameTagManager.invoke(api);
            if (tlf == null || ntm == null) return;
            mSetName = tlf.getClass().getMethod("setName", tabPlayerClass, String.class);
            mSetPrefix = ntm.getClass().getMethod("setPrefix", tabPlayerClass, String.class);
            mHideNametag = ntm.getClass().getMethod("hideNametag", tabPlayerClass);
            mShowNametag = ntm.getClass().getMethod("showNametag", tabPlayerClass);
            active = true;
        } catch (Exception ignored) {
            active = false;
        }
    }

    public boolean isActive() { return active; }

    private Object tabPlayer(Player player) {
        try {
            return mGetPlayer.invoke(api, player.getUniqueId());
        } catch (Exception e) {
            return null;
        }
    }

    public void setColoredName(Player player, ChatColor color) {
        if (!active) return;
        try {
            Object tp = tabPlayer(player);
            if (tp == null) return;
            Object tlf = mGetTabListFormatManager.invoke(api);
            if (tlf != null) mSetName.invoke(tlf, tp, color + player.getName());
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null) mSetPrefix.invoke(ntm, tp, color.toString());
        } catch (Exception ignored) {}
    }

    public void resetName(Player player) {
        if (!active) return;
        try {
            Object tp = tabPlayer(player);
            if (tp == null) return;
            Object tlf = mGetTabListFormatManager.invoke(api);
            if (tlf != null) mSetName.invoke(tlf, tp, player.getName());
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null) mSetPrefix.invoke(ntm, tp, "");
        } catch (Exception ignored) {}
    }

    public void hideNametag(Player player) {
        if (!active) return;
        try {
            Object tp = tabPlayer(player);
            if (tp == null) return;
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null) mHideNametag.invoke(ntm, tp);
        } catch (Exception ignored) {}
    }

    public void showNametag(Player player) {
        if (!active) return;
        try {
            Object tp = tabPlayer(player);
            if (tp == null) return;
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null) mShowNametag.invoke(ntm, tp);
        } catch (Exception ignored) {}
    }
}
package com.rounds.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public class TabCompat {

    private boolean active = false;
    private Object api;
    private Method mGetTabListFormatManager;
    private Method mGetNameTagManager;
    private Method mGetPlayer;
    private Method mSetName;
    private Method mSetPrefix;
    private Method mSetSuffix;
    private Method mHideNametag;
    private Method mShowNametag;
    private Method mHideNametagToViewer;
    private Method mShowNametagToViewer;

    public TabCompat() {
        try {
            if (Bukkit.getPluginManager().getPlugin("TAB") == null) return;
            Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) return;
            try { mGetTabListFormatManager = apiClass.getMethod("getTabListFormatManager"); mGetTabListFormatManager.setAccessible(true); } catch (Exception ignored) {}
            try { mGetNameTagManager = apiClass.getMethod("getNameTagManager"); mGetNameTagManager.setAccessible(true); } catch (Exception ignored) {}
            try { mGetPlayer = apiClass.getMethod("getPlayer", UUID.class); mGetPlayer.setAccessible(true); } catch (Exception ignored) {}

            if (mGetPlayer == null) return;
            Class<?> tabPlayerClass = mGetPlayer.getReturnType();

            Object tlf = null;
            if (mGetTabListFormatManager != null) {
                try { tlf = mGetTabListFormatManager.invoke(api); } catch (Exception ignored) {}
            }
            Object ntm = null;
            if (mGetNameTagManager != null) {
                try { ntm = mGetNameTagManager.invoke(api); } catch (Exception ignored) {}
            }

            if (tlf != null) {
                for (Method m : tlf.getClass().getMethods()) {
                    if (m.getName().equals("setName") && m.getParameterCount() == 2
                            && m.getParameterTypes()[0].isAssignableFrom(tabPlayerClass)
                            && m.getParameterTypes()[1] == String.class) {
                        mSetName = m;
                        mSetName.setAccessible(true);
                        break;
                    }
                }
            }

            if (ntm != null) {
                for (Method m : ntm.getClass().getMethods()) {
                    if (m.getName().equals("setPrefix") && m.getParameterCount() == 2
                            && m.getParameterTypes()[0].isAssignableFrom(tabPlayerClass)
                            && m.getParameterTypes()[1] == String.class) {
                        mSetPrefix = m;
                        mSetPrefix.setAccessible(true);
                    } else if (m.getName().equals("setSuffix") && m.getParameterCount() == 2
                            && m.getParameterTypes()[0].isAssignableFrom(tabPlayerClass)
                            && m.getParameterTypes()[1] == String.class) {
                        mSetSuffix = m;
                        mSetSuffix.setAccessible(true);
                    } else if (m.getName().equals("hideNameTag") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isAssignableFrom(tabPlayerClass)) {
                        mHideNametag = m;
                        mHideNametag.setAccessible(true);
                    } else if (m.getName().equals("showNameTag") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isAssignableFrom(tabPlayerClass)) {
                        mShowNametag = m;
                        mShowNametag.setAccessible(true);
                    } else if (m.getName().equals("hideNameTag") && m.getParameterCount() == 2
                            && m.getParameterTypes()[0].isAssignableFrom(tabPlayerClass)
                            && m.getParameterTypes()[1].isAssignableFrom(tabPlayerClass)) {
                        mHideNametagToViewer = m;
                        mHideNametagToViewer.setAccessible(true);
                    } else if (m.getName().equals("showNameTag") && m.getParameterCount() == 2
                            && m.getParameterTypes()[0].isAssignableFrom(tabPlayerClass)
                            && m.getParameterTypes()[1].isAssignableFrom(tabPlayerClass)) {
                        mShowNametagToViewer = m;
                        mShowNametagToViewer.setAccessible(true);
                    }
                }
            }

            active = true;
            Bukkit.getLogger().info("[Rounds] TAB integration active (NameTagManager=" + (ntm != null) + ", TabList=" + (tlf != null) + ")");
        } catch (Exception e) {
            active = false;
            Bukkit.getLogger().warning("[Rounds] TAB detected but integration failed: " + e);
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

    public void setColoredName(Player player, ChatColor color, String suffix) {
        if (!active) return;
        try {
            Object tp = tabPlayer(player);
            if (tp == null) return;
            Object tlf = mGetTabListFormatManager.invoke(api);
            if (tlf != null && mSetName != null) mSetName.invoke(tlf, tp, color + player.getName());
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null) {
                if (mSetPrefix != null) mSetPrefix.invoke(ntm, tp, color.toString());
                if (mSetSuffix != null) mSetSuffix.invoke(ntm, tp, suffix == null ? null : suffix);
            }
        } catch (Exception ignored) {}
    }

    public void resetName(Player player) {
        if (!active) return;
        try {
            Object tp = tabPlayer(player);
            if (tp == null) return;
            Object tlf = mGetTabListFormatManager.invoke(api);
            if (tlf != null && mSetName != null) mSetName.invoke(tlf, tp, player.getName());
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null) {
                if (mSetPrefix != null) mSetPrefix.invoke(ntm, tp, null);
                if (mSetSuffix != null) mSetSuffix.invoke(ntm, tp, null);
            }
        } catch (Exception ignored) {}
    }

    public void hideNametag(Player player) {
        if (!active) return;
        try {
            Object tp = tabPlayer(player);
            if (tp == null) return;
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null && mHideNametag != null) mHideNametag.invoke(ntm, tp);
        } catch (Exception ignored) {}
    }

    public void showNametag(Player player) {
        if (!active) return;
        try {
            Object tp = tabPlayer(player);
            if (tp == null) return;
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null && mShowNametag != null) mShowNametag.invoke(ntm, tp);
        } catch (Exception ignored) {}
    }

    public void hideNametagFrom(Player target, Player viewer) {
        if (!active) return;
        try {
            Object tpTarget = tabPlayer(target);
            Object tpViewer = tabPlayer(viewer);
            if (tpTarget == null || tpViewer == null) return;
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null && mHideNametagToViewer != null) {
                mHideNametagToViewer.invoke(ntm, tpTarget, tpViewer);
            }
        } catch (Exception ignored) {}
    }

    public void showNametagTo(Player target, Player viewer) {
        if (!active) return;
        try {
            Object tpTarget = tabPlayer(target);
            Object tpViewer = tabPlayer(viewer);
            if (tpTarget == null || tpViewer == null) return;
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null && mShowNametagToViewer != null) {
                mShowNametagToViewer.invoke(ntm, tpTarget, tpViewer);
            }
        } catch (Exception ignored) {}
    }

    public void resetAllNametagVisibilities() {
        if (!active) return;
        try {
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm == null) return;
            for (Player target : Bukkit.getOnlinePlayers()) {
                Object tpTarget = tabPlayer(target);
                if (tpTarget == null) continue;
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (target.equals(viewer)) continue;
                    Object tpViewer = tabPlayer(viewer);
                    if (tpViewer == null) continue;
                    if (mShowNametagToViewer != null) {
                        mShowNametagToViewer.invoke(ntm, tpTarget, tpViewer);
                    }
                }
                if (mShowNametag != null) {
                    mShowNametag.invoke(ntm, tpTarget);
                }
            }
        } catch (Exception ignored) {}
    }
}

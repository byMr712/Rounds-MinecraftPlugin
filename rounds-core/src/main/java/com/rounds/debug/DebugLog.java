package com.rounds.debug;

import com.rounds.RoundsPlugin;
import com.rounds.bot.BotManager;
import com.rounds.bot.FakeBot;
import com.rounds.cards.Card;
import com.rounds.cards.CardManager;
import com.rounds.cards.CardRegistry;
import com.rounds.game.GameManager;
import com.rounds.entity.RoundsEntities;
import com.rounds.player.PlayerData;
import com.rounds.player.PlayerDataManager;
import com.rounds.teams.TeamManager;
import com.rounds.teams.TeamManager.GameTeam;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class DebugLog {
    private static final int MAX_BUF = 4096;
    private static final ArrayDeque<String> BUF = new ArrayDeque<>(MAX_BUF);
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat FILE_FMT = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);

    private DebugLog() {}

    public static void log(String cat, String msg) {
        if (!DebugSession.active) {
            return;
        }
        GameManager gm = getGameManager();
        int round = gm == null ? 0 : (int) gm.getCurrentRound();
        String line = "[" + TIME_FMT.format(new Date()) + "][R#" + round + "][" + cat + "] " + msg;
        synchronized (BUF) {
            if (BUF.size() >= MAX_BUF) {
                BUF.pollFirst();
            }
            BUF.addLast(line);
        }
        append(line);
    }

    public static void statusInfo() {
        StringBuilder sb = new StringBuilder();
        synchronized (BUF) {
            int count = 0;
            for (String s : BUF) {
                if (count++ >= 5) break;
                sb.append(s).append(" | ");
            }
        }
        // caller will print
    }

    public static List<String> getRecent(int max) {
        List<String> list = new ArrayList<>();
        synchronized (BUF) {
            int take = Math.min(max, BUF.size());
            int skipped = BUF.size() - take;
            int idx = 0;
            for (String s : BUF) {
                if (idx++ >= skipped) {
                    list.add(s);
                }
            }
        }
        return list;
    }

    public static int bufferSize() {
        synchronized (BUF) {
            return BUF.size();
        }
    }

    public static void initSession() {
        try {
            RoundsPlugin plugin = RoundsPlugin.getInstance();
            if (plugin == null) return;
            File dir = new File(plugin.getDataFolder(), "debug");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String fromLabel = DebugSession.startAt == -1 ? "NOW" : String.valueOf(DebugSession.startAt);
            String name = "debug-" + FILE_FMT.format(new Date()) + "-from-R" + fromLabel + ".log";
            DebugSession.logFile = new File(dir, name);
            DebugSession.writer = new BufferedWriter(new FileWriter(DebugSession.logFile, true));
            DebugSession.sessionStartMs = System.currentTimeMillis();
            DebugSession.lastLoggedRound = -1;
            DebugSession.stopAt = DebugSession.stopAt;
            writeHeader();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void writeHeader() {
        append("==================================================");
        append("[ROUNDS DEBUG SESSION]");
        append("Started: " + new Date());
        try {
            append("Server: " + Bukkit.getServer().getName() + " " + Bukkit.getServer().getVersion());
        } catch (Exception ignored) {}
        try {
            PluginDescriptionFile desc = RoundsPlugin.getInstance().getDescription();
            append("Plugin: " + desc.getFullName());
        } catch (Exception ignored) {}
        append("Mode: " + (DebugSession.startAt == -1 ? "IMMEDIATE" : "SCHEDULED"));
        append("StartAt: " + (DebugSession.startAt == -1 ? "NOW" : "R#" + DebugSession.startAt));
        append("StopAt:  " + (DebugSession.stopAt == -1 ? "NONE (use /rdebug debug stop to end)" : "END OF R#" + DebugSession.stopAt));
        append("==================================================");
        append("");
        flush();
    }

    public static void flushRoundEnd(int round) {
        if (!DebugSession.active || DebugSession.writer == null) {
            return;
        }
        if (DebugSession.lastLoggedRound == round) {
            return;
        }
        DebugSession.lastLoggedRound = round;
        try {
            append("--------------------------------------------------");
            append("[ROUND BLOCK] END OF ROUND #" + round + " | " + new Date());
            append("--------------------------------------------------");
            writeSnapshot("SNAP");
            writeCards("CARDS");
            writeStats("STATS");
            append("--------------------------------------------------");
            append("");
            flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void flushFinal(String reason) {
        if (DebugSession.writer == null) {
            return;
        }
        try {
            append("==================================================");
            append("[DEBUG SESSION END]");
            append("Reason: " + reason);
            append("Stopped at: " + new Date());
            long elapsed = System.currentTimeMillis() - DebugSession.sessionStartMs;
            append("Total active time: " + (elapsed / 1000) + "s");
            append("Last logged round: R#" + DebugSession.lastLoggedRound);
            if (DebugSession.logFile != null) {
                append("File: " + DebugSession.logFile.getAbsolutePath());
            }
            append("==================================================");
            flush();
        } catch (Exception ignored) {
        }
    }

    private static synchronized void append(String s) {
        BufferedWriter w = DebugSession.writer;
        if (w == null) {
            return;
        }
        try {
            w.write(s);
            w.newLine();
        } catch (Exception ignored) {
        }
    }

    private static void flush() {
        BufferedWriter w = DebugSession.writer;
        if (w == null) {
            return;
        }
        try {
            w.flush();
        } catch (Exception ignored) {
        }
    }

    private static GameManager getGameManager() {
        try {
            return RoundsPlugin.getInstance().getGameManager();
        } catch (Exception e) {
            return null;
        }
    }

    private static PlayerDataManager getPdm() {
        try {
            return RoundsPlugin.getInstance().getPlayerDataManager();
        } catch (Exception e) {
            return null;
        }
    }

    private static BotManager getBm() {
        try {
            return RoundsPlugin.getInstance().getBotManager();
        } catch (Exception e) {
            return null;
        }
    }

    private static CardManager getCm() {
        try {
            return RoundsPlugin.getInstance().getCardManager();
        } catch (Exception e) {
            return null;
        }
    }

    private static TeamManager getTm() {
        try {
            return RoundsPlugin.getInstance().getTeamManager();
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeSnapshot(String cat) {
        GameManager gm = getGameManager();
        PlayerDataManager pdm = getPdm();
        BotManager bm = getBm();
        CardManager cm = getCm();
        Object re = null;

        int bullets = 0;
        try {
            bullets = RoundsEntities.getBulletCount();
        } catch (Throwable ignored) {
            bullets = 0;
        }
        int activeBots = 0;
        if (bm != null) {
            try {
                activeBots = bm.getBots().size();
            } catch (Exception ignored) {
                activeBots = 0;
            }
        }
        int pdCache = 0;
        int pdSaved = 0;
        if (pdm != null) {
            try {
                java.lang.reflect.Field f = pdm.getClass().getDeclaredField("cache");
                f.setAccessible(true);
                Object o = f.get(pdm);
                if (o instanceof java.util.Map<?, ?> m) pdCache = m.size();
            } catch (Throwable ignored) {
            }
            try {
                java.lang.reflect.Field f = pdm.getClass().getDeclaredField("savedData");
                f.setAccessible(true);
                Object o = f.get(pdm);
                if (o instanceof java.util.Map<?, ?> m) pdSaved = m.size();
            } catch (Throwable ignored) {
            }
        }
        int pending = 0;
        int choosing = 0;
        if (cm != null) {
            try {
                pending = cm.getPendingCardsCount();
            } catch (Exception ignored) {
                pending = 0;
            }
            try {
                choosing = cm.getChoosingCount();
            } catch (Exception ignored) {
                choosing = 0;
            }
        }

        String line = "[" + TIME_FMT.format(new Date()) + "][R#" + (gm == null ? 0 : (int) gm.getCurrentRound()) + "][" + cat + "] " +
                "bullets=" + bullets + " | activeBots=" + activeBots +
                " | pd.cache=" + pdCache + " pd.savedData=" + pdSaved +
                " | cm.pendingCards=" + pending + " cm.choosing=" + choosing;
        append(line);

        String line2 = "[" + TIME_FMT.format(new Date()) + "][R#" + (gm == null ? 0 : (int) gm.getCurrentRound()) + "][" + cat + "-MAPS] " +
                "PlayerDataManager.cache=" + pdCache + " | PlayerDataManager.savedData=" + pdSaved +
                " | BotManager.active=" + activeBots +
                " | RoundsEntities.bullets=" + bullets +
                " | CardManager.pendingCards=" + pending + " CardManager.choosing=" + choosing;
        append(line2);
    }

    private static void writeCards(String cat) {
        GameManager gm = getGameManager();
        PlayerDataManager pdm = getPdm();
        CardManager cm = getCm();
        TeamManager tm = getTm();
        BotManager bm = getBm();
        if (gm == null || pdm == null) {
            append("[" + TIME_FMT.format(new Date()) + "][R#" + 0 + "][" + cat + "] (no data)");
            return;
        }
        int round = (int) gm.getCurrentRound();
        append("[" + TIME_FMT.format(new Date()) + "][R#" + round + "][" + cat + "] APPLY");

        // players
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                PlayerData data = pdm.getData(p);
                List<Integer> owned = data == null ? null : data.getOwnedCards();
                GameTeam team = tm == null ? null : tm.getPlayerTeam(p.getUniqueId());
                String teamStr = team == null ? "NONE" : team.name();
                String prefix = teamStr + "  " + p.getName();
                FakeBot bot = bm == null ? null : bm.getBot(p.getName());
                if (bot != null) {
                    prefix = teamStr + "  " + p.getName() + " [BOT][L" + bot.getLevel() + "]";
                }
                if (owned == null || owned.isEmpty()) {
                    append("[" + TIME_FMT.format(new Date()) + "][R#" + round + "][" + cat + "] " + prefix + " -> (no cards applied)");
                    continue;
                }
                StringBuilder cardsStr = new StringBuilder();
                for (int id : owned) {
                    Card card = cm == null ? null : cm.getRegistry().getCard(id);
                    if (card == null) continue;
                    String rarity = card.getRarity() == null ? "UNKNOWN" : card.getRarity().name();
                    cardsStr.append("[").append(rarity).append("] ").append(card.getId()).append(", ");
                }
                if (cardsStr.length() == 0) {
                    append("[" + TIME_FMT.format(new Date()) + "][R#" + round + "][" + cat + "] " + prefix + " -> (no cards applied)");
                } else {
                    append("[" + TIME_FMT.format(new Date()) + "][R#" + round + "][" + cat + "] " + prefix + " -> " + cardsStr.substring(0, cardsStr.length() - 2));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void writeStats(String cat) {
        GameManager gm = getGameManager();
        PlayerDataManager pdm = getPdm();
        TeamManager tm = getTm();
        BotManager bm = getBm();
        if (gm == null || pdm == null) {
            append("[" + TIME_FMT.format(new Date()) + "][R#" + 0 + "][" + cat + "] (no data)");
            return;
        }
        int round = (int) gm.getCurrentRound();
        append("[" + TIME_FMT.format(new Date()) + "][R#" + round + "][" + cat + "] SNAP AFTER-CARDS");
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                PlayerData data = pdm.getData(p);
                GameTeam team = tm == null ? null : tm.getPlayerTeam(p.getUniqueId());
                String teamStr = team == null ? "NONE" : team.name();
                String name = p.getName();
                FakeBot bot = bm == null ? null : bm.getBot(name);
                if (bot != null) {
                    name = p.getName() + " [BOT][L" + bot.getLevel() + "]";
                }
                double hp = p.getHealth();
                double maxHp = 20.0;
                try {
                    if (p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null) {
                        maxHp = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                    }
                } catch (Exception ignored) {}
                double dmg = 1.0 + (data == null ? 0.0 : data.dmg);
                double atkSpeed = 1.0 + (data == null ? 0.0 : data.atkSpeed);
                int ammo = data == null ? 0 : (int) Math.round(data.ammo);
                double move = 1.0;
                double jump = 1.0 + (data == null ? 0.0 : data.jumpHeight);
                double blockCd = 0.0;
                try {
                    blockCd = com.rounds.item.GunItem.getShieldDurationSeconds(RoundsPlugin.getInstance(), data) / 1000.0;
                } catch (Exception ignored) {}
                String cardsList = "[]";
                if (data != null && data.getOwnedCards() != null && !data.getOwnedCards().isEmpty()) {
                    StringBuilder sb = new StringBuilder("[");
                    CardManager cm = getCm();
                    for (int id : data.getOwnedCards()) {
                        Card card = cm == null ? null : cm.getRegistry().getCard(id);
                        if (card == null) continue;
                        String rarity = card.getRarity() == null ? "UNKNOWN" : card.getRarity().name();
                        sb.append(card.getId()).append("(").append(rarity).append("),");
                    }
                    if (sb.length() > 1) {
                        sb.setLength(sb.length() - 1);
                    }
                    sb.append("]");
                    cardsList = sb.toString();
                }
                String line = "[" + TIME_FMT.format(new Date()) + "][R#" + round + "][" + cat + "] " +
                        name + " [" + teamStr + "] | HP:" + String.format(Locale.US, "%.1f", hp) +
                        " MAX_HP:" + String.format(Locale.US, "%.1f", maxHp) +
                        " | DMG:" + String.format(Locale.US, "%.2f", dmg) + "x" +
                        " | ATKSPEED:" + String.format(Locale.US, "%.2f", atkSpeed) + "x" +
                        " | AMMO:" + ammo +
                        " | MOVE:" + String.format(Locale.US, "%.2f", move) + "x" +
                        " | JUMP:" + String.format(Locale.US, "%.2f", jump) + "x" +
                        " | BLOCK_CD:" + String.format(Locale.US, "%.1f", blockCd) + "s" +
                        " | CARDS:" + cardsList;
                append(line);
            } catch (Exception ignored) {
            }
        }
    }
}

package com.rounds.debug;

import java.io.BufferedWriter;
import java.io.File;

public final class DebugSession {
    private DebugSession() {}

    public static volatile boolean active = false;
    public static volatile int startAt = -1;
    public static volatile int stopAt = -1;
    public static volatile boolean triggered = false;

    public static volatile File logFile = null;
    public static volatile BufferedWriter writer = null;
    public static volatile long sessionStartMs = 0L;
    public static volatile int lastLoggedRound = -1;
    public static volatile int currentRoundSnapshot = 0;

    public static void clear() {
        active = false;
        startAt = -1;
        stopAt = -1;
        triggered = false;
        lastLoggedRound = -1;
        currentRoundSnapshot = 0;
        closeWriter();
        logFile = null;
        sessionStartMs = 0L;
    }

    public static void closeWriter() {
        BufferedWriter w = writer;
        if (w != null) {
            try {
                w.flush();
                w.close();
            } catch (Exception ignored) {
            }
            writer = null;
        }
    }

    public static void scheduleStop(int round) {
        if (!active) {
            return;
        }
        stopAt = round;
    }
}

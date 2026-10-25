package com.rounds.bot;

/**
 * Precomputed, immutable difficulty and combat archetype profiles.
 * Replaces per-tick floating point calculations and table lookups with O(1) constant accesses.
 */
public class CombatProfile {

    public final float aimInaccuracy;
    public final float turnSpeed;
    public final int minShootCooldown;
    public final double strafeSpeed;
    public final int jukeThreshold;
    public final double maxShootDist;
    public final double smartChance;
    public final double pursuitChance;
    public final double jumpErrorChance;
    public final double landingErrorChance;
    public final double walkSpeed;
    public final double pursuitSpeed;
    public final double tacticalJumpChance;
    public final double rushThreshold;
    public final double retreatThreshold;

    private static final CombatProfile[][] PROFILES = new CombatProfile[11][4];

    static {
        for (int lvl = 1; lvl <= 10; lvl++) {
            double factor = lvl / 10.0;
            float aimInaccuracy = (float) (1.2f / factor);
            float turnSpeed = (float) (6.0f + 8.0f * factor);
            int minShootCooldown = 4 + (int) ((1.0 - factor) * 14.0);
            double strafeSpeed = 0.12 + 0.04 * factor;
            double maxShootDist = 10.0 + 30.0 * factor;
            double smartChance = (lvl <= 2) ? 0.50 : ((lvl <= 4) ? 0.60 : ((lvl <= 7) ? 0.70 : ((lvl <= 9) ? 0.80 : 0.90)));
            double pursuitChance = Math.min(1.0, (smartChance * 1.20) * 1.15);
            double jumpErrorChance = (lvl <= 3) ? 0.30 : ((lvl <= 7) ? 0.20 : ((lvl <= 9) ? 0.10 : 0.0));
            double landingErrorChance = (lvl <= 3) ? 0.10 : ((lvl <= 7) ? 0.07 : ((lvl <= 9) ? 0.05 : 0.01));
            double walkSpeed = 0.17 + 0.03 * factor;
            double pursuitSpeed = 0.22 + 0.04 * factor;

            for (BotAI.BotPlaystyle ps : BotAI.BotPlaystyle.values()) {
                int juke = switch (ps) {
                    case ERRATIC_BRAWLER -> (int) (26 + (1.0 - factor) * 20.0);
                    case AGGRESSIVE_FLANKER -> (int) (34 + (1.0 - factor) * 24.0);
                    case DEFENSIVE_TURTLE -> (int) (42 + (1.0 - factor) * 30.0);
                    case TACTICAL_SNIPER -> (int) (50 + (1.0 - factor) * 35.0);
                };
                double jumpChance = switch (ps) {
                    case ERRATIC_BRAWLER -> 0.025 * factor;
                    case AGGRESSIVE_FLANKER -> 0.015 * factor;
                    default -> 0.008 * factor;
                };
                double rushThresh = switch (ps) {
                    case AGGRESSIVE_FLANKER -> 9.0;
                    case TACTICAL_SNIPER -> 24.0;
                    case DEFENSIVE_TURTLE -> 15.0;
                    case ERRATIC_BRAWLER -> 11.0;
                };
                double retreatThresh = switch (ps) {
                    case AGGRESSIVE_FLANKER -> 4.0;
                    case TACTICAL_SNIPER -> 10.0;
                    case DEFENSIVE_TURTLE -> 7.0;
                    case ERRATIC_BRAWLER -> 4.5;
                };

                PROFILES[lvl][ps.ordinal()] = new CombatProfile(
                        aimInaccuracy, turnSpeed, minShootCooldown, strafeSpeed, juke,
                        maxShootDist, smartChance, pursuitChance, jumpErrorChance, landingErrorChance,
                        walkSpeed, pursuitSpeed, jumpChance, rushThresh, retreatThresh
                );
            }
        }
    }

    private CombatProfile(float aimInaccuracy, float turnSpeed, int minShootCooldown, double strafeSpeed,
                          int jukeThreshold, double maxShootDist, double smartChance, double pursuitChance,
                          double jumpErrorChance, double landingErrorChance, double walkSpeed, double pursuitSpeed,
                          double tacticalJumpChance, double rushThreshold, double retreatThreshold) {
        this.aimInaccuracy = aimInaccuracy;
        this.turnSpeed = turnSpeed;
        this.minShootCooldown = minShootCooldown;
        this.strafeSpeed = strafeSpeed;
        this.jukeThreshold = jukeThreshold;
        this.maxShootDist = maxShootDist;
        this.smartChance = smartChance;
        this.pursuitChance = pursuitChance;
        this.jumpErrorChance = jumpErrorChance;
        this.landingErrorChance = landingErrorChance;
        this.walkSpeed = walkSpeed;
        this.pursuitSpeed = pursuitSpeed;
        this.tacticalJumpChance = tacticalJumpChance;
        this.rushThreshold = rushThreshold;
        this.retreatThreshold = retreatThreshold;
    }

    public static CombatProfile get(int level, BotAI.BotPlaystyle playstyle) {
        int l = Math.max(1, Math.min(10, level));
        int p = (playstyle != null) ? playstyle.ordinal() : 0;
        return PROFILES[l][p];
    }
}

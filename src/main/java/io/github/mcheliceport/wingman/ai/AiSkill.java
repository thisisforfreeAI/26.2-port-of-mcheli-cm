package io.github.mcheliceport.wingman.ai;

/**
 * M10d: how well AI pilots fly, from the server config's {@code ai.difficulty}. A pilot looks
 * further ahead for terrain, keeps less clearance over it and reacts to a missile sooner the
 * better it is; only Normal and Veteran pilots break away from a missile, and only Veteran
 * pilots fly attack runs low, 20 to 40 blocks over the ground. Better pilots also lead a target
 * more fully, lay a fixed gun further off the nose and hold their fire for a tighter solution.
 *
 * @param lookahead      seconds ahead along its flight path a pilot checks the terrain
 * @param clearance      blocks a pilot keeps over the highest terrain it sees
 * @param reactionTicks  ticks from a missile homing on it to its countermeasures
 * @param breakTicks     ticks a pilot turns away from a missile; 0 for none
 * @param leadFactor     how much of a target's velocity a pilot leads it by
 * @param predictsTurns  the pilot leads a turning target along its turn, not only its heading
 * @param gimbal         degrees off the nose a pilot lays a fixed gun or rocket
 * @param coneFactor     how loosely a pilot fires: times the angle the target fills
 */
public enum AiSkill {
    EASY(new int[] {2}, 25.0, 30, 0, 0.6, false, 1.0F, 1.8),
    NORMAL(new int[] {2, 4}, 18.0, 12, 40, 1.0, false, 2.0F, 1.3),
    VETERAN(new int[] {2, 4, 8}, 12.0, 4, 60, 1.0, true, 3.0F, 1.0);

    /** A Veteran's attack runs, in blocks over the ground under the target. */
    public static final double LOW_MIN = 20.0;
    public static final double LOW_MAX = 40.0;

    private final int[] lookahead;
    private final double clearance;
    private final int reactionTicks;
    private final int breakTicks;
    private final double leadFactor;
    private final boolean predictsTurns;
    private final float gimbal;
    private final double coneFactor;

    AiSkill(int[] lookahead, double clearance, int reactionTicks, int breakTicks, double leadFactor,
            boolean predictsTurns, float gimbal, double coneFactor) {
        this.lookahead = lookahead;
        this.clearance = clearance;
        this.reactionTicks = reactionTicks;
        this.breakTicks = breakTicks;
        this.leadFactor = leadFactor;
        this.predictsTurns = predictsTurns;
        this.gimbal = gimbal;
        this.coneFactor = coneFactor;
    }

    public int[] lookahead() {
        return lookahead.clone();
    }

    public double clearance() {
        return clearance;
    }

    public int reactionTicks() {
        return reactionTicks;
    }

    public int breakTicks() {
        return breakTicks;
    }

    public double leadFactor() {
        return leadFactor;
    }

    public boolean predictsTurns() {
        return predictsTurns;
    }

    public float gimbal() {
        return gimbal;
    }

    public double coneFactor() {
        return coneFactor;
    }

    public boolean fliesLow() {
        return this == VETERAN;
    }
}

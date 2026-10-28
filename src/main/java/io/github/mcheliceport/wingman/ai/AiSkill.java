package io.github.mcheliceport.wingman.ai;

/**
 * M10d: how well AI pilots fly, from the server config's {@code ai.difficulty}. A pilot looks
 * further ahead for terrain, keeps less clearance over it and reacts to a missile sooner the
 * better it is; only Normal and Veteran pilots break away from a missile, and only Veteran
 * pilots fly attack runs low, 20 to 40 blocks over the ground.
 *
 * @param lookahead      seconds ahead along its flight path a pilot checks the terrain
 * @param clearance      blocks a pilot keeps over the highest terrain it sees
 * @param reactionTicks  ticks from a missile homing on it to its countermeasures
 * @param breakTicks     ticks a pilot turns away from a missile; 0 for none
 */
public enum AiSkill {
    EASY(new int[] {2}, 25.0, 30, 0),
    NORMAL(new int[] {2, 4}, 18.0, 12, 40),
    VETERAN(new int[] {2, 4, 8}, 12.0, 4, 60);

    /** A Veteran's attack runs, in blocks over the ground under the target. */
    public static final double LOW_MIN = 20.0;
    public static final double LOW_MAX = 40.0;

    private final int[] lookahead;
    private final double clearance;
    private final int reactionTicks;
    private final int breakTicks;

    AiSkill(int[] lookahead, double clearance, int reactionTicks, int breakTicks) {
        this.lookahead = lookahead;
        this.clearance = clearance;
        this.reactionTicks = reactionTicks;
        this.breakTicks = breakTicks;
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

    public boolean fliesLow() {
        return this == VETERAN;
    }
}

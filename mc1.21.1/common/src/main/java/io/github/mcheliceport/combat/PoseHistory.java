package io.github.mcheliceport.combat;

/**
 * A vehicle's recent positions, one per server tick, so a hit test can rewind the vehicle to where
 * a lagging shooter saw it (M9 lag compensation). Plain values so the tests need no world.
 */
public final class PoseHistory {

    /** One second at 20 ticks per second, plus the current tick. */
    public static final int SIZE = 21;

    private final long[] ticks = new long[SIZE];
    private final double[] xs = new double[SIZE];
    private final double[] ys = new double[SIZE];
    private final double[] zs = new double[SIZE];
    private int head = -1;
    private int count;

    /** Records the position of a tick; a second record in the same tick replaces the first. */
    public void record(long tick, double x, double y, double z) {
        if (this.count > 0 && this.ticks[this.head] == tick) {
            this.xs[this.head] = x;
            this.ys[this.head] = y;
            this.zs[this.head] = z;
            return;
        }
        if (this.count > 0 && tick < this.ticks[this.head]) {
            // The level's clock went back (a /time command does not do this, a world reload can): start over.
            this.count = 0;
        }
        this.head = (this.head + 1) % SIZE;
        this.ticks[this.head] = tick;
        this.xs[this.head] = x;
        this.ys[this.head] = y;
        this.zs[this.head] = z;
        this.count = Math.min(this.count + 1, SIZE);
    }

    public int size() {
        return this.count;
    }

    /**
     * Where the vehicle was at {@code tick}, linearly between the recorded ticks, clamped to the
     * oldest and newest records. False when nothing is recorded.
     */
    public boolean positionAt(double tick, double[] out) {
        if (this.count == 0) {
            return false;
        }
        int newer = this.head;
        if (tick >= this.ticks[newer]) {
            this.copy(newer, out);
            return true;
        }
        for (int i = 1; i < this.count; i++) {
            int older = Math.floorMod(this.head - i, SIZE);
            if (tick >= this.ticks[older]) {
                double span = this.ticks[newer] - this.ticks[older];
                double t = span > 0.0 ? (tick - this.ticks[older]) / span : 0.0;
                out[0] = this.xs[older] + (this.xs[newer] - this.xs[older]) * t;
                out[1] = this.ys[older] + (this.ys[newer] - this.ys[older]) * t;
                out[2] = this.zs[older] + (this.zs[newer] - this.zs[older]) * t;
                return true;
            }
            newer = older;
        }
        this.copy(newer, out);
        return true;
    }

    /**
     * How far to move the vehicle's current boxes to place them where it was {@code ticksBack}
     * ticks before {@code now}: the recorded position then minus {@code current}. Zero when the
     * history is empty or no rewind is asked.
     */
    public void displacement(long now, double ticksBack, double cx, double cy, double cz, double[] out) {
        out[0] = 0.0;
        out[1] = 0.0;
        out[2] = 0.0;
        if (ticksBack <= 0.0 || !this.positionAt(now - ticksBack, out)) {
            out[0] = 0.0;
            out[1] = 0.0;
            out[2] = 0.0;
            return;
        }
        out[0] -= cx;
        out[1] -= cy;
        out[2] -= cz;
    }

    private void copy(int index, double[] out) {
        out[0] = this.xs[index];
        out[1] = this.ys[index];
        out[2] = this.zs[index];
    }
}

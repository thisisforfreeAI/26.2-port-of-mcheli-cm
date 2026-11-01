package io.github.mcheliceport.entity.plane;

/** Per-plane animation counters from MCP_RenderPlane#renderExhaustFlame (one step per extraction). */
public final class PlaneExhaustAnimation {
    private int[] frames = new int[0];
    private int[] ticks = new int[0];

    public void reset(int count) {
        this.frames = new int[count];
        this.ticks = new int[count];
    }

    public int advance(int index, int count, int delay, int frameCount) {
        if (this.frames.length != count) {
            this.reset(count);
        }
        int frame = this.frames[index];
        int tick = this.ticks[index] + 1;
        if (tick >= Math.max(1, delay)) {
            tick = 0;
            frame++;
            if (frame >= frameCount) {
                frame = 0;
            }
        }
        // A resource reload can reduce the number of frames.
        if (frame >= frameCount) {
            frame = 0;
        }
        this.frames[index] = frame;
        this.ticks[index] = tick;
        return frame;
    }

    public static float steer(float current, boolean left, boolean right, float seconds) {
        float target = left == right ? 0.0F : left ? -1.0F : 1.0F;
        float follow = Math.min(1.0F, 2.0F * seconds);
        return Math.clamp((float) (current + (target - current) * follow), -1.0F, 1.0F);
    }
}

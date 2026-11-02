package io.github.mcheliceport.client.particle;

/** Slow hysteresis on complete frame intervals, independent of rendering APIs and game state. */
public final class EffectFrameBudget {
    private int pressure;
    private double warmup;
    private double elapsed;
    private double totalMillis;
    private int samples;
    private double healthySeconds;

    public int pressure() {
        return this.pressure;
    }

    public void reset() {
        this.pressure = 0;
        this.warmup = 0;
        this.clearWindow();
        this.healthySeconds = 0;
    }

    private void clearWindow() {
        this.elapsed = 0;
        this.totalMillis = 0;
        this.samples = 0;
    }

    public void sample(double millis, int targetFps) {
        // Loading, debugger stops and resource reloads must not lower detail.
        if (!Double.isFinite(millis) || millis <= 0 || millis > 250 || targetFps <= 0) {
            this.clearWindow();
            this.healthySeconds = 0;
            return;
        }
        double seconds = millis / 1000;
        if (this.warmup < 2) {
            this.warmup += seconds;
            return;
        }
        this.elapsed += seconds;
        this.totalMillis += millis;
        this.samples++;
        if (this.elapsed < 0.75) return;
        double mean = this.totalMillis / this.samples;
        double target = 1000.0 / targetFps;
        if (mean > target * 1.08) {
            this.pressure = Math.min(3, this.pressure + 1);
            this.healthySeconds = 0;
        } else if (mean <= target * 1.02) {
            this.healthySeconds += this.elapsed;
            if (this.healthySeconds >= 5) {
                this.pressure = Math.max(0, this.pressure - 1);
                this.healthySeconds = 0;
            }
        } else {
            this.healthySeconds = 0;
        }
        this.clearWindow();
    }

    /** A user's lower FPS cap (including VSync) is a valid target, not evidence of overload. */
    public static int effectiveTarget(int requested, int cap, boolean vsync, int refreshRate) {
        int target = Math.min(requested, Math.max(1, cap));
        return vsync && refreshRate > 0 ? Math.min(target, refreshRate) : target;
    }
}

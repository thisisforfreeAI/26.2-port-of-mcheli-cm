package io.github.mcheliceport.client.render;

/** Bounded tracer exposure/readability; no changes to rounds, ballistics or weapon definitions. */
public final class TracerLook {
    private TracerLook() {}

    public static boolean selected(long bits) { return (bits & 3) == 0; }

    public static double length(double speed) {
        return Double.isFinite(speed) && speed > 0 ? Math.min(speed * 0.35, 3) : 0;
    }

    /** Modest apparent-width compensation, capped before distant rounds become broad beams. */
    public static float width(double distance) {
        return Double.isFinite(distance) && distance >= 0 ? (float) Math.min(0.14, 0.06 + distance * 0.001) : 0;
    }

    public static float alpha(int skyDarken, int color) {
        float night = Math.clamp(skyDarken / 11.0F, 0, 1);
        return (0.95F - 0.20F * night) * (color >>> 24) / 255.0F;
    }

    /** Burning tracer bases are easier to see from behind; side views still read clearly. */
    public static float viewFactor(double rearCosine) {
        if (!Double.isFinite(rearCosine)) return 0;
        double cosine = Math.clamp(rearCosine, -1, 1);
        return (float) (cosine >= 0 ? 0.8 + cosine * 0.2 : 0.8 + cosine * 0.4);
    }

    public static int color(int argb) {
        int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 16 ? 0xFFAC43 : argb & 0xFFFFFF;
    }

    public static int coreColor(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        return (r + (255 - r) * 3 / 5) << 16 | (g + (255 - g) * 3 / 5) << 8 | b + (255 - b) * 3 / 5;
    }
}

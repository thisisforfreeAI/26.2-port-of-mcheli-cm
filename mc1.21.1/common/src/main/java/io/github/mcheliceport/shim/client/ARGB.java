package io.github.mcheliceport.shim.client;

import net.minecraft.util.FastColor;

/** The parts of 26.2's {@code ARGB} the client uses, over 1.21.1's {@link FastColor.ARGB32}. */
public final class ARGB {
    private ARGB() {
    }

    public static int color(int alpha, int red, int green, int blue) {
        return FastColor.ARGB32.color(alpha, red, green, blue);
    }

    public static int color(int alpha, int rgb) {
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    public static int colorFromFloat(float alpha, float red, float green, float blue) {
        return FastColor.ARGB32.colorFromFloat(alpha, red, green, blue);
    }

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int red(int color) {
        return color >> 16 & 255;
    }

    public static int green(int color) {
        return color >> 8 & 255;
    }

    public static int blue(int color) {
        return color & 255;
    }
}

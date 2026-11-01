package io.github.mcheliceport.content.weapon;

/**
 * An ARGB colour with float channels clamped to 0..1. Ported from upstream
 * {@code com.norwood.mcheli.MCH_Color}.
 */
public final class ArgbColor {

    public final float a;
    public final float r;
    public final float g;
    public final float b;

    public ArgbColor(float aa, float rr, float gg, float bb) {
        this.a = clamp(aa);
        this.r = clamp(rr);
        this.g = clamp(gg);
        this.b = clamp(bb);
    }

    public ArgbColor(int argb) {
        this.a = ((argb >> 24) & 0xFF) / 255.0F;
        this.r = ((argb >> 16) & 0xFF) / 255.0F;
        this.g = ((argb >> 8) & 0xFF) / 255.0F;
        this.b = (argb & 0xFF) / 255.0F;
    }

    public ArgbColor(float rr, float gg, float bb) {
        this(1.0F, rr, gg, bb);
    }

    /** Opaque white. */
    public ArgbColor() {
        this(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static float clamp(float f) {
        return f > 1.0F ? 1.0F : Math.max(f, 0.0F);
    }

    /** {@code #AARRGGBB}, as upstream writes colours to YAML. */
    public String toHexString() {
        return String.format("#%02X%02X%02X%02X", Math.round(this.a * 255), Math.round(this.r * 255),
                Math.round(this.g * 255), Math.round(this.b * 255));
    }

    public int toARGB() {
        int ia = Math.round(this.a * 255.0F);
        int ir = Math.round(this.r * 255.0F);
        int ig = Math.round(this.g * 255.0F);
        int ib = Math.round(this.b * 255.0F);
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    @Override
    public String toString() {
        return this.toHexString();
    }
}

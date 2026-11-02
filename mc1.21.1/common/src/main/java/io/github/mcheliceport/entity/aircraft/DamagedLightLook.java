package io.github.mcheliceport.entity.aircraft;

/** Stable cosmetic electrical faults; repairs restore output without random world changes. */
public final class DamagedLightLook {
    private DamagedLightLook() {}
    public static float intensity(float damage, boolean wreck, int id, int lamp, long tick) {
        if (wreck) return 0;
        if (!Float.isFinite(damage) || damage <= .45F) return 1;
        float severity = Math.clamp((damage - .45F) / .55F, 0, 1);
        long component = (long) id * 31 + lamp * 17L;
        if (severity > .8F && Math.floorMod(component, 3) == 0) return 0;
        long phase = Math.floorMod(tick + component, 43);
        if (phase < 2 + (int) (severity * 14)) return 0;
        if (phase < 20 && (phase & 1) == 0) return .18F;
        return 1 - severity * .35F;
    }
}

package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;

/** Nearby optional geometry is charged before birth and shed before primary billboards. */
public final class BlastDepthLook {
    private BlastDepthLook() {}
    public static boolean detail(EffectBudget quality, int pressure, double distanceSquared) {
        int range = quality == EffectBudget.HIGH ? 64 : 48;
        return quality != EffectBudget.LOW && pressure == 0 && Double.isFinite(distanceSquared)
                && distanceSquared >= 0 && distanceSquared <= range * range;
    }
    /** Fade optional planes before the existing hard distance bound; never enlarge geometry. */
    public static float visibility(EffectBudget quality,int pressure,double distanceSquared) {
        if(!detail(quality,pressure,distanceSquared))return 0;
        float range=quality==EffectBudget.HIGH?64:48;
        float t=(float)Math.clamp((range-Math.sqrt(distanceSquared))/(range*.28),0,1);
        return t*t*(3-2*t);
    }
    public static int cloudCards(boolean nearby, boolean fire, int index) {
        return !nearby ? 1 : fire ? index < 2 ? 2 : 1 : index < 12 && index % 3 == 0 ? 3 : 1;
    }
    public static float chunkScale(float power, boolean broad, int index) {
        return Float.isFinite(power) && power >= 8 && broad && index % 6 == 2 ? 2 : 1;
    }
    public static float columnScale(int segment) { return switch(segment) { case 0 -> 0.65F; case 1 -> 0.48F; default -> 0.30F; }; }
}

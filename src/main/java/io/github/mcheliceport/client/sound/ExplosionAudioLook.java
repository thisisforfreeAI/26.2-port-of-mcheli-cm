package io.github.mcheliceport.client.sound;

/** Timing in Minecraft blocks/metres and ticks; existing stock sound assets only. */
public final class ExplosionAudioLook {
    private ExplosionAudioLook() {}
    public static int arrival(double distance) {
        return Double.isFinite(distance) ? (int) Math.clamp(Math.round(Math.max(0, distance) * 20 / 343), 0, 4) : 0;
    }
    public static int tailDelay(float power) { return power >= 8 ? 4 : 2; }
    public static float tailPitch(float power) {
        return Float.isFinite(power) ? 0.65F - Math.clamp(power, 0, 16) * 0.0125F : 0.65F;
    }
}

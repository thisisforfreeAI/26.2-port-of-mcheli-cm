package io.github.mcheliceport.client.render;

/**
 * Realistic lighting numbers kept free of game state, so they can be tested: how brief lights from
 * flashes and fires fade, which of them a frame keeps, and the sun or moon that lights vehicles.
 */
public final class SceneLightLook {
    private SceneLightLook(){}

    /** Most point lights one frame passes to the vehicle shader. */
    public static final int MAX_LIGHTS = 8;
    /** Point lights farther than this from the camera are not kept. */
    public static final double RANGE = 128;

    /** Strength of a brief light {@code age} ticks after birth: a fast rise, then a smooth fade to nothing. */
    public static float fade(float age, float life) {
        if (!Float.isFinite(age) || !Float.isFinite(life) || life <= 0 || age < 0 || age >= life) return 0;
        float rise = Math.min(1, (age + 1) / 1.5F);
        float rest = 1 - age / life;
        return rise * rest * (float) Math.sqrt(rest);
    }

    /** Slow, restrained fire variation, never flickering to darkness. */
    public static float flicker(float time, int seed) {
        if (!Float.isFinite(time)) return 1;
        double t = time + (seed & 1023) * 0.37;
        return (float) (0.86 + 0.07 * Math.sin(t * 0.21) * Math.sin(t * 0.39 + 1.3) + 0.04 * Math.sin(t * 0.67));
    }

    /** How much a light matters to this frame: brighter, wider and nearer lights rank first. */
    public static float score(float intensity, float radius, double distanceSqr) {
        if (!(intensity > 0) || !(radius > 0) || !Double.isFinite(distanceSqr) || distanceSqr > RANGE * RANGE) return 0;
        return (float) (intensity * radius * radius / (distanceSqr + radius * radius));
    }

    /** Light radius in blocks of an explosion of {@code power}. */
    public static float explosionRadius(float power) {
        return Float.isFinite(power) && power > 0 ? Math.clamp(5 + power * 2.5F, 5, 28) : 0;
    }

    /** How long an explosion's light lasts, in ticks. */
    public static int explosionLife(float power) {
        return Float.isFinite(power) && power > 0 ? (int) Math.clamp(6 + power * 1.2F, 6, 20) : 0;
    }

    /** Light radius in blocks of a muzzle flash of {@code size}. */
    public static float muzzleRadius(float size) {
        return Float.isFinite(size) && size > 0 ? Math.clamp(2.5F + size * 2.5F, 2.5F, 9) : 0;
    }

    /** Light radius in blocks of a burning wreck, {@code fire} from 0 to 1. */
    public static float fireRadius(float fire) {
        return Float.isFinite(fire) && fire > 0 ? 3 + 7 * Math.min(1, fire) : 0;
    }

    /** A shrinking fire dims continuously; smoke and a cold wreck emit no light. */
    public static float fireIntensity(float fire) {
        return Float.isFinite(fire) && fire > 0 ? (float) Math.sqrt(Math.min(1, fire)) : 0;
    }

    /** Reach covers the burning pool as well as a short border of warm reflected light. */
    public static float poolRadius(float radius, float fire) {
        return Float.isFinite(radius) && radius > 0 && fireIntensity(fire) > 0
                ? Math.clamp(radius + 3 + 3 * fireIntensity(fire), 4, 14) : 0;
    }

    /**
     * The world direction to a sky body at {@code angle} radians, as vanilla's sky draws it:
     * straight up at 0, rising in the east (+X) and setting in the west.
     */
    public static float[] skyDirection(float angle) {
        return new float[]{(float) -Math.sin(angle), (float) Math.cos(angle), 0};
    }

    /**
     * The key light for vehicles: the sun by day, the moon by night, faded through the horizon and
     * dimmed by rain and storms. Angles are vanilla's sun and moon angles in radians. Returns
     * {x, y, z, strength, r, g, b}.
     */
    public static float[] keyLight(float sunAngle, float moonAngle, float rain, float thunder) {
        float[] sun = skyDirection(sunAngle);
        float height = sun[1];
        boolean day = height > -0.05F;
        float[] dir = day ? sun : skyDirection(moonAngle);
        // A light below the horizon cannot lift the underside of a vehicle.
        float x = dir[0], y = Math.max(dir[1], 0.08F), z = dir[2];
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        x /= length; y /= length; z /= length;
        float strength;
        float r, g, b;
        if (day) {
            strength = smooth(-0.05F, 0.2F, height);
            // Low sun is warm, high sun almost white.
            float warm = 1 - smooth(0.0F, 0.45F, height);
            r = 1.0F; g = 0.97F - 0.3F * warm; b = 0.9F - 0.5F * warm;
        } else {
            strength = 0.32F * smooth(0.05F, 0.3F, dir[1]);
            r = 0.72F; g = 0.8F; b = 1.0F;
        }
        float weather = 1 - 0.7F * clamp01(rain) - 0.25F * clamp01(thunder);
        return new float[]{x, y, z, strength * Math.max(0, weather), r, g, b};
    }

    /** Sky fill colour: neutral by day, warm at dusk and dawn, cool at night. */
    public static float[] fill(float sunAngle, float rain) {
        float height = skyDirection(sunAngle)[1];
        float day = smooth(-0.15F, 0.25F, height);
        float dusk = Math.max(0, 1 - Math.abs(height) / 0.25F);
        float r = lerp(0.86F, 1.0F, day) + 0.04F * dusk;
        float g = lerp(0.9F, 1.0F, day) - 0.03F * dusk;
        float b = 1.0F - 0.08F * dusk;
        // Overcast light comes from the whole sky, so shade differences flatten a little.
        float lift = 1 + 0.12F * clamp01(rain);
        return new float[]{Math.min(1, r) * lift, Math.min(1, g) * lift, Math.min(1, b) * lift};
    }

    /** Sheen strength on vehicle panels: a soft highlight that dull weather takes away. */
    public static float sheen(float rain) {
        return 0.16F * (1 - 0.6F * clamp01(rain));
    }

    private static float clamp01(float v) { return Float.isFinite(v) ? Math.clamp(v, 0, 1) : 0; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    static float smooth(float edge0, float edge1, float v) {
        float t = clamp01((v - edge0) / (edge1 - edge0));
        return t * t * (3 - 2 * t);
    }
}

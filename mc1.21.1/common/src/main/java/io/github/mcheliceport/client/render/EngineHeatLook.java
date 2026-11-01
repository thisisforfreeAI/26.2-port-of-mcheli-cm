package io.github.mcheliceport.client.render;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/** Pure screen-space bounds for small exhaust capsules. */
public final class EngineHeatLook {
    private EngineHeatLook() {}
    public record Screen(float ax, float ay, float az, float bx, float by, float bz, float radius, float strength) {}
    public static boolean enabled(EffectBudget quality, int pressure) { return quality != EffectBudget.LOW && pressure == 0; }
    public static int cap(EffectBudget quality) { return quality == EffectBudget.HIGH ? 8 : 4; }
    public static float length(boolean jet, float strength) { return (jet ? 2 : 0.8F) + Math.clamp(strength, 0, 1) * (jet ? 2 : 1); }

    public static Screen project(Matrix4fc viewProjection, Vec3 start, Vec3 end, float worldRadius,
            float strength, int width, int height, boolean zeroToOne) {
        if (width <= 0 || height <= 0 || !Float.isFinite(worldRadius + strength) || worldRadius <= 0 || strength <= 0) return null;
        Vector4f a = viewProjection.transform(new Vector4f((float) start.x, (float) start.y, (float) start.z, 1));
        Vector4f b = viewProjection.transform(new Vector4f((float) end.x, (float) end.y, (float) end.z, 1));
        if (!a.isFinite() || !b.isFinite() || a.w <= 0.1F || b.w <= 0.1F) return null;
        float verticalFocal = (float) Math.sqrt(viewProjection.m01() * viewProjection.m01()
                + viewProjection.m11() * viewProjection.m11() + viewProjection.m21() * viewProjection.m21());
        float radius = Math.clamp(verticalFocal * worldRadius * height / (2 * Math.min(a.w, b.w)), 0, 48);
        a.div(a.w); b.div(b.w);
        float ax = a.x * 0.5F + 0.5F, ay = a.y * 0.5F + 0.5F;
        float bx = b.x * 0.5F + 0.5F, by = b.y * 0.5F + 0.5F;
        float padX = (radius * 1.5F + 3) / width, padY = (radius * 1.5F + 3) / height;
        if (radius < 1.5F || Math.max(ax, bx) + padX < 0 || Math.min(ax, bx) - padX > 1
                || Math.max(ay, by) + padY < 0 || Math.min(ay, by) - padY > 1) return null;
        float az = zeroToOne ? a.z : a.z * 0.5F + 0.5F, bz = zeroToOne ? b.z : b.z * 0.5F + 0.5F;
        if (az < 0 || az > 1 || bz < 0 || bz > 1) return null;
        return new Screen(ax, ay, az, bx, by, bz, radius, Math.clamp(strength, 0, 1));
    }
}

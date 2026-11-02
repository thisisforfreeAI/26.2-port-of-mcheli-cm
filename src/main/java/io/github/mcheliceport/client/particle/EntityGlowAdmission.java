package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Entity halos cannot use particle live limits, so charge their bounded geometry once per extracted view. */
public final class EntityGlowAdmission {
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private static EffectTuning.Budget budget = new EffectTuning.Budget();
    private static long frame = Long.MIN_VALUE;
    private static int count;
    private static long extraction;
    private static int renderedCount, renderedLimit;

    private EntityGlowAdmission() {}

    public static boolean flare(ClientLevel level, double x, double y, double z) {
        if (world.get() != level) {
            world = new WeakReference<>(level);
            budget = new EffectTuning.Budget();
            count = 0;
        }
        long now = extraction;
        if (frame != now) { frame = now; count = 0; }
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        int cap = FlareLook.haloBudget(quality, EffectPerformance.pressure());
        if (cap == 0) return false;
        double distance = Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(x, y, z);
        int range = FlareLook.range(quality);
        if (!Double.isFinite(distance) || distance > range * range) return false;
        if (!budget.admit(now, cap, 1, EffectTuning.coverageCost(1.55F, distance))) return false;
        count++;
        return true;
    }

    /** Entities are extracted before RenderFrameEvent.Pre in 26.2. Close that batch here. */
    public static void extracted(net.minecraft.client.multiplayer.ClientLevel event) {
        renderedCount = world.get() == event && frame == extraction ? count : 0;
        renderedLimit = FlareLook.haloBudget(ClientConfig.get(ClientConfig.EFFECT_BUDGET), EffectPerformance.pressure());
        extraction++;
    }

    /** The completed render state's halo count/allowance, for native validation and profiling. */
    public static int flareCount() { return renderedCount; }
    public static int flareLimit() { return renderedLimit; }
}

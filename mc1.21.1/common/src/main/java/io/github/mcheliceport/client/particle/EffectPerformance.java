package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.config.ClientConfig;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import io.github.mcheliceport.shim.client.event.SubscribeEvent;
import io.github.mcheliceport.shim.client.event.RenderFrameEvent;

/** Client-only feedback. Never changes game settings, simulation, or network traffic. */
@io.github.mcheliceport.shim.client.event.ClientSubscriber
public final class EffectPerformance {
    private static final EffectFrameBudget BUDGET = new EffectFrameBudget();
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private static long lastFrame;
    private static int target;

    private EffectPerformance() {}

    public static int pressure() {
        return ClientConfig.get(ClientConfig.ADAPTIVE_EFFECTS) ? BUDGET.pressure() : 0;
    }

    public static boolean optionalGlows() {
        return ClientConfig.get(ClientConfig.EFFECT_BUDGET) != ClientConfig.EffectBudget.LOW && pressure() < 2;
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (world.get() != mc.level) {
            world = new WeakReference<>(mc.level);
            BUDGET.reset();
            lastFrame = 0;
        }
        if (!ClientConfig.get(ClientConfig.ADAPTIVE_EFFECTS)) {
            BUDGET.reset();
            lastFrame = 0;
            return;
        }
        if (mc.level == null || mc.isPaused() || mc.screen != null || mc.gui.overlay() != null || !mc.isWindowActive()) {
            // Preserve detail across menus, but discard their frame intervals.
            lastFrame = 0;
            return;
        }
        int effective = EffectFrameBudget.effectiveTarget(ClientConfig.get(ClientConfig.EFFECT_TARGET_FPS),
                mc.options.framerateLimit().get(), mc.options.enableVsync().get(), mc.getWindow().getRefreshRate());
        if (target != effective) {
            target = effective;
            BUDGET.reset();
            lastFrame = 0;
        }
        long now = System.nanoTime();
        if (lastFrame != 0) BUDGET.sample((now - lastFrame) / 1_000_000.0, target);
        lastFrame = now;
    }
}

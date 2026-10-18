package io.github.mcheliceport.probe;

import io.github.mcheliceport.client.effect.BlastMarks;
import io.github.mcheliceport.client.particle.ClientEffects;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.BlastStyle;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Opt-in disposable-world size-12 comparison, including a stepped crater. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class BombScaleProbe {
    private static final String[] NAMES = {"reference-size12", "bomb-size12", "bomb-crater"};
    private static int tick;
    private static String capture;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeBombScale")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        tick++;
        if (tick == 40) {
            mc.getWindow().setWindowed(1920, 1080);
            commands(mc, "gamemode spectator", "time set noon", "weather clear",
                    "gamerule minecraft:advance_time false", "tp @s 0 -49 4 0 38");
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.BLAST_MARKS.set(true);
            ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false);
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);
            ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(120);
        }
        if (tick < 160) return;
        int phase = (tick - 160) / 180, age = (tick - 160) % 180;
        if (phase >= NAMES.length) { mc.stop(); return; }
        if (age == 0) {
            BlastMarks.clear();
            mc.particleEngine.clearParticles();
            commands(mc, "kill @e[type=!minecraft:player]", "fill -22 -60 6 22 -48 40 air",
                    "fill -22 -64 6 22 -61 40 sandstone");
            if (phase == 2) commands(mc, "fill -6 -61 14 6 -61 26 air",
                    "fill -4 -62 16 4 -62 24 air", "fill -2 -63 18 2 -63 22 air");
        }
        if (age == 50) {
            mc.level.getRandom().setSeed(80612);
            BlastStyle style = phase == 0 ? BlastStyle.BLAST : BlastStyle.HEAVY;
            ClientEffects.INSTANCE.explosionEffects(mc.level, 0, -59.8, 20, 12, false,
                    List.of(new BlockPos(0, -61, 20)), style);
            System.out.println("M8_BOMB phase=" + NAMES[phase] + " power=12 style=" + style);
        }
        if (age == 54 || age == 62 || age == 90) capture = NAMES[phase] + "-age" + (age - 50);
        if (age == 160) {
            if (BlastMarks.markCount() != 1 || BlastMarks.quadCount() > 256) {
                throw new IllegalStateException("Missing/unbounded bomb mark: " + BlastMarks.markCount() + "/" + BlastMarks.quadCount());
            }
            System.out.println("M8_BOMB marks=" + BlastMarks.markCount() + " quads=" + BlastMarks.quadCount());
            mc.particleEngine.clearParticles();
        }
        if (age == 170) capture = NAMES[phase] + "-ground";
    }

    private static void commands(Minecraft mc, String... list) {
        for (String command : list) mc.player.connection.sendCommand(command);
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        String name = capture;
        capture = null;
        Screenshot.grab(mc.gameDirectory, "m8-scale-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("M8_BOMB " + message.getString()));
    }
}

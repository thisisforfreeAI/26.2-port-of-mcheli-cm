package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.effect.BlastMarks;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.BlastStyle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Opt-in actual renderer captures: empty affected lists exercise airburst-safe primary clouds. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class ExplosionStructureProbe {
    private static final String[] NAMES = {"classic-large", "large", "small", "missile", "incendiary", "empty", "fixed", "adaptive"};
    private static final int DURATION = 200;
    private static final List<Double> frames = new ArrayList<>();
    private static int tick;
    private static long last;
    private static String capture;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeExplosionStructure")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        tick++;
        if (tick == 40) {
            commands(mc, "kill @e[type=!minecraft:player]", "gamemode spectator", "time set noon", "weather clear",
                    "gamerule minecraft:advance_time false", "tp @s 0 -51 -5 0 12",
                    "fill -24 -60 6 24 -48 43 air", "fill -24 -47 6 24 -35 43 air",
                    "fill -24 -64 6 24 -61 43 sandstone");
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.getWindow().setWindowed(1920, 1080);
            mc.options.enableVsync().set(false); mc.options.framerateLimit().set(120);
            ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false);
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);
        }
        if (tick < 160) return;
        int phase = (tick - 160) / DURATION, age = (tick - 160) % DURATION;
        if (phase >= NAMES.length) { mc.stop(); return; }
        if (age == 0) {
            mc.particleEngine.clearParticles(); BlastMarks.clear(); frames.clear(); last = 0;
            ClientConfig.EFFECT_STYLE.set(phase == 0 ? ClientConfig.EffectStyle.CLASSIC : ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);
            ClientConfig.ADAPTIVE_EFFECTS.set(phase == 7);
            commands(mc, phase == 4 ? "time set midnight" : "time set noon");
            if (mc.getWindow().getWidth() != 1920 || mc.getWindow().getHeight() != 1080) throw new IllegalStateException("Requires 1080p");
            System.out.println("M8_STRUCTURE phase=" + NAMES[phase]);
            if (phase == 1) {
                BlastCloudParticle p = new BlastCloudParticle(mc.level, 0, -58, 20, 0, 0, 0, 3, 60, 12, 1);
                if (!p.getParticleLimit().equals(EffectAdmission.limit(false))) throw new IllegalStateException("Cloud escaped smoke limit");
                for (int i = 0; i < 60; i++) p.tick();
                if (p.isAlive()) throw new IllegalStateException("Cloud outlived lifetime");
            }
        }
        if (age == 40 && phase < 5) {
            BlastStyle style = phase == 3 ? BlastStyle.BLAST : phase == 4 ? BlastStyle.INCENDIARY : BlastStyle.HEAVY;
            float power = phase == 2 ? 2 : phase == 3 ? 4 : 12;
            blast(mc, 0, power, style);
        }
        if (phase >= 6 && age >= 40 && age % 25 == 0) {
            blast(mc, -6, 12, BlastStyle.HEAVY); blast(mc, 0, 4, BlastStyle.BLAST); blast(mc, 6, 8, BlastStyle.HEAVY);
        }
        if (phase < 5 && (age == 42 || age == 50 || age == 70 || age == 100)) capture = NAMES[phase] + "-age" + (age - 40);
        if (age == 190) {
            System.out.println("M8_STRUCTURE result=" + NAMES[phase] + " pressure=" + EffectPerformance.pressure()
                    + " particles=" + mc.particleEngine.countParticles());
            if (phase >= 5) {
                if (frames.size() < 30) throw new IllegalStateException("Insufficient samples");
                StringBuilder csv = new StringBuilder("frame,interval_ms\n");
                for (int i = 0; i < frames.size(); i++) csv.append(i).append(',').append(frames.get(i)).append('\n');
                try { java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("m8-structure-" + NAMES[phase] + ".csv"), csv); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }
        }
    }
    private static void blast(Minecraft mc, double x, float power, BlastStyle style) {
        ClientEffects.INSTANCE.explosionEffects(mc.level, x, -59.5, 20, power, false, List.of(), style);
    }
    private static void commands(Minecraft mc, String... commands) {
        for (String cmd : commands) mc.player.connection.sendCommand(cmd);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeExplosionStructure") || tick < 160) return;
        int phase = (tick - 160) / DURATION, age = (tick - 160) % DURATION;
        long now = System.nanoTime();
        if (phase >= 5 && age >= 60 && age < 185 && last != 0) frames.add((now - last) / 1_000_000.0);
        last = now;
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance(); String name = capture; capture = null;
        Screenshot.grab(mc.gameDirectory, "m8-structure-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("M8_STRUCTURE " + message.getString()));
    }
}

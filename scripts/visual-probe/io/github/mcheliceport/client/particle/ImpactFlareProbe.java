package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.effect.BlastMarks;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.BlastStyle;
import io.github.mcheliceport.entity.flare.ChaffEntity;
import io.github.mcheliceport.entity.flare.FlareEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Disposable 1080p bomb/missile, flare/chaff and bounded overlapping-effects scene. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class ImpactFlareProbe {
    private static final String[] NAMES = {"bomb", "missiles", "flare-day", "flare-night", "classic-night",
            "stress-low", "perf-empty", "perf-fixed", "perf-adaptive"};
    private static final int PHASE_TICKS = 320;
    private static final List<Integer> entities = new ArrayList<>();
    private static final List<Double> frames = new ArrayList<>();
    private static int tick, maxHalos;
    private static long lastFrame;
    private static String capture;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeImpactFlare")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        tick++;
        if (tick == 40) {
            mc.getWindow().setWindowed(1920, 1080);
            commands(mc, "kill @e[type=!minecraft:player]", "gamemode spectator", "time set noon", "weather clear",
                    "gamerule minecraft:advance_time false", "tp @s 0 -53 2 0 18",
                    "fill -22 -60 6 22 -48 40 air", "fill -22 -64 6 22 -61 40 sandstone");
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            ClientConfig.BLAST_MARKS.set(true);
            ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false);
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(120);
        }
        if (tick < 160) return;
        int phase = (tick - 160) / PHASE_TICKS, age = (tick - 160) % PHASE_TICKS;
        if (phase >= NAMES.length) { mc.stop(); return; }
        if (age == 0) {
            for (int id : entities) { var e = mc.level.getEntity(id); if (e != null) e.discard(); }
            entities.clear();
            BlastMarks.clear();
            mc.particleEngine.clearParticles();
            frames.clear(); lastFrame = 0; maxHalos = 0;
            if (mc.getWindow().getWidth() != 1920 || mc.getWindow().getHeight() != 1080) {
                throw new IllegalStateException("Impact/flare probe requires 1920x1080");
            }
            ClientConfig.EFFECT_STYLE.set(phase == 4 ? ClientConfig.EffectStyle.CLASSIC : ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(phase == 5 ? EffectBudget.LOW : EffectBudget.BALANCED);
            ClientConfig.ADAPTIVE_EFFECTS.set(phase == 8);
            commands(mc, phase == 3 || phase == 4 ? "time set midnight" : "time set noon");
            System.out.println("M8_IMPACT phase=" + NAMES[phase]);
            if (phase == 0) {
                ImpactCoreParticle core = new ImpactCoreParticle(mc.level, 0, -57, 14, 2, 7);
                if (!core.getParticleLimit().equals(EffectAdmission.limit(false)) || core.getLayer() != ParticleLayers.ADDITIVE) {
                    throw new IllegalStateException("Explosion core escaped shared smoke limit/layer");
                }
                for (int i = 0; i < 7; i++) core.tick();
                if (core.isAlive()) throw new IllegalStateException("Core outlived its seven-tick accent");
            }
        }
        if (age == 50 && (phase >= 2 && phase <= 5 || phase >= 7)) {
            int count = phase >= 5 ? 48 : 2;
            for (int i = 0; i < count; i++) {
                double x = count == 2 ? -4 + i * 8 : -7 + (i % 8) * 2;
                double y = count == 2 ? -54 : -55 + (i / 8) * 0.55;
                FlareEntity flare = new FlareEntity(mc.level, x, y, 16 + (i / 8), 0, 0, 0, 4, 0);
                flare.gravity = 0; flare.airResistance = 1;
                flare.setId(-25000 - phase * 100 - i);
                mc.level.addEntity(flare); entities.add(flare.getId());
            }
            if (phase >= 2 && phase <= 4) {
                ChaffEntity chaff = new ChaffEntity(mc.level, 0, -54, 16, 0, 0, 0);
                chaff.gravity = 0;
                chaff.setId(-29000 - phase);
                mc.level.addEntity(chaff); entities.add(chaff.getId());
            }
        }
        if (age == 60 && phase <= 1) {
            if (phase == 0) explosion(mc, 0, 8, BlastStyle.HEAVY);
            else explosion(mc, -4, 4, BlastStyle.BLAST);
        }
        // Separate isolated profiles so the first impact cannot spend the second one's admission.
        if (phase == 1 && age == 140) explosion(mc, 4, 4, BlastStyle.PENETRATOR);
        if ((phase == 5 || phase >= 7) && age >= 60 && age % 30 == 0) {
            explosion(mc, -6, 6, BlastStyle.HEAVY);
            explosion(mc, 0, 4, BlastStyle.BLAST);
            explosion(mc, 6, 4, BlastStyle.PENETRATOR);
        }
        if (phase <= 1 && (age == 62 || age == 72 || age == 100)) capture = NAMES[phase] + "-age" + (age - 60);
        if (phase == 1 && (age == 142 || age == 152 || age == 180)) capture = "penetrator-age" + (age - 140);
        if (phase >= 2 && phase <= 5 && age == 90) capture = NAMES[phase];
        if ((phase == 2 || phase == 3) && age == 120 && maxHalos == 0) throw new IllegalStateException("Missing enhanced flare halo");
        if ((phase == 4 || phase == 5) && age == 120 && maxHalos != 0) throw new IllegalStateException("Classic/Low retained halos");
        if (phase == 8 && age == 290 && EffectPerformance.pressure() < 2) {
            throw new IllegalStateException("Software overload did not shed secondary effects");
        }
        if (age == 300) {
            System.out.println("M8_IMPACT result=" + NAMES[phase] + " max_halos=" + maxHalos
                    + " pressure=" + EffectPerformance.pressure() + " particles=" + mc.particleEngine.countParticles());
            if (phase >= 6) {
                StringBuilder csv = new StringBuilder("frame,interval_ms\n");
                for (int i = 0; i < frames.size(); i++) csv.append(i).append(',').append(frames.get(i)).append('\n');
                try { java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("m8-impact-" + NAMES[phase] + ".csv"), csv); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                if (frames.size() < 30) throw new IllegalStateException("Insufficient frame samples");
            }
        }
    }

    private static void explosion(Minecraft mc, double x, float power, BlastStyle style) {
        ClientEffects.INSTANCE.explosionEffects(mc.level, x, -59.8, 20, power, false,
                List.of(new BlockPos((int) x, -61, 20)), style);
    }

    private static void commands(Minecraft mc, String... commands) {
        for (String command : commands) mc.player.connection.sendCommand(command);
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeImpactFlare") || tick < 160) return;
        int phase = (tick - 160) / PHASE_TICKS, age = (tick - 160) % PHASE_TICKS;
        if (phase >= NAMES.length) return;
        int halos = EntityGlowAdmission.flareCount();
        int cap = EntityGlowAdmission.flareLimit();
        if (halos > cap || !EffectQuality.enhanced() && halos > 0) throw new IllegalStateException("Unbounded flare halos: " + halos);
        maxHalos = Math.max(maxHalos, halos);
        long now = System.nanoTime();
        if (phase >= 6 && age >= 100 && age < 290 && lastFrame != 0) frames.add((now - lastFrame) / 1_000_000.0);
        lastFrame = now;
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        String name = capture; capture = null;
        Screenshot.grab(mc.gameDirectory, "m8-impact-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("M8_IMPACT " + message.getString()));
    }
}

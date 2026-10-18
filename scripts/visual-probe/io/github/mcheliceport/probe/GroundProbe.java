package io.github.mcheliceport.probe;

import io.github.mcheliceport.client.effect.BlastMarks;
import io.github.mcheliceport.client.effect.BlastMarkTuning;
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

/** Disposable-world native projection, material, invalidation and budget checks. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class GroundProbe {
    private static final String[] NAMES = {"families", "materials", "steps", "air-water", "budget", "perf-empty", "perf-marked"};
    private static final int PHASE_TICKS = 320;
    private static final java.util.List<Double> frames = new java.util.ArrayList<>();
    private static long lastFrame;
    private static int tick;
    private static String capture;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeGround")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        tick++;
        if (tick == 40) {
            mc.getWindow().setWindowed(1920, 1080);
            commands(mc, "kill @e[type=!minecraft:player]", "gamemode spectator", "time set noon", "weather clear",
                    "gamerule minecraft:advance_time false", "tp @s 0 -49 4 0 38",
                    "fill -22 -60 6 22 -48 40 air", "fill -22 -61 6 22 -61 40 grass_block");
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.BLAST_MARKS.set(true);
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);
            ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(120);
        }
        if (tick < 160) return;
        int phase = (tick - 160) / PHASE_TICKS;
        int age = (tick - 160) % PHASE_TICKS;
        if (phase >= NAMES.length) { mc.stop(); return; }
        if (age == 0) {
            frames.clear();
            lastFrame = 0;
            BlastMarks.clear();
            mc.particleEngine.clearParticles();
            commands(mc, "fill -22 -60 6 22 -48 40 air", "fill -22 -61 6 22 -61 40 grass_block");
            if (phase == 1) {
                commands(mc, "fill -15 -61 14 -9 -61 22 grass_block", "fill -7 -61 14 -1 -61 22 stone",
                        "fill 1 -61 14 7 -61 22 sand", "fill 9 -61 14 15 -61 22 snow_block");
            }
            if (phase == 2) {
                commands(mc, "fill -4 -60 14 0 -60 23 dirt", "fill 1 -60 14 5 -59 23 stone");
            }
            if (phase == 3) commands(mc, "fill 6 -61 13 14 -59 23 water");
            System.out.println("M8_GROUND phase=" + NAMES[phase]);
        }
        if (age == 60) {
            if (phase == 0) {
                BlastMarks.add(mc.level, -12, -59.8, 18, 1.8F, BlastStyle.CANNON);
                BlastMarks.add(mc.level, -4, -59.8, 18, 4, BlastStyle.BLAST);
                BlastMarks.add(mc.level, 4, -59.8, 18, 6, BlastStyle.HEAVY);
                BlastMarks.add(mc.level, 12, -59.8, 18, 4, BlastStyle.INCENDIARY);
            } else if (phase == 1) {
                for (int x : new int[]{-12, -4, 4, 12}) BlastMarks.add(mc.level, x, -59.8, 18, 3, BlastStyle.BLAST);
            } else if (phase == 2) {
                ClientEffects.INSTANCE.explosionEffects(mc.level, 0, -56.8, 18, 5, false,
                        List.of(new BlockPos(0, -60, 18)), BlastStyle.HEAVY);
            } else if (phase == 6) {
                for (int x : new int[]{-12, -4, 4, 12}) {
                    for (int z : new int[]{12, 20, 28}) BlastMarks.add(mc.level, x, -59.8, z, 4, BlastStyle.BLAST);
                }
            } else if (phase == 3) {
                BlastMarks.add(mc.level, -10, -35, 18, 2, BlastStyle.BLAST);
                BlastMarks.add(mc.level, 10, -58.8, 18, 3, BlastStyle.INCENDIARY);
            }
        }
        if (phase == 4 && age >= 60 && age < 110) {
            double x = -15 + (age % 20) * 1.5;
            double z = 10 + (age % 7) * 3;
            BlastMarks.add(mc.level, x, -59.8, z, 4, BlastStyle.BLAST);
        }
        if (age == 130) {
            int marks = BlastMarks.markCount(), quads = BlastMarks.quadCount();
            if (marks > BlastMarkTuning.markLimit(ClientConfig.EffectBudget.BALANCED)
                    || quads > BlastMarkTuning.quadLimit(ClientConfig.EffectBudget.BALANCED)) {
                throw new IllegalStateException("Unbounded ground marks " + marks + "/" + quads);
            }
            if (phase == 3 && marks != 0) throw new IllegalStateException("Airburst/water stamped ground");
            if (phase != 3 && phase != 5 && marks == 0) throw new IllegalStateException("Missing projected ground marks");
            System.out.println("M8_GROUND result=" + NAMES[phase] + " marks=" + marks + " quads=" + quads);
            if (phase < 5) capture = NAMES[phase];
        }
        if (phase == 4 && age == 145) commands(mc, "fill -22 -61 6 22 -59 40 air");
        if (phase == 4 && age == 180 && BlastMarks.markCount() != 0) {
            throw new IllegalStateException("Marks floated after their surfaces were removed");
        }
        if (phase == 4 && age == 185) System.out.println("M8_GROUND terrain-invalidation=passed");
        if (phase >= 5 && age == 300) {
            StringBuilder csv = new StringBuilder("frame,interval_ms\n");
            for (int i = 0; i < frames.size(); i++) csv.append(i).append(',').append(frames.get(i)).append('\n');
            try {
                java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("m8-ground-" + NAMES[phase] + ".csv"), csv);
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            var sorted = frames.stream().sorted().toList();
            System.out.printf(java.util.Locale.ROOT, "M8_GROUND frames=%s samples=%d median_ms=%.2f p95_ms=%.2f%n",
                    NAMES[phase], sorted.size(), sorted.get(sorted.size()/2), sorted.get((int) Math.ceil(sorted.size()*.95)-1));
        }
        if (phase == 6 && age == 305) ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.CLASSIC);
        if (phase == 6 && age == 312 && BlastMarks.markCount() != 0) {
            throw new IllegalStateException("Classic retained enhanced ground marks");
        }
    }

    private static void commands(Minecraft mc, String... list) {
        for (String command : list) mc.player.connection.sendCommand(command);
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeGround")) return;
        long now = System.nanoTime();
        int phase = (tick - 160) / PHASE_TICKS;
        int age = (tick - 160) % PHASE_TICKS;
        if (tick >= 160 && phase >= 5 && age >= 100 && age < 300 && lastFrame != 0) {
            frames.add((now - lastFrame) / 1_000_000.0);
        }
        lastFrame = now;
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        String name = capture;
        capture = null;
        Screenshot.grab(mc.gameDirectory, "m8-ground-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("M8_GROUND " + message.getString()));
    }
}

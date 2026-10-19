package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.render.EngineHeat;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.BlastDirection;
import io.github.mcheliceport.effect.BlastStyle;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Disposable preview world driver, excluded from normal builds. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class FollowupProbe {
    private static final boolean PERF = Boolean.getBoolean("mcheli.probeFollowupPerfOnly");
    private static final String[] NAMES = {"rocket-small", "rocket", "penetrator", "cruise", "dust-front", "water",
            "heat", "occlusion", "viper", "chinook", "jet", "low", "disabled", "adaptive", "cooldown", "ring-isolated"};
    private static final List<AircraftEntity> aircraft = new ArrayList<>();
    private static final StringBuilder csv = new StringBuilder("scene,tick,particles,ring_emitted,ground_reads,heat_sources,heat_drawn,scene_copies,pressure\n");
    private static int tick, phase = -1, age, peakRing, peakHeat, peakCopies;
    private static String capture;
    private static int[] occluded;
    private static boolean compare, compared;
    private static int[] visible;
    private static boolean visibleCompared;
    private static final StringBuilder frames = new StringBuilder("scene,tick,frame_ms,heat_drawn,scene_copies\n");
    private static long lastFrame;
    private static int skipFrames;
    private static final class HeldHeli extends HeliEntity {
        HeldHeli(Minecraft mc) { super(ModEntityTypes.HELICOPTER.get(), mc.level); }
        @Override public int getFuel() { return 1000; }
        @Override public void tick() { xo = getX(); yo = getY(); zo = getZ(); }
    }
    private static final class HeldPlane extends PlaneEntity {
        HeldPlane(Minecraft mc) { super(ModEntityTypes.PLANE.get(), mc.level); }
        @Override public int getFuel() { return 1000; }
        @Override public void tick() { xo = getX(); yo = getY(); zo = getZ(); }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeFollowups")) return;
        var mc = Minecraft.getInstance(); if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (++tick == 40) {
            commands(mc, "kill @e[type=!minecraft:player]", "gamemode spectator", "weather clear", "time set noon",
                    "gamerule minecraft:advance_time false", "fill -20 -60 4 20 -48 35 air", "fill -20 -47 4 20 -35 35 air",
                    "fill -20 -61 4 20 -61 35 grass_block");
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.getWindow().setWindowed(1920, 1080); mc.options.enableVsync().set(false); mc.options.framerateLimit().set(120);
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED); ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false);
        }
        if (tick < 160) return;
        phase = (tick - 160) / 120 + Integer.getInteger("mcheli.probeFollowupStart", 0); age = (tick - 160) % 120;
        if (PERF) { int elapsed = tick - 160; phase = elapsed < 400 ? 6 : elapsed < 800 ? 12 : NAMES.length; age = elapsed % 400; }
        if (phase >= NAMES.length) {
            try {
                java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("m8-followups.csv"), csv);
                java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("m8-followup-frames.csv"), frames);
            }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            if (!PERF && Integer.getInteger("mcheli.probeFollowupStart", 0) <= 7 && !compared) throw new IllegalStateException("Missing depth-occlusion pixel comparison");
            if (!PERF && Integer.getInteger("mcheli.probeFollowupStart", 0) <= 6 && !visibleCompared) throw new IllegalStateException("Missing visible heat pixel comparison");
            mc.stop(); return;
        }
        if (age == 0) setup(mc);
        if (phase == 15 && age == 20) {
            BlastGroundEffects.emit(mc.level, 0, -59.5, 21, 12, BlastStyle.HEAVY);
            peakRing = BlastGroundEffects.ringEmitted();
        }
        if (age == 20 && phase < 6) {
            float power = phase == 0 ? 3 : phase == 1 ? 5 : phase == 2 ? 4 : phase == 3 ? 10 : 12;
            BlastStyle style = phase == 2 ? BlastStyle.PENETRATOR : phase >= 4 ? BlastStyle.HEAVY : BlastStyle.BLAST;
            ClientEffects.INSTANCE.explosionEffects(mc.level, 0, -59.5, 21, power, phase == 5, List.of(), style,
                    BlastDirection.from(new Vec3(0.2, -0.6, 1)));
            peakRing = Math.max(peakRing, BlastGroundEffects.ringEmitted());
            System.out.println("M8_FOLLOW blast lobes=" + ExplosionSequence.lobes(power, style, ClientConfig.get(ClientConfig.EFFECT_BUDGET), 0)
                    + " ring=" + peakRing + " particles=" + mc.particleEngine.countParticles());
        }
        if (phase >= 6 && phase <= 13 && age >= 20) {
            if (phase >= 8 && phase <= 10) for (var a : aircraft) EngineExhaustEmitter.emit(a);
            else if (!(phase == 7 && age >= 65))
                for (int i = 0; i < 10; i++) EngineHeat.add(mc.level, i, new Vec3(-3 + i * 0.6, -55, 21),
                        new Vec3(0.3, 0.03, 1).normalize(), Vec3.ZERO, 1.3F, 1, true);
        }
        if (phase == 7 && age == 65) ClientConfig.ENGINE_HEAT_HAZE.set(false);
        if (phase == 6 && age == 65 && !PERF) ClientConfig.ENGINE_HEAT_HAZE.set(false);
        if (phase >= 8 && phase <= 10 && age == 50) for (var a : aircraft)
            System.out.println("M8_FOLLOW outletDiagnostic name=" + a.getAircraftTypeName() + " throttle=" + a.getCurrentThrottle()
                    + " fuel=" + a.canUseFuel() + " destroyed=" + a.isDestroyed() + " outlets=" + a.getAcInfo().engineOutlets.size()
                    + " scale=" + a.getAcInfo().particlesScale + " heatSources=" + EngineHeat.sourceCount()
                    + " emitted=" + EngineExhaustEmitter.emitted() + " heatDrawn=" + EngineHeat.drawn());
        if (age == 26 || age == 35 || age == 75) capture = NAMES[phase] + "-age" + age;
        if ((phase == 4 || phase == 15) && age == 24) capture = NAMES[phase] + "-age24";
        if (phase == 7 && (age == 50 || age == 80)) { compare = age == 80; capture = "occlusion-" + (compare ? "off" : "on"); }
        if (phase == 6 && (age == 50 || age == 80)) capture = "heat-control-" + (age == 80 ? "off" : "on");
        if (PERF) capture = null;
        if (EngineHeat.sourceCount() > 16 || EngineHeat.copies() > 1 || EngineHeat.drawn() > 4
                || BlastGroundEffects.ringEmitted() > 12 || BlastGroundEffects.surfaceReads() > 96)
            throw new IllegalStateException("Follow-up work cap exceeded");
        csv.append(NAMES[phase]).append(',').append(age).append(',').append(mc.particleEngine.countParticles()).append(',')
                .append(BlastGroundEffects.ringEmitted()).append(',').append(BlastGroundEffects.surfaceReads()).append(',')
                .append(EngineHeat.sourceCount()).append(',').append(EngineHeat.drawn()).append(',')
                .append(EngineHeat.copies()).append(',').append(EffectPerformance.pressure()).append('\n');
        if (age == 110) {
            if ((phase == 4 || phase == 15) && peakRing == 0) throw new IllegalStateException("Missing dust front");
            if ((phase == 6 || phase >= 8 && phase <= 10) && (peakHeat == 0 || peakCopies != 1))
                throw new IllegalStateException("Missing visible exhaust heat: sources=" + EngineHeat.sourceCount()
                        + " drawn=" + peakHeat + " copies=" + peakCopies + " emitted=" + EngineExhaustEmitter.emitted());
            if ((phase == 11 || phase == 12 || phase == 14) && (peakCopies != 0 || EngineHeat.sourceCount() != 0))
                throw new IllegalStateException("Low/disabled/cooldown retained heat work");
            if (phase == 13 && (EffectPerformance.pressure() != 3 || EngineHeat.copies() != 0))
                throw new IllegalStateException("Adaptive pressure did not shed heat pass");
            System.out.println("M8_FOLLOW result=" + NAMES[phase] + " ring=" + peakRing + " heat=" + peakHeat + " copies=" + peakCopies);
        }
    }
    private static void setup(Minecraft mc) {
        aircraft.forEach(AircraftEntity::discard); aircraft.clear(); mc.particleEngine.clearParticles();
        EngineHeat.clear(); EngineExhaustEmitter.clear(); BlastGroundEffects.clear();
        peakRing = peakHeat = peakCopies = 0;
        lastFrame = 0; skipFrames = 0;
        ClientConfig.ADAPTIVE_EFFECTS.set(phase == 13);
        ClientConfig.ENGINE_HEAT_HAZE.set(phase != 12); ClientConfig.BLAST_DUST_RING.set(true);
        ClientConfig.EFFECT_BUDGET.set(phase == 11 ? ClientConfig.EffectBudget.LOW : ClientConfig.EffectBudget.BALANCED);
        commands(mc, "fill -14 -60 12 14 -44 28 air", "fill -14 -61 12 14 -61 28 grass_block", "tp @s 0 -55 8 0 10");
        if (phase == 5) commands(mc, "fill -14 -60 12 14 -60 28 water");
        if (phase == 4) commands(mc, "tp @s 0 -49 -12 0 16");
        if (phase >= 6 && phase < 15) {
            commands(mc, "tp @s 0 -55 8 0 0", "fill -12 -60 26 12 -45 26 white_concrete");
            for (int x = -10; x <= 10; x += 2) commands(mc, "fill " + x + " -60 26 " + x + " -45 26 black_concrete");
        }
        if (phase == 7) commands(mc, "fill -12 -60 16 12 -45 16 stone");
        if (phase == 15) commands(mc, "tp @s 0 -43 -2 0 38");
        if (phase >= 8 && phase <= 10) {
            String name = phase == 8 ? "ah-1z" : phase == 9 ? "ch47" : "f-35a";
            AircraftEntity a = phase == 10 ? new HeldPlane(mc) : new HeldHeli(mc);
            a.setId(-810000 - phase); a.setAircraftTypeName(name); a.setTextureName(name);
            a.setPos(0, -60, name.equals("ch47") ? 34 : 28); a.setCurrentThrottle(0.95);
            var parts = a.getParts(); for (int i = 0; i < parts.length; i++) parts[i].setId(-820000 - phase * 100 - i);
            mc.level.addEntity(a); aircraft.add(a);
        }
        System.out.println("M8_FOLLOW phase=" + NAMES[phase]);
    }
    private static void commands(Minecraft mc, String... commands) { for (var c : commands) mc.player.connection.sendCommand(c); }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeFollowups") || phase < 0 || phase >= NAMES.length) return;
        peakHeat = Math.max(peakHeat, EngineHeat.drawn()); peakCopies = Math.max(peakCopies, EngineHeat.copies());
        long now = System.nanoTime();
        if (lastFrame != 0 && age > 40 && skipFrames == 0)
            frames.append(NAMES[phase]).append(',').append(age).append(',').append((now - lastFrame) / 1_000_000.0)
                    .append(',').append(EngineHeat.drawn()).append(',').append(EngineHeat.copies()).append('\n');
        lastFrame = now; if (skipFrames > 0) skipFrames--;
        if (capture == null) return;
        skipFrames = 3;
        var mc = Minecraft.getInstance(); String name = capture; capture = null;
        Screenshot.grab(mc.gameDirectory, "m8-follow-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("M8_FOLLOW " + message.getString()));
        if (!name.startsWith("occlusion-o") && !name.startsWith("heat-control-")) return;
        boolean check = name.endsWith("off"), foreground = name.startsWith("occlusion-");
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                int[] pixels = new int[800 * 400];
                for (int y = 0; y < 400; y++) for (int x = 0; x < 800; x++) pixels[y * 800 + x] = image.getPixel(560 + x, 340 + y);
                if (!foreground) {
                    if (!check) visible = pixels;
                    else {
                        int changed = 0;
                        if (visible == null) throw new IllegalStateException("Missing heat-on pixels");
                        for (int i = 0; i < pixels.length; i++) if (visible[i] != pixels[i]) changed++;
                        if (changed == 0 || changed > 8000) throw new IllegalStateException("Heat refraction missing or unconfined: " + changed);
                        visibleCompared = true; System.out.println("M8_FOLLOW refracted background pixels=" + changed);
                    }
                } else if (!check) occluded = pixels;
                else {
                    if (occluded == null || !java.util.Arrays.equals(occluded, pixels)) throw new IllegalStateException("Heat warped foreground occluder");
                    compared = true; System.out.println("M8_FOLLOW foreground pixels unchanged=320000");
                }
            }
        });
    }
}

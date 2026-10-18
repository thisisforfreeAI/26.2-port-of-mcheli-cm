package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Actual common heli emitter, using disposable unrendered fixture helicopters without flight ticks. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class RotorWashProbe {
    private static final String[] NAMES = {"classic-sand", "sand", "grass", "snow", "water", "moving", "high",
            "perf-empty", "perf-classic", "perf-enhanced", "perf-adaptive"};
    private static final int LENGTH = 320;
    private static final boolean SNOW_CHECK = Boolean.getBoolean("mcheli.probeWashSnowCheck");
    private static final List<Double> frames = new ArrayList<>();
    private static final List<ProbeHeli> helis = new ArrayList<>();
    private static int tick, peakLookups, peakEmission;
    private static long lastFrame;
    private static String capture;
    private static RotorRippleParticle testRipple;
    private static boolean duplicateChecked;

    private static final class ProbeHeli extends HeliEntity {
        private final HeliInfo definition;
        ProbeHeli(Minecraft mc, int id) {
            super(ModEntityTypes.HELICOPTER.get(), mc.level);
            this.definition = new HeliInfo(new ContentLocation("probe", "rotor"), "rotor", "probe");
            this.definition.maxHp = 100;
            this.definition.rotorList.add(new HeliInfo.Rotor(4, 90, 0, 2, 0, 0, 1, 0, "main", false, false));
            this.setId(id); this.setAircraftTypeName("rotor"); this.random.setSeed(id);
            this.setCurrentThrottle(0.9);
        }
        @Override protected AircraftInfo lookupInfo(String name) { return this.definition; }
        void wash() { this.onUpdateParticleSandCloud(false); }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeRotorWash")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        tick++;
        if (tick == 40) {
            mc.getWindow().setWindowed(1920, 1080);
            commands(mc, "kill @e[type=!minecraft:player]", "gamemode spectator", "time set noon", "weather clear",
                    "gamerule minecraft:advance_time false", "tp @s 0 -52 2 0 25",
                    "fill -22 -60 6 22 -42 40 air", "fill -22 -64 6 22 -61 40 stone");
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);
            mc.options.enableVsync().set(false); mc.options.framerateLimit().set(120);
        }
        if (tick < 160) return;
        int phase = (tick - 160) / LENGTH + (SNOW_CHECK ? 3 : 0), age = (tick - 160) % LENGTH;
        if (phase >= (SNOW_CHECK ? 4 : NAMES.length)) { mc.stop(); return; }
        if (age == 0) {
            if (mc.getWindow().getWidth() != 1920 || mc.getWindow().getHeight() != 1080) throw new IllegalStateException("Wrong probe resolution");
            mc.particleEngine.clearParticles(); RotorWashEmitter.clear(); helis.clear();
            frames.clear(); lastFrame = 0; peakLookups = peakEmission = 0; testRipple = null;
            duplicateChecked = false;
            ClientConfig.EFFECT_STYLE.set(phase == 0 || phase == 8 ? ClientConfig.EffectStyle.CLASSIC : ClientConfig.EffectStyle.POLISHED);
            ClientConfig.ADAPTIVE_EFFECTS.set(phase == 10);
            String material = phase == 2 ? "grass_block" : phase == 3 ? SNOW_CHECK ? "snow" : "snow_block" : phase == 4 ? "water" : "sand";
            commands(mc, "fill -22 -61 6 22 -61 40 " + material);
            if (phase != 7) {
                for (int i = 0; i < (phase >= 8 ? 8 : 1); i++) helis.add(new ProbeHeli(mc, -31000 - i));
            }
            System.out.println("M8_WASH phase=" + NAMES[phase]);
        }
        if (age >= 40) {
            for (int i = 0; i < helis.size(); i++) {
                ProbeHeli heli = helis.get(i);
                double x = phase >= 8 ? (i % 4) * 5 - 7.5 : phase == 5 ? Math.sin(age * 0.025) * 5 : 0;
                double z = phase >= 8 ? 15 + (i / 4) * 8 : 20;
                heli.setPos(x, phase == 6 ? -25 : -56, z);
                heli.setDeltaMovement(phase == 5 ? Math.cos(age * 0.025) * 0.125 : 0, 0, 0);
                heli.wash();
                if (phase >= 1 && phase <= 5 && !duplicateChecked && RotorWashEmitter.emitted() > 0) {
                    int before = RotorWashEmitter.emitted(), queries = RotorWashEmitter.lookups();
                    heli.wash();
                    if (before != RotorWashEmitter.emitted() || queries != RotorWashEmitter.lookups()) {
                        throw new IllegalStateException("Repeated game tick duplicated wash");
                    }
                    duplicateChecked = true;
                }
            }
            if (RotorWashEmitter.lookups() > 256 || RotorWashEmitter.cacheSize() > 128) throw new IllegalStateException("Unbounded wash terrain work");
            peakLookups = Math.max(peakLookups, RotorWashEmitter.lookups());
            peakEmission = Math.max(peakEmission, RotorWashEmitter.emitted());
        }
        if (phase <= 6 && age == 115) capture = NAMES[phase];
        if (SNOW_CHECK && age == 150 && peakEmission == 0) throw new IllegalStateException("Thin snow layer lost its wash");
        if (SNOW_CHECK && age == 160) {
            commands(mc, "fill -22 -61 6 22 -61 40 powder_snow");
            RotorWashEmitter.clear(); peakEmission = 0;
        }
        if (SNOW_CHECK && age == 255) capture = "powder-snow";
        if (phase == 4 && age == 120) {
            BlockPos pos = new BlockPos(0, -61, 20);
            double y = pos.getY() + mc.level.getFluidState(pos).getHeight(mc.level, pos) + 0.015;
            testRipple = new RotorRippleParticle(mc.level, 0, y, 20, 2, 0.2F, 0);
            if (!testRipple.getParticleLimit().equals(EffectAdmission.limit(false))) throw new IllegalStateException("Unbudgeted ripple");
        }
        if (testRipple != null) testRipple.tick();
        if (phase == 4 && age == 130) commands(mc, "fill -22 -61 6 22 -61 40 air");
        if (phase == 4 && age == 140 && testRipple != null && testRipple.isAlive()) throw new IllegalStateException("Ripple floated over drained water");
        if (phase == 6 && age == 200 && !mc.particleEngine.countParticles().endsWith("T 0")) throw new IllegalStateException("High rotor emitted wash");
        if (phase == 10 && age == 290 && EffectPerformance.pressure() < 2) throw new IllegalStateException("Software overload did not thin wash");
        if (age == 300) {
            if (phase > 0 && phase < 6 && (peakEmission == 0 || !duplicateChecked)) throw new IllegalStateException("Missing wash emission/duplicate check");
            System.out.println("M8_WASH result=" + NAMES[phase] + " lookups=" + peakLookups + " emission=" + peakEmission
                    + " pressure=" + EffectPerformance.pressure() + " particles=" + mc.particleEngine.countParticles());
            if (phase >= 7) {
                StringBuilder csv = new StringBuilder("frame,interval_ms\n");
                for (int i = 0; i < frames.size(); i++) csv.append(i).append(',').append(frames.get(i)).append('\n');
                try { java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("m8-wash-" + NAMES[phase] + ".csv"), csv); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                if (frames.size() < 30) throw new IllegalStateException("Insufficient wash timing sample");
            }
        }
    }

    private static void commands(Minecraft mc, String... list) { for (String c : list) mc.player.connection.sendCommand(c); }
    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeRotorWash") || tick < 160) return;
        int phase = (tick - 160) / LENGTH, age = (tick - 160) % LENGTH;
        long now = System.nanoTime();
        if (phase >= 7 && age >= 100 && age < 290 && lastFrame != 0) frames.add((now - lastFrame) / 1_000_000.0);
        lastFrame = now;
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance(); String name = capture; capture = null;
        Screenshot.grab(mc.gameDirectory, "m8-wash-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("M8_WASH " + message.getString()));
    }
}

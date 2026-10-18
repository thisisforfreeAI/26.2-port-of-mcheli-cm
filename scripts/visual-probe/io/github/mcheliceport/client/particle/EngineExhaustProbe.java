package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.McheliEffects;
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

/** Real stock models and common effect sink, held still to isolate plume placement/cost. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class EngineExhaustProbe {
    private static final String[] NAMES = {"off", "apache", "blackhawk", "jet", "vtol", "classic", "water", "empty", "models", "fixed", "adaptive"};
    private static final int LENGTH = 320;
    private static final boolean PERF_ONLY = Boolean.getBoolean("mcheli.probeExhaustPerfOnly");
    private static final boolean JET_ONLY = Boolean.getBoolean("mcheli.probeExhaustJetOnly");
    private static final List<AircraftEntity> vehicles = new ArrayList<>();
    private static final List<Double> frames = new ArrayList<>();
    private static int tick, peak;
    private static long last;
    private static String capture;

    private static final class HeldHeli extends HeliEntity {
        private int probeFuel;
        HeldHeli(Minecraft mc) { super(ModEntityTypes.HELICOPTER.get(), mc.level); }
        @Override public int getFuel() { return this.probeFuel; }
        @Override public void setFuel(int fuel) { this.probeFuel = fuel; }
        @Override public void tick() { this.xo = this.getX(); this.yo = this.getY(); this.zo = this.getZ(); }
    }
    private static final class HeldPlane extends PlaneEntity {
        private final boolean vtol;
        private int probeFuel;
        HeldPlane(Minecraft mc, boolean vtol) { super(ModEntityTypes.PLANE.get(), mc.level); this.vtol = vtol; }
        @Override public int getFuel() { return this.probeFuel; }
        @Override public void setFuel(int fuel) { this.probeFuel = fuel; }
        @Override public float getNozzleRotation() { return this.vtol ? 90 : 0; }
        @Override public void tick() {
            this.xo = this.getX(); this.yo = this.getY(); this.zo = this.getZ();
            if (this.vtol && this.partNozzle != null) this.partNozzle.rotation = this.partNozzle.prevRotation = 90;
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeEngineExhaust")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        tick++;
        if (tick == 40) {
            commands(mc, "kill @e[type=!minecraft:player]", "gamemode spectator", "time set noon", "weather clear",
                    "gamerule minecraft:advance_time false", "tp @s -7 -54 4 -25 16",
                    "fill -22 -60 6 22 -42 40 air", "fill -22 -64 6 22 -61 40 stone");
            mc.getWindow().setWindowed(1920, 1080);
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);
            mc.options.enableVsync().set(false); mc.options.framerateLimit().set(120);
        }
        if (tick < 160) return;
        int phase = (tick - 160) / LENGTH + (PERF_ONLY ? 7 : JET_ONLY ? 3 : 0), age = (tick - 160) % LENGTH;
        if (phase >= (JET_ONLY ? 4 : NAMES.length)) { mc.stop(); return; }
        if (age == 0) {
            for (var v : vehicles) v.discard(); vehicles.clear();
            mc.particleEngine.clearParticles(); EngineExhaustEmitter.clear();
            frames.clear(); last = 0; peak = 0;
            ClientConfig.EFFECT_STYLE.set(phase == 5 ? ClientConfig.EffectStyle.CLASSIC : ClientConfig.EffectStyle.POLISHED);
            ClientConfig.ADAPTIVE_EFFECTS.set(phase == 10);
            commands(mc, "fill -4 -60 12 4 -56 18 air", phase == 3 ? "time set midnight" : "time set noon");
            if (phase == 6) commands(mc, "fill -4 -60 12 4 -56 18 water");
            if (mc.getWindow().getWidth() != 1920 || mc.getWindow().getHeight() != 1080) throw new IllegalStateException("Requires 1080p");
            if (phase != 7) for (int i = 0; i < (phase >= 8 ? 8 : 1); i++) {
                boolean jet = phase == 3 || phase == 4 || phase == 5 || phase >= 8 && i >= 4;
                AircraftEntity v = jet ? new HeldPlane(mc, phase == 4) : new HeldHeli(mc);
                v.setId(-34000 - phase * 100 - i);
                v.setAircraftTypeName(jet ? phase == 4 ? "f-35b" : "f-35a" : phase == 2 ? "mh-60g" : "ah-64");
                v.setPos(phase >= 8 ? (i % 4) * 6 - 9 : 0, phase == 6 ? -62 : -59.5, phase >= 8 ? 20 + (i / 4) * 13 : 20);
                v.setYaw(0); v.setCurrentThrottle(phase == 0 || phase == 8 ? 0 : 0.95); v.setFuel(1000); v.setDamageTaken(0);
                if (v.getAcInfo() == null) throw new IllegalStateException("Missing stock definition");
                v.setTextureName(v.getAircraftTypeName());
                // Direct client fixtures bypass the spawn packet that normally assigns multipart IDs.
                var parts = v.getParts();
                for (int j = 0; j < parts.length; j++) parts[j].setId(-50000 - phase * 1000 - i * 32 - j);
                mc.level.addEntity(v); vehicles.add(v);
            }
            if (phase == 1) {
                EnginePlumeParticle p = new EnginePlumeParticle(mc.level, 0, -58, 20, 0, 0, 0, .3F, .2F, 18, 0);
                if (!p.getParticleLimit().equals(EffectAdmission.limit(false))) throw new IllegalStateException("Plume escaped smoke limit");
                for (int i = 0; i < 18; i++) p.tick();
                if (p.isAlive()) throw new IllegalStateException("Plume outlived lifetime");
                EngineGlowParticle g = new EngineGlowParticle(mc.level, new Vec3(0, -58, 20), Vec3.ZERO, .2F, .2F);
                if (!g.getParticleLimit().equals(EffectAdmission.limit(false)) || g.getLayer() != ParticleLayers.ADDITIVE) throw new IllegalStateException("Jet escaped limit/layer");
                for (int i = 0; i < 4; i++) g.tick();
                if (g.isAlive()) throw new IllegalStateException("Jet outlived four ticks");
            }
            System.out.println("M8_EXHAUST phase=" + NAMES[phase]);
        }
        if (phase == 1 && age == 110) vehicles.getFirst().setCurrentThrottle(.25);
        if (phase == 3 && age == 120) commands(mc, "time set noon");
        if (phase == 0 && age == 90) { vehicles.getFirst().setCurrentThrottle(1); vehicles.getFirst().setFuel(0); }
        if (age >= 30) for (var v : vehicles) {
            boolean handled = McheliEffects.engineExhaust(v);
            int before = EngineExhaustEmitter.emitted();
            if (handled) {
                McheliEffects.engineExhaust(v);
                if (before != EngineExhaustEmitter.emitted()) throw new IllegalStateException("Repeated tick duplicated exhaust");
            }
            if (phase == 5 && handled) throw new IllegalStateException("Classic lost original nozzle emitter");
        }
        peak = Math.max(peak, EngineExhaustEmitter.emitted());
        if (EngineExhaustEmitter.emitted() > EngineExhaustLook.cap(ClientConfig.get(ClientConfig.EFFECT_BUDGET)) || EngineExhaustEmitter.cacheSize() > 128) throw new IllegalStateException("Exhaust exceeded bounds");
        if (age == 100 && phase < 6) capture = NAMES[phase];
        if (phase == 1 && age == 130) capture = "apache-low-throttle";
        if (phase == 3 && age == 140) capture = "jet-day";
        // Software rendering may need more wall time to collect useful frame distributions.
        if (phase >= 7 && age == 289 && frames.size() < 48) tick--;
        if (age == 300) {
            if ((phase == 0 || phase == 5 || phase == 6 || phase == 7 || phase == 8) && peak != 0) throw new IllegalStateException("Off/Classic/water emitted new exhaust");
            if (phase >= 1 && phase <= 4 && peak == 0) throw new IllegalStateException("Supported outlet emitted nothing");
            System.out.println("M8_EXHAUST result=" + NAMES[phase] + " peak=" + peak + " pressure=" + EffectPerformance.pressure() + " particles=" + mc.particleEngine.countParticles());
            if (phase >= 7) {
                if (frames.size() < 30) throw new IllegalStateException("Insufficient frame samples");
                StringBuilder csv = new StringBuilder("frame,interval_ms\n");
                for (int i = 0; i < frames.size(); i++) csv.append(i).append(',').append(frames.get(i)).append('\n');
                try { java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("m8-exhaust-" + NAMES[phase] + ".csv"), csv); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }
        }
    }
    private static void commands(Minecraft mc, String... cmds) { for (String cmd : cmds) mc.player.connection.sendCommand(cmd); }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeEngineExhaust") || tick < 160) return;
        int phase = (tick - 160) / LENGTH + (PERF_ONLY ? 7 : JET_ONLY ? 3 : 0), age = (tick - 160) % LENGTH;
        long now = System.nanoTime();
        if (phase >= 7 && age >= 100 && age < 290 && last != 0) frames.add((now - last) / 1_000_000.0);
        last = now;
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance(); String name = capture; capture = null;
        Screenshot.grab(mc.gameDirectory, "m8-exhaust-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("M8_EXHAUST " + message.getString()));
    }
}

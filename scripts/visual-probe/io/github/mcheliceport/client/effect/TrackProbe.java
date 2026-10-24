package io.github.mcheliceport.client.effect;

import io.github.mcheliceport.client.particle.ClientEffects;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.BlastStyle;
import io.github.mcheliceport.effect.McheliEffects;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Disposable terrain/material/real-contact fixture; never included in a normal build. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class TrackProbe {
    private static final String[] NAMES = {"materials", "steps", "water", "air", "removal", "vehicles", "disabled", "stress", "low", "adaptive", "ramps"};
    private static final int LENGTH = 180;
    private static final List<Entity> fixtures = new ArrayList<>();
    private static final StringBuilder csv = new StringBuilder("phase,tick,reads,fitted,pending,stored_quads,drawn_quads,requests,vehicles,states,pressure\n");
    private static int tick, peakQuads, peakReads, peakDrawn, peakRequests;
    private static int beforeRequests, treadRequests, tireRequests;
    private static long sourceTick = Long.MIN_VALUE;
    private static String capture;

    private static final class HeldTank extends TankEntity {
        private int age;
        private final double lane;
        private final boolean ramp;
        HeldTank(Minecraft mc, double lane, boolean ramp) { super(ModEntityTypes.TANK.get(), mc.level); this.lane = lane; this.ramp = ramp; }
        @Override public void tick() {
            this.xo = this.getX(); this.yo = this.getY(); this.zo = this.getZ();
            age++;
            double x = lane, z;
            float yaw = 0;
            if (age < 65) z = 12 + age * 0.15;
            else if (age < 110) {
                double angle = (age - 65) / 45.0 * Math.PI;
                x += (1 - Math.cos(angle)) * 2; z = 21.75 + Math.sin(angle) * 2;
                yaw = (float) -Math.toDegrees(angle);
            } else { x += 4; z = 21.75 + (age - 110) * 0.12; yaw = -180; }
            // The final leg reverses relative to the hull heading; position changes are controlled.
            double y = -60;
            if (ramp) {
                x = lane; z = 12 + age * 0.11; yaw = 0;
                y += z < 18 ? 0 : z < 24 ? 0.5 : z < 30 ? 1 : 1.5;
            }
            this.setPos(x, y, z); this.setYaw(yaw);
            this.setDeltaMovement(x - xo, 0, z - zo); this.setOnGround(true);
            if (this.terrain() != null) this.terrain().move();
        }
    }
    private static final class HeldHeli extends HeliEntity {
        HeldHeli(Minecraft mc) { super(ModEntityTypes.HELICOPTER.get(), mc.level); }
        @Override public int getFuel() { return 1000; }
        @Override public void tick() { this.xo = getX(); this.yo = getY(); this.zo = getZ(); }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeTracks")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        tick++;
        if (tick == 40) {
            commands(mc, "kill @e[type=!minecraft:player]", "gamemode spectator", "weather clear", "time set noon",
                    "gamerule minecraft:advance_time false", "fill -24 -60 6 24 -50 48 air",
                    "fill -24 -49 6 24 -40 48 air", "fill -24 -64 6 24 -61 48 stone");
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            ClientConfig.EFFECT_TARGET_FPS.set(60);
            mc.getWindow().setWindowed(1920, 1080); mc.options.enableVsync().set(false); mc.options.framerateLimit().set(120);
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);
            ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false); ClientConfig.ADAPTIVE_EFFECTS.set(false);
        }
        if (tick < 160) return;
        int phase = (tick - 160) / LENGTH, age = (tick - 160) % LENGTH;
        if (phase >= NAMES.length) {
            try { java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("m8-tracks.csv"), csv); }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            mc.stop(); return;
        }
        if (age == 0) {
            for (var e : fixtures) e.discard(); fixtures.clear(); mc.particleEngine.clearParticles();
            TrackMarks.clear(); VehicleTracks.clear(); BlastMarks.clear(); peakQuads = peakReads = peakDrawn = peakRequests = 0;
            treadRequests = tireRequests = 0; sourceTick = Long.MIN_VALUE;
            ClientConfig.VEHICLE_TRACKS.set(phase != 6);
            ClientConfig.ADAPTIVE_EFFECTS.set(phase == 9);
            ClientConfig.EFFECT_BUDGET.set(phase == 8 ? ClientConfig.EffectBudget.LOW : ClientConfig.EffectBudget.BALANCED);
            commands(mc, "fill -24 -60 6 24 -58 48 air", "fill -24 -61 6 24 -61 48 grass_block", "tp @s 0 -47 4 0 38");
            if (phase == 0) commands(mc, "fill -6 -61 6 -1 -61 48 sand", "fill 1 -61 6 6 -61 48 snow_block", "fill 7 -61 6 13 -61 48 stone");
            if (phase == 1) commands(mc, "fill -12 -61 18 12 -61 22 stone_slab[type=bottom]", "fill -12 -60 28 12 -60 31 stone");
            if (phase == 2) commands(mc, "fill -24 -60 6 24 -60 48 water");
            if (phase == 10) commands(mc, "fill -12 -60 18 12 -60 23 stone_slab[type=bottom]",
                    "fill -12 -60 24 12 -60 40 stone", "fill -12 -59 30 12 -59 40 stone_slab[type=bottom]");
            if (phase == 5 || phase == 7 || phase == 9 || phase == 10) vehicles(mc, phase);
            System.out.println("M8_TRACK phase=" + NAMES[phase]);
        }
        if (age >= 20 && age < 100 && phase != 5 && phase != 10) {
            int count = phase == 0 ? 4 : phase == 7 || phase == 9 ? 16 : 1;
            for (int i = 0; i < count; i++) {
                double x = phase == 0 ? -9 + i * 6 : phase == 7 ? -10 + i % 8 * 3 : 0;
                double z = phase == 4 ? 20 : 12 + (age - 20) * 0.2;
                double y = phase == 3 ? -55 : phase == 1 && z >= 28 ? -59 : -60;
                TrackMarks.add(mc.level, new Vec3(x - 0.8, y, z), new Vec3(0, 0, 1), 0.7F, TrackMarks.Kind.TREAD, 0.8F);
                TrackMarks.add(mc.level, new Vec3(x + 0.8, y, z), new Vec3(0, 0, 1), 0.28F, TrackMarks.Kind.TIRE, 0.8F);
            }
        }
        if (phase == 4 && age == 105) commands(mc, "fill -3 -61 18 3 -61 22 air");
        if (phase == 7 || phase == 9) {
            for (var entity : fixtures) if (entity instanceof HeliEntity heli) {
                McheliEffects.rotorWash(heli, false); McheliEffects.engineExhaust(heli);
            }
            if (age > 20 && age < 120 && age % 20 == 0)
                ClientEffects.INSTANCE.explosionEffects(mc.level, 0, -59.5, 36, 12, false, List.of(), BlastStyle.HEAVY);
        }
        int cap = TrackTuning.quads(ClientConfig.get(ClientConfig.EFFECT_BUDGET));
        int readCap = TrackTuning.reads(ClientConfig.get(ClientConfig.EFFECT_BUDGET));
        if (TrackMarks.reads() > readCap || TrackMarks.fittedSurfaces() > TrackTuning.fits(ClientConfig.get(ClientConfig.EFFECT_BUDGET))
                || TrackMarks.pendingCount() > 16 || TrackMarks.quadCount() > cap
                || VehicleTracks.requests() > 16 || VehicleTracks.vehicles() > 8 || VehicleTracks.stateCount() > 64)
            throw new IllegalStateException("Unbounded track work");
        peakReads = Math.max(peakReads, TrackMarks.reads()); peakQuads = Math.max(peakQuads, TrackMarks.quadCount());
        peakRequests = Math.max(peakRequests, VehicleTracks.requests());
        csv.append(NAMES[phase]).append(',').append(age).append(',').append(TrackMarks.reads()).append(',').append(TrackMarks.fittedSurfaces()).append(',')
                .append(TrackMarks.pendingCount()).append(',').append(TrackMarks.quadCount()).append(',')
                .append(TrackMarks.drawnQuads()).append(',').append(VehicleTracks.requests()).append(',')
                .append(VehicleTracks.vehicles()).append(',').append(VehicleTracks.stateCount()).append(',')
                .append(io.github.mcheliceport.client.particle.EffectPerformance.pressure()).append('\n');
        if (age == 50 || age == 120) capture = NAMES[phase] + "-age" + age;
        if (age == 165) {
            if ((phase == 2 || phase == 3 || phase == 4 || phase == 6) && TrackMarks.quadCount() != 0)
                throw new IllegalStateException("Unsupported/removed/disabled surface retained tracks");
            if ((phase == 5 || phase == 10) && (peakQuads == 0 || peakRequests == 0)) throw new IllegalStateException("Actual vehicle contacts emitted no tracks");
            if ((phase == 5 || phase == 10) && (treadRequests == 0 || tireRequests == 0))
                throw new IllegalStateException("Missing tracked or wheeled stock contact emission");
            if (phase == 9) System.out.println("M8_TRACK feedback pressure="
                    + io.github.mcheliceport.client.particle.EffectPerformance.pressure()
                    + " active=" + mc.isWindowActive() + " target=" + ClientConfig.get(ClientConfig.EFFECT_TARGET_FPS));
            if (phase == 8 && peakQuads == 0) throw new IllegalStateException("Low omitted all tracks");
            System.out.println("M8_TRACK result=" + NAMES[phase] + " reads=" + peakReads + " quads=" + peakQuads
                    + " drawn=" + peakDrawn + " requests=" + peakRequests + " pending=" + TrackMarks.pendingCount()
                    + " pressure=" + io.github.mcheliceport.client.particle.EffectPerformance.pressure()
                    + " treadRequests=" + treadRequests + " tireRequests=" + tireRequests);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void beforeContact(EntityTickEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeTracks") || !(event.getEntity() instanceof HeldTank tank)) return;
        long now = tank.level().getGameTime();
        beforeRequests = sourceTick == now ? VehicleTracks.requests() : 0; sourceTick = now;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void afterContact(EntityTickEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeTracks") || !(event.getEntity() instanceof HeldTank tank)) return;
        int added = Math.max(0, VehicleTracks.requests() - beforeRequests);
        if (tank.getTankInfo().weightType == 1) tireRequests += added; else treadRequests += added;
    }

    private static void vehicles(Minecraft mc, int phase) {
        for (int i = 0; i < 2; i++) {
            var tank = new HeldTank(mc, i == 0 ? -7 : 5, phase == 10); tank.setId(-700000 - phase * 100 - i);
            tank.setAircraftTypeName(i == 0 ? "m1a2" : "growler"); tank.setTextureName(tank.getAircraftTypeName());
            tank.setPos(i == 0 ? -7 : 5, -60, 12);
            if (tank.getTankInfo() == null) throw new IllegalStateException("Missing stock ground vehicle");
            add(mc, tank, phase * 10 + i);
        }
        if (phase == 7 || phase == 9) {
            var heli = new HeldHeli(mc); heli.setId(-710000); heli.setAircraftTypeName("ah-64"); heli.setTextureName("ah-64");
            heli.setPos(0, -54, 37); heli.setCurrentThrottle(0.95); add(mc, heli, 99);
        }
    }
    private static void add(Minecraft mc, AircraftEntity aircraft, int key) {
        var parts = aircraft.getParts(); for (int i = 0; i < parts.length; i++) parts[i].setId(-720000 - key * 100 - i);
        mc.level.addEntity(aircraft); fixtures.add(aircraft);
    }
    private static void commands(Minecraft mc, String... commands) { for (var c : commands) mc.player.connection.sendCommand(c); }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeTracks")) return;
        int pressure = io.github.mcheliceport.client.particle.EffectPerformance.pressure();
        int limit = Math.max(16, TrackTuning.quads(ClientConfig.get(ClientConfig.EFFECT_BUDGET)) / (1 + pressure));
        if (TrackMarks.drawnQuads() > limit) throw new IllegalStateException("Track draw cap exceeded at observed pressure");
        peakDrawn = Math.max(peakDrawn, TrackMarks.drawnQuads());
        if (capture == null) return;
        var mc = Minecraft.getInstance(); String name = capture; capture = null;
        Screenshot.grab(mc.gameDirectory, "m8-tracks-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("M8_TRACK " + message.getString()));
    }
}

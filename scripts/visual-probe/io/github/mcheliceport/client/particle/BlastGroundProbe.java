package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.effect.BlastMarks;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.BlastStyle;
import io.github.mcheliceport.effect.McheliEffects;
import io.github.mcheliceport.effect.ParticleParam;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.flare.FlareEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Material closeups plus real stock models, exhaust/wash, flare trails and scripted weapon VFX. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class BlastGroundProbe {
    private static final String[] NAMES = {"sand", "soil", "snow", "stone", "missile", "airburst", "water", "low",
            "empty", "models", "fixed", "adaptive"};
    private static final boolean COMBAT = Boolean.getBoolean("mcheli.probeCombatSoak");
    private static final int LENGTH = COMBAT ? 600 : 240;
    private static final int OFFSET = (COMBAT || Boolean.getBoolean("mcheli.probeGroundPerfOnly")) ? 8 : 0;
    private static final List<Entity> entities = new ArrayList<>();
    private static final List<Double> frames = new ArrayList<>();
    private static final List<HeldBullet> bullets = new ArrayList<>();
    private static final StringBuilder telemetry = new StringBuilder("phase,tick,particles,bullets,fragment_slots,terrain_reads,terrain_rays,maintenance,pending,active,tiles,exhaust,wash,pressure\n");
    private static int sequence;
    private static int tick, peakFragments, peakReads, peakHalos, peakExhaust, peakWash;
    private static long last;
    private static String capture;

    /** Renderer fixture: bounded lifetime and motion, without running server ballistics. */
    private static final class HeldBullet extends BulletEntity {
        private int age;
        HeldBullet(Minecraft mc) { super(ModEntityTypes.BULLET.get(), mc.level); }
        @Override public void tick() {
            this.xo = this.getX(); this.yo = this.getY(); this.zo = this.getZ();
            this.setPos(this.getX() + 1, this.getY(), this.getZ());
            if (++this.age >= 16) this.discard();
        }
    }

    private static final class HeldHeli extends HeliEntity {
        private int fuel;
        HeldHeli(Minecraft mc) { super(ModEntityTypes.HELICOPTER.get(), mc.level); }
        @Override public int getFuel() { return this.fuel; }
        @Override public void setFuel(int fuel) { this.fuel = fuel; }
        @Override public void tick() { this.xo = this.getX(); this.yo = this.getY(); this.zo = this.getZ(); }
    }
    private static final class HeldPlane extends PlaneEntity {
        private int fuel;
        HeldPlane(Minecraft mc) { super(ModEntityTypes.PLANE.get(), mc.level); }
        @Override public int getFuel() { return this.fuel; }
        @Override public void setFuel(int fuel) { this.fuel = fuel; }
        @Override public void tick() { this.xo = this.getX(); this.yo = this.getY(); this.zo = this.getZ(); }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!COMBAT && !Boolean.getBoolean("mcheli.probeBlastGround")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        tick++;
        if (tick == 40) {
            commands(mc, "kill @e[type=!minecraft:player]", "gamemode spectator", "time set noon", "weather clear",
                    "gamerule minecraft:advance_time false", "fill -24 -60 6 24 -50 48 air", "fill -24 -49 6 24 -40 48 air",
                    "fill -24 -64 6 24 -61 48 stone");
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.getWindow().setWindowed(1920, 1080);
            mc.options.enableVsync().set(false); mc.options.framerateLimit().set(120);
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);
            ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false);
            if (COMBAT) ClientConfig.BLAST_MARKS.set(true);
        }
        if (tick < 160) return;
        int phase = (tick - 160) / LENGTH + OFFSET, age = (tick - 160) % LENGTH;
        if (phase >= NAMES.length) {
            if (COMBAT) write(mc, "m8-combat-ticks.csv", telemetry.toString());
            mc.stop(); return;
        }
        if (age == 0) {
            for (var entity : entities) entity.discard(); entities.clear();
            for (var bullet : bullets) bullet.discard(); bullets.clear();
            mc.particleEngine.clearParticles(); BlastMarks.clear(); BlastGroundEffects.clear(); EngineExhaustEmitter.clear();
            RotorWashEmitter.clear();
            frames.clear(); last = 0; peakFragments = peakReads = peakHalos = peakExhaust = peakWash = 0;
            ClientConfig.EFFECT_BUDGET.set(phase == 7 ? ClientConfig.EffectBudget.LOW : ClientConfig.EffectBudget.BALANCED);
            ClientConfig.ADAPTIVE_EFFECTS.set(phase == 11);
            String material = phase == 0 ? "sand" : phase == 1 ? "grass_block" : phase == 2 ? "snow_block" : "stone";
            commands(mc, "fill -24 -60 6 24 -58 48 air", "fill -24 -61 6 24 -61 48 " + material,
                    phase < 8 ? "tp @s -6 -56 9 -25 15" : "tp @s 0 -48 -10 0 16");
            if (phase == 6) commands(mc, "fill -24 -60 6 24 -59 48 water");
            if (mc.getWindow().getWidth() != 1920 || mc.getWindow().getHeight() != 1080) throw new IllegalStateException("Requires 1080p");
            if (phase >= 9) vehicles(mc, phase);
            if (phase == 0) checkParticles(mc);
            System.out.println("M8_GROUND phase=" + NAMES[phase]);
        }
        if (age == 40 && phase < 8) {
            if (phase == 4) blast(mc, 0, 4, BlastStyle.BLAST);
            else BlastGroundEffects.emit(mc.level, 0, phase == 5 ? -42 : phase == 6 ? -58.5 : -59.5, 20, 12, BlastStyle.HEAVY);
            if ((phase <= 4) && BlastGroundEffects.emittedFragments() == 0) throw new IllegalStateException("Missing surface fragments");
            if (phase >= 5 && BlastGroundEffects.emittedFragments() != 0) throw new IllegalStateException("Air/water/Low emitted ground fragments");
        }
        if (phase == 3 && age == 120) {
            for (int i = 0; i < 100; i++) BlastGroundEffects.emit(mc.level, 0, -59.5, 20, 12, BlastStyle.HEAVY);
        }
        if (phase >= 10 && age >= 30 && (!COMBAT || age < 400)) {
            for (var entity : entities) if (entity instanceof AircraftEntity aircraft) {
                McheliEffects.engineExhaust(aircraft);
                if (aircraft instanceof HeliEntity) McheliEffects.rotorWash(aircraft, false);
            }
            if (age == 40 || COMBAT && age % 100 == 40) for (int i = 0; i < 12; i++) {
                var flare = new FlareEntity(mc.level, -10 + i * 1.8, -51 - (i % 3) * 0.4, 27, 0, 0, 0, 4, 0);
                flare.gravity = 0; flare.airResistance = 1; flare.setId(-90000 - phase * 10000 - age * 12 - i);
                mc.level.addEntity(flare); entities.add(flare);
            }
            if (age >= 40 && age % 25 == 0) {
                blast(mc, -6, 12, BlastStyle.HEAVY); blast(mc, 0, 4, BlastStyle.BLAST);
                blast(mc, 6, COMBAT ? 10 : 8, COMBAT ? BlastStyle.BLAST : BlastStyle.HEAVY);
            }
            if (COMBAT) fire(mc);
            if (age % 4 == 0) {
                ParticleParam trail = new ParticleParam("smoke", -12 + age % 40 * 0.6, -54, 21, -0.05, 0.01, 0, 3);
                trail.age = 24; trail.r = trail.g = trail.b = 0.8F;
                McheliEffects.spawnParticle(mc.level, trail);
            }
            if (age % 6 == 0) ClientEffects.INSTANCE.spawnMuzzleFlash(mc.level, -10, -54, 30, 0.6F, 1, 1, 1, 1, 3);
        }
        bullets.removeIf(b -> !b.isAlive());
        if (COMBAT) {
            // Client-only flare fixtures have no server lifetime/removal packet.
            // End their source explicitly, without clearing the particles being tested.
            if (age == 400) for (var entity : entities) if (entity instanceof FlareEntity) entity.discard();
            if (bullets.size() > 64 || BlastMarks.projectionReads() > 256 || BlastMarks.projectionRays() > 32
                    || BlastMarks.maintenanceChecks() > 40 || BlastMarks.pendingCount() > 16
                    || BlastMarks.activeCount() > 2 || BlastMarks.quadCount() > 384
                    || EngineExhaustEmitter.emitted() > 16) throw new IllegalStateException("Mixed combat budget exceeded");
            telemetry.append(NAMES[phase]).append(',').append(age).append(',').append(particleCount(mc)).append(',')
                    .append(bullets.size()).append(',').append(BlastGroundEffects.trackedFragments()).append(',')
                    .append(BlastMarks.projectionReads()).append(',').append(BlastMarks.projectionRays()).append(',')
                    .append(BlastMarks.maintenanceChecks()).append(',').append(BlastMarks.pendingCount()).append(',')
                    .append(BlastMarks.activeCount()).append(',').append(BlastMarks.quadCount()).append(',')
                    .append(age < 400 ? EngineExhaustEmitter.emitted() : 0).append(',')
                    .append(age < 400 ? RotorWashEmitter.emitted() : 0).append(',')
                    .append(EffectPerformance.pressure()).append('\n');
            if (age == 580 && (bullets.size() != 0 || particleCount(mc) != 0
                    || BlastMarks.pendingCount() != 0 || BlastMarks.activeCount() != 0))
                throw new IllegalStateException("Transient effects failed to drain: particles=" + mc.particleEngine.countParticles()
                        + " bullets=" + bullets.size() + " pending=" + BlastMarks.pendingCount() + " active=" + BlastMarks.activeCount());
            if (age == 580) System.out.println("M8_COMBAT drained=" + NAMES[phase] + " tiles=" + BlastMarks.quadCount());
            if (phase >= 10 && age == 420) capture = NAMES[phase] + "-cooldown";
        }
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        if (BlastGroundEffects.emittedFragments() > BlastGroundLook.fragmentTickCap(quality, 0)
                || BlastGroundEffects.trackedFragments() > BlastGroundLook.fragmentLiveCap(quality)
                || BlastGroundEffects.surfaceReads() > BlastGroundLook.surfaceReadCap(quality)) throw new IllegalStateException("Unbounded debris/read work");
        peakFragments = Math.max(peakFragments, BlastGroundEffects.trackedFragments());
        peakReads = Math.max(peakReads, BlastGroundEffects.surfaceReads());
        peakExhaust = Math.max(peakExhaust, EngineExhaustEmitter.emitted());
        peakWash = Math.max(peakWash, RotorWashEmitter.emitted());
        if (RotorWashEmitter.lookups() > 256 || RotorWashEmitter.cacheSize() > 128) throw new IllegalStateException("Unbounded rotor wash work");
        if (phase < 5 && (age == 44 || age == 50 || age == 60 || age == 80)) capture = NAMES[phase] + "-age" + (age - 40);
        // Capture before the frame-sampling window so readback/compression cannot skew it.
        if (phase >= 10 && age == 60) capture = NAMES[phase];
        if (phase >= 8 && age == (COMBAT ? 379 : 209) && frames.size() < 48) tick--;
        if (age == (COMBAT ? 590 : 220)) {
            if (phase >= 10 && peakExhaust == 0) throw new IllegalStateException("Mixed scene never emitted exhaust");
            if (phase == 10 && peakWash == 0) throw new IllegalStateException("Mixed scene never emitted rotor wash");
            if (phase == 10 && peakHalos == 0) throw new IllegalStateException("Mixed scene never drew flare halos");
            System.out.println("M8_GROUND result=" + NAMES[phase] + " fragments=" + peakFragments + " reads=" + peakReads
                    + " halos=" + peakHalos + " exhaust=" + peakExhaust + " wash=" + peakWash + " pressure=" + EffectPerformance.pressure()
                    + " particles=" + mc.particleEngine.countParticles());
            if (phase >= 8) {
                StringBuilder csv = new StringBuilder("frame,interval_ms\n");
                for (int i = 0; i < frames.size(); i++) csv.append(i).append(',').append(frames.get(i)).append('\n');
                try { java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve((COMBAT ? "m8-combat-" : "m8-ground-") + NAMES[phase] + ".csv"), csv); }
                catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            }
        }
    }

    private static int particleCount(Minecraft mc) {
        return Integer.parseInt(mc.particleEngine.countParticles().replaceAll(".*T ", ""));
    }
    private static void write(Minecraft mc, String name, String content) {
        try { java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve(name), content); }
        catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
    }
    private static void fire(Minecraft mc) {
        for (int i = 0; i < 4; i++) {
            var bullet = new HeldBullet(mc); bullet.setId(-200000 - sequence); bullet.setUUID(new java.util.UUID(0, sequence++));
            bullet.setName("m134"); bullet.isBomblet = 0;
            bullet.setPos(-12, -54 + i % 2 * 0.5, 18 + i * 3); bullet.setYRot(-90); bullet.yRotO = -90;
            bullet.setDeltaMovement(8, 0, 0); bullet.prevMotionX = 8;
            mc.level.addEntity(bullet); bullets.add(bullet);
        }
    }

    private static void checkParticles(Minecraft mc) {
        if (!McheliParticle.sprite(BlastGroundDustParticle.TEXTURE).contents().name().equals(BlastGroundDustParticle.TEXTURE))
            throw new IllegalStateException("Missing dedicated ground dust sheet");
        var p = new BlastFragmentParticle(mc.level, 0, -58, 20, 0.2, 0.1, 0, Blocks.STONE.defaultBlockState(),
                new BlockPos(0, -61, 20), -100, 0.2F, 999, true);
        if (!p.getParticleLimit().equals(EffectAdmission.limit(false))) throw new IllegalStateException("Fragment escaped smoke limit");
        for (int i = 0; i < 40; i++) p.tick();
        if (p.isAlive() || p.getQuadSize(0) != 0) throw new IllegalStateException("Fragment outlived/shrank beyond its cap");
        var low = new BlastFragmentParticle(mc.level, 0, -59.5, 20, 0, -0.5, 0, Blocks.DIRT.defaultBlockState(),
                new BlockPos(0, -61, 20), -60, 0.2F, 40, false);
        low.tick(); if (low.isAlive()) throw new IllegalStateException("Fragment crossed sampled ground plane");
        if (BlastGroundEffects.material(Blocks.SNOW.defaultBlockState()) != BlastGroundLook.Surface.SNOW
                || BlastGroundEffects.material(Blocks.POWDER_SNOW.defaultBlockState()) != BlastGroundLook.Surface.SNOW)
            throw new IllegalStateException("Missing loose snow classification");
        if (BlastGroundEffects.material(Blocks.GRASS_BLOCK.defaultBlockState()) != BlastGroundLook.Surface.SOIL
                || BlastGroundEffects.material(Blocks.MYCELIUM.defaultBlockState()) != BlastGroundLook.Surface.SOIL)
            throw new IllegalStateException("Grass must throw unheated soil clods");
        BlockPos pos = new BlockPos(0, -61, 20);
        if (Math.abs(BlastGroundEffects.surfaceHeight(mc.level, pos, Blocks.SNOW.defaultBlockState()) - 0.125) > 0.0001
                || BlastGroundEffects.surfaceHeight(mc.level, pos, Blocks.POWDER_SNOW.defaultBlockState()) != 1)
            throw new IllegalStateException("Loose/layered snow lost its visible surface height");
    }

    private static void vehicles(Minecraft mc, int phase) {
        for (int i = 0; i < 4; i++) {
            AircraftEntity aircraft = i < 2 ? new HeldHeli(mc) : new HeldPlane(mc);
            aircraft.setId(-80000 - phase * 100 - i);
            aircraft.setAircraftTypeName(i < 2 ? "ah-64" : "f-35a");
            aircraft.setTextureName(aircraft.getAircraftTypeName());
            aircraft.setPos(i % 2 == 0 ? -10 : 10, -54, i < 2 ? 31 : 45);
            aircraft.setCurrentThrottle(phase == 9 ? 0 : 0.95); aircraft.setFuel(1000); aircraft.setYaw(0);
            if (aircraft.getAcInfo() == null) throw new IllegalStateException("Missing stock fixture");
            var parts = aircraft.getParts();
            for (int j = 0; j < parts.length; j++) parts[j].setId(-100000 - phase * 1000 - i * 32 - j);
            mc.level.addEntity(aircraft); entities.add(aircraft);
        }
    }

    private static void blast(Minecraft mc, double x, float power, BlastStyle style) {
        ClientEffects.INSTANCE.explosionEffects(mc.level, x, -59.5, 20, power, false, List.of(), style);
    }
    private static void commands(Minecraft mc, String... commands) {
        for (String command : commands) mc.player.connection.sendCommand(command);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if ((!COMBAT && !Boolean.getBoolean("mcheli.probeBlastGround")) || tick < 160) return;
        int phase = (tick - 160) / LENGTH + OFFSET, age = (tick - 160) % LENGTH;
        int halos = EntityGlowAdmission.flareCount();
        if (BlastMarks.flashQuadCount() > 64) throw new IllegalStateException("Terrain flash exceeded its limit");
        if (halos > EntityGlowAdmission.flareLimit()) throw new IllegalStateException("Flare geometry exceeded its limit");
        peakHalos = Math.max(peakHalos, halos);
        long now = System.nanoTime();
        if (phase >= 8 && age >= 80 && age < (COMBAT ? 380 : 210) && last != 0) frames.add((now - last) / 1_000_000.0);
        last = now;
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance(); String name = capture; capture = null;
        Screenshot.grab(mc.gameDirectory, (COMBAT ? "m8-combat-" : "m8-ground-") + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("M8_GROUND " + message.getString()));
    }
}

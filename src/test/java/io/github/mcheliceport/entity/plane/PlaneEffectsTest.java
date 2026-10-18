package io.github.mcheliceport.entity.plane;

import static org.junit.jupiter.api.Assertions.*;

import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.effect.ParticleParam;
import io.github.mcheliceport.util.McheliMath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class PlaneEffectsTest {
    private static PlaneInfo info() {
        return new PlaneInfo(new ContentLocation("test", "plane"), "plane", "test");
    }

    private static PlaneParticles.Frame frame(Vec3 position, Vec3 previous, int hp, double throttle) {
        return new PlaneParticles.Frame(position, previous, new Vec3(0.2, 0, -0.3),
                35, -12, 20, 60, throttle, hp, 100, 2.0F, position.y - 0.3, null);
    }

    private record World(boolean air, boolean water) implements PlaneParticles.World {
        @Override public boolean isAir(int x, int y, int z) { return air; }
        @Override public boolean isWater(int x, int y, int z) { return water; }
    }

    @Test
    void nozzleMatchesUpstreamRandomSequenceInAirAndNearBlocks() {
        PlaneInfo info = info();
        info.particlesScale = 1.25F;
        info.nozzles.add(new AircraftInfo.DrawnPart(1, 2, -3, 0, 1, 0, "nozzle"));
        PlaneParticles.Frame frame = frame(new Vec3(-20.5, 70, 4.5), Vec3.ZERO, 100, 1.0);
        for (boolean air : new boolean[] {true, false}) {
            List<ParticleParam> out = new ArrayList<>();
            PlaneParticles.nozzle(info, frame, RandomSource.create(1234), new World(air, false), out::add);
            assertEquals(1, out.size());
            RandomSource random = RandomSource.create(1234);
            random.nextFloat(); // Upstream's per-nozzle probability check.
            Vec3 direction = McheliMath.rotVec3(0, 0, 1, -frame.yaw() - 180.0F,
                    frame.pitch() - frame.nozzle(), frame.roll());
            Vec3 offset = McheliMath.rotVec3(info.nozzles.getFirst().pos, -frame.yaw(), -frame.pitch(), -frame.roll());
            ParticleParam p = out.getFirst();
            assertEquals(frame.position().x + offset.x + direction.x, p.posX, 1.0E-12);
            assertEquals(frame.position().y + offset.y + direction.y, p.posY, 1.0E-12);
            assertEquals(frame.position().z + offset.z + direction.z, p.posZ, 1.0E-12);
            float spread = air ? 0.7F : 2.0F;
            assertEquals(direction.x + (random.nextFloat() - 0.5F) * spread, p.motionX, 1.0E-12);
            assertEquals(direction.y, p.motionY);
            assertEquals(direction.z + (random.nextFloat() - 0.5F) * spread, p.motionZ, 1.0E-12);
            assertEquals(6.25F, p.size);
        }
        List<ParticleParam> idle = new ArrayList<>();
        PlaneParticles.nozzle(info, frame(Vec3.ZERO, Vec3.ZERO, 100, 0.1F),
                RandomSource.create(1), new World(true, false), idle::add);
        assertTrue(idle.isEmpty(), "upstream's idle throttle threshold emits no exhaust smoke");
    }

    @Test
    void damageTrailPreservesUpstreamInterpolationAndVehicleLocalHistory() {
        PlaneInfo info = info();
        PlaneParticles particles = new PlaneParticles();
        List<ParticleParam> out = new ArrayList<>();
        RandomSource random = RandomSource.create(42);
        Vec3 start = new Vec3(1, 30, 2);
        particles.damage(info, frame(start, start, 50, 1), random, out::add);
        assertTrue(out.isEmpty(), "no damage smoke at half health");
        particles.damage(info, frame(start, start, 10, 1), random, out::add);
        assertEquals(1, out.size());
        assertEquals(start.x, out.getFirst().posX);
        assertTrue(out.getFirst().size >= 10 && out.getFirst().size <= 18,
                "a jet without rotors uses upstream's double-size fuselage plume");
        out.clear();
        Vec3 next = start.add(1.2, 0, 0);
        particles.damage(info, frame(next, start, 10, 1), random, out::add);
        assertEquals(5, out.size(), "one sample per 0.3 blocks, including the starting sample");
        for (int i = 0; i < out.size(); i++) {
            assertEquals(start.x + 1.2 * i / 3.0, out.get(i).posX, 1.0E-12);
            assertEquals(start.y, out.get(i).posY);
        }
        List<ParticleParam> other = new ArrayList<>();
        new PlaneParticles().damage(info, frame(next, start, 10, 1), RandomSource.create(1), other::add);
        assertEquals(1, other.size(), "a second plane has no first plane's trail history");
        assertEquals(next.x, other.getFirst().posX);
        particles.reset(); out.clear();
        particles.damage(info, frame(next.add(100, 0, 0), next, 10, 1), random, out::add);
        assertEquals(1, out.size(), "type changes reset histories rather than emitting a trail across the map");
    }

    @Test
    void damageSmokeParticleValuesMatchUpstreamEmitter() {
        PlaneInfo info = info();
        List<ParticleParam> out = new ArrayList<>();
        new PlaneParticles().damage(info, frame(Vec3.ZERO, Vec3.ZERO, 10, 0), RandomSource.create(765), out::add);
        RandomSource random = RandomSource.create(765);
        float color = 0.2F + random.nextFloat() * 0.3F;
        ParticleParam p = out.getFirst();
        assertEquals(1.0F, p.damageHeat, "critical-health smoke carries a fire appearance hint");
        assertEquals(2.0F * (random.nextDouble() - 0.5) * 0.3, p.motionX);
        assertEquals(2.0F * random.nextDouble() * 0.1, p.motionY);
        assertEquals(2.0F * (random.nextDouble() - 0.5) * 0.3, p.motionZ);
        assertEquals(2.0F * (random.nextInt(5) + 5.0F), p.size);
        assertEquals(0.7F + random.nextFloat() * 0.1F, p.a);
        assertEquals(color, p.r); assertEquals(color, p.g); assertEquals(color, p.b);
    }

    @Test
    void damageTrailRoundsTheSquareRootResultAsUpstream() {
        PlaneParticles particles = new PlaneParticles();
        List<ParticleParam> out = new ArrayList<>();
        PlaneInfo info = info();
        RandomSource random = RandomSource.create(1);
        particles.damage(info, frame(Vec3.ZERO, Vec3.ZERO, 10, 0), random, out::add);
        out.clear();
        particles.damage(info, frame(new Vec3(1.49999996, 0, 0), Vec3.ZERO, 10, 0), random, out::add);
        assertEquals(6, out.size(), "rounding distance squared before sqrt would incorrectly emit only five");
    }

    @Test
    void splashesRequireMovementAndWaterAndUseContentSettings() {
        PlaneInfo info = info();
        info.particleSplashs.add(AircraftInfo.ParticleSplash.of(new Vec3(0, -1, -2), 3, 4, 2, 20, 0.3F, -0.02F));
        List<ParticleParam> wet = new ArrayList<>();
        PlaneParticles.Frame moving = frame(new Vec3(20, 60, 10), new Vec3(19, 60, 10), 100, 0);
        PlaneParticles.splash(info, moving, RandomSource.create(4), new World(true, true), wet::add);
        assertEquals(3, wet.size());
        for (ParticleParam p : wet) {
            assertEquals("smoke", p.name, "upstream's plane wake uses the smoke particle");
            assertEquals(0.3F, p.motionY);
            assertEquals(-0.02F, p.gravity);
            assertEquals(0.9F, p.a);
            assertTrue(p.size >= 3.2 && p.size <= 4.0);
            assertTrue(p.motionX >= -2.35 && p.motionX <= -1.65);
            assertTrue(p.age >= 20 && p.age < 30);
        }
        List<ParticleParam> dry = new ArrayList<>();
        PlaneParticles.splash(info, moving, RandomSource.create(4), new World(true, false), dry::add);
        assertTrue(dry.isEmpty());
        PlaneParticles.splash(info, frame(Vec3.ZERO, Vec3.ZERO, 100, 0),
                RandomSource.create(4), new World(true, true), dry::add);
        assertTrue(dry.isEmpty(), "stationary floatplanes have no wake");
    }

    @Test
    void landingDustUsesSampledBlockAndOpposesHorizontalMotion() {
        List<double[]> cracks = new ArrayList<>();
        PlaneParticles.Sink sink = new PlaneParticles.Sink() {
            @Override public void accept(ParticleParam p) { fail("landing dust is a block crack"); }
            @Override public void tileCrack(int bx, int by, int bz, double x, double y, double z,
                    double mx, double my, double mz) { cracks.add(new double[] {bx, by, bz, x, y, z, mx, my, mz}); }
        };
        PlaneParticles.landingGear(frame(new Vec3(-5.6, 30.1, 4.7), Vec3.ZERO, 100, 0), RandomSource.create(1), sink);
        assertEquals(1, cracks.size());
        double[] p = cracks.getFirst();
        assertArrayEquals(new double[] {-6, 29, 5}, new double[] {p[0], p[1], p[2]});
        assertEquals(-0.8, p[6]); assertEquals(1.5, p[7]); assertEquals(1.2, p[8]);
    }

    @Test
    void destroyedAttitudeFollowsUpstreamBoundaryRules() {
        assertEquals(1, PlaneVisualMotion.destroyedRollDirection(0));
        assertEquals(1, PlaneVisualMotion.destroyedRollDirection(90));
        assertEquals(-1, PlaneVisualMotion.destroyedRollDirection(91));
        assertEquals(-1, PlaneVisualMotion.destroyedRollDirection(-89));
        assertEquals(1, PlaneVisualMotion.destroyedRollDirection(-90));
        PlaneFlightModelTest.State body = new PlaneFlightModelTest.State();
        body.pitch = 9.9F; body.roll = 44.9F;
        PlaneVisualMotion.destroyed(body, false, 0.3F, 1.0F);
        assertEquals(10.2F, body.pitch, 1.0E-5); assertEquals(45.9F, body.roll, 1.0E-5);
        PlaneVisualMotion.destroyed(body, false, 0.3F, 1.0F);
        assertEquals(10.2F, body.pitch, 1.0E-5); assertEquals(45.9F, body.roll, 1.0E-5);
        body.pitch = -30; body.roll = 150;
        PlaneVisualMotion.destroyed(body, true, 0.3F, 1);
        assertEquals(-29.7F, body.pitch, 1.0E-5); assertEquals(150, body.roll);
        PlaneVisualMotion.destroyed(body, false, 0.3F, 1);
        assertEquals(151, body.roll);
    }

    @Test
    void exhaustCountersAndSteeringMatchUpstream() {
        PlaneExhaustAnimation animation = new PlaneExhaustAnimation();
        int[] expected = {0, 1, 1, 2, 2, 0, 0, 1};
        for (int frame : expected) {
            assertEquals(frame, animation.advance(0, 2, 2, 3));
            assertTrue(animation.advance(1, 2, 0, 2) < 2, "each exhaust has independent delay/frame counters");
        }
        assertEquals(0, animation.advance(0, 2, 2, 1), "resource reload can reduce available frames");
        assertEquals(-0.1F, PlaneExhaustAnimation.steer(0, true, false, 0.05F));
        assertEquals(0, PlaneExhaustAnimation.steer(1, true, true, 0.5F));
        assertEquals(1, PlaneExhaustAnimation.steer(0, false, true, 1.0F));
    }
}

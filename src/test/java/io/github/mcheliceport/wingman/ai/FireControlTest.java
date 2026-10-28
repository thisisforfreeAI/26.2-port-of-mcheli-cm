package io.github.mcheliceport.wingman.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.wingman.ai.FireControl.Ballistics;
import io.github.mcheliceport.wingman.ai.FireControl.Role;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The AI fire control's roles, ballistics, intercepts and bomb drops on plain values. */
class FireControlTest {

    /** A 20 mm gun pod: 8 blocks a tick, flown at 3.9 a tick twice over, no drop. */
    private static final Ballistics GUN = Ballistics.of("machinegun1", 8.0F, 0.0F, 0.0, false, 0);
    /** A slow, dropping round. */
    private static final Ballistics LOBBED = Ballistics.of("machinegun2", 3.0F, -0.04F, 0.0, false, 0);
    private static final Ballistics ROCKET = Ballistics.of("rocket", 4.0F, -0.01F, 0.02, false, 0);
    private static final Ballistics BOMB = Ballistics.of("bomb", 0.1F, -0.03F, 0.0, false, 0);

    /** How close a shot along {@code dir} passes a target moving with {@code vel}, flown independently. */
    private static double flyAndMeasure(Vec3 muzzle, Vec3 dir, Vec3 target, Vec3 vel, Vec3 acc, Ballistics b) {
        double[] pass = FireControl.closestPass(muzzle, dir, Vec3.ZERO, target, vel, acc, b);
        return new Vec3(pass[1], pass[2], pass[3]).length();
    }

    @Test
    void weaponTypesHaveRoles() {
        assertEquals(Role.GUN, FireControl.role("machinegun1"));
        assertEquals(Role.GUN, FireControl.role("MachineGun2"));
        assertEquals(Role.ROCKET, FireControl.role("rocket"));
        assertEquals(Role.AA_MISSILE, FireControl.role("aamissile"));
        assertEquals(Role.AG_MISSILE, FireControl.role("atmissile"));
        assertEquals(Role.AG_MISSILE, FireControl.role("tvmissile"));
        assertEquals(Role.BOMB, FireControl.role("bomb"));
        assertEquals(Role.NONE, FireControl.role("mkrocket"));
        assertEquals(Role.NONE, FireControl.role("smoke"));
        assertEquals(Role.NONE, FireControl.role(null));
        assertTrue(Role.GUN.ballistic() && Role.ROCKET.ballistic() && !Role.BOMB.ballistic());
    }

    @Test
    void eachTargetGetsTheWeaponMadeForIt() {
        // Aircraft: the air-to-air missile, then the gun; never bombs or ground missiles.
        assertTrue(FireControl.score(Role.AA_MISSILE, true, true, false, true)
                > FireControl.score(Role.GUN, true, true, false, true));
        assertEquals(0.0, FireControl.score(Role.BOMB, true, false, false, true));
        assertEquals(0.0, FireControl.score(Role.AG_MISSILE, true, true, false, true));
        // A tank: the anti-tank missile; an air-to-air missile not at all.
        assertTrue(FireControl.score(Role.AG_MISSILE, false, true, false, true)
                > FireControl.score(Role.ROCKET, false, true, false, true));
        assertEquals(0.0, FireControl.score(Role.AA_MISSILE, false, true, false, true));
        // A zombie: the gun before a missile.
        assertTrue(FireControl.score(Role.GUN, false, false, false, true)
                > FireControl.score(Role.AG_MISSILE, false, false, false, true));
        // Out of range a weapon counts for less.
        assertTrue(FireControl.score(Role.GUN, false, false, false, false)
                < FireControl.score(Role.GUN, false, false, false, true));
    }

    @Test
    void fastRoundsMoveAtTheCappedSpeedTimesTheirFactor() {
        assertEquals(3.9, GUN.speed(), 1e-6);
        assertEquals(2.0, GUN.scale(), 1e-6);
        assertEquals(7.8, GUN.muzzleSpeed(), 1e-6);
        assertEquals(1.0, LOBBED.scale(), 1e-6);
        assertTrue(GUN.halfStep() && !ROCKET.halfStep());
        assertTrue(BOMB.inherits() && BOMB.speed() == 0.0);
        assertEquals(30, Ballistics.of("rocket", 4.0F, 0.0F, 0.0, false, 30).life());
    }

    @Test
    void aStillTargetIsAimedStraightAt() {
        Vec3 muzzle = new Vec3(0, 100, 0);
        Vec3 target = new Vec3(10, 95, 80);
        FireControl.Solution s = FireControl.intercept(muzzle, Vec3.ZERO, target, Vec3.ZERO, Vec3.ZERO, GUN);
        assertNotNull(s);
        assertTrue(s.miss() < 0.1, "miss " + s.miss());
        assertTrue(FireControl.angle(s.dir(), target.subtract(muzzle)) < 0.5);
    }

    @Test
    void aCrossingTargetIsLedAndHit() {
        Vec3 muzzle = new Vec3(0, 100, 0);
        Vec3 target = new Vec3(0, 100, 100);
        // An aircraft crossing at 2 blocks a tick (40 a second).
        Vec3 vel = new Vec3(2.0, 0.0, 0.0);
        FireControl.Solution s = FireControl.intercept(muzzle, Vec3.ZERO, target, vel, Vec3.ZERO, GUN);
        assertNotNull(s);
        assertTrue(s.miss() < 0.25, "miss " + s.miss());
        // About 13 ticks of flight: the aim is some 26 blocks ahead of it, towards +x.
        assertTrue(s.dir().x > 0.2, "not led: " + s.dir());
        assertTrue(flyAndMeasure(muzzle, s.dir(), target, vel, Vec3.ZERO, GUN) < 0.25);
        // Aimed straight at it, the same burst misses by the whole lead.
        Vec3 straight = target.subtract(muzzle).normalize();
        assertTrue(flyAndMeasure(muzzle, straight, target, vel, Vec3.ZERO, GUN) > 10.0);
    }

    @Test
    void aDroppingRoundIsAimedHigh() {
        Vec3 muzzle = new Vec3(0, 100, 0);
        Vec3 target = new Vec3(0, 100, 90);
        FireControl.Solution s = FireControl.intercept(muzzle, Vec3.ZERO, target, Vec3.ZERO, Vec3.ZERO, LOBBED);
        assertNotNull(s);
        assertTrue(s.miss() < 0.25, "miss " + s.miss());
        assertTrue(FireControl.pitch(s.dir()) < -1.0F, "not aimed up: " + FireControl.pitch(s.dir()));
    }

    @Test
    void aDraggedRocketReachesATargetDivingAway() {
        Vec3 muzzle = new Vec3(0, 120, 0);
        Vec3 target = new Vec3(20, 90, 120);
        Vec3 vel = new Vec3(0.5, -0.2, 1.0);
        FireControl.Solution s = FireControl.intercept(muzzle, Vec3.ZERO, target, vel, Vec3.ZERO, ROCKET);
        assertNotNull(s);
        assertTrue(s.miss() < 0.5, "miss " + s.miss());
    }

    @Test
    void aTurningTargetIsLedAlongItsTurn() {
        Vec3 muzzle = new Vec3(0, 100, 0);
        Vec3 target = new Vec3(0, 100, 120);
        Vec3 vel = new Vec3(1.5, 0.0, 0.0);
        // Pulling up across the line of fire.
        Vec3 acc = new Vec3(0.0, 0.08, 0.0);
        FireControl.Solution turn = FireControl.intercept(muzzle, Vec3.ZERO, target, vel, acc, GUN);
        assertNotNull(turn);
        assertTrue(flyAndMeasure(muzzle, turn.dir(), target, vel, acc, GUN) < 0.25);
        FireControl.Solution straight = FireControl.intercept(muzzle, Vec3.ZERO, target, vel, Vec3.ZERO, GUN);
        assertTrue(flyAndMeasure(muzzle, straight.dir(), target, vel, acc, GUN) > 2.0,
                "a turn of 0.08 a tick should take the target off a straight lead");
    }

    @Test
    void aBombFallsShortUntilTheReleasePointThenLongAfter() {
        Vec3 vel = new Vec3(0.0, 0.0, 1.5);
        Vec3 target = new Vec3(0.0, 64.0, 0.0);
        // Released 300 blocks short, the bomb lands short; right over the target, long.
        FireControl.Drop early = FireControl.drop(new Vec3(0, 164, -300), vel, target, Vec3.ZERO, BOMB);
        FireControl.Drop late = FireControl.drop(new Vec3(0, 164, 0), vel, target, Vec3.ZERO, BOMB);
        assertNotNull(early);
        assertNotNull(late);
        assertTrue(early.along() > 0.0 && late.along() < 0.0, early + " " + late);
        assertTrue(early.cross() < 1e-6);
        // Somewhere between, a release lands on it: search it out.
        double lo = -300;
        double hi = 0;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            FireControl.Drop d = FireControl.drop(new Vec3(0, 164, mid), vel, target, Vec3.ZERO, BOMB);
            if (d.along() > 0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        FireControl.Drop hit = FireControl.drop(new Vec3(0, 164, lo), vel, target, Vec3.ZERO, BOMB);
        assertEquals(0.0, hit.impact().z, 0.05);
        // 100 blocks at 0.03 a tick squared is about 82 ticks of fall, about 120 blocks of throw.
        assertTrue(hit.ticks() > 75 && hit.ticks() < 90, "fall " + hit.ticks());
        assertTrue(-lo > 110 && -lo < 125, "throw " + -lo);
        // A bomb from a hovering aircraft has no track to aim along.
        assertNull(FireControl.drop(new Vec3(0, 164, 0), Vec3.ZERO, target, Vec3.ZERO, BOMB));
    }

    @Test
    void aBombLeadsAMovingTarget() {
        Vec3 vel = new Vec3(0.0, 0.0, 1.5);
        Vec3 target = new Vec3(10.0, 64.0, 0.0);
        FireControl.Drop still = FireControl.drop(new Vec3(0, 164, -100), vel, target, Vec3.ZERO, BOMB);
        FireControl.Drop moving = FireControl.drop(new Vec3(0, 164, -100), vel, target, new Vec3(-0.12, 0.0, 0.0), BOMB);
        assertEquals(10.0, still.cross(), 0.05);
        // Moving 0.12 a tick towards the track, over about 82 ticks it arrives on it.
        assertTrue(moving.cross() < 1.0, "cross " + moving.cross());
    }

    @Test
    void aMountLaysItsShotWithinItsArc() {
        FireControl.Mount fixed = FireControl.Mount.fixed(0.0F, 0.0F, 2.0F);
        float[] in = fixed.clamp(90.0F, 5.0F, 91.0F, 4.0F);
        assertEquals(91.0F, in[0], 1e-4F);
        assertEquals(4.0F, in[1], 1e-4F);
        float[] out = fixed.clamp(90.0F, 5.0F, 120.0F, 30.0F);
        assertEquals(92.0F, out[0], 1e-4F);
        assertEquals(7.0F, out[1], 1e-4F);
        // A gunship's side gun, fixed 90 degrees left, still lays its 2 degrees about that.
        FireControl.Mount side = FireControl.Mount.fixed(-90.0F, 10.0F, 2.0F);
        float[] s = side.clamp(0.0F, 0.0F, -91.0F, 11.0F);
        assertEquals(-91.0F, s[0], 1e-4F);
        assertEquals(11.0F, s[1], 1e-4F);
        assertEquals(-90.0F, side.centre(0.0F, 0.0F)[0], 1e-4F);
    }

    @Test
    void lookAndAnglesAgreeWithMinecraft() {
        Vec3 south = FireControl.look(0.0F, 0.0F);
        assertEquals(1.0, south.z, 1e-9);
        Vec3 west = FireControl.look(90.0F, 0.0F);
        assertEquals(-1.0, west.x, 1e-9);
        Vec3 down = FireControl.look(0.0F, 90.0F);
        assertEquals(-1.0, down.y, 1e-9);
        assertEquals(90.0F, FireControl.yaw(west), 1e-4F);
        assertEquals(45.0F, FireControl.pitch(new Vec3(0, -1, 1)), 1e-4F);
        assertEquals(90.0, FireControl.angle(south, west), 1e-6);
        // A 2-block target 100 blocks off fills a little over a degree.
        assertEquals(1.146, FireControl.tolerance(2.0, 100.0), 1e-3);
    }
}

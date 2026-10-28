package io.github.mcheliceport.wingman.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** M10d terrain lookahead, the missile break heading and the skill levels on plain values. */
class PilotAiTest {

    /** Flat ground at 64 with a ridge at z 100 to 102 whose top is 120. */
    private static final TerrainAvoidance.Ground RIDGE = (x, z) -> z >= 100 && z <= 102 ? 120 : 64;

    @Test
    void hoveringSeesOnlyTheGroundUnderIt() {
        assertEquals(64, TerrainAvoidance.highestAhead(RIDGE, new Vec3(0, 90, 60), Vec3.ZERO, new int[] {2, 4, 8}));
    }

    @Test
    void theRidgeIsSeenOnlyAsFarAheadAsTheSkillLooks() {
        // 1 block a tick north: 20 blocks a second, the ridge 40 blocks ahead, 2 seconds out.
        Vec3 pos = new Vec3(0, 90, 60);
        Vec3 vel = new Vec3(0, 0, 1);
        assertEquals(120, TerrainAvoidance.highestAhead(RIDGE, pos, vel, AiSkill.EASY.lookahead()));
        // 80 blocks short of the ridge: 4 seconds out, past Easy's 2 but within Normal's 4.
        Vec3 far = new Vec3(0, 90, 20);
        assertEquals(64, TerrainAvoidance.highestAhead(RIDGE, far, vel, AiSkill.EASY.lookahead()));
        assertEquals(120, TerrainAvoidance.highestAhead(RIDGE, far, vel, AiSkill.NORMAL.lookahead()));
        // 160 blocks short: only the Veteran's 8 seconds reach it.
        Vec3 farther = new Vec3(0, 90, -60);
        assertEquals(64, TerrainAvoidance.highestAhead(RIDGE, farther, vel, AiSkill.NORMAL.lookahead()));
        assertEquals(120, TerrainAvoidance.highestAhead(RIDGE, farther, vel, AiSkill.VETERAN.lookahead()));
    }

    @Test
    void everySecondInBetweenIsChecked() {
        // A ridge 60 blocks ahead is 3 seconds out, between Normal's 2 and 4 second points.
        assertEquals(120, TerrainAvoidance.highestAhead(RIDGE, new Vec3(0, 90, 40), new Vec3(0, 0, 1), new int[] {2, 4}));
    }

    @Test
    void theSafeHeightKeepsTheClearance() {
        assertEquals(138, TerrainAvoidance.safeY(90, 120, 18));
        assertEquals(200, TerrainAvoidance.safeY(200, 120, 18));
    }

    @Test
    void theBreakBeamsTheMissileOnTheNearerSide() {
        // A missile due south (+z, bearing 0) of an aircraft heading 80 degrees: it turns to 90.
        Vec3 at = Vec3.ZERO;
        assertEquals(90.0F, PilotAi.beam(80.0F, at, new Vec3(0, 0, 50)), 1e-4F);
        assertEquals(-90.0F, PilotAi.beam(-100.0F, at, new Vec3(0, 0, 50)), 1e-4F);
        // From the east (-x is bearing 90 in Minecraft's yaw): beam 0 or 180.
        float beam = PilotAi.beam(10.0F, at, new Vec3(-50, 0, 0));
        assertEquals(0.0F, beam, 1e-4F);
    }

    @Test
    void betterPilotsLookFurtherKeepLessClearanceAndReactSooner() {
        AiSkill[] skills = AiSkill.values();
        for (int i = 1; i < skills.length; i++) {
            AiSkill worse = skills[i - 1];
            AiSkill better = skills[i];
            assertTrue(better.lookahead().length > worse.lookahead().length, better + " looks further");
            assertTrue(better.clearance() < worse.clearance(), better + " flies lower");
            assertTrue(better.reactionTicks() < worse.reactionTicks(), better + " reacts sooner");
        }
        assertEquals(0, AiSkill.EASY.breakTicks(), "an Easy pilot only fires its decoys");
        assertTrue(AiSkill.VETERAN.fliesLow() && !AiSkill.NORMAL.fliesLow());
    }
}

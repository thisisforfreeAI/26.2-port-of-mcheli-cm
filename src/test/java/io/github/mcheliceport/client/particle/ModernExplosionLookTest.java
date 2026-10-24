package io.github.mcheliceport.client.particle;
import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.effect.BlastStyle;
import org.junit.jupiter.api.Test;
class ModernExplosionLookTest {
    @Test void preferenceIsOptInAndGlobalClassicOrVanillaRemainAuthoritative() {
        assertFalse(ClientConfig.MODERN_EXPLOSIONS.getDefault());
        assertFalse(ModernExplosionLook.active(false,true,false));
        assertFalse(ModernExplosionLook.active(true,false,false));
        assertFalse(ModernExplosionLook.active(true,true,true));
        assertTrue(ModernExplosionLook.active(true,true,false));
    }
    @Test void heavyPenetratorSmokeFollowsTheCraterWithoutInflatingItsFireProfile() {
        float nominal=ExplosionSequence.radius(15,BlastStyle.PENETRATOR);
        float radius=ModernExplosionLook.craterRadius(8,nominal,17,15,BlastStyle.PENETRATOR,ClientConfig.EffectBudget.BALANCED);
        assertTrue(radius>8 && radius<=16);
        assertTrue(radius>=17*.74F);
        assertEquals(nominal,ExplosionSequence.radius(15,BlastStyle.PENETRATOR));
        assertTrue(ModernExplosionLook.craterRadius(3,nominal,6,4,BlastStyle.PENETRATOR,ClientConfig.EffectBudget.BALANCED)>=4.5F);
        assertTrue(ModernExplosionLook.craterRadius(3,nominal,8,4,BlastStyle.BLAST,ClientConfig.EffectBudget.BALANCED)>=6);
        assertEquals(8,ModernExplosionLook.craterRadius(8,nominal,Float.NaN,15,BlastStyle.PENETRATOR,ClientConfig.EffectBudget.BALANCED));
    }
    @Test void shortWarheadFireAndLongIncendiaryFireRetainSmokeWithinThePrimaryQuota() {
        assertTrue(ModernExplosionLook.fireTicks(20,BlastStyle.HEAVY)<20);
        assertEquals(52,ModernExplosionLook.fireTicks(52,BlastStyle.INCENDIARY));
        for(int count=2;count<=28;count++)for(var style:BlastStyle.values()) {
            int smoke=ModernExplosionLook.smallSmokeLobes(count,style);
            assertTrue(smoke>=1 && smoke<count);
        }
        assertEquals(0,ModernExplosionLook.smallSmokeLobes(0,BlastStyle.BLAST));
        assertEquals(0,ModernExplosionLook.smallSmokeLobes(1,BlastStyle.BLAST));
    }
}

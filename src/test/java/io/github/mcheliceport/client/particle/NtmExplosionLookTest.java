package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import org.junit.jupiter.api.Test;

class NtmExplosionLookTest {
    @Test void vulkanCannotEnterTheNativeDriverCrashPath() {
        assertFalse(NtmExplosionLook.acceleratedBackend("Vulkan"));
        assertFalse(NtmExplosionLook.acceleratedBackend(null));
        assertTrue(NtmExplosionLook.acceleratedBackend("OpenGL"));
    }
    @Test void craterFootprintCanPromoteNarrowWarheadsWithoutChangingDamage() {
        assertTrue(NtmExplosionLook.profile(2,4).small());
        assertFalse(NtmExplosionLook.profile(2,16).small());
        assertTrue(NtmExplosionLook.profile(4,18).scale()>NtmExplosionLook.profile(4,0).scale());
        assertEquals(30,NtmExplosionLook.profile(12,0).clouds());
        assertEquals(10,NtmExplosionLook.profile(12,0).faces());
        assertEquals(1,NtmExplosionLook.profile(2,0).faces());
    }
    @Test void pathologicalPayloadsCannotRequestUnboundedCloudsOrSizes() {
        for(float power:new float[]{Float.NaN,Float.NEGATIVE_INFINITY,0,-1}) {
            var profile=NtmExplosionLook.profile(power,Float.MAX_VALUE);
            assertEquals(0,profile.clouds());
            assertEquals(0,NtmExplosionLook.clouds(profile,EffectBudget.HIGH,0,0,0));
        }
        for(float power:new float[]{.01F,2,4,12,Float.MAX_VALUE})
            for(float footprint:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,0,8,31.5F,Float.MAX_VALUE}) {
                var p=NtmExplosionLook.profile(power,footprint);
                assertTrue(Float.isFinite(p.scale()) && p.scale()>0 && p.scale()<=12);
                assertTrue(p.clouds()>0 && p.clouds()<=30);
                assertTrue(p.life()<=89);
            }
    }
    @Test void pressureDistanceAndParticleSettingReduceCostAndPreserveOneCore() {
        var p=NtmExplosionLook.profile(12,16);
        int near=NtmExplosionLook.clouds(p,EffectBudget.BALANCED,0,0,0);
        for(var q:EffectBudget.values())for(int pressure=0;pressure<=3;pressure++)for(int setting=0;setting<=2;setting++) {
            int count=NtmExplosionLook.clouds(p,q,pressure,200*200,setting);
            assertTrue(count>=1 && count<=near);
            assertTrue(NtmExplosionLook.tickFaces(q,pressure)>=p.faces());
            assertTrue(NtmExplosionLook.tickFaces(q,pressure)<=NtmExplosionLook.liveFaces(q));
        }
        assertTrue(NtmExplosionLook.clouds(p,EffectBudget.BALANCED,3,0,0)<near);
    }
}

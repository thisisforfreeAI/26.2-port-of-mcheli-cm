package io.github.mcheliceport.client.particle;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.weapon.WeaponInfo;
class CookoffAmmoTest {
    @Test void turretCookoffsRequireRealExplosiveStoresAndExcludeNtmAndTrainingEffects(){
        var info=new WeaponInfo(ContentLocation.builtin("test"),"test","txt");
        assertFalse(CookoffEffects.explosiveStore(info));info.explosion=4;assertTrue(CookoffEffects.explosiveStore(info));
        info.useHBM=true;assertFalse(CookoffEffects.explosiveStore(info));info.useHBM=false;info.payloadNTM=WeaponInfo.Payload.NTM_NUKE;assertFalse(CookoffEffects.explosiveStore(info));
        info.payloadNTM=WeaponInfo.Payload.NONE;info.effectOnly=true;assertFalse(CookoffEffects.explosiveStore(info));
        assertFalse(DestructionTimeline.turret("t-90",0,7));
    }
}

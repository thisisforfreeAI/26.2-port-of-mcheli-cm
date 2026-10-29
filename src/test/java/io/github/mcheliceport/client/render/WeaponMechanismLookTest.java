package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.client.model.ObjParser;
import org.junit.jupiter.api.Test;

class WeaponMechanismLookTest {
    @Test void bayIsFullyOpenWithinTheExistingFifteenTickSwitchWait(){
        assertEquals(0,WeaponMechanismLook.bay(0));assertEquals(90,WeaponMechanismLook.bay(15*3));
        assertEquals(90,WeaponMechanismLook.bay(90));
        float before=0;for(int angle=0;angle<=90;angle++){float now=WeaponMechanismLook.bay(angle);assertTrue(now>=before&&now<=90);before=now;}
    }
    @Test void recoilHasASharperAttackWithoutExceedingAuthoredTravel(){
        assertTrue(WeaponMechanismLook.recoil(.25F)>.25F);assertEquals(0,WeaponMechanismLook.recoil(0));assertEquals(1,WeaponMechanismLook.recoil(1));
        for(float raw:new float[]{-10,0,.1F,.5F,1,10,Float.NaN})assertTrue(WeaponMechanismLook.recoil(raw)>=0&&WeaponMechanismLook.recoil(raw)<=1);
    }
    @Test void barrelInterpolationCannotReverseOrOvershootIncludingAWrappedTick(){
        for(float start:new float[]{-20,0,350})for(float step:new float[]{0,1,26,74})for(float previousStep:new float[]{0,1,52,74}){
            float last=start;for(int i=0;i<=100;i++){float angle=WeaponMechanismLook.barrel(start,start+step,previousStep,i/100F);assertTrue(angle>=start-.0001&&angle<=start+step+.0001);assertTrue(angle>=last-.0001);last=angle;}
            assertEquals(start+step,last,.0001);
        }
    }
    @Test void knownClosedFeedsRetainCasesAndFallbackDimensionsMatchTheActualCaseMesh()throws Exception{
        for(String name:new String[]{"gau-8","m61a1_l","gau-22a","rehinmetall_he"})assertTrue(WeaponMechanismLook.retainsCases(name));
        for(String name:new String[]{"m230","m134","m2hb","addon-gun"})assertFalse(WeaponMechanismLook.retainsCases(name));
        try(var stream=getClass().getResourceAsStream("/assets/mcheli/models/bullets/cartridge.obj")){
            var mesh=ObjParser.parse("cartridge",stream);float length=mesh.maxZ-mesh.minZ;
            assertEquals(.113,length*WeaponMechanismLook.caseScale("m230"),.005);
            assertEquals(.051,length*WeaponMechanismLook.caseScale("m134"),.005);
            assertEquals(.099,length*WeaponMechanismLook.caseScale("m2hb"),.005);
        }
    }
    @Test void eitherNtmFlagExcludesTheNewMechanismAndFallbackPath(){
        var info=new io.github.mcheliceport.content.weapon.WeaponInfo(io.github.mcheliceport.content.ContentLocation.builtin("scope"),"scope.yml","test");
        assertTrue(WeaponMechanisms.conventional(info));info.useHBM=true;assertFalse(WeaponMechanisms.conventional(info));
        info.useHBM=false;info.payloadNTM=io.github.mcheliceport.content.weapon.WeaponInfo.Payload.NTM_NUKE;assertFalse(WeaponMechanisms.conventional(info));
    }
    @Test void oversizedKnownStandardCasesAreCappedWithoutUpsizingOrChangingUnknownModels(){
        assertEquals(1.13F,WeaponMechanismLook.authoredCaseScale("m230",3.5F));
        assertEquals(.51F,WeaponMechanismLook.authoredCaseScale("m134",2));
        assertEquals(.3F,WeaponMechanismLook.authoredCaseScale("m230",.3F));
        assertEquals(3.5F,WeaponMechanismLook.authoredCaseScale("addon-gun",3.5F));
    }
}

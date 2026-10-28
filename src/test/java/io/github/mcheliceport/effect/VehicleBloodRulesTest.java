package io.github.mcheliceport.effect;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VehicleBloodRulesTest {
    @Test void ntmPayloadsAndModWeaponsAreNeverConventional() {
        var info = new WeaponInfo(ContentLocation.builtin("blood_test"),"test","txt");
        assertTrue(VehicleBloodRules.conventional(info));
        info.useHBM=true; assertFalse(VehicleBloodRules.conventional(info)); info.useHBM=false;
        for(var payload:WeaponInfo.Payload.values()) if(payload!=WeaponInfo.Payload.NONE) {
            info.payloadNTM=payload; assertFalse(VehicleBloodRules.conventional(info));
        }
        info.payloadNTM=WeaponInfo.Payload.NONE; info.requiredMod="hbm";
        assertFalse(VehicleBloodRules.conventional(info));
        info.requiredMod=null; info.effectOnly=true; assertFalse(VehicleBloodRules.conventional(info));
        assertFalse(VehicleBloodRules.conventional(null));
        assertFalse(VehicleBloodRules.allowedNamespace("hbm"));
        assertFalse(VehicleBloodRules.allowedNamespace("ntm"));
    }

    @Test void deduplicationAndBurstBudgetsBoundMassKillsWithoutDelayedReplay() {
        var gate=new VehicleBloodRules.Gate(2);
        var a=new UUID(0,1);var b=new UUID(0,2);var c=new UUID(0,3);
        assertTrue(gate.admit(a,1));assertFalse(gate.admit(a,1));
        assertTrue(gate.admit(b,1));assertFalse(gate.admit(c,1));
        assertFalse(gate.contains(c)); assertFalse(gate.admit(c,2));
        assertTrue(gate.admit(new UUID(0,4),2));
        assertTrue(gate.admit(a,201));
        for(int i=0;i<2000;i++)gate.admit(new UUID(1,i),202);
        assertTrue(gate.size()<=1024);
        assertTrue(gate.admit(b,0)); // New world/timeline cannot inherit old throttling.
    }
}

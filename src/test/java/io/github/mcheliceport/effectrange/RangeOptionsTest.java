package io.github.mcheliceport.effectrange;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class RangeOptionsTest {
    @Test void resetFootprintIsUniqueBoundedAndAirClearsBeforeBottomUpTerrain(){
        var seen=new HashSet<Integer>();int previousFloor=-13;
        for(int i=0;i<RangeOptions.TOTAL;i++){
            var p=RangeOptions.block(i);assertTrue(Math.abs(p[0])<=24&&Math.abs(p[2])<=24&&p[1]>=-12&&p[1]<32);
            assertTrue(seen.add((p[0]+24)+(p[2]+24)*49+(p[1]+12)*49*49));
            if(i<RangeOptions.AREA*RangeOptions.AIR_HEIGHT)assertTrue(p[1]>=0);
            else{assertTrue(p[1]>=previousFloor);previousFloor=p[1];}
        }
        assertEquals(49*49*44,seen.size());assertThrows(IllegalArgumentException.class,()->RangeOptions.block(RangeOptions.TOTAL));
    }
    @Test void fixedWeaponChoicesNeverAdmitNtmOrDestructPayloads(){
        assertEquals(List.of("hydra70","agm158","mk82","gbu57"),RangeOptions.WEAPONS);
        var info=new WeaponInfo(ContentLocation.builtin("range_test"),"range_test.yml","test");assertTrue(RangeOptions.conventional(info));
        info.useHBM=true;assertFalse(RangeOptions.conventional(info));info.useHBM=false;info.payloadNTM=WeaponInfo.Payload.NTM_NUKE;assertFalse(RangeOptions.conventional(info));
        info.payloadNTM=WeaponInfo.Payload.NONE;info.destruct=true;assertFalse(RangeOptions.conventional(info));
        assertFalse(RangeOptions.valid(-1,0,0));assertFalse(RangeOptions.valid(0,4,0));assertFalse(RangeOptions.valid(0,0,3));
    }
    @Test void rangeUsesItsOwnDimensionClockAndTimelineRatherThanChangingTheOverworld()throws Exception{
        for(String kind:List.of("dimension_type","timeline"))try(var in=getClass().getResourceAsStream("/data/mcheli_ce_port/"+kind+"/effects_range.json")){
            var json=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("mcheli_ce_port:effects_range",json.get(kind.equals("dimension_type")?"default_clock":"clock").getAsString());
        }
        assertEquals(6000,new RangeOptions(0,0,0).time());assertEquals(18000,new RangeOptions(0,0,2).time());
    }
}

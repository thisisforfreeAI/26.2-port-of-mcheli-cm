package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CockpitLightMeshTest {
    @Test void stockInstrumentRegionsProduceSmallFiniteUvPreservingOverlays()throws Exception{
        for(String name:new String[]{"f-35a","f-15e","a-10"}){
            try(var stream=getClass().getResourceAsStream("/assets/mcheli/models/planes/"+name+".mqo")){
                var model=MqoParser.parse(name,stream);Vec3 center=name.equals("f-35a")?new Vec3(0,2.5,1.9):name.equals("f-15e")?new Vec3(0,3,1.6):new Vec3(0,2.7,0);
                var lamp=new AircraftInfo.FlightLamp(center,"body",AircraftInfo.FlightLampKind.COCKPIT,0x93BFAD,.7F);
                var groups=CockpitLightMesh.build(model.part("$body").getFirst(),lamp);assertFalse(groups.isEmpty(),name);
                assertTrue(groups.getFirst().vertexCount()<=4096);for(float value:groups.getFirst().data())assertTrue(Float.isFinite(value));
            }
        }
    }
}

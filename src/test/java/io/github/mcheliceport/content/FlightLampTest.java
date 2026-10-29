package io.github.mcheliceport.content;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.emitter.YamlEmitter;
import io.github.mcheliceport.content.parser.txt.TxtParser;
import io.github.mcheliceport.content.parser.yaml.YamlParser;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class FlightLampTest {
    @Test void onlyTheThreeAuthoredStockAircraftGainOptionalFlightLighting(){
        var stock=ContentTestSupport.load(ContentTestSupport.stockPack());int optional=0;
        for(var info:stock.aircraft()){
            boolean supported=List.of("f-35a","f-15e","a-10").contains(info.name);
            assertEquals(supported?5:0,info.flightLamps.size(),info.name);
            if(supported){assertEquals(1,info.flightLamps.stream().filter(l->l.kind()==AircraftInfo.FlightLampKind.COCKPIT).count());assertEquals(1,info.searchLights.size());assertTrue(info.searchLights.getFirst().m8Only);}
            optional+=(int)info.searchLights.stream().filter(l->l.m8Only).count();
        }
        assertEquals(3,optional);
    }
    @Test void authoredLampAndOptionalSearchlightSurviveTxtYamlRoundTrip()throws Exception{
        var location=ContentLocation.builtin("flight_lamp");
        var info=TxtParser.INSTANCE.parsePlane(location,"flight_lamp.txt",List.of("AddFlightLamp = STROBE,1,2,-3,0xFFFFFF,0.1,body","AddFixedSearchLight = 0,1,0,0x80FFFFFF,0x00FFFFFF,48,9,0,12,0,true"),false);
        assertEquals(1,info.flightLamps.size());assertTrue(info.searchLights.getFirst().m8Only);
        var yaml=new YamlEmitter().emitPlane(info);var restored=YamlParser.INSTANCE.parsePlane(location,"flight_lamp.yml",List.of(yaml.split("\n")),false);
        assertEquals(info.flightLamps,restored.flightLamps);assertTrue(restored.searchLights.getFirst().m8Only);
        var old=TxtParser.INSTANCE.parsePlane(location,"old_light.txt",List.of("AddFixedSearchLight = 0,1,0,0x80FFFFFF,0x00FFFFFF,48,9"),false);assertFalse(old.searchLights.getFirst().m8Only);
    }
    @Test void invalidLampGeometryAndPartsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new AircraftInfo.FlightLamp(new Vec3(Double.NaN,0,0),"body",AircraftInfo.FlightLampKind.NAVIGATION,0xFFFFFF,.1F));
        assertThrows(IllegalArgumentException.class,()->new AircraftInfo.FlightLamp(Vec3.ZERO,"../other",AircraftInfo.FlightLampKind.NAVIGATION,0xFFFFFF,.1F));
        assertThrows(IllegalArgumentException.class,()->new AircraftInfo.FlightLamp(Vec3.ZERO,"body",AircraftInfo.FlightLampKind.NAVIGATION,0xFFFFFFFF,.1F));
        assertThrows(IllegalArgumentException.class,()->new AircraftInfo.FlightLamp(Vec3.ZERO,"body",AircraftInfo.FlightLampKind.NAVIGATION,0xFFFFFF,Float.POSITIVE_INFINITY));
    }
}

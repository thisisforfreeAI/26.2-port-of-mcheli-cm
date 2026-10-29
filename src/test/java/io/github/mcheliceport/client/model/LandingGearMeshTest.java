package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LandingGearMeshTest {
    @Test void stockF35GearDoorsAreExcludedFromLoadedFootprints()throws Exception{
        try(var mesh=getClass().getResourceAsStream("/assets/mcheli/models/planes/f-35a.mqo");var yaml=getClass().getResourceAsStream("/assets/mcheli/planes/f-35a.yml")){
            var model=MqoParser.parse("f-35a",mesh);
            var text=new String(yaml.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            var info=io.github.mcheliceport.content.parser.yaml.YamlParser.INSTANCE.parsePlane(io.github.mcheliceport.content.ContentLocation.builtin("landing"),"f-35a.yml",java.util.List.of(text.split("\n")),false);
            LandingGearMesh.prepare(info,new AircraftModels.VehicleModels(model,java.util.Map.of()));
            for(String part:new String[]{"$lg0","$lg3","$lg4"})assertNotNull(LandingGearMesh.get(model.part(part).getFirst()));
            for(String part:new String[]{"$lg1","$lg2"})assertNull(LandingGearMesh.get(model.part(part).getFirst()));
        }
    }
    @Test void stockPrimaryGearHasSeparateRealWheelFootprints()throws Exception{
        for(String name:new String[]{"f-35a","f-15e","a-10"}){
            try(var input=getClass().getResourceAsStream("/assets/mcheli/models/planes/"+name+".mqo")){
                assertNotNull(input);var model=MqoParser.parse(name,input);
                String[] parts=name.equals("f-35a")?new String[]{"$lg0","$lg3","$lg4"}:name.equals("f-15e")?new String[]{"$lg0","$lg1","$lg2"}:new String[]{"$lg0","$lg1"};
                int contacts=0;for(String part:parts){var shape=LandingGearMesh.build(model.part(part).getFirst());assertNotNull(shape);assertTrue(shape.bottom()<0);contacts+=shape.contacts().size();}
                assertEquals(3,contacts,"Three wheel contacts on "+name);
            }
        }
    }
    @Test void emptyOversizedAndInvalidGeometryCannotSupplyAContact(){
        assertNull(LandingGearMesh.build(new McheliModel.Group("empty",new float[0])));
        assertNull(LandingGearMesh.build(new McheliModel.Group("oversized",new float[20001*8])));
        assertNull(LandingGearMesh.build(new McheliModel.Group("invalid",new float[]{Float.NaN,0,0,0,0,0,1,0})));
    }
}

package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.content.ContentTestSupport;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class TankBlowoutMeshTest {
    @Test void stockAbramsRoofActuallyOpensAndPreservesItsOriginalSurface() throws Exception{
        var file=ContentTestSupport.projectDir().resolve("src/main/resources/assets/mcheli/models/tanks/m1a2.mqo");
        McheliModel model;try(var in=Files.newInputStream(file)){model=MqoParser.parse("m1a2",in);}
        var turret=model.part("$weapon0").getFirst();var roof=TankBlowoutMesh.build(turret);
        assertNotNull(roof);assertEquals(2,roof.panels().size());
        double area=BreakupMeshes.area(roof.kept());
        for(var panel:roof.panels()){
            assertTrue(BreakupMeshes.area(panel.mesh())>.5);
            var b=BreakupMeshes.bounds(panel.mesh());assertTrue(b.low().y>2.64&&b.high().y<2.8);
            assertTrue(b.high().z<-1.73,"Crew hatches must remain attached");
            area+=BreakupMeshes.area(panel.mesh());
        }
        assertEquals(BreakupMeshes.area(turret),area,.001);
        assertTrue(BreakupMeshes.area(roof.kept())>BreakupMeshes.area(turret)*.85);
        assertTrue(roof.cavity().vertexCount()>0);
    }
}

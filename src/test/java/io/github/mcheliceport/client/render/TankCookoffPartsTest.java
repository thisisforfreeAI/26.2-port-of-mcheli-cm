package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.client.model.*;
import io.github.mcheliceport.content.ContentTestSupport;
import java.nio.file.Files;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class TankCookoffPartsTest {
    @Test void allStockCarouselTurretPiecesKeepTheirAimedPoses() throws Exception{
        for(String name:List.of("t-90","t84")){
            var file=ContentTestSupport.projectDir().resolve("src/main/resources/assets/mcheli/models/tanks/"+name+".mqo");
            McheliModel model;try(var in=Files.newInputStream(file)){model=MqoParser.parse(name,in);}
            var draws=new ArrayList<AircraftPartPoses.Draw>();int count=0;
            var turn=new Matrix4f().translate(0,.35F,0).rotateY(.83F);
            for(var g:model.groups)if(g.name().startsWith("$weapon")){
                var pose=new Matrix4f(turn);if(!g.name().equals("$weapon0"))pose.translate(0,1.6F,0).rotateX(-.2F).translate(0,-1.6F,0);
                draws.add(new AircraftPartPoses.Draw(pose,List.of(g)));count+=g.vertexCount();
            }
            var snap=TankCookoffParts.snapshot(draws);assertNotNull(snap,name);assertEquals(count,snap.mesh().vertexCount());
            var input=draws.getFirst();var d=input.groups().getFirst().data();var expected=input.pose().transformPosition(new Vector3f(d[0],d[1],d[2]));
            var out=snap.mesh().data();assertEquals(expected.x,out[0],1e-5);assertEquals(expected.y,out[1],1e-5);assertEquals(expected.z,out[2],1e-5);
            assertTrue(snap.mesh().vertexCount()<=12000);
        }
    }
}

package io.github.mcheliceport.client.model;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class PanelInteriorMeshTest {
    private static McheliModel.Group panel(String name){float[] d=new float[48];d[0]=-1;d[2]=-1;d[8]=1;d[9]=.2F;d[10]=1;return new McheliModel.Group(name,d);}
    @Test void canopyAddsRailsWithoutAnOpaqueCockpitFloor(){
        var result=PanelInteriorMesh.build(panel("$canopy0"));assertNotNull(result);
        assertEquals(0,result.dark().vertexCount());assertTrue(result.metal().vertexCount()>0);
    }
    @Test void hatchHasRecessedMachineryButUnknownBodyIsUntouched(){
        var result=PanelInteriorMesh.build(panel("$hatch0"));assertNotNull(result);assertTrue(result.dark().vertexCount()>0);
        assertTrue(result.dark().vertexCount()+result.metal().vertexCount()<800);
        assertNull(PanelInteriorMesh.build(panel("$body")));
    }
}

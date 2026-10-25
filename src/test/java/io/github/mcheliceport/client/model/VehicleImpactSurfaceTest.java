package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class VehicleImpactSurfaceTest {
    private static McheliModel.Group plane(float z){
        float[] data=new float[48];int at=0;
        for(float[] v:new float[][]{{-1,-1},{1,-1},{1,1},{-1,-1},{1,1},{-1,1}}){
            data[at++]=v[0];data[at++]=v[1];data[at++]=z;at+=4;data[at++]=1;
        }
        return new McheliModel.Group("$body",data);
    }
    @Test void rayFindsVisibleSurfaceAndRejectsEmptyBoxSpace(){
        var g=plane(0);var index=new VehicleImpactSurface.Index(g.data());
        assertEquals(2,index.ray(g.data(),new Vector3f(0,0,2),new Vector3f(0,0,-1),5).distance(),1e-6);
        assertNull(index.ray(g.data(),new Vector3f(2,0,2),new Vector3f(0,0,-1),5));
        assertNull(index.ray(g.data(),new Vector3f(0,0,2),new Vector3f(0,0,1),5));
    }
    @Test void decalClipsToPanelEdgesAndStaysOnSurface(){
        var g=plane(0);var index=new VehicleImpactSurface.Index(g.data());
        var origin=new Vector3f(.95F,.95F,2);var direction=new Vector3f(0,0,-1);
        var hit=index.ray(g.data(),origin,direction,5);
        var mesh=index.decal(g.data(),hit,origin,direction,.3F,.7F);
        assertFalse(mesh.vertices().isEmpty());assertTrue(mesh.vertices().size()<=VehicleImpactSurface.MAX_MARK_TRIANGLES*3);
        for(var v:mesh.vertices()){
            assertTrue(v.x()>=-1.0001&&v.x()<=1.0001);assertTrue(v.y()>=-1.0001&&v.y()<=1.0001);
            assertEquals(.003,v.z(),1e-6);assertTrue(v.u()>=-1e-6&&v.u()<=1.000001);assertTrue(v.v()>=-1e-6&&v.v()<=1.000001);
        }
    }
    @Test void indexChoosesNearestOfLayeredPanels(){
        var a=plane(0);var b=plane(-1);float[] data=new float[96];
        System.arraycopy(a.data(),0,data,0,48);System.arraycopy(b.data(),0,data,48,48);
        var index=new VehicleImpactSurface.Index(data);
        assertEquals(2,index.ray(data,new Vector3f(.1F,.2F,2),new Vector3f(0,0,-1),5).distance(),1e-6);
        var origin=new Vector3f(.1F,.2F,2);var direction=new Vector3f(0,0,-1);
        var mesh=index.decal(data,index.ray(data,origin,direction,5),origin,direction,.4F,0);
        assertTrue(mesh.vertices().stream().allMatch(v->Math.abs(v.z()-.003)<1e-6));
    }
    @Test void preparationIsExplicitAndCacheClearsOnReload(){
        var group=plane(0);VehicleImpactSurface.clear();assertNull(VehicleImpactSurface.get(group));
        VehicleImpactSurface.prepare(new McheliModel("fixture",List.of(group),-1,-1,0,1,1,0));
        assertNotNull(VehicleImpactSurface.get(group));VehicleImpactSurface.clear();assertNull(VehicleImpactSurface.get(group));
    }
}

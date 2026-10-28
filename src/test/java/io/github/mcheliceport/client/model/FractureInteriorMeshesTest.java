package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class FractureInteriorMeshesTest {
    @Test void disconnectedCutHolesStaySeparateAndTheOriginalSkinDoesNotChange() {
        var b=new DetailBoxes();b.box(-1,0,-3,1,.4F,-1);b.box(-1,0,1,1,.4F,3);
        var kept=BreakupMeshes.split(b.group("$body"),0,0)[1];var original=kept.data().clone();
        var details=FractureInteriorMeshes.build(kept,0,0,-1);
        assertEquals(2,details.contours());assertEquals(1.6,BreakupMeshes.area(details.cavity()),.003);
        var data=details.cavity().data();for(int i=0;i<data.length;i+=8){assertEquals(.025,data[i],.00001);assertTrue(Math.abs(data[i+2])>=.999);assertEquals(-1,data[i+5]);}
        assertArrayEquals(original,kept.data());assertTrue(details.ribs().vertexCount()>0);assertTrue(details.cables().vertexCount()>0);
    }
    @Test void concaveContoursDoNotGrowIntoAConvexFakeWall() {
        var source=prism(new float[][]{{0,0},{2,0},{2,1},{1,1},{1,2},{0,2}});
        var kept=BreakupMeshes.split(source,0,0)[1];var details=FractureInteriorMeshes.build(kept,0,0,-1);
        assertEquals(1,details.contours());assertEquals(3,BreakupMeshes.area(details.cavity()),.002);
        var d=details.cavity().data();for(int i=0;i<d.length;i+=24){
            double y=(d[i+1]+d[i+9]+d[i+17])/3,z=(d[i+2]+d[i+10]+d[i+18])/3;
            assertFalse(y>1&&z>1,"Cap filled the concave missing corner");
        }
    }
    @Test void openOrOverlyComplexCutsSkipTheCavityAndDetailGeometryStaysBounded() {
        var open=new McheliModel.Group("$body",new float[]{-1,0,-1,0,0,0,1,0, 1,0,-1,1,0,0,1,0, 1,0,1,1,1,0,1,0});
        var details=FractureInteriorMeshes.build(BreakupMeshes.split(open,0,0)[1],0,0,-1);assertEquals(0,details.cavity().vertexCount());
        float[][] circle=new float[160][2];for(int i=0;i<circle.length;i++){circle[i][0]=(float)Math.cos(i*Math.PI*2/circle.length);circle[i][1]=(float)Math.sin(i*Math.PI*2/circle.length);}
        assertEquals(0,FractureInteriorMeshes.build(BreakupMeshes.split(prism(circle),0,0)[1],0,0,-1).vertices());
        var b=new DetailBoxes();b.box(-7,0,-10,7,2,6);var frame=BreakupMeshes.airframeBuild(b.group("$body"),7);
        assertTrue(frame.fracture().vertices()<=3072);
        for(var g:new McheliModel.Group[]{frame.fracture().cavity(),frame.fracture().ribs(),frame.fracture().cables()})for(float value:g.data())assertTrue(Float.isFinite(value));
    }
    private static McheliModel.Group prism(float[][] points) {
        var d=new FloatArrayList();
        for(int i=0;i<points.length;i++) {
            var p=points[i];var q=points[(i+1)%points.length];
            Vec3 a=new Vec3(-1,p[1],p[0]),b=new Vec3(1,p[1],p[0]),c=new Vec3(1,q[1],q[0]),e=new Vec3(-1,q[1],q[0]);
            Vec3 normal=b.subtract(a).cross(c.subtract(a)).normalize();
            for(Vec3 v:new Vec3[]{a,b,c,a,c,e}){d.add((float)v.x);d.add((float)v.y);d.add((float)v.z);d.add(0);d.add(0);d.add((float)normal.x);d.add((float)normal.y);d.add((float)normal.z);}
        }
        return new McheliModel.Group("$body",d.toFloatArray());
    }
}

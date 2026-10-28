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
        var data=details.cavity().data();for(int i=0;i<data.length;i+=8){assertTrue(data[i]>=.025-1E-5&&data[i]<=.041,"cap sits just inside the cut");assertTrue(Math.abs(data[i+2])>=.999);assertEquals(-1,data[i+5]);}
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
    @Test void openOrDetailedCutsStillGetACapAndDetailGeometryStaysBounded() {
        var open=new McheliModel.Group("$body",new float[]{-1,0,-1,0,0,0,1,0, 1,0,-1,1,0,0,1,0, 1,0,1,1,1,0,1,0});
        var details=FractureInteriorMeshes.build(BreakupMeshes.split(open,0,0)[1],0,0,-1);assertEquals(0,details.cavity().vertexCount());
        // Detailed round fuselages used to skip the cap entirely and show straight through.
        for(int n:new int[]{160,600}) {
            float[][] circle=new float[n][2];for(int i=0;i<n;i++){circle[i][0]=(float)Math.cos(i*Math.PI*2/n);circle[i][1]=(float)Math.sin(i*Math.PI*2/n);}
            var cap=FractureInteriorMeshes.build(BreakupMeshes.split(prism(circle),0,0)[1],0,0,-1).cavity();
            assertEquals(Math.PI,BreakupMeshes.area(cap),.03,"cap for "+n+" sides");
        }
        // An open channel (no top skin) is not a closed contour, but its hollow must still be covered.
        var data=BreakupMeshes.split(prism(new float[][]{{0,0},{2,0},{2,2},{0,2}}),0,0)[1].data();var trimmed=new FloatArrayList();
        for(int t=0;t+23<data.length;t+=24)if(data[t+1]+data[t+9]+data[t+17]<5.9F)for(int i=t;i<t+24;i++)trimmed.add(data[i]);
        var channelCap=FractureInteriorMeshes.build(new McheliModel.Group("$body",trimmed.toFloatArray()),0,0,-1).cavity();
        assertEquals(4,BreakupMeshes.area(channelCap),.05,"open contour gets a row-hull cap");
        var b=new DetailBoxes();b.box(-7,0,-10,7,2,6);var frame=BreakupMeshes.airframeBuild(b.group("$body"),7);
        assertTrue(frame.fracture().vertices()<=3072);assertTrue(frame.lining().vertexCount()>0&&frame.lining().vertexCount()<=6144);
        for(var g:new McheliModel.Group[]{frame.fracture().cavity(),frame.fracture().ribs(),frame.fracture().cables(),frame.lining()})for(float value:g.data())assertTrue(Float.isFinite(value));
    }
    @Test void liningMirrorsTheSkinInwardAndRespectsItsBudget() {
        var kept=BreakupMeshes.split(prism(new float[][]{{0,0},{2,0},{2,2},{0,2}}),0,0)[1];
        var lining=FractureInteriorMeshes.lining(kept,0,0,4096);var s=kept.data();var l=lining.data();
        assertEquals(kept.vertexCount(),lining.vertexCount());
        for(int t=0;t+23<s.length;t+=24){
            Vec3 source=normal(s,t),inner=normal(l,t);assertTrue(source.dot(inner)<-.99,"lining faces the other way");
            Vec3 stored=new Vec3(l[t+5],l[t+6],l[t+7]);assertTrue(stored.dot(inner)>.99,"lighting normal follows the lining");
            for(int k=0;k<3;k++){Vec3 centroid=new Vec3((s[t]+s[t+8]+s[t+16])/3,(s[t+1]+s[t+9]+s[t+17])/3,(s[t+2]+s[t+10]+s[t+18])/3);
                double offset=new Vec3(l[t+k*8],l[t+k*8+1],l[t+k*8+2]).subtract(centroid).dot(source);assertTrue(offset<0,"lining sits inside the skin");}
        }
        assertTrue(FractureInteriorMeshes.lining(kept,0,0,6).vertexCount()<=6);
        var shell=FractureInteriorMeshes.shell(kept,0,0,-1,4096);assertTrue(shell.cap().vertexCount()>0&&shell.lining().vertexCount()>0);
    }
    private static Vec3 normal(float[] d,int t){
        Vec3 a=new Vec3(d[t],d[t+1],d[t+2]),b=new Vec3(d[t+8],d[t+9],d[t+10]),c=new Vec3(d[t+16],d[t+17],d[t+18]);return b.subtract(a).cross(c.subtract(a)).normalize();
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

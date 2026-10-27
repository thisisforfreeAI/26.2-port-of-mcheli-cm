package io.github.mcheliceport.client.model;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;
class BreakupMeshesTest {
    private static double area(McheliModel.Group group){double sum=0;var d=group.data();for(int t=0;t+23<d.length;t+=24){var a=new Vec3(d[t+8]-d[t],d[t+9]-d[t+1],d[t+10]-d[t+2]);var b=new Vec3(d[t+16]-d[t],d[t+17]-d[t+1],d[t+18]-d[t+2]);sum+=a.cross(b).length()*.5;}return sum;}
    @Test void splitConservesSurfaceAndInterpolatesUvsWithoutChangingSource(){
        var box=new DetailBoxes();box.box(-4,0,-8,4,2,5);var source=box.group("$body");var original=source.data().clone();
        var halves=BreakupMeshes.split(source,0,1.25F);assertArrayEquals(original,source.data());
        assertEquals(area(source),area(halves[0])+area(halves[1]),.001);
        for(var group:halves)for(int i=0;i<group.data().length;i+=8){assertTrue(group.data()[i+3]>=0&&group.data()[i+3]<=1);assertTrue(group.data()[i+4]>=0&&group.data()[i+4]<=1);}
    }
    @Test void bodyStagesConserveAllRemovedSectionsAndLeaveTheCore(){
        var box=new DetailBoxes();box.box(-6,0,-10,6,2,6);var source=box.group("$body");var original=source.data().clone();
        var parts=BreakupMeshes.bodyBuild(source,true,new Vec3(2,2,-4));assertArrayEquals(original,source.data());
        assertTrue(parts.wing().vertexCount()>0);assertTrue(parts.tail().vertexCount()>0);assertTrue(parts.tailGone().vertexCount()>0);
        assertEquals(area(source),area(parts.cover())+area(parts.wing())+area(parts.tail())+area(parts.tailGone()),.01);
    }
    @Test void bladeTipSelectionCutsTheOuterNegativeArmInsteadOfItsHub(){
        var box=new DetailBoxes();box.box(-6,1,-.1F,0,1.1F,.1F);var p=BreakupMeshes.partBuild(box.group("$blade0"));
        assertTrue(p.bounds().center().x<0);assertTrue(BreakupMeshes.bounds(p.tip()).high().x<-4);
        assertTrue(BreakupMeshes.bounds(p.stem()).high().x>-.01F);
    }
}

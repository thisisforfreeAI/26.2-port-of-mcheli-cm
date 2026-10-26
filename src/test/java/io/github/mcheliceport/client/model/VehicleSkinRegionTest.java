package io.github.mcheliceport.client.model;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;
class VehicleSkinRegionTest {
    @Test void outletSootIsClippedEvenWhenTheHullHasHugeTriangles(){
        float[] d={-10,0,-10,0,0,0,1,0, 10,0,-10,1,0,0,1,0, 0,0,10,0,1,0,1,0};var original=d.clone();
        var mesh=VehicleSkinMeshes.regionMesh(new McheliModel.Group("$body",d),Vec3.ZERO,1);
        assertArrayEquals(original,d);assertTrue(mesh.vertexCount()>0&&mesh.vertexCount()<=3000);
        for(int i=0;i<mesh.data().length;i+=8){assertTrue(Math.abs(mesh.data()[i])<=1.001);assertTrue(Math.abs(mesh.data()[i+2])<=1.001);
            assertTrue(mesh.data()[i+3]>=0&&mesh.data()[i+3]<=1);assertTrue(mesh.data()[i+4]>=0&&mesh.data()[i+4]<=1);}
    }
}

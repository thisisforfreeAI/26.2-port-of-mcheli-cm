package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AircraftMarkingMeshTest {
    @Test void supportedStockLayoutsFitAllThreeDigitsOnBothIntactBodySides()throws Exception{
        var layouts=List.of(new AircraftMarkingMesh.Layout(1.95F,1.1F,.17F),new AircraftMarkingMesh.Layout(2.2F,3.1F,.19F),new AircraftMarkingMesh.Layout(2.7F,.55F,.18F));int n=0;
        for(String name:List.of("f-35a","f-15e","a-10"))try(var stream=getClass().getResourceAsStream("/assets/mcheli/models/planes/"+name+".mqo")){
            var model=MqoParser.parse(name,stream);VehicleImpactSurface.prepare(model);
            var patches=AircraftMarkingMesh.build(model.part("$body").getFirst(),layouts.get(n++));
            for(int slot=0;slot<5;slot++){int s=slot;assertEquals(2,patches.stream().filter(p->p.slot()==s).count(),name+" slot "+slot);}
            int vertices=0;for(var patch:patches){assertTrue(patch.mesh().normal().isFinite());vertices+=patch.mesh().vertices().size();for(var v:patch.mesh().vertices())assertTrue(v.u()>=-.00001&&v.u()<=1.00001&&v.v()>=-.00001&&v.v()<=1.00001);}
            assertTrue(vertices<=960,name+" vertex bound");
        }
        assertFalse(AircraftMarkingMesh.supported("addon-aircraft"));
    }
}

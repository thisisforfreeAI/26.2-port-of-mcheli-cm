package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ControlSurfaceMeshesTest {
    private static McheliModel.Group plane() {
        return plane(false);
    }
    private static McheliModel.Group plane(boolean centralFin) {
        var b=new DetailBoxes();b.box(-1,0,-10,1,2,6);
        b.box(-7,1,-4,-1,1.15F,1);b.box(1,1,-4,7,1.15F,1);
        b.box(-3,1,-9,-1,1.15F,-7);b.box(1,1,-9,3,1.15F,-7);
        if(centralFin)b.box(-.05F,1,-9.8F,.05F,5,-6.4F);
        else{b.box(-2,1,-9.8F,-1.9F,5,-6.4F);b.box(1.9F,1,-9.8F,2,5,-6.4F);}
        return b.group("$body");
    }
    @Test void trailingPanelsConserveOriginalSkinUvsAndKeepSourceUntouched() {
        var source=plane();var original=source.data().clone();var layout=BreakupMeshes.airframeBuild(source,0);
        var model=ControlSurfaceMeshes.build(source,layout);
        assertEquals(6,model.panels().size(),model.panels().stream().map(ControlSurfaceMeshes.Panel::slot).toList().toString());
        double area=BreakupMeshes.area(model.kept());
        for(var panel:model.panels()) {
            area+=BreakupMeshes.area(panel.group());assertTrue(panel.group().vertexCount()<=1200);
            assertTrue(BreakupMeshes.area(panel.group())<=BreakupMeshes.area(source)*.09);
            assertEquals("$body",panel.group().name());
            var d=panel.group().data();for(int i=0;i<d.length;i+=8){assertTrue(d[i+3]>=0&&d[i+3]<=1);assertTrue(d[i+4]>=0&&d[i+4]<=1);}
        }
        assertEquals(BreakupMeshes.area(source),area,.015);assertArrayEquals(original,source.data());
    }
    @Test void wholeWingLossDoesNotRegrowItsControlPanelsOrDuplicateOldDetachedSkin() {
        var source=plane();var full=BreakupMeshes.airframeBuild(source,0);var profile=ControlSurfaceMeshes.build(source,full);
        var missing=BreakupMeshes.airframeBuild(source,1);var model=ControlSurfaceMeshes.build(missing.kept(),missing);
        assertTrue(model.panels().stream().noneMatch(p->p.slot()==0));
        assertTrue(model.panels().stream().anyMatch(p->p.slot()==1));
        var wing=full.sections().get(0);var fragment=ControlSurfaceMeshes.strip(wing,profile,1);
        assertTrue(BreakupMeshes.area(fragment)<BreakupMeshes.area(wing));
        var panel=profile.panels().stream().filter(p->p.slot()==0).findFirst().orElseThrow();
        assertEquals(BreakupMeshes.area(wing),BreakupMeshes.area(fragment)+BreakupMeshes.area(panel.group()),.015);
    }
    @Test void narrowUnsupportedGeometryRetainsItsOriginalSkin() {
        var b=new DetailBoxes();b.box(-.4F,0,-1,.4F,.4F,1);var source=b.group("$body");
        var model=ControlSurfaceMeshes.build(source,BreakupMeshes.airframeBuild(source,0));
        assertTrue(model.panels().isEmpty());assertSame(source,model.kept());
    }
    @Test void aCentralRudderKeepsBothTexturedSidesOnTheSameHinge() {
        var source=plane(true);var model=ControlSurfaceMeshes.build(source,BreakupMeshes.airframeBuild(source,0));
        var rudders=model.panels().stream().filter(p->p.slot()==2||p.slot()==3).toList();
        assertEquals(1,rudders.size());var rudder=rudders.getFirst();var bounds=BreakupMeshes.bounds(rudder.group());
        assertTrue(bounds.low().x<0&&bounds.high().x>0);assertEquals(0,rudder.pivot().x,.001);
    }
}

package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class VehicleCanopyFinishTest {
    @Test void malformedOrMissingAddonLiveriesKeepTheModelUsableAsPaint(){
        var info=new io.github.mcheliceport.content.aircraft.PlaneInfo(
                io.github.mcheliceport.content.ContentLocation.builtin("fixture"),"fixture","fixture");
        info.getTextureNames().clear();info.addTextureName("Invalid addon livery");
        assertTrue(VehicleCanopyFinish.load(net.minecraft.server.packs.resources.ResourceManager.Empty.INSTANCE,"planes",info).isEmpty());
        info.getTextureNames().clear();info.addTextureName("missing");
        assertTrue(VehicleCanopyFinish.load(net.minecraft.server.packs.resources.ResourceManager.Empty.INSTANCE,"planes",info).isEmpty());
    }
    private static final float[] TRIANGLE={0,0,0,.2F,.2F,0,1,0, 1,0,0,.3F,.2F,0,1,0, 0,0,1,.2F,.3F,0,1,0};
    private static VehicleGlassAlpha.Mask mask(int alpha){return new VehicleGlassAlpha.Mask(1,1,new byte[]{(byte)alpha});}
    @Test void partialAlphaMustAgreeAcrossAllLiveriesAndHolesRemainUnshaded(){
        assertTrue(VehicleCanopyFinish.glass(TRIANGLE,0,List.of(mask(160),mask(210))));
        assertFalse(VehicleCanopyFinish.glass(TRIANGLE,0,List.of(mask(160),mask(255))));
        assertFalse(VehicleCanopyFinish.glass(TRIANGLE,0,List.of(mask(0))));
        assertFalse(VehicleCanopyFinish.glass(TRIANGLE,0,List.of()));
        assertFalse(VehicleCanopyFinish.glass(TRIANGLE,24,List.of(mask(160))));
    }
    @Test void onlyCanopiesUseAlphaEvidenceAndBothRenderersReceiveTheSameBakedColor(){
        var group=new McheliModel.Group("$canopy0",TRIANGLE);
        var model=new McheliModel("fixture",List.of(group),0,0,0,1,0,1);
        VehicleSurfaceShade.prepare(model,List.of(),List.of(mask(160)));
        int glass=VehicleSurfaceShade.get(group)[0];
        VehicleSurfaceShade.prepare(model,List.of(),List.of(mask(255)));
        assertTrue((glass&255)>(VehicleSurfaceShade.get(group)[0]&255));
        assertFalse(VehicleCanopyFinish.canopy("$body"));
        assertTrue(VehicleCanopyFinish.canopy("$Canopy0"));
    }
}

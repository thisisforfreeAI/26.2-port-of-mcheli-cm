package io.github.mcheliceport.client.render;
import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.client.model.McheliModel;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
class VehicleDetailLayersTest {
 private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("mcheli_ce_port","textures/test.png");
 @Test void aShownLayerStaysWhenTheOrderOrBudgetShifts(){
  var a=List.of(new McheliModel.Group("$a",new float[0]));var b=List.of(new McheliModel.Group("$b",new float[0]));
  VehicleDetailLayers.frame(null);VehicleDetailLayers.frame(null);
  assertTrue(VehicleDetailLayers.admit(a,TEXTURE,600,1000));
  assertFalse(VehicleDetailLayers.admit(b,TEXTURE,600,1000),"a new layer must fit the cap");
  // Next frame another vehicle comes first and takes most of the cap: the shown layer is kept.
  VehicleDetailLayers.frame(null);
  assertTrue(VehicleDetailLayers.admit(b,TEXTURE,600,1000));
  assertTrue(VehicleDetailLayers.admit(a,TEXTURE,600,1000),"shown last frame, so it may run past the cap");
  // Not without bound.
  VehicleDetailLayers.frame(null);
  assertTrue(VehicleDetailLayers.admit(b,TEXTURE,1500,1000));
  assertFalse(VehicleDetailLayers.admit(a,TEXTURE,600,1000));
  assertFalse(VehicleDetailLayers.admit(a,TEXTURE,0,1000),"empty layers are never drawn");
 }
}

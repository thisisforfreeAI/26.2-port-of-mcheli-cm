package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.client.model.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;

/** One ambient-lit atlas batch per nearby supported aircraft, sharing a bounded view allowance. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class AircraftMarkings {
    private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("mcheli_ce_port","textures/effect/m8_aircraft_markings.png");
    public record Draw(Matrix4f pose,VehicleImpactSurface.Mesh mesh,int tile,int color){}
    private static int vertices,lastVertices;
    private AircraftMarkings(){}
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){lastVertices=vertices;vertices=0;}
    public static int vertices(){return lastVertices;}
    public static void extract(AircraftEntity ac,AircraftRenderState render){
        if(ac.isDestroyed()||render.far||render.thermal||!EffectQuality.enhanced()
                ||!ClientConfig.get(ClientConfig.AIRCRAFT_CUSTOMIZATION)
                ||net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(ac.position())>96*96)return;
        var appearance=ac.getAppearance();if(appearance.packed()==0)return;
        int cap=switch(ClientConfig.get(ClientConfig.EFFECT_BUDGET)){case LOW->2048;case BALANCED->6144;case HIGH->12288;};
        cap/=1+io.github.mcheliceport.client.particle.EffectPerformance.pressure();
        for(var draw:render.draws)for(var group:draw.groups())for(var patch:AircraftMarkingMesh.get(group)){
            int slot=patch.slot(),tile,color;
            if(slot<3){if(appearance.number()<0)continue;tile=appearance.number()/(slot==0?100:slot==1?10:1)%10;color=0xCC000000|appearance.color();}
            else if(slot==3){if(appearance.symbol()==0)continue;tile=9+appearance.symbol();color=0xCC000000|appearance.color();}
            else{if(appearance.wear()==0)continue;tile=15;color=(appearance.wear()==1?0x32000000:0x64000000)|0xA3A5A0;}
            int count=patch.mesh().vertices().size();if(vertices+count>cap)return;
            vertices+=count;render.markingDraws.add(new Draw(new Matrix4f(draw.pose()),patch.mesh(),tile,color));
        }
    }
    public static void submit(AircraftRenderState render,PoseStack poses,SubmitNodeCollector collector){
        if(render.markingDraws.isEmpty())return;var draws=List.copyOf(render.markingDraws);int light=render.lightCoords;
        collector.submitCustomGeometry(poses,RenderTypes.entityTranslucent(TEXTURE,false),(pose,buffer)->{
            for(var draw:draws){var local=pose.copy();local.mulPose(draw.pose());var normal=draw.mesh().normal();var mesh=draw.mesh().vertices();
                for(int t=0;t+2<mesh.size();t+=3)for(int k=0;k<4;k++){
                    var v=mesh.get(t+Math.min(2,k));
                    buffer.addVertex(local,v.x(),v.y(),v.z()).setColor(draw.color())
                        .setUv((draw.tile()%4+Math.clamp(v.u(),0,1))/4,(draw.tile()/4+1-Math.clamp(v.v(),0,1))/4)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(local,normal.x,normal.y,normal.z);
                }
            }
        });
    }
}

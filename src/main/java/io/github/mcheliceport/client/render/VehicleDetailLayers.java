package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.config.*;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;

/** Shared per-view geometry allowance for all living-vehicle overlays. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class VehicleDetailLayers {
    private static int vertices;
    private VehicleDetailLayers(){}
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){vertices=0;}
    public static boolean add(AircraftRenderState render,Matrix4f pose,Identifier texture,List<McheliModel.Group> groups,int color){
        if(render.far||render.thermal||!EffectQuality.enhanced())return false;
        int count=groups.stream().mapToInt(McheliModel.Group::vertexCount).sum();
        int cap=switch(ClientConfig.get(ClientConfig.EFFECT_BUDGET)){case LOW->4096;case BALANCED->12288;case HIGH->24576;};
        cap/=1+io.github.mcheliceport.client.particle.EffectPerformance.pressure();
        if(count==0||vertices+count>cap)return false;vertices+=count;
        render.detailDraws.add(new AircraftRenderState.DetailDraw(texture,new Matrix4f(pose),List.copyOf(groups),color));return true;
    }
    /** Structural closure (cut caps and inner lining) is never dropped for budget, or the airframe reads as hollow. */
    public static boolean require(AircraftRenderState render,Matrix4f pose,Identifier texture,List<McheliModel.Group> groups,int color){
        if(render.far||render.thermal)return false;
        int count=groups.stream().mapToInt(McheliModel.Group::vertexCount).sum();if(count==0)return false;vertices+=count;
        render.detailDraws.add(new AircraftRenderState.DetailDraw(texture,new Matrix4f(pose),List.copyOf(groups),color));return true;
    }
    /** A layer whose opacity varies per vertex ({@code weights}, 0 to 1); never dropped for budget, like {@link #require}. */
    public static boolean require(AircraftRenderState render,Matrix4f pose,Identifier texture,McheliModel.Group group,int color,float[] weights){
        if(render.far||render.thermal||group.vertexCount()==0||weights.length!=group.vertexCount())return false;vertices+=group.vertexCount();
        render.detailDraws.add(new AircraftRenderState.DetailDraw(texture,new Matrix4f(pose),List.of(group),color,weights));return true;
    }
    public static int vertices(){return vertices;}
}

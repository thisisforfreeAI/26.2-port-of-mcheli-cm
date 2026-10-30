package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.config.*;
import java.util.*;
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
    /** Layers drawn last frame and this frame: a shown layer keeps its place instead of popping in and out. */
    private static Set<Layer> shown=new HashSet<>(),drawn=new HashSet<>();
    private record Layer(McheliModel.Group group,Identifier texture){}
    private VehicleDetailLayers(){}
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){vertices=0;var last=shown;shown=drawn;drawn=last;drawn.clear();}
    public static boolean add(AircraftRenderState render,Matrix4f pose,Identifier texture,List<McheliModel.Group> groups,int color){
        if(render.far||render.thermal||!EffectQuality.enhanced())return false;
        int count=groups.stream().mapToInt(McheliModel.Group::vertexCount).sum();
        int cap=switch(ClientConfig.get(ClientConfig.EFFECT_BUDGET)){case LOW->4096;case BALANCED->12288;case HIGH->24576;};
        cap/=1+io.github.mcheliceport.client.particle.EffectPerformance.pressure();
        if(!admit(groups,texture,count,cap))return false;
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
    /**
     * A new layer must fit the cap; one already shown last frame may run to twice it. Without the slack, the
     * vehicles' extraction order and the frame-time pressure decided per frame which layers fit, so open panels,
     * cables and wreck paint flickered on and off.
     */
    static boolean admit(List<McheliModel.Group> groups,Identifier texture,int count,int cap){
        if(count==0)return false;
        boolean kept=true;for(var g:groups)kept&=shown.contains(new Layer(g,texture));
        if(vertices+count>(kept?cap*2:cap))return false;
        vertices+=count;for(var g:groups)drawn.add(new Layer(g,texture));return true;
    }
    public static int vertices(){return vertices;}
}

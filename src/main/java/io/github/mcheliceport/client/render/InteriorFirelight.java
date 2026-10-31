package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.client.particle.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/** Restrained amber reflection on existing exposed machinery, never an overlay over the entire hull. */
public final class InteriorFirelight {
    private static final Identifier METAL=Identifier.withDefaultNamespace("textures/block/iron_block.png");
    private static Object world;private static int vertices,lastVertices;
    private static final java.util.Map<McheliModel.Group,McheliModel.Group> HIGHLIGHTS=new java.util.WeakHashMap<>();
    private InteriorFirelight(){}
    public static void frame(){var mc=Minecraft.getInstance();var level=mc==null?null:mc.level;if(world!=level){world=level;vertices=lastVertices=0;HIGHLIGHTS.clear();}lastVertices=vertices;vertices=0;}
    public static int vertices(){return lastVertices;}
    public static void add(AircraftEntity ac,AircraftRenderState render,Matrix4f pose,McheliModel.Group machinery){
        if(!WreckSequenceEffects.enabled()||!ac.isDestroyed()||render.thermal||render.far||machinery.vertexCount()==0
                ||machinery.vertexCount()>768
                ||Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(ac.position())>32*32)return;
        var state=WreckSequenceEffects.get(ac);if(state==null||!state.seenAlive)return;
        int cap=switch(io.github.mcheliceport.config.ClientConfig.get(io.github.mcheliceport.config.ClientConfig.EFFECT_BUDGET)){
            case LOW->768;case BALANCED->1536;case HIGH->3072;};
        cap/=1+EffectPerformance.pressure();if(vertices+machinery.vertexCount()>cap)return;
        float partial=Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float flash=state.flashTick==Long.MIN_VALUE?1000:ac.level().getGameTime()-state.flashTick+partial;
        float strength=DestructionLook.interior(VehicleAtmosphere.wreckAge(ac)+partial,flash,state.seed);
        if(strength<=.01F)return;
        var highlight=HIGHLIGHTS.get(machinery);
        if(highlight==null){
            if(HIGHLIGHTS.size()>=64)HIGHLIGHTS.clear();float[] data=machinery.data().clone();
            for(int i=0;i+7<data.length;i+=8){data[i]+=.002F*data[i+5];data[i+1]+=.002F*data[i+6];data[i+2]+=.002F*data[i+7];}
            highlight=new McheliModel.Group("$m8_fire_reflection",data);HIGHLIGHTS.put(machinery,highlight);
        }
        int alpha=Math.round(strength*255);
        render.emissiveDraws.add(new AircraftRenderState.EmissiveDraw(METAL,new Matrix4f(pose),List.of(highlight),alpha<<24|0xFF8C35));
        vertices+=machinery.vertexCount();
    }
}

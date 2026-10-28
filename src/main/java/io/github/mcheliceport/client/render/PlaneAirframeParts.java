package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.*;
import io.github.mcheliceport.client.particle.WreckSequenceEffects;
import io.github.mcheliceport.damage.AirframeDamage;
import io.github.mcheliceport.effect.VehicleImpactLook;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Identical structural silhouettes while alive, wrecked, saved/reloaded, or viewed at a distance. */
public final class PlaneAirframeParts {
    private static final Identifier METAL=Identifier.withDefaultNamespace("textures/block/iron_block.png");
    private PlaneAirframeParts() {}
    public static boolean tailMissing(PlaneEntity ac) {var state=WreckSequenceEffects.get(ac);return state!=null&&state.tail;}
    public static void apply(PlaneEntity ac,AircraftRenderState render,WreckSequenceEffects.State state,BreakupMeshes.Body engineBody) {
        long now=ac.level().getGameTime();int missing=AirframeDamage.missing(ac);
        if(state.liveMask>=0&&!ac.isDestroyed())for(int bit:new int[]{1,2,4}){
            if((missing&bit)!=0&&(state.liveMask&bit)==0)state.airframeLossAt.put(bit,now);
            if((missing&bit)==0){state.airframeLossAt.remove(bit);state.scattered.remove("liveAirframe"+bit);}
        }
        state.liveMask=missing;
        int requested=missing|(state.age>=56?AirframeDamage.RIGHT:0)|(state.age>=110?AirframeDamage.TAIL:0);
        boolean nearby=Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(ac.position())<=40*40;
        var output=new ArrayList<AircraftPartPoses.Draw>();BreakupMeshes.Airframe frame=null;
        // Resolve the body once, so attachments use the exact mask whose geometry is ready this frame.
        for(var draw:render.draws)for(var group:draw.groups())if(group.name().equals("$body")){
            var source=state.age>=36&&engineBody!=null?engineBody.coverOpen():group;
            frame=BreakupMeshes.airframe(source,requested);
            if(frame==null&&state.airframe!=null&&(state.airframe.mask()&requested)==state.airframe.mask())frame=state.airframe;
            if(frame!=null)state.airframe=frame;
            break;
        }
        int actual=frame==null?0:frame.mask();
        state.wing=(actual&3)!=0;state.tail=(actual&4)!=0;
        if(state.wing)state.parts|=4;else state.parts&=~4;
        if(state.tail)state.parts|=8;else state.parts&=~8;
        var inverse=VehicleImpactLook.body(render.yaw,render.pitch,render.roll).invert();
        for(var draw:render.draws)for(var group:draw.groups()){
            String name=group.name();var pose=draw.pose();
            if(name.equals("$body")){
                output.add(new AircraftPartPoses.Draw(pose,List.of(frame==null?group:frame.kept())));
                var settled=new Matrix4f(WreckAppearance.root(ac)).mul(pose);
                if(nearby&&frame!=null&&actual!=0){
                    var detail=frame.fracture();
                    if(io.github.mcheliceport.config.ClientConfig.get(io.github.mcheliceport.config.ClientConfig.EFFECT_STYLE)
                            ==io.github.mcheliceport.config.ClientConfig.EffectStyle.POLISHED&&detail.vertices()>0) {
                        VehicleDetailLayers.add(render,settled,METAL,List.of(detail.cavity()),0xFF3D3934);
                        VehicleDetailLayers.add(render,settled,METAL,List.of(detail.ribs()),0xFF777168);
                        VehicleDetailLayers.add(render,settled,METAL,List.of(detail.cables()),0xFF302C27);
                    }else VehicleDetailLayers.add(render,settled,METAL,List.of(frame.ribs()),0xFF595752);
                    if((actual&4)!=0){
                        var b=frame.bounds();var at=settled.transformPosition(new Vector3f(0,(b.low().y+b.high().y)*.45F,frame.tailCut()+.1F));
                        state.opening=ac.position().add(at.x,at.y,at.z);
                    }
                    for(int i=0;i<3;i++)if((actual&(1<<i))!=0)detach(ac,render,state,frame.sections().get(i),pose,i,now,missing);
                }
                if(nearby&&state.age>=36&&engineBody!=null&&engineBody.cover().vertexCount()>0){
                    state.cover=true;state.parts|=1;
                    scatterWreck(ac,render,state,"cover",engineBody.cover(),pose,36,new Vec3(.1,.34,-.08));
                    VehicleDetailLayers.add(render,settled,METAL,List.of(engineBody.machinery()),0xFF514D45);
                    if((actual&4)==0&&engineBody.opening()!=null){var v=engineBody.opening();var p=settled.transformPosition(new Vector3f((float)v.x,(float)v.y,(float)v.z));state.opening=ac.position().add(p.x,p.y,p.z);}
                }
                continue;
            }
            if(actual!=0&&frame!=null){
                var part=BreakupMeshes.part(group);
                if(part!=null){
                    var center=new Matrix4f(inverse).mul(pose).transformPosition(part.bounds().center());
                    boolean lost=(actual&1)!=0&&center.x<frame.leftCut() || (actual&2)!=0&&center.x>frame.rightCut()
                            || (actual&4)!=0&&center.z<frame.tailCut();
                    if(lost)continue;
                }
            }
            if(nearby&&state.age>=36&&(name.startsWith("$enginecover")||name.startsWith("$cover"))&&group.vertexCount()<=1200){
                scatterWreck(ac,render,state,"named_cover"+name,group,pose,36,new Vec3(.1,.3,0));
                PanelInteriors.add(render,new Matrix4f(WreckAppearance.root(ac)).mul(pose),group);state.cover=true;state.parts|=1;continue;
            }
            output.add(new AircraftPartPoses.Draw(pose,List.of(group)));
        }
        render.draws.clear();render.draws.addAll(output);
    }
    private static void detach(PlaneEntity ac,AircraftRenderState render,WreckSequenceEffects.State state,
            McheliModel.Group group,Matrix4f pose,int index,long now,int missing){
        group=DamagedControlSurfaces.fragment(state,group,index);if(group==null||group.vertexCount()==0)return;
        int bit=1<<index;String key="liveAirframe"+bit;
        Long born=state.airframeLossAt.get(bit);
        if(born!=null&&now>=born&&now-born<=60&&!state.scattered.contains(key)){
            var part=BreakupMeshes.part(group);
            Vec3 impulse=index==2?new Vec3(0,.05,-.15):new Vec3(index==0?-.15:.15,.05,0);
            if(part!=null&&LoosePanels.airframePiece(ac,render.texture,group,pose,part.bounds().center(),impulse,bit))state.scattered.add(key);
        } else if((missing&bit)==0){
            int age=index==2?110:56;String wreck=index==2?"tail":index==0?"left_wing":"wing";
            scatterWreck(ac,render,state,wreck,group,pose,age,new Vec3(index==0?-.13:.13,.18,index==2?-.12:.02));
        }
    }
    private static void scatterWreck(PlaneEntity ac,AircraftRenderState render,WreckSequenceEffects.State state,String key,
            McheliModel.Group group,Matrix4f pose,int age,Vec3 impulse){
        if(!state.seenAlive||state.age<age||state.age>age+28||state.scattered.contains(key)||group.vertexCount()==0)return;
        var part=BreakupMeshes.part(group);
        if(part!=null&&LoosePanels.scatter(ac,render.texture,group,pose,part.bounds().center(),impulse))state.scattered.add(key);
    }
}

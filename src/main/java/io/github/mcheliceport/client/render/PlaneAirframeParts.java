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
    /** Inner skin is the aircraft's own paint in shadow; bulkheads are bare scorched alloy. */
    static final int LINING=0xFF6A665F,CAP=0xFF3D3934;
    private PlaneAirframeParts() {}
    public static boolean tailMissing(PlaneEntity ac) {var state=WreckSequenceEffects.get(ac);return state!=null&&state.tail;}
    public static void apply(PlaneEntity ac,AircraftRenderState render,WreckSequenceEffects.State state,BreakupMeshes.Body engineBody) {
        long now=ac.level().getGameTime();int missing=AirframeDamage.missing(ac);
        if(state.liveMask>=0&&!ac.isDestroyed())for(int bit:new int[]{1,2,4}){
            if((missing&bit)!=0&&(state.liveMask&bit)==0)state.airframeLossAt.put(bit,now);
            if((missing&bit)==0){state.airframeLossAt.remove(bit);state.scattered.remove("liveAirframe"+bit);
                for(int i=0;i<3;i++)state.scattered.remove("skin"+bit+":"+i);}
        }
        state.liveMask=missing;
        int requested=missing|(state.age>=56?AirframeDamage.RIGHT:0)|(state.age>=110?AirframeDamage.TAIL:0);
        double distance=Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(ac.position());
        boolean nearby=distance<=40*40,solid=distance<=96*96;
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
                if(solid&&frame!=null&&actual!=0){
                    // Close the torn skin in every style: lining for the culled inner walls, caps over the hole.
                    VehicleDetailLayers.require(render,settled,render.texture,List.of(frame.lining()),LINING);
                    VehicleDetailLayers.require(render,settled,METAL,List.of(frame.fracture().cavity()),CAP);
                }
                if(nearby&&frame!=null&&actual!=0){
                    var detail=frame.fracture();
                    if(io.github.mcheliceport.config.ClientConfig.get(io.github.mcheliceport.config.ClientConfig.EFFECT_STYLE)
                            ==io.github.mcheliceport.config.ClientConfig.EffectStyle.POLISHED&&detail.vertices()>0) {
                        VehicleDetailLayers.add(render,settled,METAL,List.of(detail.ribs()),0xFF777168);
                        VehicleDetailLayers.add(render,settled,METAL,List.of(detail.cables()),0xFF302C27);
                    }else VehicleDetailLayers.add(render,settled,METAL,List.of(frame.ribs()),0xFF595752);
                    if((actual&4)!=0){
                        var b=frame.bounds();var at=settled.transformPosition(new Vector3f(0,(b.low().y+b.high().y)*.45F,frame.tailCut()+.1F));
                        state.opening=ac.position().add(at.x,at.y,at.z);
                    }
                    for(int i=0;i<3;i++)if((actual&(1<<i))!=0){
                        detach(ac,render,state,frame,pose,i,now,missing);
                        shed(ac,render,state,frame,pose,i,now,missing);
                    }
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
                    int section=frame.section(center);boolean lost=section>=0&&(actual&(1<<section))!=0;
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
            BreakupMeshes.Airframe frame,Matrix4f pose,int index,long now,int missing){
        var group=DamagedControlSurfaces.fragment(state,frame.sections().get(index),index);if(group==null||group.vertexCount()==0)return;
        int bit=1<<index;String key="liveAirframe"+bit;
        Long born=state.airframeLossAt.get(bit);
        if(born!=null&&now>=born&&now-born<=60&&!state.scattered.contains(key)){
            var part=BreakupMeshes.part(group);
            Vec3 impulse=index==2?new Vec3(0,.05,-.15):new Vec3(index==0?-.15:.15,.05,0);
            if(part!=null&&LoosePanels.airframePiece(ac,render.texture,group,pose,part.bounds().center(),impulse,bit)){
                state.scattered.add(key);LoosePanels.tear(tear(frame,index,part));
            }
        } else if((missing&bit)==0){
            int age=index==2?110:56;String wreck=index==2?"tail":index==0?"left_wing":"wing";
            if(scatterWreck(ac,render,state,wreck,group,pose,age,new Vec3(index==0?-.13:.13,.18,index==2?-.12:.02))){
                var part=BreakupMeshes.part(group);if(part!=null)LoosePanels.tear(tear(frame,index,part));
            }
        }
    }
    /** The section's own cut: same plane as the kept stump, with its cap facing back toward the fuselage. */
    static LoosePanels.Tear tear(BreakupMeshes.Airframe frame,int index,BreakupMeshes.Part part){
        int axis=index==2?2:0,outward=index==1?-1:1;float cut=index==0?frame.leftCut():index==1?frame.rightCut():frame.tailCut();
        var opening=frame.openings().get(1<<index);Vector3f edge;
        if(opening!=null)edge=new Vector3f((float)opening.x,(float)opening.y,(float)opening.z);
        else{edge=part.bounds().center();if(axis==0)edge.x=cut;else edge.z=cut;}
        return new LoosePanels.Tear(axis,cut,outward,edge);
    }
    private static void shed(PlaneEntity ac,AircraftRenderState render,WreckSequenceEffects.State state,
            BreakupMeshes.Airframe frame,Matrix4f pose,int index,long now,int missing) {
        int bit=1<<index;Long born=state.airframeLossAt.get(bit);long age;
        if(!ac.isDestroyed()) {
            if(born==null||now<born)return;age=now-born;
        }else {
            if(!state.seenAlive)return;
            if((missing&bit)!=0){if(born==null||now<born)return;age=now-born;}
            else age=state.age-(index==2?110:56);
        }
        int period=8*(1+io.github.mcheliceport.client.particle.EffectPerformance.pressure());
        if(age<8||age>40||Math.floorMod(now+ac.getId(),period)!=0)return;
        var section=DamagedControlSurfaces.fragment(state,frame.sections().get(index),index);
        if(section==null)return;int axis=index==2?2:0,sign=index==1?1:-1;
        float cut=index==0?frame.leftCut():index==1?frame.rightCut():frame.tailCut();
        var chips=BreakupMeshes.skinDebris(section,axis,cut,sign);if(chips==null)return;
        for(int i=0;i<chips.size();i++) {
            String key="skin"+bit+":"+i;if(state.scattered.contains(key))continue;
            var chip=chips.get(i);var impulse=index==2?new Vec3((i-1)*.025,.04,-.065):new Vec3(sign*.065,.04,(i-1)*.025);
            var settled=new Matrix4f(WreckAppearance.root(ac)).mul(pose);
            if(LoosePanels.skinPiece(ac,render.texture,chip.skin(),settled,chip.bounds().center(),impulse,bit))state.scattered.add(key);
            break;
        }
    }
    private static boolean scatterWreck(PlaneEntity ac,AircraftRenderState render,WreckSequenceEffects.State state,String key,
            McheliModel.Group group,Matrix4f pose,int age,Vec3 impulse){
        if(!state.seenAlive||state.age<age||state.age>age+28||state.scattered.contains(key)||group.vertexCount()==0)return false;
        var part=BreakupMeshes.part(group);
        if(part==null||!LoosePanels.scatter(ac,render.texture,group,pose,part.bounds().center(),impulse))return false;
        state.scattered.add(key);return true;
    }
}

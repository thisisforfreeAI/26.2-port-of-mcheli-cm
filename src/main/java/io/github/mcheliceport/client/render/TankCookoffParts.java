package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.*;
import io.github.mcheliceport.client.particle.*;
import io.github.mcheliceport.entity.tank.TankEntity;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Tank-specific pressure relief and complete posed turret separation. No gameplay geometry changes. */
public final class TankCookoffParts {
    public record Snapshot(McheliModel.Group mesh,Vector3f center,Matrix4f ring){}
    private static final Identifier DARK=Identifier.withDefaultNamespace("textures/block/polished_deepslate.png");
    private static final McheliModel.Group RING;
    static{var b=new DetailBoxes();b.box(-.78F,0,-.78F,.78F,.03F,.78F);RING=b.group("$m8_open_ring");}
    private TankCookoffParts(){}
    /** Bakes each barrel/attachment's actual pose once; prevents double turret rotation and detached floating guns. */
    public static Snapshot snapshot(List<AircraftPartPoses.Draw> draws){
        int count=draws.stream().flatMap(d->d.groups().stream()).filter(g->g.name().startsWith("$weapon")).mapToInt(McheliModel.Group::vertexCount).sum();
        if(count==0||count>12000)return null;
        float[] out=new float[count*8];int cursor=0;Matrix4f ring=null;var p=new Vector3f();var n=new Vector3f();
        for(var draw:draws)for(var g:draw.groups())if(g.name().startsWith("$weapon")){
            if(g.name().equals("$weapon0")){var bounds=BreakupMeshes.bounds(g);var c=bounds.center();c.y=bounds.low().y;ring=new Matrix4f(draw.pose()).translate(c);}
            var normal=new Matrix3f(draw.pose()).invert().transpose();var data=g.data();
            for(int i=0;i+7<data.length;i+=8){
                draw.pose().transformPosition(p.set(data[i],data[i+1],data[i+2]));normal.transform(n.set(data[i+5],data[i+6],data[i+7]));if(n.lengthSquared()>0)n.normalize();
                out[cursor++]=p.x;out[cursor++]=p.y;out[cursor++]=p.z;out[cursor++]=data[i+3];out[cursor++]=data[i+4];out[cursor++]=n.x;out[cursor++]=n.y;out[cursor++]=n.z;
            }
        }
        if(ring==null)return null;var mesh=new McheliModel.Group("$m8_complete_turret",out);
        return new Snapshot(mesh,BreakupMeshes.bounds(mesh).center(),ring);
    }
    public static void apply(TankEntity ac,AircraftRenderState render,WreckSequenceEffects.State s,float partial){
        if(s.age<0)return;
        if(DestructionTimeline.blowout(ac.getAcInfo().name,s.heavyAmmo)&&s.age>=36)blowout(ac,render,s,partial);
        if(!DestructionTimeline.turret(ac.getAcInfo().name,s.seed,s.heavyAmmo)||s.age<96)return;
        if(s.tankTurret==null)s.tankTurret=snapshot(render.draws);
        var turret=s.tankTurret;if(turret==null)return; // Unsupported layouts retain their complete assembly.
        if(s.seenAlive&&!s.scattered.contains("turret")&&s.age<=156
                &&BreakupMeshes.part(turret.mesh())!=null
                &&LoosePanels.scatter(ac,render.texture,turret.mesh(),WreckAppearance.root(ac),turret.center(),new Vec3(.12,.85,-.12)))s.scattered.add("turret");
        s.turret=true;s.parts|=16;
        var ringPose=new Matrix4f(WreckAppearance.root(ac)).mul(turret.ring());
        VehicleDetailLayers.add(render,ringPose,DARK,List.of(RING),0xFF211C18);InteriorFirelight.add(ac,render,ringPose,RING);
        var at=ringPose.transformPosition(new Vector3f(0,.1F,0));s.ring=ac.position().add(at.x,at.y,at.z);
        boolean flying=s.scattered.contains("turret");s.turretFallback=!flying;var kept=new ArrayList<AircraftPartPoses.Draw>();
        float t=Math.clamp((s.age+partial-96)/12,0,1);
        float rest=Math.clamp((s.age+partial-116)/40,0,1);
        // If the shared fragment budget is busy, visibly lift/dislodge the posed turret instead of hiding it.
        var lift=new Matrix4f().translate(.8F*t,1.2F*(float)Math.sin(t*Math.PI*.65)*(1-.85F*rest),-.25F*t).rotateZ((.18F+.12F*rest)*t);
        for(var draw:render.draws){var body=draw.groups().stream().filter(g->!g.name().startsWith("$weapon")).toList();
            if(!body.isEmpty())kept.add(new AircraftPartPoses.Draw(draw.pose(),body));
            if(!flying){var guns=draw.groups().stream().filter(g->g.name().startsWith("$weapon")).toList();if(!guns.isEmpty())kept.add(new AircraftPartPoses.Draw(new Matrix4f(lift).mul(draw.pose()),guns));}
        }
        render.draws.clear();render.draws.addAll(kept);
    }
    private static void blowout(TankEntity ac,AircraftRenderState render,WreckSequenceEffects.State s,float partial){
        s.openLids=0;
        var kept=new ArrayList<AircraftPartPoses.Draw>();
        for(var draw:render.draws)for(var group:draw.groups()){
            if(!group.name().equals("$weapon0")){kept.add(new AircraftPartPoses.Draw(draw.pose(),List.of(group)));continue;}
            var roof=TankBlowoutMesh.get(group);if(roof==null){kept.add(new AircraftPartPoses.Draw(draw.pose(),List.of(group)));continue;}
            kept.add(new AircraftPartPoses.Draw(draw.pose(),List.of(roof.kept())));
            float t=Math.clamp((s.age+partial-36)/7,0,1);float angle=t*t*(3-2*t)*1.43F;
            for(int i=0;i<roof.panels().size();i++){
                String key="bustle-panel-"+i;var panel=roof.panels().get(i);var hinge=panel.hinge();
                var opened=new Matrix4f(draw.pose()).translate(hinge).rotateZ(panel.sign()*angle).translate(-hinge.x,-hinge.y,-hinge.z);
                if(!s.scattered.contains(key)&&s.seenAlive&&s.age>=46&&s.age<=96
                        &&BreakupMeshes.part(panel.mesh())!=null
                        &&LoosePanels.scatter(ac,render.texture,panel.mesh(),new Matrix4f(WreckAppearance.root(ac)).mul(opened),BreakupMeshes.bounds(panel.mesh()).center(),new Vec3(panel.sign()*-.1,.65,0)))s.scattered.add(key);
                if(!s.scattered.contains(key)){s.openLids++;kept.add(new AircraftPartPoses.Draw(opened,List.of(panel.mesh())));}
            }
            var pose=new Matrix4f(WreckAppearance.root(ac)).mul(draw.pose());
            VehicleDetailLayers.require(render,pose,DARK,List.of(roof.cavity()),0xFF211914);InteriorFirelight.add(ac,render,pose,roof.cavity());
            var vent=pose.transformPosition(new Vector3f(roof.vent()));s.vent=ac.position().add(vent.x,vent.y,vent.z);s.blowout=true;s.parts|=64;
        }
        render.draws.clear();render.draws.addAll(kept);
    }
}

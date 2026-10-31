package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.*;
import io.github.mcheliceport.client.particle.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Named parts and cached clipped sections. All movement is presentation-only. */
public final class DestructionParts {
    private static final Identifier DARK=Identifier.withDefaultNamespace("textures/block/polished_deepslate.png");
    private static final McheliModel.Group RING;
    static {var b=new DetailBoxes();b.box(-.8F,0,-.8F,.8F,.035F,.8F);b.box(-.82F,.03F,-.82F,-.73F,.12F,.82F);b.box(.73F,.03F,-.82F,.82F,.12F,.82F);RING=b.group("$open_turret_ring");}
    private DestructionParts(){}
    public static void apply(AircraftEntity ac,AircraftRenderState render,float partial){
        boolean plane=ac instanceof PlaneEntity,tank=ac instanceof TankEntity;
        var s=WreckSequenceEffects.get(ac);
        if(s==null&&plane&&WreckSequenceEffects.enabled()){
            s=new WreckSequenceEffects.State();s.age=ac.isDestroyed()?1200:-1;
        }
        if(s==null||render.texture==null)return;
        double distance=Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(ac.position());
        if(!plane&&(render.far||distance>40*40))return;
        var models=AircraftModels.peek(ac.getAcInfo());if(models==null)return;
        var outlets=EngineExhaustLook.outlets(ac.getAcInfo(),0);Vec3 anchor=outlets.isEmpty()?new Vec3(0,1.8,-2):outlets.getFirst().position();
        BreakupMeshes.Body body=null;if(distance<=40*40)for(var draw:render.draws)for(var group:draw.groups())if(group.name().equals("$body")){body=BreakupMeshes.body(group,plane,anchor);break;}
        if(plane){PlaneAirframeParts.apply((PlaneEntity)ac,render,s,body);DamagedControlSurfaces.apply((PlaneEntity)ac,render,s,partial);return;}
        boolean eject=tank&&DestructionTimeline.turret(ac.getAcInfo().name,s.seed,s.heavyAmmo);
        var turretGroups=models.body().groups.stream().filter(g->g.name().startsWith("$weapon")).toList();
        var turret=eject?BreakupMeshes.merge(turretGroups):null;
        if(s.age<0)return; // Prewarm sections while alive; never alter live geometry.
        var trackTotals=new HashMap<String,Integer>();var trackIndices=new HashMap<String,Integer>();
        for(var draw:render.draws)for(var group:draw.groups())if(group.name().startsWith("$crawler_track"))trackTotals.merge(group.name(),1,Integer::sum);
        var output=new ArrayList<AircraftPartPoses.Draw>();int index=0;
        for(var draw:render.draws){
            var pose=draw.pose();String rotorKey=null;BreakupMeshes.Part rotorPart=null;
            if(ac instanceof HeliEntity heli){
                var spun=rotor(heli,models,draw,s,index,partial,render);pose=spun.pose();rotorKey=spun.key();rotorPart=spun.part();
            }
            for(var group:draw.groups()){
                String name=group.name();
                if(eject&&turret!=null&&s.age>=96&&name.startsWith("$weapon")){
                    if(name.equals("$weapon0")){
                        scatter(ac,render,s,"turret",turret.source(),pose,96,new Vec3(.08,.48,-.04));s.turret=true;s.parts|=16;
                        var center=turret.bounds().center();center.y=turret.bounds().low().y;
                        var ringPose=new Matrix4f(WreckAppearance.root(ac)).mul(pose).translate(center);
                        VehicleDetailLayers.add(render,ringPose,DARK,List.of(RING),0xFF252322);InteriorFirelight.add(ac,render,ringPose,RING);s.ring=world(ac,ringPose,new Vector3f(0,.1F,0));
                    }continue;
                }
                if(tank&&name.equals("$crawler_track"+Math.floorMod(s.seed,2))&&s.age>=20){
                    int tooth=trackIndices.merge(name,1,Integer::sum)-1,total=trackTotals.getOrDefault(name,1),gap=Math.max(1,total/3);
                    s.track=true;s.parts|=2;
                    if(tooth<gap){if(tooth==0)scatter(ac,render,s,"track",group,pose,20,new Vec3(.08,.10,.06));continue;}
                    // Repeated links retain their geometry. The last four links sag into the gap.
                    if(tooth>=total-4){float k=tooth-(total-4)+1;var hanging=new Matrix4f().translate(0,-.045F*k*k,0).mul(pose);output.add(new AircraftPartPoses.Draw(hanging,List.of(group)));}
                    else output.add(new AircraftPartPoses.Draw(pose,List.of(group)));
                    continue;
                }

                if(name.equals("$body")&&body!=null){
                    var shape=group;
                    if(s.age>=36){shape=body.coverOpen();if(body.cover().vertexCount()>0){s.cover=true;s.parts|=1;scatter(ac,render,s,"cover",body.cover(),pose,36,new Vec3(.10,.34,-.08));}}
                    if(plane&&s.age>=56&&body.wing().vertexCount()>0){shape=body.wingGone();s.wing=true;s.parts|=4;scatter(ac,render,s,"wing",body.wing(),pose,56,new Vec3(.13,.18,.02));}
                    if(plane&&s.age>=110&&body.tail().vertexCount()>0){shape=body.tailGone();s.tail=true;s.parts|=8;scatter(ac,render,s,"tail",body.tail(),pose,110,new Vec3(-.08,.16,-.12));}
                    output.add(new AircraftPartPoses.Draw(pose,List.of(shape)));
                    if(s.cover||s.wing||s.tail){
                        var settled=new Matrix4f(WreckAppearance.root(ac)).mul(pose);
                        VehicleDetailLayers.add(render,settled,DARK,List.of(body.machinery()),0xFF514D45);
                        InteriorFirelight.add(ac,render,settled,body.machinery());
                        Vec3 local=s.tail?new Vec3(0,(body.bounds().low().y+body.bounds().high().y)*.45,body.tailCut()+.1):body.opening();
                        if(local!=null)s.opening=world(ac,settled,new Vector3f((float)local.x,(float)local.y,(float)local.z));
                    }continue;
                }
                if((name.startsWith("$enginecover")||name.startsWith("$cover"))&&s.age>=36){
                    var part=BreakupMeshes.part(group);if(part!=null&&group.vertexCount()<=1200){
                        scatter(ac,render,s,"named_cover"+name,group,pose,36,new Vec3(.1,.3,0));PanelInteriors.add(render,new Matrix4f(WreckAppearance.root(ac)).mul(pose),group,ac);s.cover=true;s.parts|=1;continue;
                    }
                }
                if(plane&&body!=null&&!name.equals("$body")&&(s.wing||s.tail)){
                    var part=BreakupMeshes.part(group);
                    if(part!=null&&(s.wing&&part.bounds().center().x>body.wingCut()||s.tail&&part.bounds().center().z<body.tailCut()))continue;
                }
                if(rotorKey!=null&&rotorPart!=null&&s.struck.contains(rotorKey)){output.add(new AircraftPartPoses.Draw(pose,List.of(rotorPart.stem())));s.parts|=32;}
                else output.add(new AircraftPartPoses.Draw(pose,List.of(group)));
            }index++;
        }
        render.draws.clear();render.draws.addAll(output);
    }
    private record Rotor(Matrix4f pose,String key,BreakupMeshes.Part part){}
    private static Rotor rotor(HeliEntity ac,AircraftModels.VehicleModels models,AircraftPartPoses.Draw draw,WreckSequenceEffects.State s,int index,float partial,AircraftRenderState render){
        if(draw.groups().size()!=1)return new Rotor(draw.pose(),null,null);
        var source=draw.groups().getFirst();int rotorIndex=-1;
        var info=ac.getHeliInfo();for(int r=0;r<Math.min(8,info.rotorList.size());r++){
            var groups=models.part(info.rotorList.get(r).modelName);if(groups.stream().anyMatch(g->source.name().startsWith(g.name()))){rotorIndex=r;break;}
        }
        if(rotorIndex<0)return new Rotor(draw.pose(),null,null);
        String key=rotorIndex+":"+index;if(s.rotorStarts.size()>=16&&!s.rotorStarts.containsKey(key))return new Rotor(draw.pose(),null,null);
        var rotor=info.rotorList.get(rotorIndex);var hub=rotor.oldRenderMethod?Vec3.ZERO:rotor.pos;var axis=rotor.rot;
        if(axis.lengthSqr()<.01)return new Rotor(draw.pose(),null,null);
        var body=new Matrix4f().translate(0,io.github.mcheliceport.content.aircraft.AircraftInfo.GLOBAL_Y_OFFSET,0)
                .rotateY((float)Math.toRadians(-render.yaw)).rotateX((float)Math.toRadians(render.pitch)).rotateZ((float)Math.toRadians(render.roll));
        var start=s.rotorStarts.computeIfAbsent(key,k->new Matrix4f(body).invert().mul(draw.pose()));
        float speed=s.rotorSpeeds.computeIfAbsent(key,k->(float)Math.clamp(Math.abs(ac.rotationRotor),8,35));
        float spin=(float)Math.toRadians(DestructionTimeline.spin(s.age+partial, speed));var normal=axis.normalize();
        var pose=new Matrix4f(body).mul(start).translate((float)hub.x,(float)hub.y,(float)hub.z).rotate(spin,(float)normal.x,(float)normal.y,(float)normal.z).translate((float)-hub.x,(float)-hub.y,(float)-hub.z);
        var part=BreakupMeshes.rotor(source,hub);var level=Minecraft.getInstance().level;
        if(part!=null&&s.seenAlive&&s.age<140&&!s.struck.contains(key)&&s.strikes<2&&level!=null){
            var settled=new Matrix4f(WreckAppearance.root(ac)).mul(pose);
            var bounds=part.bounds();float[] spans={bounds.high().x-bounds.low().x,bounds.high().y-bounds.low().y,bounds.high().z-bounds.low().z};int primary=0;for(int a=1;a<3;a++)if(spans[a]>spans[primary])primary=a;
            for(int n=0;n<part.tips().size();n++){
                int pointIndex=(n+primary*2)%part.tips().size();var p=part.tips().get(pointIndex);var at=world(ac,settled,new Vector3f((float)p.x,(float)p.y,(float)p.z));String point=key+":"+pointIndex;
                var previous=s.tips.put(point,at);if(previous==null||previous.distanceToSqr(at)>256||previous.distanceToSqr(at)<.0025)continue;
                if(!WreckSequenceEffects.rotorRay(level))break;
                if(!level.hasChunkAt(BlockPos.containing(at))||!level.hasChunkAt(BlockPos.containing(previous)))continue;
                var hit=level.clip(new ClipContext(previous,at.add(0,-.12,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty()));
                if(hit.getType()==HitResult.Type.BLOCK&&!hit.isInside()&&WreckSequenceEffects.rotorStrike(level,ac,hit.getLocation())){
                    s.struck.add(key);s.fragments.put(key,new WreckSequenceEffects.Strike(part.tip(),new Matrix4f(pose),s.age));break;
                }
            }
        }
        var fragment=s.fragments.get(key);
        if(fragment!=null)scatter(ac,newRenderTexture(ac),s,"rotor"+key,fragment.group(),fragment.pose(),fragment.age(),new Vec3(.1,.12,.1));
        return new Rotor(pose,key,part);
    }
    private static Identifier newRenderTexture(AircraftEntity ac){return Identifier.fromNamespaceAndPath("mcheli","textures/helicopters/"+ac.getTextureName()+".png");}
    private static Vec3 world(AircraftEntity ac,Matrix4f pose,Vector3f point){var p=pose.transformPosition(new Vector3f(point));return ac.position().add(p.x,p.y,p.z);}
    private static void scatter(AircraftEntity ac,AircraftRenderState render,WreckSequenceEffects.State s,String name,McheliModel.Group group,Matrix4f pose,int age,Vec3 impulse){scatter(ac,render.texture,s,name,group,pose,age,impulse);}
    private static void scatter(AircraftEntity ac,Identifier texture,WreckSequenceEffects.State s,String name,McheliModel.Group group,Matrix4f pose,int age,Vec3 impulse){
        if(!s.seenAlive||s.age<age||s.age>age+28||s.scattered.contains(name)||group.vertexCount()==0)return;
        var part=BreakupMeshes.part(group);if(part!=null&&LoosePanels.scatter(ac,texture,group,pose,part.bounds().center(),impulse))s.scattered.add(name);
    }
}

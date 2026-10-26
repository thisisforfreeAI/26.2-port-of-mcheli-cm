package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.client.model.VehicleImpactSurface;
import io.github.mcheliceport.client.model.VehicleSurfaceFinish;
import io.github.mcheliceport.client.particle.EffectPerformance;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.VehicleImpactLook;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.net.VehicleImpactPayload;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Bounded session-local marks, fitted once and subsequently animated with their owning model group. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class VehicleImpactMarks {
    public static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("mcheli_ce_port","textures/effect/vehicle_impacts.png");
    private static final Map<AircraftEntity,State> STATES=new LinkedHashMap<>();
    private static ClientLevel world;
    private static int fits,visible;
    private static final class State {
        int lastDamage;
        net.minecraft.world.phys.Vec3 smokeOrigin; long smokeTick;
        final ArrayDeque<Pending> pending=new ArrayDeque<>();
        final ArrayDeque<Mark> marks=new ArrayDeque<>();
        State(int damage){lastDamage=damage;}
    }
    private record Pending(VehicleImpactPayload payload,long born,boolean blast) {}
    private static final class Mark {
        final McheliModel.Group group;
        final int occurrence,instances,tile;
        final VehicleImpactSurface.Mesh mesh;
        final long born;
        final boolean blast; final Vector3f contact;
        final float radius,angle; final int crackStage;
        float opacity=1,target=1;
        Mark(McheliModel.Group group,int occurrence,int instances,int tile,VehicleImpactSurface.Mesh mesh,long born,boolean blast,Vector3f contact,float radius,float angle,int crackStage){
            this.group=group;this.occurrence=occurrence;this.instances=instances;this.tile=tile;this.mesh=mesh;this.born=born;this.blast=blast;this.contact=contact;this.radius=radius;this.angle=angle;this.crackStage=crackStage;
        }
    }
    public record Draw(Matrix4f pose,VehicleImpactSurface.Mesh mesh,int tile,float opacity) {}
    private VehicleImpactMarks() {}
    public static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS);}
    public static void clear(){STATES.clear();world=null;}
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){fits=0;visible=0;}
    public static void receive(VehicleImpactPayload payload){ receive(payload,false); }
    static void receiveBlast(VehicleImpactPayload payload){ receive(payload,true); }
    private static void receive(VehicleImpactPayload payload,boolean blast){
        var mc=Minecraft.getInstance();if(mc.level==null||!enabled()||!payload.valid())return;
        if(world!=mc.level){clear();world=mc.level;}
        if(!(world.getEntity(payload.entityId()) instanceof AircraftEntity ac)||!ac.getUUID().equals(payload.vehicle())
                ||mc.player==null||mc.player.distanceToSqr(ac)>96*96)return;
        if(STATES.size()>=64&&!STATES.containsKey(ac))STATES.remove(STATES.keySet().iterator().next());
        var s=STATES.computeIfAbsent(ac,k->new State(ac.getDamageTaken()));
        if(s.pending.size()>=4)s.pending.removeFirst();s.pending.addLast(new Pending(payload,world.getGameTime(),blast));
    }
    public static void tick(ClientLevel level){
        if(level!=world||!enabled()){clear();world=level;}
        if(level==null)return;
        long now=level.getGameTime();
        var quality=ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        int cap=quality==ClientConfig.EffectBudget.LOW?8:quality==ClientConfig.EffectBudget.HIGH?24:16;
        for(var iterator=STATES.entrySet().iterator();iterator.hasNext();){
            var entry=iterator.next();var ac=entry.getKey();var state=entry.getValue();
            if(ac.isRemoved()||ac.level()!=level){iterator.remove();continue;}
            while(state.marks.size()>cap)state.marks.removeFirst();
            int damage=ac.getDamageTaken(),healed=Math.max(0,state.lastDamage-damage);
            for(var mark:state.marks){
                if(healed>0)mark.target=VehicleImpactLook.repaired(mark.target,healed,ac.getMaxHP(),damage==0);
                if(now-mark.born>6000)mark.target=0;
                mark.opacity=Math.max(mark.target,mark.opacity-.08F);
            }
            state.lastDamage=damage;
            state.pending.removeIf(p->now-p.born>40);
            state.marks.removeIf(m->m.opacity<=.001F);
            if(state.marks.isEmpty()&&state.pending.isEmpty())iterator.remove();
        }
    }
    public static void extract(AircraftEntity entity,AircraftRenderState render){
        if(!enabled()||render.thermal||render.far||entity.isDestroyed()||world==null)return;
        var state=STATES.get(entity);if(state==null)return;
        var mc=Minecraft.getInstance();double distance=mc.gameRenderer.mainCamera().position().distanceToSqr(entity.position());
        var quality=ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        float range=quality==ClientConfig.EffectBudget.LOW?32:quality==ClientConfig.EffectBudget.HIGH?64:48;
        if(distance>=range*range)return;
        int pressure=EffectPerformance.pressure();
        int cap=quality==ClientConfig.EffectBudget.LOW?8:quality==ClientConfig.EffectBudget.HIGH?24:16;
        if(!state.pending.isEmpty()&&fits<2&&pressure<2){
            fits++;
            var pending=state.pending.removeFirst();
            boolean[] waiting={false};
            Mark mark=fit(pending,render,waiting,entity,state);
            if(mark==null&&waiting[0]&&world.getGameTime()-pending.born<=40)state.pending.addLast(pending);
            if(mark!=null){
                while(state.marks.size()>=cap)state.marks.removeFirst();
                if(stored()>=512){for(var s:STATES.values())if(!s.marks.isEmpty()){s.marks.removeFirst();break;}}
                state.marks.addLast(mark);
            }
        }
        int sceneCap=quality==ClientConfig.EffectBudget.LOW?32:quality==ClientConfig.EffectBudget.HIGH?128:96;
        if(pressure>0)sceneCap/=1+pressure;
        float fade=Math.clamp((range-(float)Math.sqrt(distance))/(range*.3F),0,1);fade=fade*fade*(3-2*fade);
        int count=0;boolean smokeChosen=false;
        for(var iterator=state.marks.descendingIterator();iterator.hasNext()&&count<cap&&visible<sceneCap;){
            Mark mark=iterator.next();Matrix4f pose=partPose(render.draws,mark.group,mark.occurrence,mark.instances);
            // Hidden/replaced instances must not transfer a mark to a different moving part.
            if(pose==null){iterator.remove();continue;}
            if(!smokeChosen&&mark.blast&&mark.opacity>.25F&&world.getGameTime()-mark.born<400){
                var at=pose.transformPosition(new Vector3f(mark.contact));
                state.smokeOrigin=entity.position().add(at.x,at.y,at.z);state.smokeTick=world.getGameTime();smokeChosen=true;
            }
            render.impactDraws.add(new Draw(new Matrix4f(pose),mark.mesh,mark.tile,mark.opacity*fade));count++;visible++;
        }
    }
    static Matrix4f partPose(List<AircraftPartPoses.Draw> draws,McheliModel.Group group,int occurrence,int expectedInstances){
        Matrix4f pose=null;int count=0;
        for(var draw:draws)for(var candidate:draw.groups())if(candidate==group){
            if(count++==occurrence)pose=draw.pose();break;
        }
        return count==expectedInstances?pose:null;
    }
    private static Mark fit(Pending pending,AircraftRenderState render,boolean[] waiting,AircraftEntity entity,State state){
        var p=pending.payload;
        Matrix4f body=VehicleImpactLook.body(render.yaw,render.pitch,render.roll);
        Vector3f point=body.transformPosition(new Vector3f(p.x(),p.y(),p.z()));
        Vector3f direction=body.transformDirection(new Vector3f(p.dx(),p.dy(),p.dz()));
        Vector3f origin=new Vector3f(point).fma(pending.blast?-32:-2,direction);
        McheliModel.Group chosen=null;VehicleImpactSurface.Hit hit=null;Vector3f localOrigin=null,localDirection=null;
        VehicleImpactSurface.Index chosenIndex=null;
        Matrix4f chosenPose=null;
        int occurrence=0,chosenOccurrence=0,groups=0;
        var occurrences=new IdentityHashMap<McheliModel.Group,Integer>();
        int[] work={8192};
        for(var draw:render.draws){
            Matrix4f inverse=new Matrix4f(draw.pose()).invert();if(!inverse.isFinite())continue;
            Vector3f local=inverse.transformPosition(new Vector3f(origin));
            Vector3f dir=inverse.transformDirection(new Vector3f(direction));
            for(var group:draw.groups()){
                occurrence=occurrences.getOrDefault(group,0);occurrences.put(group,occurrence+1);
                if(++groups>256||work[0]<=0)return null;
                var index=VehicleImpactSurface.get(group);
                if(index==null){waiting[0]|=VehicleImpactSurface.request(group);continue;}
                var candidate=index.ray(group.data(),local,dir,hit==null?(pending.blast?64:7):hit.distance(),work);
                if(candidate!=null){chosen=group;chosenIndex=index;chosenPose=draw.pose();hit=candidate;localOrigin=local;localDirection=dir;chosenOccurrence=occurrence;}
            }
        }
        if(chosen==null||work[0]<=0||waiting[0])return null;
        var localContact=new Vector3f(localDirection).mul(hit.distance()).add(localOrigin);
        boolean glass=VehicleSurfaceFinish.material(chosen.name())==VehicleSurfaceFinish.Material.GLASS;
        if(!pending.blast&&!glass&&chosen.name().toLowerCase(java.util.Locale.ROOT).startsWith("$canopy")){
            var uv=VehicleImpactSurface.textureUv(chosen.data(),hit.triangle(),localContact);
            if(uv!=null){
                Integer alpha=io.github.mcheliceport.client.model.VehicleGlassAlpha.alpha(render.texture,uv.x,uv.y);
                if(alpha==null){waiting[0]=true;return null;}glass=alpha<240;
            }
        }
        Mark previous=null;
        if(glass&&!pending.blast)for(var m:state.marks){
            if(m.tile>=6&&m.group==chosen&&m.occurrence==chosenOccurrence&&m.instances==occurrences.get(chosen)&&m.target>.25F
                    &&m.contact.distanceSquared(localContact)<Math.max(.2F,m.radius)*Math.max(.2F,m.radius))previous=m;
        }
        float radius=glass&&!pending.blast?io.github.mcheliceport.client.model.VehicleGlassLook.radius(previous==null?0:previous.radius,p.radius()):p.radius();
        int stage=glass&&!pending.blast?io.github.mcheliceport.client.model.VehicleGlassLook.stage(previous==null?0:previous.crackStage):0;
        float angle=previous!=null?previous.angle:Math.floorMod(p.seed(),628)*.01F;
        if(previous!=null)localContact=new Vector3f(previous.contact);
        // Keep the existing fracture center as its footprint grows over repeated strikes.
        Vector3f decalOrigin=new Vector3f(localContact).fma(-hit.distance(),localDirection);
        var mesh=chosenIndex.decal(chosen.data(),hit,decalOrigin,localDirection,radius,angle);
        if(mesh.vertices().isEmpty())return null;
        int tile=pending.blast?4+Math.floorMod(p.seed(),2):glass?(stage>1?7:6):tile(chosen.name(),p.cannon(),p.seed());
        if(previous!=null)state.marks.remove(previous);
        if(tile<6&&!pending.blast&&world.getGameTime()-pending.born<=6){
            var contact=chosenPose.transformPosition(new Vector3f(localDirection).mul(hit.distance()).add(localOrigin));
            var normal=new org.joml.Matrix3f(chosenPose).invert().transpose().transform(new Vector3f(mesh.normal())).normalize();
            io.github.mcheliceport.client.particle.VehicleShrapnel.emit(world,
                    entity.position().add(contact.x,contact.y,contact.z),new net.minecraft.world.phys.Vec3(direction.x,direction.y,direction.z),
                    new net.minecraft.world.phys.Vec3(normal.x,normal.y,normal.z),entity.getDeltaMovement(),p.cannon(),p.seed());
        }
        return new Mark(chosen,chosenOccurrence,occurrences.get(chosen),tile,mesh,pending.born,pending.blast,localContact,radius,angle,stage);
    }
    static int tile(String group,boolean cannon,int seed){
        return VehicleSurfaceFinish.material(group)==VehicleSurfaceFinish.Material.GLASS
                ?6+Math.floorMod(seed,2):cannon?4+Math.floorMod(seed,2):Math.floorMod(seed,4);
    }
    public static net.minecraft.world.phys.Vec3 damageOrigin(AircraftEntity entity){
        var state=STATES.get(entity);
        return enabled()&&world==entity.level()&&state!=null&&world.getGameTime()-state.smokeTick<=4?state.smokeOrigin:null;
    }
    public static int crackStage(AircraftEntity entity){var state=STATES.get(entity);return state==null?0:state.marks.stream().mapToInt(m->m.crackStage).max().orElse(0);}
    public static int stored(){return STATES.values().stream().mapToInt(s->s.marks.size()).sum();}
    public static int pending(){return STATES.values().stream().mapToInt(s->s.pending.size()).sum();}
}

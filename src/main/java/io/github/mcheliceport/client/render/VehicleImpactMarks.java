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
        final ArrayDeque<Pending> pending=new ArrayDeque<>();
        final ArrayDeque<Mark> marks=new ArrayDeque<>();
        State(int damage){lastDamage=damage;}
    }
    private record Pending(VehicleImpactPayload payload,long born) {}
    private static final class Mark {
        final McheliModel.Group group;
        final int occurrence,instances,tile;
        final VehicleImpactSurface.Mesh mesh;
        final long born;
        float opacity=1,target=1;
        Mark(McheliModel.Group group,int occurrence,int instances,int tile,VehicleImpactSurface.Mesh mesh,long born){
            this.group=group;this.occurrence=occurrence;this.instances=instances;this.tile=tile;this.mesh=mesh;this.born=born;
        }
    }
    public record Draw(Matrix4f pose,VehicleImpactSurface.Mesh mesh,int tile,float opacity) {}
    private VehicleImpactMarks() {}
    public static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS);}
    public static void clear(){STATES.clear();world=null;}
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){fits=0;visible=0;}
    public static void receive(VehicleImpactPayload payload){
        var mc=Minecraft.getInstance();if(mc.level==null||!enabled()||!payload.valid())return;
        if(world!=mc.level){clear();world=mc.level;}
        if(!(world.getEntity(payload.entityId()) instanceof AircraftEntity ac)||!ac.getUUID().equals(payload.vehicle())
                ||mc.player==null||mc.player.distanceToSqr(ac)>96*96)return;
        if(STATES.size()>=64&&!STATES.containsKey(ac))STATES.remove(STATES.keySet().iterator().next());
        var s=STATES.computeIfAbsent(ac,k->new State(ac.getDamageTaken()));
        if(s.pending.size()>=4)s.pending.removeFirst();s.pending.addLast(new Pending(payload,world.getGameTime()));
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
            Mark mark=fit(pending,render,waiting);
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
        int count=0;
        for(var iterator=state.marks.descendingIterator();iterator.hasNext()&&count<cap&&visible<sceneCap;){
            Mark mark=iterator.next();Matrix4f pose=partPose(render.draws,mark.group,mark.occurrence,mark.instances);
            // Hidden/replaced instances must not transfer a mark to a different moving part.
            if(pose==null){iterator.remove();continue;}
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
    private static Mark fit(Pending pending,AircraftRenderState render,boolean[] waiting){
        var p=pending.payload;
        Matrix4f body=VehicleImpactLook.body(render.yaw,render.pitch,render.roll);
        Vector3f point=body.transformPosition(new Vector3f(p.x(),p.y(),p.z()));
        Vector3f direction=body.transformDirection(new Vector3f(p.dx(),p.dy(),p.dz()));
        Vector3f origin=new Vector3f(point).fma(-2,direction);
        McheliModel.Group chosen=null;VehicleImpactSurface.Hit hit=null;Vector3f localOrigin=null,localDirection=null;
        VehicleImpactSurface.Index chosenIndex=null;
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
                var candidate=index.ray(group.data(),local,dir,hit==null?7:hit.distance(),work);
                if(candidate!=null){chosen=group;chosenIndex=index;hit=candidate;localOrigin=local;localDirection=dir;chosenOccurrence=occurrence;}
            }
        }
        if(chosen==null||work[0]<=0||waiting[0])return null;
        var mesh=chosenIndex.decal(chosen.data(),hit,localOrigin,localDirection,p.radius(),
                Math.floorMod(p.seed(),628)*.01F);
        if(mesh.vertices().isEmpty())return null;
        int tile=tile(chosen.name(),p.cannon(),p.seed());
        return new Mark(chosen,chosenOccurrence,occurrences.get(chosen),tile,mesh,pending.born);
    }
    static int tile(String group,boolean cannon,int seed){
        return VehicleSurfaceFinish.material(group)==VehicleSurfaceFinish.Material.GLASS
                ?6+Math.floorMod(seed,2):cannon?4+Math.floorMod(seed,2):Math.floorMod(seed,4);
    }
    public static int stored(){return STATES.values().stream().mapToInt(s->s.marks.size()).sum();}
    public static int pending(){return STATES.values().stream().mapToInt(s->s.pending.size()).sum();}
}

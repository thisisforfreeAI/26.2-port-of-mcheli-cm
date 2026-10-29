package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.model.*;
import io.github.mcheliceport.client.render.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.damage.AirframeDamage;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.util.McheliMath;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;

/** Real ground transitions and affine gear travel. Uses the existing audio scan, not another scan. */
public final class LandingEffects {
    private record Contact(Vec3 at,BlockState block){}
    private static final class Gear {
        final AircraftInfo.LandingGear part;final McheliModel.Group group;final LandingGearMesh.Shape shape;
        final LandingLook look=new LandingLook();List<Contact> points=List.of();
        long sampled;boolean contact;double penetration;
        Gear(AircraftInfo.LandingGear part,McheliModel.Group group,LandingGearMesh.Shape shape){this.part=part;this.group=group;this.shape=shape;}
    }
    private static final class State {
        final AircraftModels.VehicleModels models;final List<Gear> gears=new ArrayList<>();Vec3 previous;long seen;
        State(PlaneEntity ac,AircraftModels.VehicleModels models){
            this.models=models;
            for(int i=0;i<Math.min(8,ac.getAcInfo().landingGear.size());i++){
                var part=ac.getAcInfo().landingGear.get(i);if(part.hatch||part.reverse)continue;
                for(var group:models.part(part.modelName)){var shape=LandingGearMesh.get(group);if(shape!=null&&gears.size()<8)gears.add(new Gear(part,group,shape));}
            }
        }
    }
    private static final Map<PlaneEntity,State> STATES=new LinkedHashMap<>();
    private static ClientLevel world;private static long tick;private static int rays,births,touchdowns;
    private LandingEffects(){}
    public static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.LANDING_FEEDBACK);}
    public static int states(){return STATES.size();}public static int rays(){return rays;}public static int births(){return births;}public static int touchdowns(){return touchdowns;}
    public static void clear(){STATES.clear();world=null;tick=0;rays=births=touchdowns=0;}
    public static void begin(Minecraft mc){
        if(world!=mc.level||!enabled()){clear();world=mc.level;}
        if(world==null||!enabled()||mc.isPaused())return;
        ++tick;rays=births=0;var ear=mc.gameRenderer.mainCamera().position();
        STATES.entrySet().removeIf(e->e.getKey().isRemoved()||e.getKey().level()!=world||tick-e.getValue().seen>4||ear.distanceToSqr(e.getKey().position())>64*64);
    }
    public static void observe(AircraftEntity aircraft){
        var mc=Minecraft.getInstance();if(world==null||!enabled()||!(aircraft instanceof PlaneEntity ac)||mc.isPaused())return;
        var info=ac.getAcInfo();
        if(info==null||ac.isRemoved()||ac.isDestroyed()||ac.isInWater()||ac.partLandingGear==null||ac.partLandingGear.rotation>5
                ||Math.abs(ac.getRoll())>35||AirframeDamage.missing(ac)!=0||mc.gameRenderer.mainCamera().position().distanceToSqr(ac.position())>64*64){STATES.remove(ac);return;}
        var models=AircraftModels.peek(info);if(models==null)return;
        var state=STATES.get(ac);
        if(state==null||state.models!=models){if(state==null&&STATES.size()>=32)return;state=new State(ac,models);if(state.gears.isEmpty()){STATES.remove(ac);return;}STATES.put(ac,state);}
        state.seen=tick;
        Vec3 step=state.previous==null?Vec3.ZERO:ac.position().subtract(state.previous);boolean stable=state.previous!=null&&Double.isFinite(step.lengthSqr())&&step.lengthSqr()<16*16;state.previous=ac.position();
        double vertical=Math.min(step.y,ac.getDeltaMovement().y),speed=Math.hypot(step.x,step.z);
        for(int i=0;i<state.gears.size();i++){
            var gear=state.gears.get(i);
            if(Math.floorMod(tick+ac.getId(),4)==0&&rays+gear.shape.contacts().size()<=24)sample(ac,gear);
            var impact=gear.look.tick(gear.contact,vertical,speed,gear.penetration,stable&&gear.sampled>0&&tick-gear.sampled<=8);
            if(impact.touchdown()){touchdowns++;for(var point:gear.points)emit(ac,point,impact);}
        }
    }
    private static void sample(PlaneEntity ac,Gear gear){
        int contacts=0;double penetration=0;var points=new ArrayList<Contact>();
        for(var local:gear.shape.contacts()){
            Vec3 p=ac.position().add(McheliMath.rotVec3(local.x,local.y,local.z,-ac.getYaw(),-ac.getPitch(),-ac.getRoll())).add(0,AircraftInfo.GLOBAL_Y_OFFSET,0);
            var pos=BlockPos.containing(p);if(!world.hasChunkAt(pos))continue;
            rays++;var hit=world.clip(new ClipContext(p.add(0,.35,0),p.add(0,-.65,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,CollisionContext.empty()));
            if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=Direction.UP)continue;
            var ground=world.getBlockState(hit.getBlockPos());if(!ground.getFluidState().isEmpty())continue;
            double gap=p.y-hit.getLocation().y;if(gap>.14||gap<-.3)continue;
            contacts++;penetration+=Math.clamp(.05-gap,0,.12);points.add(new Contact(hit.getLocation().add(0,.035,0),ground));
        }
        gear.sampled=tick;gear.contact=contacts>0;gear.penetration=contacts==0?0:penetration/contacts;gear.points=List.copyOf(points);
    }
    private static void emit(PlaneEntity ac,Contact point,LandingLook.Impact impact){
        Vec3 p=point.at;var mc=Minecraft.getInstance();int count=ClientConfig.get(ClientConfig.EFFECT_BUDGET)==ClientConfig.EffectBudget.LOW?1:2;
        count=Math.max(1,count/(1+EffectPerformance.pressure()));
        var color=ClientEffects.groundTint(world,BlockPos.containing(p.add(0,-.1,0)),point.block,.72F,.7F,.66F);
        float hardness=point.block.getDestroySpeed(world,BlockPos.containing(p.add(0,-.1,0)));
        if(hardness>1||hardness<0)color=new float[]{.8F,.8F,.78F};
        for(int i=0;i<count&&births<12;i++){
            if(!EffectAdmission.admit(world,p.x,p.y,p.z,false,.65F))continue;
            mc.particleEngine.add(new LandingPuffParticle(world,p,ac.getDeltaMovement().x*.15+(i==0?.08:-.08),ac.getDeltaMovement().z*.15,color));births++;
        }
        if(impact.hard()&&(hardness>1||hardness<0)){
            for(int i=0;i<2&&births<12;i++)if(EffectAdmission.admit(world,p.x,p.y,p.z,false,.06F)){
                mc.particleEngine.add(new SparkParticle(world,p.x,p.y,p.z,(i==0?.1:-.1),.07,-.1,8,.055F));births++;}
        }
    }
    public static float compression(PlaneEntity ac){var state=STATES.get(ac);if(state==null||state.gears.isEmpty())return 0;float total=0;for(var gear:state.gears)total+=gear.look.compression(1);return total/state.gears.size();}
    public static int supported(PlaneEntity ac){var state=STATES.get(ac);return state==null?0:state.gears.size();}
    public static float spread(PlaneEntity ac){var state=STATES.get(ac);if(state==null||state.gears.isEmpty())return 0;float lo=1,hi=0;for(var gear:state.gears){float value=gear.look.compression(1);lo=Math.min(lo,value);hi=Math.max(hi,value);}return hi-lo;}
    public static void apply(AircraftEntity ac,AircraftRenderState render,float partial){
        if(!enabled()||render.far||render.thermal||ac.isDestroyed()||!(ac instanceof PlaneEntity plane))return;
        var state=STATES.get(plane);if(state==null||state.gears.isEmpty())return;
        float drop=0;for(var gear:state.gears)drop+=gear.look.compression(partial);drop/=state.gears.size();
        if(drop<=.0001F)return;
        for(int i=0;i<render.draws.size();i++){
            var draw=render.draws.get(i);var pose=new Matrix4f().translate(0,-drop,0).mul(draw.pose());
            for(var gear:state.gears)if(draw.groups().contains(gear.group)){
                float top=(float)gear.part.pos.y,height=top-gear.shape.bottom();
                float scale=Math.clamp(1-gear.look.compression(partial)/height,.85F,1);
                pose.translate(0,top,0).scale(1,scale,1).translate(0,-top,0);break;
            }
            render.draws.set(i,new AircraftPartPoses.Draw(pose,draw.groups()));
        }
    }
}

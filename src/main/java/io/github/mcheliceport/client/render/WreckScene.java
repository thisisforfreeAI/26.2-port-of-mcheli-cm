package io.github.mcheliceport.client.render;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Where each nearby crashed plane or helicopter hit the ground, which way it was going and the path it
 * slid along. Ground scars, the fuel fire and the debris field read this; nothing here touches the world.
 * A wreck first seen already destroyed (joining near an old crash) is {@link Scene#late}: it gets a scar
 * and a settled debris field, but no fresh fire, flying debris or trail.
 */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class WreckScene {
    public static final int MAX_TRAIL=48,FADE=200;
    public static final double TRAIL_STEP=.9;
    public static final class Scene {
        public final int seed;
        public boolean alive=true,late;
        /** Ground contact: where, the horizontal direction and speed (blocks/tick), and the game tick. */
        public Vec3 impact,heading=new Vec3(1,0,0);public double speed;public long impactTick;
        public final List<Vec3> trail=new ArrayList<>();
        /** Set when the entity is gone; scars and debris fade for {@link #FADE} ticks after it. */
        public long goneTick=-1;
        public Vec3 position;public float span,length;
        Scene(int seed){this.seed=seed;}
        public boolean fresh(){return impact!=null&&!late;}
        public float fade(long now){return goneTick<0?1:Math.clamp(1-(now-goneTick)/(float)FADE,0,1);}
    }
    private static final Map<AircraftEntity,Scene> STATES=new LinkedHashMap<>();
    private static ClientLevel world;
    private WreckScene(){}
    public static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS);}
    /** Planes and helicopters crash and slide; ground vehicles and ships keep the older wreck look. */
    public static boolean supported(AircraftEntity ac){
        return ac instanceof io.github.mcheliceport.entity.plane.PlaneEntity||ac instanceof io.github.mcheliceport.entity.heli.HeliEntity;
    }
    public static Scene get(AircraftEntity ac){return STATES.get(ac);}
    public static Collection<Scene> scenes(){return Collections.unmodifiableCollection(STATES.values());}
    public static void clear(){STATES.clear();world=null;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.level!=world){clear();world=mc.level;}
        if(world==null||!enabled()){STATES.clear();return;}
        if(mc.isPaused())return;
        long now=world.getGameTime();var camera=mc.gameRenderer.mainCamera().position();
        for(var entity:world.entitiesForRendering()){
            if(!(entity instanceof AircraftEntity ac)||!supported(ac)||ac.getAcInfo()==null)continue;
            var scene=STATES.get(ac);
            if(scene==null){
                if(camera.distanceToSqr(ac.position())>128*128||STATES.size()>=64)continue;
                scene=new Scene(ac.getUUID().hashCode());scene.late=ac.isDestroyed();STATES.put(ac,scene);
            }
            if(!ac.isDestroyed()){scene.alive=true;scene.late=false;scene.impact=null;scene.trail.clear();continue;}
            scene.alive=false;scene.position=ac.position();
            scene.span=Math.max(ac.getBbWidth(),1);
            var model=io.github.mcheliceport.client.model.AircraftModels.peek(ac.getAcInfo());
            scene.length=model==null?scene.span:Math.max(1,model.body().maxZ-model.body().minZ);
            update(scene,ac,now);
        }
        STATES.entrySet().removeIf(e->{
            var ac=e.getKey();var s=e.getValue();
            if((ac.isRemoved()||ac.level()!=world)&&s.goneTick<0)s.goneTick=now;
            if(s.goneTick>=0)return now-s.goneTick>FADE;
            return camera.distanceToSqr(ac.position())>160*160;
        });
    }
    private static void update(Scene scene,AircraftEntity ac,long now){
        var at=ac.position();
        if(scene.impact==null){
            if(!grounded(ac))return;
            scene.impact=at;scene.impactTick=now;
            var motion=at.subtract(ac.xo,ac.yo,ac.zo);var flat=new Vec3(motion.x,0,motion.z);
            if(scene.late||flat.lengthSqr()<1e-4){
                double yaw=Math.toRadians(ac.getYaw());scene.heading=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));scene.speed=0;
            }else{scene.heading=flat.normalize();scene.speed=flat.length();}
            if(!scene.late)scene.trail.add(at);
            return;
        }
        if(scene.late||scene.trail.isEmpty()||scene.trail.size()>=MAX_TRAIL)return;
        var last=scene.trail.getLast();
        if(Math.hypot(at.x-last.x,at.z-last.z)>=TRAIL_STEP&&grounded(ac))scene.trail.add(at);
    }
    private static boolean grounded(AircraftEntity ac){
        if(ac.onGround())return true;
        var level=ac.level();int x=(int)Math.floor(ac.getX()),z=(int)Math.floor(ac.getZ());
        if(!level.hasChunkAt(new net.minecraft.core.BlockPos(x,(int)ac.getY(),z)))return false;
        return ac.getY()-level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)<1.2;
    }
}

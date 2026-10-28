package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.weapon.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Bounded client motor trails. No weapon simulation, payload calls or additional entity scan. */
@net.neoforged.fml.common.EventBusSubscriber(modid="mcheli_ce_port",value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class ProjectileTrailEffects {
    private static final Map<BaseBulletEntity,Track> TRACKS=new WeakHashMap<>();
    private static final Map<WeaponInfo,ProjectileTrailLook.Profile> PROFILES=new WeakHashMap<>();
    private static ClientLevel world;private static long tick=Long.MIN_VALUE;private static int births,active;
    private static final class Track {Vec3 previous;long seen,lastEmission=Long.MIN_VALUE;Track(Vec3 p,long now){previous=p;seen=now;}}
    private ProjectileTrailEffects() {}
    @net.neoforged.bus.api.SubscribeEvent
    public static void lifecycle(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        if(world!=Minecraft.getInstance().level||!EffectQuality.enhanced())clear();
    }
    public static void clear(){TRACKS.clear();PROFILES.clear();world=null;tick=Long.MIN_VALUE;births=active=0;}
    public static boolean conventional(BaseBulletEntity bullet){
        var info=bullet.getInfo();return info!=null&&!info.useHBM&&info.payloadNTM==WeaponInfo.Payload.NONE
                &&(bullet instanceof RocketEntity||bullet instanceof AAMissileEntity||bullet instanceof ATMissileEntity
                ||bullet instanceof ASMissileEntity||bullet instanceof TvMissileEntity||bullet instanceof MarkerRocketEntity);
    }
    public static ProjectileTrailLook.Profile profile(BaseBulletEntity bullet,float size){
        var info=bullet.getInfo();if(info==null)return null;
        if(bullet.level() instanceof ClientLevel level&&world!=level){clear();world=level;}
        var p=PROFILES.get(info);if(p==null){if(PROFILES.size()>=256)PROFILES.clear();
            p=ProjectileTrailLook.profile(info.name,info.type,size,info.smokeMaxAge);PROFILES.put(info,p);}return p;
    }
    public static float motorScale(BaseBulletEntity bullet){
        if(!EffectQuality.enhanced()||!conventional(bullet))return 1;
        var info=bullet.getInfo();float size=info.smokeSize*(bullet instanceof ASMissileEntity?5:bullet instanceof AAMissileEntity||bullet instanceof ATMissileEntity?3.5F:2.5F);
        var p=profile(bullet,size);return ProjectileTrailLook.weight(bullet.getCountOnUpdate(),info.trajectoryParticleStartTick,info.trajectoryParticleEndTick)*(p.cruise()?.035F:.18F);
    }
    public static boolean emit(BaseBulletEntity bullet,String name,int requested,float size){
        if(!EffectQuality.enhanced()){clear();return false;}
        if(!conventional(bullet)||!name.equalsIgnoreCase("explode"))return false;
        if(!(bullet.level() instanceof ClientLevel level))return false;
        var mc=Minecraft.getInstance();if(world!=level){clear();world=level;}
        long now=level.getGameTime();
        if(tick!=now){tick=now;births=0;TRACKS.entrySet().removeIf(e->e.getKey().isRemoved()||e.getKey().level()!=level||now-e.getValue().seen>4);
            active=Math.max(1,TRACKS.size());}
        double distance=mc.gameRenderer.mainCamera().position().distanceToSqr(bullet.position());
        if(distance>144*144){TRACKS.remove(bullet);return true;}
        var info=bullet.getInfo();float weight=ProjectileTrailLook.weight(bullet.getCountOnUpdate(),info.trajectoryParticleStartTick,info.trajectoryParticleEndTick);
        Vec3 at=bullet.position();
        if(weight<=0||info.disableSmoke||!level.hasChunkAt(BlockPos.containing(at))||!level.getFluidState(BlockPos.containing(at)).isEmpty()){
            TRACKS.remove(bullet);return true;
        }
        var state=TRACKS.get(bullet);if(state==null){if(TRACKS.size()>=128)return true;
            state=new Track(new Vec3(bullet.xo,bullet.yo,bullet.zo),now);TRACKS.put(bullet,state);}
        if(state.lastEmission==now)return true;state.lastEmission=now;state.seen=now;
        Vec3 previous=state.previous;state.previous=at;
        int cap=ProjectileTrailLook.birthLimit(ClientConfig.get(ClientConfig.EFFECT_BUDGET));
        int period=Math.max(distance>64*64?2:1,Math.max(1,(active*3+cap-1)/cap))*(1+EffectPerformance.pressure());
        if(Math.floorMod(now+bullet.getId(),period)!=0)return true;
        var p=profile(bullet,size);Vec3 travel=at.subtract(previous);double length=travel.length();
        int count=ProjectileTrailLook.samples(length,requested,EffectPerformance.pressure(),distance>64*64);
        Vec3 direction=length>1E-6?travel.scale(1/length):Vec3.ZERO;
        for(int i=0;i<count&&births<cap;i++){
            Vec3 source=previous.lerp(at,(i+.5)/count).subtract(direction.scale(.3));
            float radius=ProjectileTrailLook.radius(p,length,count)*(.94F+.06F*(float)Math.sin(now*.47+bullet.getId()+i));
            if(!EffectAdmission.admit(level,source.x,source.y,source.z,false,radius*1.1F))continue;
            boolean wisp=Math.floorMod(now+bullet.getId()+i,5)==0;
            mc.particleEngine.add(new ProjectileSmokeParticle(level,source,travel,p,radius,weight,wisp));births++;
        }
        return true;
    }
    public static int births(){return births;}
    public static int tracks(){return TRACKS.size();}
}

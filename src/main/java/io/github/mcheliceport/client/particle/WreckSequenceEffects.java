package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Finite staged effects, on existing shared admission. State is owned by VehicleAtmosphere. */
public final class WreckSequenceEffects {
    public record Strike(io.github.mcheliceport.client.model.McheliModel.Group group,Matrix4f pose,int age){}
    public static final class State {
        public int age=-1,ammo,heavyAmmo,seed,parts,strikes,bursts;long tick=Long.MIN_VALUE;public boolean seenAlive;
        public boolean turret,cover,wing,tail,track;
        public Vec3 opening,ring;
        public final Set<String> scattered=new HashSet<>(),struck=new HashSet<>();
        public final Map<String,Strike> fragments=new HashMap<>();
        public final Map<String,Float> rotorSpeeds=new HashMap<>();
        public final Map<String,Matrix4f> rotorStarts=new HashMap<>();
        public final Map<String,Vec3> tips=new HashMap<>();
    }
    private static final Map<AircraftEntity,State> STATES=new LinkedHashMap<>();
    private static long budgetTick=Long.MIN_VALUE;private static int rays,bursts,strikes;
    private static java.lang.ref.WeakReference<ClientLevel> world=new java.lang.ref.WeakReference<>(null);
    private WreckSequenceEffects(){}
    public static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS)&&ClientConfig.get(ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES);}
    public static void clear(){STATES.clear();world.clear();budgetTick=Long.MIN_VALUE;rays=bursts=strikes=0;}
    private static void budget(ClientLevel level){if(world.get()!=level){clear();world=new java.lang.ref.WeakReference<>(level);}if(budgetTick!=level.getGameTime()){budgetTick=level.getGameTime();rays=bursts=strikes=0;STATES.keySet().removeIf(e->e.isRemoved()||e.level()!=level||Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(e.position())>80*80);}}
    public static State get(AircraftEntity ac){return enabled()?STATES.get(ac):null;}
    public static void tick(ClientLevel level,AircraftEntity ac,int age){
        budget(level);if(!enabled()){STATES.clear();return;}
        if(STATES.size()>=128&&!STATES.containsKey(ac))return;
        var s=STATES.computeIfAbsent(ac,k->new State());if(s.tick==level.getGameTime())return;s.tick=level.getGameTime();s.seed=ac.getUUID().hashCode();
        if(age<0){if(s.age>=0){s.scattered.clear();s.struck.clear();s.fragments.clear();s.rotorStarts.clear();s.rotorSpeeds.clear();s.tips.clear();s.opening=s.ring=null;s.turret=s.cover=s.wing=s.tail=s.track=false;s.parts=s.strikes=s.bursts=0;}
            s.age=-1;s.seenAlive=true;if(level.getGameTime()%20==0||s.ammo==0){s.ammo=CookoffEffects.conventionalAmmo(ac);s.heavyAmmo=CookoffEffects.explosiveAmmo(ac);}return;}
        if(s.age<0&&!s.seenAlive){s.ammo=CookoffEffects.conventionalAmmo(ac);s.heavyAmmo=CookoffEffects.explosiveAmmo(ac);}
        s.age=age;if(!s.seenAlive)return; // Old wrecks keep their static silhouette, without fresh bursts.
        if(age==0){s.ammo=CookoffEffects.conventionalAmmo(ac);s.heavyAmmo=CookoffEffects.explosiveAmmo(ac);}
        if(Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(ac.position())>40*40)return;
        Vec3 at=s.opening==null?VehicleAtmosphere.wreckOrigins(ac).getFirst():s.opening;
        int pressure=1+EffectPerformance.pressure();
        if(s.cover&&age>=36&&age<76&&age%(4*pressure)==0){flame(level,at,.5F,.85F);smoke(level,at,.18F,.8F,28);}
        if(s.turret&&s.ring!=null&&age>=96&&age<240&&age%(6*pressure)==0)flame(level,s.ring,.55F,.75F);
        if((DestructionTimeline.secondary(age,s.seed)||s.turret&&age==98)&&s.ammo>0&&bursts<2){
            if(burst(level,s.turret&&s.ring!=null?s.ring:at,s.seed)){bursts++;s.bursts++;}
        }
        float ember=DestructionTimeline.ember(age);
        if(ember>0&&age%(17*pressure)==0){flame(level,at.add(.12*Math.sin(age*.2),-.08,0),.10F,.5F*ember);}
        if(ember>0&&age%(43*pressure)==0)smoke(level,at,.5F,.22F*ember,35);
    }
    private static void flame(ClientLevel level,Vec3 at,float radius,float alpha){if(VehicleAtmosphere.admit(level,at,radius))Minecraft.getInstance().particleEngine.add(new WreckFlameParticle(level,at.x,at.y,at.z,radius,alpha));}
    private static void smoke(ClientLevel level,Vec3 at,float gray,float alpha,int life){
        if(!VehicleAtmosphere.admit(level,at,.6F))return;var p=new SmokeParticle(level,at.x,at.y,at.z,0,.025,0);
        p.enhanced=true;p.coherentDrift=true;p.disableCollision();p.setColor(gray,gray,gray);p.setAlphaF(alpha);p.setParticleMaxAge(life);p.diffusible=true;p.particleScale=1.5F;p.particleMaxScale=5;Minecraft.getInstance().particleEngine.add(p);
    }
    private static boolean burst(ClientLevel level,Vec3 at,int seed){
        if(!VehicleAtmosphere.admit(level,at,.7F))return false;var engine=Minecraft.getInstance().particleEngine;
        engine.add(new ImpactCoreParticle(level,at.x,at.y,at.z,.65F,3));
        for(int i=0;i<3;i++)if(VehicleAtmosphere.admit(level,at,.05F))engine.add(new SparkParticle(level,at.x,at.y,at.z,Math.sin(seed+i)*.12,.12,Math.cos(seed+i)*.12,16,.04F));
        if(VehicleAtmosphere.admit(level,at,.035F))engine.add(new SurfaceDebrisParticle(level,at.x,at.y,at.z,.06,.15,-.04,.04F,.35F,.32F,.28F,Double.NEGATIVE_INFINITY,20,.2F));
        sound(at,SoundEvents.FIREWORK_ROCKET_BLAST,.5F,1.3F);return true;
    }
    public static boolean rotorRay(ClientLevel level){budget(level);if(rays>=2)return false;rays++;return true;}
    public static boolean rotorStrike(ClientLevel level,AircraftEntity ac,Vec3 at){
        budget(level);var s=get(ac);if(s==null||strikes>=2||s.strikes>=2||!VehicleAtmosphere.admit(level,at,.08F))return false;
        strikes++;s.strikes++;var engine=Minecraft.getInstance().particleEngine;
        engine.add(new SparkParticle(level,at.x,at.y,at.z,.08,.15,.04,16,.045F));
        for(int i=0;i<2;i++)if(VehicleAtmosphere.admit(level,at,.04F))engine.add(new SparkParticle(level,at.x,at.y+.02,at.z,(i-.5)*.14,.17+i*.03,-.04,18,.035F));
        for(int i=0;i<3;i++)if(VehicleAtmosphere.admit(level,at,.04F))engine.add(new SurfaceDebrisParticle(level,at.x,at.y,at.z,(i-1)*.08,.12,.04,.04F,.3F,.3F,.28F,at.y-.3,20,.25F));
        sound(at,SoundEvents.ANVIL_HIT,.3F,1.5F);return true;
    }
    private static void sound(Vec3 at,SoundEvent event,float volume,float pitch){Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(event,SoundSource.BLOCKS,volume,pitch,RandomSource.create(),at.x,at.y,at.z));}
    public static int rays(){return rays;}
}

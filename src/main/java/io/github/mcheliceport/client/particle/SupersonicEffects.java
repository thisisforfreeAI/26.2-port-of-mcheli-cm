package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.damage.AirframeDamage;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Called by the existing engine-audio scan: at most 64 observers and 16 pending shock arrivals. */
public final class SupersonicEffects {
    private static final Identifier BOOM=Identifier.fromNamespaceAndPath("mcheli_ce_port","m8_sonic_boom");
    private static final Map<AircraftEntity,State> STATES=new LinkedHashMap<>();
    private static final ArrayDeque<Arrival> PENDING=new ArrayDeque<>();
    private static final ArrayDeque<Playing> PLAYING=new ArrayDeque<>();
    private static ClientLevel world;private static long tick,nextVoice;
    private static Vec3 listener=Vec3.ZERO;private static boolean observerWarp;
    private static int cones,played,requests,coneChecks;
    private static final class State {final SupersonicLook look=new SupersonicLook();Vec3 position,motion;long at;}
    private record Arrival(long at,Vec3 position,double distance){}
    private record Playing(long until,SoundInstance sound){}
    private SupersonicEffects(){}
    private static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.SUPERSONIC_EFFECTS);}
    public static int stateCount(){return STATES.size();}public static int pending(){return PENDING.size();}
    public static int conesThisTick(){return cones;}public static int boomsPlayed(){return played;}
    public static int voicesPlaying(){return PLAYING.size();}
    public static void clear(){
        var sounds=Minecraft.getInstance().getSoundManager();for(var p:PLAYING)sounds.stop(p.sound);
        STATES.clear();PENDING.clear();PLAYING.clear();world=null;tick=0;listener=Vec3.ZERO;nextVoice=0;cones=played=requests=coneChecks=0;
    }
    public static void begin(Minecraft mc){
        if(mc.level!=world||!enabled()||mc.player==null){clear();world=mc.level;}
        if(world==null||!enabled()||mc.isPaused())return;
        // This hook runs once per unpaused client tick. Server clock corrections are not missed observations.
        long now=++tick;cones=requests=coneChecks=0;
        var at=mc.gameRenderer.mainCamera().position();observerWarp=listener.distanceToSqr(at)>16*16;listener=at;
        STATES.keySet().removeIf(ac->ac.isRemoved()||ac.level()!=world||listener.distanceToSqr(ac.position())>512*512);
        while(!PLAYING.isEmpty()&&PLAYING.getFirst().until<=now)PLAYING.removeFirst();
        if(!PENDING.isEmpty()&&now>=nextVoice){
            var candidate=PENDING.stream().filter(a->a.at<=now).findFirst().orElse(null);
            if(candidate!=null){
                PENDING.remove(candidate);
                if(PLAYING.size()<4){
                    var p=candidate.position;var sound=new SimpleSoundInstance(BOOM,SoundSource.NEUTRAL,SupersonicLook.volume(candidate.distance),1,
                            RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,p.x,p.y,p.z,false);
                    mc.getSoundManager().play(sound);PLAYING.addLast(new Playing(now+40,sound));played++;nextVoice=now+5;
                }
            }
        }
    }
    public static void observe(AircraftEntity aircraft){
        var mc=Minecraft.getInstance();if(world==null||!enabled()||mc.player==null||mc.isPaused()||!(aircraft instanceof PlaneEntity ac))return;
        var info=ac.getAcInfo();double threshold=info==null?0:info.machOneSpeed*CommonConfig.ALL_PLANE_SPEED.get();
        if(!Double.isFinite(threshold)||threshold<=0||ac.isDestroyed()||ac.onGround()||ac.isInWater()||listener.distanceToSqr(ac.position())>512*512){STATES.remove(ac);return;}
        if(requests++>=64){STATES.remove(ac);return;}
        var state=STATES.get(ac);if(state==null){if(STATES.size()>=64)return;state=new State();STATES.put(ac,state);}
        Vec3 at=ac.position(),step=state.position==null?Vec3.ZERO:at.subtract(state.position);
        Vec3 motion=AirframeDamage.fragmentMotion(ac.getDeltaMovement(),step);
        boolean stable=(state.position==null||SupersonicLook.continuous(tick-state.at,step.length(),threshold))
                &&(state.motion==null||motion.lengthSqr()<1E-6||state.motion.lengthSqr()<1E-6||motion.normalize().dot(state.motion.normalize())>.95);
        Vec3 relative=listener.subtract(at);double distance=relative.length();
        var event=state.look.tick(tick,motion.length(),threshold,relative.dot(motion.normalize()),distance,stable,true,!observerWarp);
        state.position=at;state.motion=motion;state.at=tick;
        if(event.cone())cone(mc,ac);
        if(event.boom()&&ac.getSeatIdByEntity(mc.player)<0&&PENDING.size()<16)PENDING.addLast(new Arrival(tick+SupersonicLook.delay(distance),at,distance));
    }
    private static void cone(Minecraft mc,PlaneEntity ac){
        if(!ClientConfig.get(ClientConfig.AIRCRAFT_CONDENSATION)||ClientConfig.get(ClientConfig.EFFECT_BUDGET)==ClientConfig.EffectBudget.LOW
                ||EffectPerformance.pressure()>0||cones>=2||AirframeDamage.missing(ac)!=0||listener.distanceToSqr(ac.position())>96*96)return;
        if(coneChecks++>=4)return;
        float wetness=world.getRainLevel(1);if(wetness<.2F||!world.isRainingAt(net.minecraft.core.BlockPos.containing(ac.position()).above(3)))return;
        var model=AircraftModels.peek(ac.getAcInfo());if(model==null)return;var body=model.body();
        float radius=Math.clamp((body.maxX-body.minX)*.6F,2.5F,6);
        if(!EffectAdmission.admitGeometry(world,ac.getX(),ac.getY(),ac.getZ(),radius*1.5F,8))return;
        var local=new Vec3(0,(body.minY+body.maxY)*.5,(body.minZ+body.maxZ)*.5);
        mc.particleEngine.add(new SupersonicConeParticle(world,ac,local,radius,wetness));cones++;
    }
}

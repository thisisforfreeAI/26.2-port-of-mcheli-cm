package io.github.mcheliceport.client.sound;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.weapon.*;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Short, positional near misses, admitted by the existing engine scan. No projectile changes. */
public final class ProjectileFlybySounds {
    private static final Map<BaseBulletEntity,Track> TRACKS=new LinkedHashMap<>();
    private static final List<Cue> PLAYING=new ArrayList<>();
    private static ClientLevel world;
    private static long tick,nextVoice;
    private static Vec3 ear;
    private static boolean observerWarp;
    private static int requests,played;
    private static final class Track {Vec3 at;long seen;boolean sounded;Track(Vec3 at,long seen){this.at=at;this.seen=seen;}}
    private ProjectileFlybySounds(){}
    public static int tracks(){return TRACKS.size();}
    public static int voices(){return PLAYING.size();}
    public static int played(){return played;}
    public static void clear(){
        var sounds=Minecraft.getInstance().getSoundManager();for(var cue:PLAYING){cue.end();sounds.stop(cue);}
        TRACKS.clear();PLAYING.clear();world=null;ear=null;tick=nextVoice=0;requests=played=0;
    }
    public static void begin(Minecraft mc){
        if(world!=mc.level||mc.player==null||!AircraftEngineSound.polished()){clear();world=mc.level;}
        if(world==null||mc.isPaused()||!AircraftEngineSound.polished())return;
        ++tick;requests=0;
        var at=mc.gameRenderer.mainCamera().position();observerWarp=ear==null||ear.distanceToSqr(at)>16*16;ear=at;
        TRACKS.entrySet().removeIf(e->e.getKey().isRemoved()||e.getKey().level()!=world||tick-e.getValue().seen>4||ear.distanceToSqr(e.getKey().position())>192*192);
        PLAYING.removeIf(cue->cue.isStopped());
    }
    public static void observe(BaseBulletEntity shot){
        var mc=Minecraft.getInstance();
        if(world==null||ear==null||mc.player==null||mc.isPaused()||!AircraftEngineSound.polished()||requests++>=256)return;
        var kind=kind(shot);
        if(kind==ProjectileFlybyLook.Kind.NONE||shot.isRemoved()||shot.isInWater()||shot.shootingEntity==mc.player
                ||shot.shootingAircraft instanceof AircraftEntity ac&&ac.getSeatIdByEntity(mc.player)>=0
                ||ear.distanceToSqr(shot.position())>192*192){TRACKS.remove(shot);return;}
        var state=TRACKS.get(shot);var at=shot.position();
        if(state==null){if(TRACKS.size()<128)TRACKS.put(shot,new Track(at,tick));return;}
        var motion=at.subtract(state.at);boolean stable=tick-state.seen==1&&motion.lengthSqr()<=32*32;
        state.at=at;state.seen=tick;
        if(observerWarp||!stable||state.sounded||PLAYING.size()>=4||tick<nextVoice||!ProjectileFlybyLook.incoming(at,motion,ear,kind))return;
        var cue=new Cue(shot,kind,ear);mc.getSoundManager().play(cue);PLAYING.add(cue);
        state.sounded=true;played++;nextVoice=tick+5;
    }
    private static ProjectileFlybyLook.Kind kind(BaseBulletEntity shot){
        var info=shot.getInfo();
        if(info==null||!(shot instanceof RocketEntity||shot instanceof AAMissileEntity||shot instanceof ATMissileEntity
                ||shot instanceof ASMissileEntity||shot instanceof TvMissileEntity||shot instanceof BulletEntity))return ProjectileFlybyLook.Kind.NONE;
        return ProjectileFlybyLook.kind(info.type,info.useHBM||info.payloadNTM!=WeaponInfo.Payload.NONE,shot instanceof BulletEntity,info.power,info.explosion);
    }
    private static final class Cue extends AbstractTickableSoundInstance {
        private final WeakReference<BaseBulletEntity> source;
        private final FlightAudioLook motion=new FlightAudioLook();
        private final float gain;
        private int age;
        Cue(BaseBulletEntity shot,ProjectileFlybyLook.Kind kind,Vec3 ear){
            super(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("mcheli_ce_port",kind==ProjectileFlybyLook.Kind.ROCKET?"m8_rocket_flyby":"m8_shell_flyby")),SoundSource.NEUTRAL,RandomSource.create());
            source=new WeakReference<>(shot);gain=kind==ProjectileFlybyLook.Kind.ROCKET?.65F:.5F;
            x=shot.getX();y=shot.getY();z=shot.getZ();volume=gain*.35F;pitch=1;
            motion.tick(shot.position(),ear,false,true);
        }
        void end(){stop();}
        @Override public void tick(){
            var mc=Minecraft.getInstance();var shot=source.get();
            if(shot==null||shot.isRemoved()||shot.level()!=mc.level||!AircraftEngineSound.polished()||++age>=18){stop();return;}
            var ear=mc.gameRenderer.mainCamera().position();var at=shot.position();
            x=at.x;y=at.y;z=at.z;pitch=motion.tick(at,ear,false,true);
            volume=gain*Math.min(1,age/2F)*Math.min(1,(18-age)/5F);
        }
    }
}

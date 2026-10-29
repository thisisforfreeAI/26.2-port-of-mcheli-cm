package io.github.mcheliceport.client.sound;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.weapon.*;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Production audio on controlled passes; visual captures are unedited native client images. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class FlybyProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeFlyby");
    private static final int START=Integer.getInteger("mcheli.probeFlybyStart",0),END=Integer.getInteger("mcheli.probeFlybyEnd",14);
    private static final String[] NAMES={"f-35a","f-15e","ah-64","vertical-pass","own-pilot","disabled","hydra70","cannon","agm158-smokeless","ntm-bypass","projectile-load","engine-load-low","engine-load-balanced","classic"};
    private static final List<AircraftEntity> actors=new ArrayList<>();
    private static final List<BaseBulletEntity> shots=new ArrayList<>();
    private static int ticks,phase=-1,age,voices,nearMisses;private static float approach,recede;private static String capture;
    private static WeaponInfo definition;
    private static Vec3 ear(){return phase>=6&&phase<=10?new Vec3(0,100,0):new Vec3(15,108,-10);}
    private static void fly(AircraftEntity ac){
        int i=actors.indexOf(ac);ac.xo=ac.getX();ac.yo=ac.getY();ac.zo=ac.getZ();ac.yRotO=0;ac.xRotO=0;ac.prevRoll=0;
        double z=-60+age*1.5;
        Vec3 at=phase==11||phase==12?new Vec3((i%8-3.5)*3,100,i/8*3):phase==3?new Vec3(15,108+z,-5):new Vec3(0,100,z);
        ac.setPos(at.x,at.y,at.z);ac.setDeltaMovement(at.subtract(ac.xo,ac.yo,ac.zo));ac.setYaw(0);ac.setPitch(0);ac.setRoll(0);ac.setOnGround(false);
    }
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);soundVolume=1;}
        @Override public void tick(){fly(this);}@Override public double getCurrentThrottle(){return .9;}
        @Override public int getFuel(){return getMaxFuel();}@Override public int getHP(){return getMaxHP();}@Override public boolean isDestroyed(){return false;}
    }
    private static final class Heli extends HeliEntity {
        Heli(Minecraft mc){super(ModEntityTypes.HELICOPTER.get(),mc.level);}
        @Override public void tick(){fly(this);}@Override public double getCurrentThrottle(){return .9;}
        @Override public int getFuel(){return getMaxFuel();}@Override public int getHP(){return getMaxHP();}@Override public boolean isDestroyed(){return false;}
    }
    private static void shot(BaseBulletEntity shot){
        int i=shots.indexOf(shot);shot.xo=shot.getX();shot.yo=shot.getY();shot.zo=shot.getZ();shot.tickCount++;
        var at=new Vec3(phase==10?(i%4)*.5+2:2,100,80-Math.max(0,age-40)*4);
        shot.setPos(at.x,at.y,at.z);shot.setDeltaMovement(at.subtract(shot.xo,shot.yo,shot.zo));
        if(age>90)shot.discard();
    }
    private static final class Rocket extends RocketEntity {
        Rocket(Minecraft mc){super(ModEntityTypes.ROCKET.get(),mc.level);}
        @Override public void tick(){shot(this);}@Override public WeaponInfo getInfo(){return definition;}
    }
    private static final class Shell extends BulletEntity {
        Shell(Minecraft mc){super(ModEntityTypes.BULLET.get(),mc.level);}
        @Override public void tick(){shot(this);}@Override public WeaponInfo getInfo(){return definition;}
    }
    private static final class Missile extends TvMissileEntity {
        Missile(Minecraft mc){super(ModEntityTypes.TV_MISSILE.get(),mc.level);}
        @Override public void tick(){shot(this);}@Override public WeaponInfo getInfo(){return definition;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.fov().set(60);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.ADAPTIVE_EFFECTS.set(false);ClientConfig.SUPERSONIC_EFFECTS.set(false);
        }
        if(ticks<160)return;int next=START+(ticks-160)/150;age=(ticks-160)%150;
        if(next>=Math.min(END,NAMES.length)){System.out.println("M8_FLYBY all_passed=true start="+START+" end="+END);mc.stop();return;}
        if(next!=phase){
            mc.player.stopRiding();actors.forEach(AircraftEntity::discard);actors.clear();shots.forEach(BaseBulletEntity::discard);shots.clear();AircraftEngineSound.clear();mc.particleEngine.clearParticles();
            phase=next;voices=nearMisses=0;approach=recede=1;
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(phase==13?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);
            ClientConfig.FLYBY_AUDIO.set(phase!=5);ClientConfig.EFFECT_BUDGET.set(phase==11?ClientConfig.EffectBudget.LOW:ClientConfig.EffectBudget.BALANCED);
            if(phase<6||phase>=11){
                String model=phase==1?"f-15e":phase==2?"ah-64":"f-35a";
                for(int j=0;j<(phase==11||phase==12?64:1);j++){
                    AircraftEntity ac=phase==2?new Heli(mc):new Plane(mc);ac.setId(981000+phase*100+j);ac.setAircraftTypeName(model);ac.setTextureName(model);ac.setPos(0,100,-60);
                    var parts=ac.getParts();for(int k=0;k<parts.length;k++)parts[k].setId(1100000+phase*10000+j*100+k);actors.add(ac);mc.level.addEntity(ac);
                }
                if(phase==4)mc.player.startRiding(actors.getFirst(),true,true);
            }else{
                definition=ContentRegistries.weapon().get(phase==7?"gau-8":phase==8?"agm158":"hydra70");
                if(definition==null)throw new IllegalStateException("Missing real weapon");
                if(phase==9){var base=definition;definition=new WeaponInfo(base.location,base.filePath,"probe");definition.type=base.type;definition.useHBM=true;}
                for(int j=0;j<(phase==10?128:1);j++){BaseBulletEntity shot=phase==7?new Shell(mc):phase==8?new Missile(mc):new Rocket(mc);shot.setId(1040000+phase*1000+j);shot.setPos(2,100,80);shots.add(shot);mc.level.addEntity(shot);}
            }
            for(String sound:new String[]{"m8_rocket_flyby","m8_shell_flyby"})if(mc.getSoundManager().getSoundEvent(Identifier.fromNamespaceAndPath("mcheli_ce_port",sound))==null)throw new IllegalStateException("Missing flyby cue "+sound);
            System.out.println("M8_FLYBY scene="+NAMES[phase]+" actors="+actors.size()+" shots="+shots.size());
        }
        if(age%30==0){var at=ear();mc.player.connection.sendCommand("tp @s "+at.x+" "+at.y+" "+at.z+" 30 18");}
        voices=Math.max(voices,AircraftEngineSound.voices());nearMisses=Math.max(nearMisses,ProjectileFlybySounds.voices());
        int cap=phase==11?12:24;
        if(AircraftEngineSound.voices()>cap||ProjectileFlybySounds.voices()>4||ProjectileFlybySounds.tracks()>128)throw new IllegalStateException("Audio voice bound exceeded");
        if(!actors.isEmpty()){
            var status=AircraftEngineSound.status(actors.getFirst());
            if(age==25)approach=status.doppler();if(age==80)recede=status.doppler();
            if(phase==4&&Math.abs(status.doppler()-1)>1E-5)throw new IllegalStateException("Own cockpit received Doppler");
            if(age==40&&phase==3){mc.player.connection.sendCommand("tp @s 100 100 100");}
        }
        if(phase==9&&age==75){definition.useHBM=false;definition.payloadNTM=WeaponInfo.Payload.NTM_NUKE;}
        if(age==50&&phase<3)capture=NAMES[phase];
        if(age==120){
            if((phase<4)&&!(approach>1&&recede<1))throw new IllegalStateException("Missing 3D pass pitch "+NAMES[phase]+" "+approach+"/"+recede);
            if((phase==6||phase==7||phase==8||phase==10)&&ProjectileFlybySounds.played()==0)throw new IllegalStateException("No incoming cue "+NAMES[phase]);
            if((phase==5||phase==9||phase==13)&&ProjectileFlybySounds.played()!=0)throw new IllegalStateException("Excluded incoming cue");
            if(phase==11&&voices!=12||phase==12&&voices!=24)throw new IllegalStateException("Engine cap not exercised "+voices);
            System.out.println("M8_FLYBY passed=true scene="+NAMES[phase]+" approach="+approach+" recede="+recede+" peak_engine_voices="+voices+" peak_incoming_voices="+nearMisses+" cues="+ProjectileFlybySounds.played());
        }
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&phase>=0)e.getCamera().setPosition(ear());}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(ENABLED&&phase>=0){e.setYaw(30);e.setPitch(18);e.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(capture!=null){var mc=Minecraft.getInstance();String n=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-flyby-"+n+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_FLYBY "+m.getString()));}}
}

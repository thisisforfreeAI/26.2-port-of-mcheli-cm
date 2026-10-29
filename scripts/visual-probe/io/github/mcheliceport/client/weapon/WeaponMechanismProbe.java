package io.github.mcheliceport.client.weapon;

import io.github.mcheliceport.client.render.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.weapon.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.weapon.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Real models with replicated client shot-bit replays. No server projectiles, damage or ammo use. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class WeaponMechanismProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeMechanisms");
    private static final int START=Integer.getInteger("mcheli.probeMechanismStart",0),END=Integer.getInteger("mcheli.probeMechanismEnd",10);
    private static final String[] NAMES={"a-10-barrel","ah-64-cases","m1a2-recoil","f-35a-bay","disabled","classic","case-load","support-removed","ntm-bypass","ntm-bay"};
    private static int ticks,phase=-1,age,peakCases,peakRest,peakRays,peakBatches;private static float peakRecoil;private static boolean rotated,woke;private static float lastBarrel;private static AircraftEntity actor;private static WeaponSet set;private static int gunId,bit;private static String capture;
    @SuppressWarnings("unchecked") private static EntityDataAccessor<Integer> shotKey(){try{var field=AircraftEntity.class.getDeclaredField("DATA_USE_WEAPON");field.setAccessible(true);return (EntityDataAccessor<Integer>)field.get(null);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static final EntityDataAccessor<Integer> SHOT=ENABLED?shotKey():null;
    private static final class Plane extends PlaneEntity {Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}@Override public void tick(){hold(this);}@Override public int getHP(){return getMaxHP();}@Override public boolean isDestroyed(){return false;}}
    private static final class Heli extends HeliEntity {Heli(Minecraft mc){super(ModEntityTypes.HELICOPTER.get(),mc.level);}@Override public void tick(){hold(this);}@Override public int getHP(){return getMaxHP();}@Override public boolean isDestroyed(){return false;}}
    private static final class Tank extends TankEntity {Tank(Minecraft mc){super(ModEntityTypes.TANK.get(),mc.level);}@Override public void tick(){hold(this);}@Override public int getHP(){return getMaxHP();}@Override public boolean isDestroyed(){return false;}}
    private static void hold(AircraftEntity ac){
        ac.xo=ac.getX();ac.yo=ac.getY();ac.zo=ac.getZ();ac.yRotO=0;ac.xRotO=0;ac.prevRoll=0;ac.tickCount++;ac.setYaw(0);ac.setPitch(0);ac.setRoll(0);ac.setDeltaMovement(Vec3.ZERO);ac.setOnGround(phase!=3&&phase!=9);
        if(set==null)return;int period=Math.max(1,Math.abs(set.getInfo().delay));boolean fire=phase==0||phase==1||phase==4||phase==5||phase==7;
        int mask=fire&&age>=40&&age<80&&age%period==0?bit:phase==2&&age==50?bit:(phase==3||phase==9)&&age>=57?bit:0;
        ac.getEntityData().set(SHOT,mask);ac.weapons().tick(ac.getEntityData(),SHOT);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode creative","time set noon","weather clear","gamerule minecraft:advance_time false","fill -24 -60 0 24 -48 42 air","fill -24 -61 0 24 -61 42 stone"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.fov().set(60);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.ADAPTIVE_EFFECTS.set(false);ClientConfig.LANDING_FEEDBACK.set(false);ClientConfig.NIGHT_FLIGHT_LIGHTING.set(false);ClientConfig.ALIVE_TIME_OF_CARTRIDGE.set(200);
        }
        if(ticks<160)return;int next=START+(ticks-160)/200;age=(ticks-160)%200;
        if(next>=Math.min(END,NAMES.length)){mc.player.stopRiding();System.out.println("M8_MECHANISM all_passed=true start="+START+" end="+END);mc.stop();return;}
        if(next!=phase){
            mc.player.stopRiding();if(actor!=null)actor.discard();WeaponEffects.clear();WeaponMechanisms.clear();mc.particleEngine.clearParticles();phase=next;set=null;peakCases=peakRest=peakRays=peakBatches=0;peakRecoil=0;rotated=woke=false;lastBarrel=0;
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(phase==5?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);ClientConfig.WEAPON_MECHANISMS.set(phase!=4);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);
            mc.options.setCameraType((phase==3||phase==9)?CameraType.THIRD_PERSON_BACK:CameraType.FIRST_PERSON);mc.player.connection.sendCommand("fill -24 -61 0 24 -61 42 stone");
            actor=phase==0||(phase==3||phase==9)?new Plane(mc):phase==2?new Tank(mc):new Heli(mc);String model=phase==0?"a-10":phase==2?"m1a2":(phase==3||phase==9)?"f-35a":"ah-64";
            actor.setId(1180000+phase);actor.setAircraftTypeName(model);actor.setTextureName(model);actor.setPos(0,(phase==3||phase==9)?-56:-60,20);
            var parts=actor.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(1650000+phase*100+i);mc.level.addEntity(actor);
            String weapon=phase==0?"gau-8":phase==2?"rehinmetall_apfsds":(phase==3||phase==9)?phase==9?"ntm_b61_tactical":"gbu-39_b_sdb":"m230";set=actor.weapons().getWeaponByName(weapon);if(set==null)throw new IllegalStateException("Missing mechanism weapon "+weapon);
            int offset=0;for(gunId=0;gunId<actor.weapons().getWeaponNum();gunId++){if(actor.weapons().getWeapon(gunId)==set)break;offset+=actor.weapons().getWeapon(gunId).getWeaponsCount();}bit=1<<offset;
            if((phase==3||phase==9)){actor.weapons().updateWeaponID(0,-1);if(!mc.player.startRiding(actor,true,true))throw new IllegalStateException("Bay pilot mount failed");}
            System.out.println("M8_MECHANISM scene="+NAMES[phase]+" weapon="+weapon+" authored_case="+(set.getInfo().cartridge!=null));
        }
        if((phase==3||phase==9)){
            if(mc.player.getVehicle()!=actor)mc.player.startRiding(actor,true,true);
            if(age==40){actor.weapons().updateWeaponID(0,gunId);set.onSwitchWeapon(false,true);}
            if(age==56){float[] bays=WeaponPartPoses.weaponBays(actor,1);int checked=0;
                for(int i=0;i<bays.length;i++)if(Arrays.asList(actor.getAcInfo().partWeaponBay.get(i).weaponIds).contains(gunId)){
                    checked++;float expected=phase==9?actor.weapons().weaponBays[i].rot:90;
                    if(Math.abs(bays[i]-expected)>.1F)throw new IllegalStateException("Selected bay "+i+" wrong angle "+bays[i]+" expected "+expected);
                }
                if(checked==0||!set.canFire())throw new IllegalStateException("No selected bay inside existing switch wait");
                System.out.println("M8_MECHANISM selected_bays="+checked+" legacy_ntm="+(phase==9));}
        }else if(age%30==0){var p=actor.position();mc.player.connection.sendCommand("tp @s "+(p.x-7)+" "+(p.y+3)+" "+(p.z+7)+" 225 12");}
        if(phase==6&&age>=40&&age<72){var info=ContentRegistries.weapon().get("m230");var p=actor.position().add(0,1,1);for(int i=0;i<40;i++)WeaponClientHooks.spawnCartridge(mc.level,info.cartridge,p.x,p.y,p.z,0,.04,0,0,0,info);}
        if(phase==8&&age>=40&&age<72){var base=set.getInfo();var info=new WeaponInfo(base.location,"ntm_case_probe.yml","probe");info.type="machinegun2";info.useHBM=true;WeaponClientHooks.weaponShot(mc.level,actor.position().add(0,1,1),Vec3.ZERO,info,0,0);}
        if(phase==7&&age==120){if(WeaponEffects.restingCartridges()==0)throw new IllegalStateException("No cases settled before terrain removal");mc.player.connection.sendCommand("fill -6 -61 10 4 -61 23 air");}
        if(phase==7&&age>122&&WeaponEffects.casingRays()>0)woke=true;
        peakCases=Math.max(peakCases,WeaponEffects.cartridges());peakRest=Math.max(peakRest,WeaponEffects.restingCartridges());peakRays=Math.max(peakRays,WeaponEffects.casingRays());peakBatches=Math.max(peakBatches,WeaponEffects.casingBatches());
        if(set.getRotBarrel()!=lastBarrel)rotated=true;lastBarrel=set.getRotBarrel();
        var poses=WeaponPartPoses.weaponParts(actor,1,null,false);for(var pose:poses){peakRecoil=Math.max(peakRecoil,pose.recoil());for(float value:pose.childRecoil())peakRecoil=Math.max(peakRecoil,value);}
        if(WeaponEffects.cartridges()>128||WeaponEffects.casingRays()>128||WeaponMechanisms.spins()>128)throw new IllegalStateException("Mechanism budget exceeded");
        if(age==53||age==68)capture=NAMES[phase]+(age==53?"-action":"-detail");
        if(age==170){
            if(phase==0&&(!rotated||peakCases!=0))throw new IllegalStateException("Closed-feed barrel/casing behavior wrong");
            if((phase==1||phase==6||phase==7)&&(peakCases==0||peakRest==0))throw new IllegalStateException("No eject/bounce/settle sequence");
            if(phase==2&&peakRecoil==0)throw new IllegalStateException("Tank recoil absent");
            if(phase==6&&(peakCases!=128||peakBatches!=1||WeaponEffects.casingRays()!=0))throw new IllegalStateException("Casing batching/settlement load failed "+peakCases+"/"+peakBatches+"/"+WeaponEffects.casingRays());
            if(phase==7&&!woke)throw new IllegalStateException("Cases floated after support removal");
            if(phase==8&&peakCases!=0)throw new IllegalStateException("NTM fallback emitted cases");
            System.out.println("M8_MECHANISM passed=true scene="+NAMES[phase]+" peak_cases="+peakCases+" peak_resting="+peakRest+" peak_rays="+peakRays+" peak_batches="+peakBatches+" peak_recoil="+peakRecoil+" rotated="+rotated+" support_wake="+woke);
        }
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&actor!=null)e.getCamera().setPosition(actor.position().add(phase==0?new Vec3(-3,2.8,8):phase==2?new Vec3(8,4,12):(phase==3||phase==9)?new Vec3(8,-.5,-9):new Vec3(-7,3,7)));}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(ENABLED&&phase>=0){e.setYaw(phase==0?215:phase==2?145:(phase==3||phase==9)?60:225);e.setPitch(phase==0?8:(phase==3||phase==9)?-14:12);e.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-mechanism-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_MECHANISM "+m.getString()));}}
}

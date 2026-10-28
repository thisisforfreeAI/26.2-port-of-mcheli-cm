package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.render.LoosePanels;
import io.github.mcheliceport.client.render.WreckAppearance;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.damage.*;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Controlled component losses through production mesh/atmosphere paths; never shipped. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class WingFuelProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeWingFuel");
    private static final int START=Integer.getInteger("mcheli.probeWingFuelStart",0);
    private static final int END=Integer.getInteger("mcheli.probeWingFuelEnd",10);
    private static final String[] NAMES={"f-35a","f-15e","a-10","burning-f35","loaded-missing","classic","details-off","sequences-off","shared-load","ignition-fixture"};
    private static final List<PlaneEntity> actors=new ArrayList<>();
    private static int ticks,phase=-1,age,peak,fuelPeak,hotPeak;private static String capture;
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){
            int index=actors.indexOf(this);xo=getX();yo=getY();zo=getZ();
            setPos(phase==8?(index%4-1.5)*7:0,-40,20+age*.22+(phase==8?index/4*5:0));setDeltaMovement(0,0,.22);
            if(phase==3){setYRot(30);setXRot(-8);setRoll(18);}
        }
        @Override public boolean isDestroyed(){return false;}
    }
    private static void damage(PlaneEntity plane,int mask) {
        long health=ComponentHealth.FULL;var zones=DamageZones.of(plane.getAcInfo());
        for(int i=0;i<zones.size();i++)if((AirframeDamage.bit(zones.get(i).kind(),zones.get(i).index())&mask)!=0)
            health=ComponentHealth.withLevel(health,i,0);
        plane.setComponentHealth(health);plane.setDamageTaken(mask==0?0:(int)(plane.getMaxHP()*(phase==3||phase==8||phase==9?.85:.15)));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40) {
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false","tp @s 9 -31 24 135 29"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);
        }
        if(ticks<160)return;int next=START+(ticks-160)/260;age=(ticks-160)%260;
        if(next>=Math.min(END,NAMES.length)){System.out.println("M8_WING_FUEL all_passed=true");mc.stop();return;}
        if(next!=phase) {
            for(var ac:actors)ac.discard();actors.clear();VehicleAtmosphere.clear();LoosePanels.clear();WreckAppearance.clear();mc.particleEngine.clearParticles();
            phase=next;peak=fuelPeak=hotPeak=0;ClientConfig.EFFECT_STYLE.set(phase==5?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);
            ClientConfig.VEHICLE_DAMAGE_DETAILS.set(phase!=6);ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(phase!=7);
            String model=phase==1?"f-15e":phase==2?"a-10":"f-35a";
            for(int j=0;j<(phase==8?32:1);j++) {
                var plane=new Plane(mc);plane.setId(975000+phase*100+j);plane.setAircraftTypeName(model);plane.setTextureName(model);actors.add(plane);
                plane.setPos(phase==8?(j%4-1.5)*7:0,-40,20+(phase==8?j/4*5:0));
                var parts=plane.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(985000+phase*10000+j*100+i);
                if(phase==4)damage(plane,1);mc.level.addEntity(plane);
            }
            System.out.println("M8_WING_FUEL scene="+NAMES[phase]+" actors="+actors.size());
        }
        if(age%40==0){Vec3 p=actors.getFirst().position();mc.player.connection.sendCommand("tp @s "+(p.x+9)+" "+(p.y+9)+" "+(p.z+4)+" 135 29");}
        if(age==60&&phase!=4)for(var ac:actors)damage(ac,phase==2||phase==8?3:phase==1?2:1);
        if(age==(phase==3||phase==8?180:120))for(var ac:actors)damage(ac,0);
        // Isolate the conditional art/light transition beside a real engine fire. This is
        // deliberately a labeled manual particle fixture, separate from natural wing streams.
        if(phase==9&&age>=65&&age<=110&&age%9==0) {
            var ac=actors.getFirst();Vec3 engine=VehicleAtmosphere.origins(ac).getFirst();
            Vec3 at=engine.add(0,.1,-1.3),travel=ac.getDeltaMovement(),velocity=travel.scale(.4).add(0,.02,-.03);
            int ignition=AirframeFuelLook.ignitionTick(at.subtract(engine),velocity.subtract(travel));
            if(ignition<0)throw new IllegalStateException("Ignition fixture missed its burner");
            if(VehicleAtmosphere.admit(mc.level,at,.7F))mc.particleEngine.add(new FuelSprayParticle(mc.level,at,velocity,1,ignition));
        }
        int total=Integer.parseInt(mc.particleEngine.countParticles().replaceAll(".*T ",""));int[] fuel=count(mc);
        peak=Math.max(peak,total);fuelPeak=Math.max(fuelPeak,fuel[0]);hotPeak=Math.max(hotPeak,fuel[1]);
        if(total>EffectTuning.smokeLimit(ClientConfig.EffectBudget.BALANCED)+EffectTuning.flashLimit(ClientConfig.EffectBudget.BALANCED)+32
                ||VehicleAtmosphere.emitted()>EngineExhaustLook.cap(ClientConfig.EffectBudget.BALANCED))throw new IllegalStateException("Wing fuel shared budget exceeded");
        if(age==85||age==105||age==225)capture=NAMES[phase]+(age==85?"-early":age==105?"-spray":"-repaired");
        if(age==100&&(phase<4||phase==8||phase==9)&&fuel[0]==0)throw new IllegalStateException("Wing fuel missing: "+NAMES[phase]);
        if(age==100&&phase==9&&fuel[1]==0)throw new IllegalStateException("Fuel ignition transition missing");
        if(phase>=4&&phase<=7&&fuel[0]!=0)throw new IllegalStateException("Wing fuel replayed or ignored setting: "+NAMES[phase]);
        if(age==175&&phase<3&&fuel[0]!=0)throw new IllegalStateException("Repair retained wing fuel");
        if(age==179&&phase==3&&fuel[0]!=0)throw new IllegalStateException("Fuel window did not expire");
        if(age==250){if(fuel[0]!=0)throw new IllegalStateException("Wing fuel did not drain");System.out.println("M8_WING_FUEL drained=true scene="+NAMES[phase]+" peak="+peak+" fuel_peak="+fuelPeak+" hot_peak="+hotPeak);}
    }
    private static int[] count(Minecraft mc) {
        try {
            var field=net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");field.setAccessible(true);
            var particles=net.minecraft.client.particle.ParticleGroup.class.getDeclaredField("particles");particles.setAccessible(true);int count=0,hot=0;
            for(var group:((java.util.Map<?,?>)field.get(mc.particleEngine)).values())for(var p:(java.util.Collection<?>)particles.get(group)) {
                if(p instanceof FuelSprayParticle f){count++;if(f.burning())hot++;}else if(p instanceof FuelSprayEffects.Vapor)count++;
            }
            return new int[]{count,hot};
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static boolean leftView(){return phase!=1&&phase!=2&&phase!=8;}
    private static Vec3 closeOrigin(){
        if(phase>=3||age<100||age>=120||actors.isEmpty())return null;
        var ac=actors.getFirst();var state=WreckSequenceEffects.get(ac);
        if(state==null||state.airframe==null)return null;Vec3 local=state.airframe.openings().get(phase==0?1:2);
        return local==null?null:ac.position().add(io.github.mcheliceport.util.McheliMath.rotVec3(local,-ac.getYaw(),-ac.getPitch(),-ac.getRoll()))
                .add(0,io.github.mcheliceport.content.aircraft.AircraftInfo.GLOBAL_Y_OFFSET,0);
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov event){if(ENABLED&&!actors.isEmpty()){
        Vec3 close=closeOrigin();event.getCamera().setPosition(close==null?actors.getFirst().position().add(phase==2?new Vec3(12,12,7):new Vec3(leftView()?-9:9,9,4))
                :close.add(leftView()?-3.5:3.5,2.5,3.5));}}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){if(ENABLED&&phase>=0){event.setYaw(leftView()?225:135);event.setPitch(closeOrigin()!=null?27:phase==2?31:29);event.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-wing-fuel-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_WING_FUEL "+m.getString()));}}
}

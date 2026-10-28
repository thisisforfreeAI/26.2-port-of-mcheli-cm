package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Held position/throttle/fuel fixtures through production cold-air admission. Not a combat recording. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class ContrailProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeContrails");
    private static final int START=Integer.getInteger("mcheli.probeContrailStart",0);
    private static final String[] NAMES={"f-35a","f22a-remote","b-2a","low-altitude","engine-off","far-observer","settings","classic","shared-load","mixed-load","unsupported-f-15e"};
    private static final List<PlaneEntity> actors=new ArrayList<>();
    private static int ticks,phase=-1,age,peak,coldPeak,farPeak,afterTail;private static String capture;
    private static boolean far(){return phase==5;}
    private static boolean active(){return age>=40&&age<(phase==6?250:180);}
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){
            int i=actors.indexOf(this);double t=Math.max(0,Math.min(age-40,phase==6?210:140));
            xo=getX();yo=getY();zo=getZ();yRotO=getYaw();xRotO=getPitch();prevRoll=getRoll();
            setPos(Math.sin(t*.025)*7+(phase==9?(i%10-4.5)*2:phase>=8?(i%4-1.5)*7:0),phase==3?100:phase==9&&(i==0||age<60)?64:270,20+t*1.3+(phase==9?i/10*2:phase>=8?i/4*5:0));
            Vec3 motion=position().subtract(xo,yo,zo);setDeltaMovement(phase==1?Vec3.ZERO:motion);
            setYaw((float)Math.toDegrees(Math.atan2(-motion.x,motion.z)));setRoll(0);setOnGround(false);
        }
        @Override public double getCurrentThrottle(){return phase==4||!active()?0:.85;}
        @Override public int getFuel(){return phase==4?0:getMaxFuel();}
        @Override public int getHP(){return phase==9&&actors.indexOf(this)==0&&active()?Math.max(1,getMaxHP()/4):getMaxHP();}
        @Override public boolean isDestroyed(){return false;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(true);
        }
        if(ticks<160)return;int next=START+(ticks-160)/390;age=(ticks-160)%390;
        if(next>=NAMES.length){System.out.println("M8_CONTRAIL all_passed=true");mc.stop();return;}
        if(next!=phase){
            for(var ac:actors)ac.discard();actors.clear();VehicleAtmosphere.clear();mc.particleEngine.clearParticles();phase=next;peak=coldPeak=farPeak=afterTail=0;
            ClientConfig.EFFECT_STYLE.set(phase==7?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);ClientConfig.AIRCRAFT_CONDENSATION.set(true);
            mc.player.connection.sendCommand(far()?"time set 3000":"time set noon");
            ClientConfig.ENGINE_STARTUP_EFFECTS.set(far());mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);mc.options.fov().set(far()?35:70);mc.options.renderDistance().set(far()||phase==9?16:4);mc.options.broadcastOptions();
            String model=phase==1?"f22a":phase==2?"b-2a":phase==10?"f-15e":"f-35a";
            for(int j=0;j<(phase==8?32:phase==9?97:1);j++){
                var ac=new Plane(mc);ac.setId(970000+phase*100+j);ac.setAircraftTypeName(model);ac.setTextureName(model);actors.add(ac);
                ac.setPos(0,phase==3?100:phase==9?64:270,20);
                var parts=ac.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(990000+phase*10000+j*100+i);mc.level.addEntity(ac);
            }
            System.out.println("M8_CONTRAIL scene="+NAMES[phase]+" actors="+actors.size()+" outlets="+EngineExhaustLook.outlets(actors.getFirst().getAcInfo(),0).size());
        }
        if(age%30==0){var p=actors.getFirst().position();mc.player.connection.sendCommand("tp @s "+(p.x+(far()?35:14))+" "+(p.y+(far()?-200:10))+" "+(p.z+(far()?-35:-22))+" 33 "+(far()?-76:21));}
        if(phase==0&&age==160){var ac=actors.getFirst();long health=io.github.mcheliceport.damage.ComponentHealth.FULL;
            var zones=io.github.mcheliceport.damage.DamageZones.of(ac.getAcInfo());for(int i=0;i<zones.size();i++)if(io.github.mcheliceport.damage.AirframeDamage.bit(zones.get(i).kind(),zones.get(i).index())==4)health=io.github.mcheliceport.damage.ComponentHealth.withLevel(health,i,0);
            ac.setComponentHealth(health);}
        if(phase==6){if(age==100)ClientConfig.AIRCRAFT_CONDENSATION.set(false);if(age==210)ClientConfig.AIRCRAFT_CONDENSATION.set(true);}
        var all=particles(mc);int cold=(int)all.stream().filter(CondensationParticle.class::isInstance).count(),total=(int)all.stream().filter(McheliParticle.class::isInstance).count();
        peak=Math.max(peak,total);coldPeak=Math.max(coldPeak,cold);farPeak=Math.max(farPeak,VehicleAtmosphere.farContrailBirths());
        if(total>1664||VehicleAtmosphere.emitted()>16||VehicleAtmosphere.farContrailBirths()>2||VehicleAtmosphere.stateCount()>128)throw new IllegalStateException("Cold-air allowance exceeded");
        if(age==150&&(phase<3||phase==5||phase==8||phase==9)&&cold==0)throw new IllegalStateException("No high-altitude contrail "+NAMES[phase]);
        if((phase==3||phase==4||phase==7||phase==10)&&cold!=0)throw new IllegalStateException("Low/off/Classic emitted contrails");
        if(phase==0&&age>=165&&age<180)afterTail|=VehicleAtmosphere.contrailMask(actors.getFirst());
        if(phase==0&&age==185&&afterTail!=0)throw new IllegalStateException("Missing tail retained its engine trail");
        if(phase==5&&age==150){var ac=actors.getFirst();if(VehicleAtmosphere.handlesDamage(ac)||VehicleAtmosphere.engineHeat(ac)<.5||VehicleAtmosphere.startupVoiceCount()!=0)throw new IllegalStateException("Far cold states changed damage ownership, heat or sound scope");}
        if(phase==6&&(age==205&&cold!=0||age==235&&cold==0))throw new IllegalStateException("Cold trail setting did not drain/restore");
        if(phase==9&&age>=100&&VehicleAtmosphere.stateCount()>65)throw new IllegalStateException("Departing aircraft kept nearby slots");
        if(phase==9&&age==150&&(!VehicleAtmosphere.handlesDamage(actors.getFirst())||all.stream().noneMatch(DamageSmokeParticle.class::isInstance)))throw new IllegalStateException("Distant cold detail starved nearby damage smoke");
        if(age==110||age==150||age==230||age==370)capture=NAMES[phase]+(age==110?"-flight":age==150?"-engine-close":age==230?"-fading":"-drained");
        if(age==370){if(cold!=0)throw new IllegalStateException("Cold trails failed to drain");System.out.println("M8_CONTRAIL drained=true scene="+NAMES[phase]+" peak_mod_particles="+peak+" peak_contrail="+coldPeak+" peak_far_births="+farPeak);}
    }
    private static Collection<?> particles(Minecraft mc){try{
        var f=net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");f.setAccessible(true);var p=net.minecraft.client.particle.ParticleGroup.class.getDeclaredField("particles");p.setAccessible(true);var all=new ArrayList<>();
        for(var group:((Map<?,?>)f.get(mc.particleEngine)).values())all.addAll((Collection<?>)p.get(group));return all;
    }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&!actors.isEmpty())e.getCamera().setPosition(actors.getFirst().position().add(far()?new Vec3(35,-200,-35):age>=140&&age<160?phase==2?new Vec3(16,8,-25):new Vec3(9,5,-17):phase==2?new Vec3(35,18,-42):new Vec3(14,10,-22)));}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(ENABLED&&phase>=0){e.setYaw(far()?45:phase==2&&!(age>=140&&age<160)?40:33);e.setPitch(far()?-76:phase==2&&!(age>=140&&age<160)?18:21);e.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(capture!=null){var mc=Minecraft.getInstance();String n=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-contrail-"+n+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_CONTRAIL "+m.getString()));}}
}

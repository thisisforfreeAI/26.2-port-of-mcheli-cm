package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
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

/** Production atmosphere on held turning aircraft. Never shipped in a normal build. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class WingVaporProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeWingVapor");
    private static final int START=Integer.getInteger("mcheli.probeWingVaporStart",0);
    private static final String[] NAMES={"f-35a","f-15e-remote","a-10","dry-turn","straight-rain","lost-wing","settings","shared-load"};
    private static final List<PlaneEntity> actors=new ArrayList<>();
    private static int ticks,phase=-1,age,peak,vaporPeak,seenMask;private static String capture;
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){
            int i=actors.indexOf(this);double t=Math.min(Math.max(0,age-40),phase==6?240:200),angle=t*.08,r=12;
            xo=getX();yo=getY();zo=getZ();yRotO=getYaw();xRotO=getPitch();prevRoll=getRoll();
            setPos((phase==4?0:Math.sin(angle)*r)+(phase==7?(i%4-1.5)*7:0),-40,20+(phase==4?t*.96:(1-Math.cos(angle))*r)+(phase==7?i/4*5:0));
            setYaw(phase==4?0:(float)(-90+Math.toDegrees(angle)));setRoll(phase==4?0:-24);
            setDeltaMovement(phase==1?Vec3.ZERO:position().subtract(xo,yo,zo));setOnGround(false);
        }
        @Override public int getHP(){return getMaxHP();}
        @Override public boolean isDestroyed(){return false;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(true);ClientConfig.AIRCRAFT_CONDENSATION.set(true);
        }
        if(ticks<160)return;int next=START+(ticks-160)/340;age=(ticks-160)%340;
        if(next>=NAMES.length){System.out.println("M8_VAPOR all_passed=true");mc.stop();return;}
        if(next!=phase){
            for(var ac:actors)ac.discard();actors.clear();VehicleAtmosphere.clear();mc.particleEngine.clearParticles();phase=next;peak=vaporPeak=seenMask=0;
            ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);
            mc.player.connection.sendCommand(phase==3?"weather clear":"weather rain");
            ClientConfig.AIRCRAFT_CONDENSATION.set(true);
            String model=phase==1?"f-15e":phase==2?"a-10":"f-35a";
            for(int j=0;j<(phase==7?32:1);j++){
                var ac=new Plane(mc);ac.setId(972000+phase*100+j);ac.setAircraftTypeName(model);ac.setTextureName(model);actors.add(ac);ac.setPos(0,-40,20);
                var parts=ac.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(982000+phase*10000+j*100+i);mc.level.addEntity(ac);
            }
            System.out.println("M8_VAPOR scene="+NAMES[phase]+" actors="+actors.size());
        }
        if(age%40==0){var p=actors.getFirst().position();mc.player.connection.sendCommand("tp @s "+(p.x+14)+" "+(p.y+12)+" "+(p.z-22)+" 33 25");}
        if(phase==5&&age==100){var ac=actors.getFirst();long health=io.github.mcheliceport.damage.ComponentHealth.FULL;
            var zones=io.github.mcheliceport.damage.DamageZones.of(ac.getAcInfo());for(int i=0;i<zones.size();i++)if(io.github.mcheliceport.damage.AirframeDamage.bit(zones.get(i).kind(),zones.get(i).index())==1)health=io.github.mcheliceport.damage.ComponentHealth.withLevel(health,i,0);
            ac.setComponentHealth(health);}
        if(phase==6){if(age==110)ClientConfig.AIRCRAFT_CONDENSATION.set(false);if(age==160)ClientConfig.AIRCRAFT_CONDENSATION.set(true);
            if(age==200)ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.CLASSIC);if(age==250)ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);}
        var vapor=vapor(mc);int total=countMod(mc);peak=Math.max(peak,total);vaporPeak=Math.max(vaporPeak,vapor.size());
        if(total>1664||VehicleAtmosphere.emitted()>16||VehicleAtmosphere.stateCount()>128)throw new IllegalStateException("Wing vapor exceeded shared allowance");
        if(age==150&&phase<3&&vapor.isEmpty())throw new IllegalStateException("No wet-turn vapor: "+NAMES[phase]);
        if(phase==3&&age>=180&&!vapor.isEmpty()||phase==4&&!vapor.isEmpty())throw new IllegalStateException("Dry/straight flight emitted vapor");
        if(phase==5&&age>=135&&age<=180){int mask=VehicleAtmosphere.wingVaporMask(actors.getFirst());seenMask|=mask;
            if((mask&1)!=0)throw new IllegalStateException("Lost wing retained vapor emitter");}
        if(phase==5&&age==185&&seenMask!=2)throw new IllegalStateException("Surviving wing lost its vapor emitter: "+seenMask);
        if(phase==6){if((age==145||age==235)&&!vapor.isEmpty())throw new IllegalStateException("Settings did not drain vapor");
            if((age==195||age==270)&&vapor.isEmpty())throw new IllegalStateException("Settings did not restore vapor");}
        if(age==120||age==165||age==225||age==310)capture=NAMES[phase]+(age==120?"-turn":age==165?"-close":age==225?"-later":"-drained");
        if(age==310){if(!vapor.isEmpty())throw new IllegalStateException("Straightened aircraft retained vapor");
            System.out.println("M8_VAPOR drained=true scene="+NAMES[phase]+" peak_mod_particles="+peak+" peak_vapor="+vaporPeak+" surviving_mask="+seenMask);}
    }
    private static Collection<?> particles(Minecraft mc){
        try{var field=net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");field.setAccessible(true);
            var p=net.minecraft.client.particle.ParticleGroup.class.getDeclaredField("particles");p.setAccessible(true);var all=new ArrayList<>();
            for(var group:((Map<?,?>)field.get(mc.particleEngine)).values())all.addAll((Collection<?>)p.get(group));return all;
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static List<CondensationParticle> vapor(Minecraft mc){return particles(mc).stream().filter(CondensationParticle.class::isInstance).map(CondensationParticle.class::cast).toList();}
    private static int countMod(Minecraft mc){return (int)particles(mc).stream().filter(McheliParticle.class::isInstance).count();}
    private static Vec3 closeOrigin(){if(actors.isEmpty()||age<155||age>=180)return null;
        var ac=actors.getFirst();var models=io.github.mcheliceport.client.model.AircraftModels.peek(ac.getAcInfo());if(models==null)return null;
        for(var g:models.body().groups)if(g.name().equals("$body")){var part=io.github.mcheliceport.client.model.BreakupMeshes.part(g);if(part==null)return null;
            return ac.position().add(io.github.mcheliceport.util.McheliMath.rotVec3(part.tips().get(phase==5?1:0),-ac.getYaw(),-ac.getPitch(),-ac.getRoll())).add(0,io.github.mcheliceport.content.aircraft.AircraftInfo.GLOBAL_Y_OFFSET,0);}
        return null;
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&!actors.isEmpty()){var close=closeOrigin();e.getCamera().setPosition(close==null?actors.getFirst().position().add(14,12,-22):close.add(8,5,-12));}}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(ENABLED&&phase>=0){e.setYaw(closeOrigin()==null?33:34);e.setPitch(closeOrigin()==null?25:19);e.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-vapor-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_VAPOR "+m.getString()));}}
}

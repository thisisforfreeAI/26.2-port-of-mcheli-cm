package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.particle.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.damage.*;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.client.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Dev-only stock-model snapshots: saved/live tail damage, old wrecks and retained wing breakup. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class RetainedFuselageProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeRetainedFuselage");
    private static final String[] NAMES={"f22a","b-2a","f-35a","a-10"};
    private static int ticks,phase=-1,age;private static PlaneEntity actor;private static String capture;
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){xo=getX();yo=getY();zo=getZ();setPos(0,-44,20);setDeltaMovement(Vec3.ZERO);}
        @Override public boolean isDestroyed(){return age>=145;}
    }
    private static void health(int mask){
        long value=ComponentHealth.FULL;var zones=DamageZones.of(actor.getAcInfo());
        for(int i=0;i<zones.size();i++)if((AirframeDamage.bit(zones.get(i).kind(),zones.get(i).index())&mask)!=0)value=ComponentHealth.withLevel(value,i,0);
        actor.setComponentHealth(value);actor.setDamageTaken(mask==0?0:(int)(actor.getMaxHP()*.4));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false","tp @s 14 -34 6 40 20"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.fov().set(65);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(true);
            ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
        }
        if(ticks<160)return;int next=(ticks-160)/320;age=(ticks-160)%320;
        if(next>=NAMES.length){System.out.println("M8_RETAINED_FUSELAGE all_passed=true");mc.stop();return;}
        if(next!=phase){
            if(actor!=null)actor.discard();VehicleAtmosphere.clear();LoosePanels.clear();WreckAppearance.clear();mc.particleEngine.clearParticles();phase=next;
            actor=new Plane(mc);actor.setId(-995000-phase);actor.setAircraftTypeName(NAMES[phase]);actor.setTextureName(NAMES[phase]);actor.setPos(0,-44,20);
            var parts=actor.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(-996000-phase*100-i);
            health(4);mc.level.addEntity(actor);
        }
        if(age==35)health(0);if(age==65)health(4);
        if(age==105)ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.CLASSIC);
        if(age==115)ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
        if(age==25||age==95||age==290){
            var s=WreckSequenceEffects.get(actor);
            if(s==null||s.airframe==null||s.tail||(s.airframe.mask()&4)!=0||s.airframe.openings().containsKey(4))throw new IllegalStateException("Fuselage separated: "+NAMES[phase]);
            if(age<145&&(s.airframe.mask()!=0||LoosePanels.airframePieces(actor)!=0))throw new IllegalStateException("Tail-only damage emitted a whole airframe piece");
            if(age==290&&(s.airframe.mask()&2)==0)throw new IllegalStateException("Wing breakup was lost");
            System.out.println("M8_RETAINED_FUSELAGE plane="+NAMES[phase]+" age="+age+" saved_damage="+AirframeDamage.missing(actor)+" mesh="+s.airframe.mask()+" tail_attached=true");
        }
        if(age==95||age==290)capture=NAMES[phase]+(age==95?"-tail-damage":"-wreck");
    }
    private static Vec3 eye(){double span=phase==1?36:phase==3?26:24;return actor.position().add(span*.72,span*.70,-span*.9);}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&actor!=null)e.getCamera().setPosition(eye());}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(ENABLED&&actor!=null){var d=actor.position().add(0,1,0).subtract(eye());e.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));e.setPitch((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));e.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-retained-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_RETAINED_FUSELAGE "+m.getString()));}}
}

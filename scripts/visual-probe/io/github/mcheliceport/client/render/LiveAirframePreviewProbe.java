package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.particle.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.damage.*;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Non-shipping airborne visual states; real damage/save behavior is tested by LiveAirframeGameTests. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class LiveAirframePreviewProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeLiveAirframe");
    private static final String[] NAMES={"f-35a","f-15e","a-10"};
    private static int ticks,phase=-1,age;private static PlaneEntity plane;private static String capture;
    private static final class PreviewPlane extends PlaneEntity {
        PreviewPlane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){xo=getX();yo=getY();zo=getZ();setPos(0,-40,20+age*.18);setDeltaMovement(0,0,.18);}
        @Override public boolean isDestroyed(){return age>=215&&age<330;}
    }
    private static void damage(int mask){
        long health=ComponentHealth.FULL;var zones=DamageZones.of(plane.getAcInfo());
        for(int i=0;i<zones.size();i++)if((AirframeDamage.bit(zones.get(i).kind(),zones.get(i).index())&mask)!=0)health=ComponentHealth.withLevel(health,i,0);
        plane.setComponentHealth(health);plane.setDamageTaken(mask==0?0:(int)(plane.getMaxHP()*.4));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false","tp @s 12 -28 25 135 31"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(true);
            ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
        }
        if(ticks<160)return;int next=(ticks-160)/380;age=(ticks-160)%380;
        if(next>=3){System.out.println("M8_LIVE_AIRFRAME all_scenes_passed=true");mc.stop();return;}
        if(next!=phase){
            phase=next;if(plane!=null)plane.discard();VehicleAtmosphere.clear();LoosePanels.clear();WreckAppearance.clear();
            plane=new PreviewPlane(mc);plane.setId(976000+phase);plane.setAircraftTypeName(NAMES[phase]);plane.setTextureName(NAMES[phase]);plane.setPos(0,-40,20);
            var parts=plane.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(986000+phase*100+i);
            mc.level.addEntity(plane);damage(0);
        }
        if(age==211)ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(false);
        if(age==213){if(LoosePanels.airframePieces(plane)!=0)throw new IllegalStateException("Disabled effect retained live fragments");System.out.println("M8_LIVE_AIRFRAME toggle_cleared=true");}
        if(age==214)ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(true);
        if(age==60)damage(1);if(age==110)damage(0);if(age==130)damage(2);if(age==185)damage(6);if(age==330)damage(0);
        if(age==40||age==80||age==155||age==205||age==300)capture=NAMES[phase]+"-age"+age;
        if(age==90||age==165||age==210){
            int expected=age==90?1:age==165?2:6;var s=WreckSequenceEffects.get(plane);
            if(plane.isDestroyed()||plane.getDamageTaken()>=plane.getMaxHP()||s==null||s.airframe==null||s.airframe.mask()!=expected||LoosePanels.airframePieces(plane)<1)
                throw new IllegalStateException("Missing live airframe breakup: "+NAMES[phase]+" expected="+expected+" mesh="+(s==null||s.airframe==null?-1:s.airframe.mask())+" debris="+LoosePanels.airframePieces(plane));
            System.out.println("M8_LIVE_AIRFRAME vehicle="+NAMES[phase]+" live_mask="+expected+" hp="+plane.getHP()+" pieces="+LoosePanels.airframePieces(plane));
        }
        if(age==120||age==350){
            var s=WreckSequenceEffects.get(plane);
            if(AirframeDamage.missing(plane)!=0||LoosePanels.pieces()!=0||s!=null&&(s.parts&12)!=0)throw new IllegalStateException("Repair retained lost structure/debris");
            System.out.println("M8_LIVE_AIRFRAME vehicle="+NAMES[phase]+" repair_restored=true");
        }
        if(age==300){var s=WreckSequenceEffects.get(plane);if(s==null||s.airframe==null||(s.airframe.mask()&6)!=6)throw new IllegalStateException("Wreck regrew a live-lost part");System.out.println("M8_LIVE_AIRFRAME wreck_preserved=true");}
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov event){if(ENABLED&&plane!=null)event.getCamera().setPosition(plane.position().add(9,9,4));}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){if(ENABLED&&plane!=null){event.setYaw(135);event.setPitch(29);event.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-live-airframe-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_LIVE_AIRFRAME "+m.getString()));}}
}

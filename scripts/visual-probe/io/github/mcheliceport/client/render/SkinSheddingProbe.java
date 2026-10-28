package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.particle.*;
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

/** Held component/wreck states and production detail draws; excluded from normal builds. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class SkinSheddingProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeSkinShedding");
    private static final int START=Integer.getInteger("mcheli.probeSkinStart",0);
    private static final String[] NAMES={"f-35a","f-15e","a-10","loaded-damage","classic-transition","settings-transition","fresh-wreck","shared-load"};
    private static final List<PlaneEntity> actors=new ArrayList<>();
    private static int ticks,phase=-1,age,peak,peakSkin;private static String capture;
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){int i=actors.indexOf(this);xo=getX();yo=getY();zo=getZ();setPos(phase==7?(i%4-1.5)*7:0,-40,20+age*.18+(phase==7?i/4*5:0));setDeltaMovement(0,0,.18);}
        @Override public boolean isDestroyed(){return phase==6&&age>=60&&age<220;}
    }
    private static void damage(PlaneEntity ac,int mask) {
        long health=ComponentHealth.FULL;var zones=DamageZones.of(ac.getAcInfo());
        for(int i=0;i<zones.size();i++)if((AirframeDamage.bit(zones.get(i).kind(),zones.get(i).index())&mask)!=0)health=ComponentHealth.withLevel(health,i,0);
        ac.setComponentHealth(health);ac.setDamageTaken(mask==0?0:(int)(ac.getMaxHP()*.15));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false","tp @s -9 -31 24 225 29"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);
        }
        if(ticks<160)return;int next=START+(ticks-160)/280;age=(ticks-160)%280;
        if(next>=NAMES.length){System.out.println("M8_SKIN all_passed=true");mc.stop();return;}
        if(next!=phase) {
            for(var ac:actors)ac.discard();actors.clear();VehicleAtmosphere.clear();LoosePanels.clear();WreckAppearance.clear();mc.particleEngine.clearParticles();
            phase=next;peak=peakSkin=0;ClientConfig.EFFECT_STYLE.set(phase==4?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);
            ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(true);
            String model=phase==1?"f-15e":phase==2?"a-10":"f-35a";
            for(int j=0;j<(phase==7?32:1);j++){
                var ac=new Plane(mc);ac.setId(973000+phase*100+j);ac.setAircraftTypeName(model);ac.setTextureName(model);actors.add(ac);
                ac.setPos(phase==7?(j%4-1.5)*7:0,-40,20+(phase==7?j/4*5:0));
                var parts=ac.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(983000+phase*10000+j*100+i);mc.level.addEntity(ac);damage(ac,phase==3?1:0);
            }
            System.out.println("M8_SKIN scene="+NAMES[phase]+" actors="+actors.size());
        }
        if(age%40==0){Vec3 p=actors.getFirst().position();mc.player.connection.sendCommand("tp @s "+(p.x-9)+" "+(p.y+9)+" "+(p.z+4)+" 225 29");}
        if(phase<3||phase==7){if(age==60)for(var ac:actors)damage(ac,1);if(age==120)for(var ac:actors)damage(ac,0);
            if(age==160)for(var ac:actors)damage(ac,4);}
        if(phase==4){if(age==60)for(var ac:actors)damage(ac,1);if(age==120)ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);}
        if(phase==5){if(age==60)for(var ac:actors)damage(ac,1);if(age==80)ClientConfig.VEHICLE_DAMAGE_DETAILS.set(false);
            if(age==100)ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);if(age==120)for(var ac:actors)damage(ac,0);
            if(age==160)for(var ac:actors)damage(ac,4);if(age==180)ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(false);
            if(age==200)ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(true);
            if(age==85||age==110||age==185||age==210)if(skinCount()!=0)throw new IllegalStateException("Settings retained or replayed skin pieces");}
        if(age==220)for(var ac:actors)damage(ac,0);
        peak=Math.max(peak,LoosePanels.pieces());peakSkin=Math.max(peakSkin,skinCount());
        if(LoosePanels.pieces()>8)throw new IllegalStateException("Skin debris exceeded shared mesh cap");
        if((phase<3||phase==7)&&(age==78||age==94||age==178||age==194)||phase==6&&(age==130||age==145||age==190||age==205)||age==250)
            capture=NAMES[phase]+(age==250?"-repaired":age<150?(age==78||age==130?"-wing-overview":"-wing-close"):(age==178||age==190?"-tail-overview":"-tail-close"));
        if(phase==3||phase==4)if(skinCount()!=0)throw new IllegalStateException("Loaded/toggled damage replayed skin debris");
        if(phase<3&&(age==105||age==205)||phase==6&&age==155){
            if(skinCount()==0)throw new IllegalStateException("Fresh fracture shed no skin pieces: "+NAMES[phase]+" age="+age);
            System.out.println("M8_SKIN profile="+NAMES[phase]+" age="+age+" live_skin="+skinCount());}
        if(age==250){if(skinCount()!=0)throw new IllegalStateException("Repair retained skin pieces");
            System.out.println("M8_SKIN repaired=true scene="+NAMES[phase]+" peak_mesh_pieces="+peak+" peak_skin_pieces="+peakSkin);}
    }
    private static int skinCount(){return actors.stream().mapToInt(LoosePanels::skinPieces).sum();}
    private static Vec3 closeOrigin(){
        if(actors.isEmpty()||!((age>=88&&age<110)||(age>=188&&age<215)||(phase==6&&age>=140&&age<155)))return null;
        var ac=actors.getFirst();var s=WreckSequenceEffects.get(ac);if(s==null||s.airframe==null)return null;
        var p=s.airframe.openings().get((phase==6&&age<155)?2:age<150?1:4);return p==null?null:ac.position().add(io.github.mcheliceport.util.McheliMath.rotVec3(p,-ac.getYaw(),-ac.getPitch(),-ac.getRoll())).add(0,io.github.mcheliceport.content.aircraft.AircraftInfo.GLOBAL_Y_OFFSET,0);
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov event){if(ENABLED&&!actors.isEmpty()){
        var close=closeOrigin();event.getCamera().setPosition(close==null?actors.getFirst().position().add(phase==2?new Vec3(-12,12,7):new Vec3(-9,9,4))
                :close.add(age<155?new Vec3(phase==6?3.5:-3.5,2.2,3.5):new Vec3(3,1.7,-3.5)));}}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){if(ENABLED&&phase>=0){boolean close=closeOrigin()!=null;event.setYaw(close&&phase==6&&age<155?135:close&&age>=155?41:225);event.setPitch(close?(age<155?24:20):phase==2?31:29);event.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-skin-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_SKIN "+m.getString()));}}
}

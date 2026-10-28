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
public final class FractureInteriorProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeFractureInteriors");
    private static final int START=Integer.getInteger("mcheli.probeFractureStart",0);
    private static final String[] NAMES={"f-35a","f-15e","a-10","classic","details-off","shared-load"};
    private static final List<PlaneEntity> actors=new ArrayList<>();
    private static int ticks,phase=-1,age,peak;private static String capture;
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){int i=actors.indexOf(this);xo=getX();yo=getY();zo=getZ();setPos(phase==5?(i%4-1.5)*7:0,-40,20+age*.18+(phase==5?i/4*5:0));setDeltaMovement(0,0,.18);}
        @Override public boolean isDestroyed(){return age>=230&&age<300;}
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
        if(ticks<160)return;int next=START+(ticks-160)/380;age=(ticks-160)%380;
        if(next>=NAMES.length){System.out.println("M8_FRACTURE all_passed=true");mc.stop();return;}
        if(next!=phase) {
            for(var ac:actors)ac.discard();actors.clear();VehicleAtmosphere.clear();LoosePanels.clear();WreckAppearance.clear();mc.particleEngine.clearParticles();
            phase=next;peak=0;ClientConfig.EFFECT_STYLE.set(phase==3?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);
            ClientConfig.VEHICLE_DAMAGE_DETAILS.set(phase!=4);ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(true);
            String model=phase==1?"f-15e":phase==2?"a-10":"f-35a";
            for(int j=0;j<(phase==5?32:1);j++){
                var ac=new Plane(mc);ac.setId(973000+phase*100+j);ac.setAircraftTypeName(model);ac.setTextureName(model);actors.add(ac);
                ac.setPos(phase==5?(j%4-1.5)*7:0,-40,20+(phase==5?j/4*5:0));
                var parts=ac.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(983000+phase*10000+j*100+i);mc.level.addEntity(ac);damage(ac,0);
            }
            System.out.println("M8_FRACTURE scene="+NAMES[phase]+" actors="+actors.size());
        }
        if(age%40==0){Vec3 p=actors.getFirst().position();mc.player.connection.sendCommand("tp @s "+(p.x-9)+" "+(p.y+9)+" "+(p.z+4)+" 225 29");}
        if(age==60)for(var ac:actors)damage(ac,1);
        if(age==125||age==300)for(var ac:actors)damage(ac,0);
        if(age==160)for(var ac:actors)damage(ac,4);
        peak=Math.max(peak,VehicleDetailLayers.vertices());
        if(VehicleDetailLayers.vertices()>12288||LoosePanels.pieces()>8)throw new IllegalStateException("Fracture detail budget exceeded");
        if(age==85||age==105||age==185||age==200||age==260||age==350)capture=NAMES[phase]+(age==85?"-wing-overview":age==105?"-wing-close":age==185?"-tail-overview":age==200?"-tail-close":age==260?"-wreck-close":"-repaired");
        if((age==110||age==205)&&phase!=3&&phase!=4){
            var s=WreckSequenceEffects.get(actors.getFirst());
            if(s==null||s.airframe==null||s.airframe.mask()!=(age==110?1:4))throw new IllegalStateException("Missing fracture geometry");
            var d=s.airframe.fracture();
            if(d.vertices()>3072||d.ribs().vertexCount()==0)throw new IllegalStateException("Invalid fracture detail bounds");
            if(age==110&&phase<3&&d.cables().vertexCount()==0)throw new IllegalStateException("Supported wing has no fracture wires");
            System.out.println("M8_FRACTURE profile="+NAMES[phase]+" part="+(age==110?"wing":"tail")+" contours="+d.contours()+" cavity="+d.cavity().vertexCount()+" ribs="+d.ribs().vertexCount()+" cables="+d.cables().vertexCount());
        }
        if((phase==3||phase==4)&&WreckSequenceEffects.get(actors.getFirst())!=null)throw new IllegalStateException("Disabled damage retained fracture state");
        if(age==360){var s=WreckSequenceEffects.get(actors.getFirst());if(s!=null&&s.airframe!=null&&(s.airframe.mask()!=0||s.airframe.fracture().vertices()!=0))throw new IllegalStateException("Repair retained fracture detail");
            System.out.println("M8_FRACTURE repaired=true scene="+NAMES[phase]+" peak_detail_vertices="+peak);}
    }
    private static Vec3 closeOrigin(){
        if(actors.isEmpty()||!((age>=100&&age<120)||(age>=195&&age<215)||(age>=250&&age<280)))return null;
        var ac=actors.getFirst();var s=WreckSequenceEffects.get(ac);if(s==null||s.airframe==null)return null;
        var p=s.airframe.openings().get(age<120?1:4);return p==null?null:ac.position().add(io.github.mcheliceport.util.McheliMath.rotVec3(p,-ac.getYaw(),-ac.getPitch(),-ac.getRoll())).add(0,io.github.mcheliceport.content.aircraft.AircraftInfo.GLOBAL_Y_OFFSET,0);
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov event){if(ENABLED&&!actors.isEmpty()){
        var close=closeOrigin();event.getCamera().setPosition(close==null?actors.getFirst().position().add(phase==2?new Vec3(-12,12,7):new Vec3(-9,9,4))
                :close.add(age<120?new Vec3(-3.5,2.2,3.5):new Vec3(3,1.7,-3.5)));}}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){if(ENABLED&&phase>=0){boolean close=closeOrigin()!=null;event.setYaw(close&&age>=120?41:225);event.setPitch(close?(age<120?24:20):phase==2?31:29);event.setRoll(0);}}
    // 26.2 extracts entities before RenderFrame.Pre; sample before the shared allowance resets.
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void extracted(RenderFrameEvent.Pre event){
        if(ENABLED&&phase>=0){peak=Math.max(peak,VehicleDetailLayers.vertices());
            if(VehicleDetailLayers.vertices()>12288)throw new IllegalStateException("Fracture frame budget exceeded");}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-fracture-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_FRACTURE "+m.getString()));}}
}

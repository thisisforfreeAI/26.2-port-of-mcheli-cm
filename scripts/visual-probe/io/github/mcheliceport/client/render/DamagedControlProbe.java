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

/** Actual component states through the production renderer; no normal build includes this. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class DamagedControlProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeDamagedControls");
    private static final int START=Integer.getInteger("mcheli.probeControlStart",0);
    private static final String[] NAMES={"f-35a","f22a","a-10","loaded-critical","classic-transition","settings-transition","shared-load"};
    private static final List<PlaneEntity> actors=new ArrayList<>();
    private static int ticks,phase=-1,age,peak;private static String capture;
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){int i=actors.indexOf(this);xo=getX();yo=getY();zo=getZ();
            setPos(phase==6?(i%4-1.5)*7:0,-40,20+age*.28+(phase==6?i/4*5:0));setDeltaMovement(0,0,phase==1?0:.28);}
        @Override public boolean isDestroyed(){return false;}
    }
    private static void damage(PlaneEntity ac,float left,float right,float tail) {
        var zones=DamageZones.of(ac.getAcInfo());long value=ComponentHealth.FULL;
        for(int i=0;i<zones.size();i++) {
            var z=zones.get(i);float fraction=z.kind()==ComponentKind.WING?(z.index()==0?left:right):z.kind()==ComponentKind.AIRFRAME_TAIL?tail:1;
            value=ComponentHealth.withLevel(value,i,Math.round(fraction*255));
        }
        ac.setComponentHealth(value);ac.setDamageTaken(left==1&&right==1&&tail==1?0:(int)(ac.getMaxHP()*.15));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40) {
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false","tp @s -9 -31 24 225 29"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);
            ClientConfig.ADAPTIVE_EFFECTS.set(false);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);
        }
        if(ticks<160)return;int next=START+(ticks-160)/290;age=(ticks-160)%290;
        if(next>=NAMES.length){System.out.println("M8_CONTROLS all_passed=true");mc.stop();return;}
        if(next!=phase) {
            for(var ac:actors)ac.discard();actors.clear();VehicleAtmosphere.clear();LoosePanels.clear();WreckAppearance.clear();mc.particleEngine.clearParticles();
            phase=next;peak=0;ClientConfig.EFFECT_STYLE.set(phase==4?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);
            ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(true);
            String model=phase==1?"f22a":phase==2?"a-10":"f-35a";
            for(int j=0;j<(phase==6?32:1);j++) {
                var ac=new Plane(mc);ac.setId(974000+phase*100+j);ac.setAircraftTypeName(model);ac.setTextureName(model);actors.add(ac);
                ac.setPos(phase==6?(j%4-1.5)*7:0,-40,20+(phase==6?j/4*5:0));
                var parts=ac.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(984000+phase*10000+j*100+i);
                damage(ac,phase==3?.08F:1,phase==3?.4F:1,phase==3?.08F:1);mc.level.addEntity(ac);
            }
            System.out.println("M8_CONTROLS scene="+NAMES[phase]+" actors="+actors.size());
        }
        if(age%40==0){Vec3 p=actors.getFirst().position();mc.player.connection.sendCommand("tp @s "+(p.x-9)+" "+(p.y+9)+" "+(p.z+4)+" 225 29");}
        if(phase<3||phase==6) {
            if(age==40)for(var ac:actors)damage(ac,.4F,.4F,.4F);
            if(age==110)for(var ac:actors)damage(ac,.08F,.4F,.08F);
            if(age==160)for(var ac:actors)damage(ac,0,.4F,.08F);
        }
        if(phase==4) {
            if(age==60)for(var ac:actors)damage(ac,.08F,.4F,.08F);
            if(age==100)ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
        }
        if(phase==5) {
            if(age==40)for(var ac:actors)damage(ac,.4F,.4F,.4F);
            if(age==110)for(var ac:actors)damage(ac,.08F,.4F,.08F);
            if(age==120)ClientConfig.VEHICLE_DAMAGE_DETAILS.set(false);
            if(age==140)ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);
            if(age==160)ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(false);
            if(age==180)ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(true);
            if(age==125||age==155||age==175||age==195)if(LoosePanels.controlPieces(actors.getFirst())!=0)throw new IllegalStateException("Settings replayed/retained controls");
        }
        if(age==210)for(var ac:actors)damage(ac,1,1,1);
        peak=Math.max(peak,LoosePanels.pieces());
        if(LoosePanels.pieces()>8)throw new IllegalStateException("Control meshes exceeded shared piece cap");
        if(age==30||age==80||age==95||age==130||age==185||age==250)
            capture=NAMES[phase]+(age==30?"-healthy":age==80?"-flutter-a":age==95?"-flutter-b":age==130?"-separated":age==185?"-wing-loss":"-repaired");
        if(age==90&&phase<3) {
            var s=WreckSequenceEffects.get(actors.getFirst());var profile=s==null?null:s.controlLayout;
            if(profile==null||profile.panels().stream().noneMatch(p->p.slot()==0)||profile.panels().stream().noneMatch(p->p.slot()==1)
                    ||profile.panels().stream().noneMatch(p->p.slot()==2||p.slot()==3))
                throw new IllegalStateException("Control profile missing: "+NAMES[phase]+" slots="+(profile==null?"null":profile.panels().stream().map(p->p.slot()).toList()));
            System.out.println("M8_CONTROLS profile="+NAMES[phase]+" slots="+profile.panels().stream().map(p->p.slot()).toList()
                    +" vertices="+(profile.kept().vertexCount()+profile.panels().stream().mapToInt(p->p.group().vertexCount()).sum()));
        }
        if(age==135&&phase<3&&LoosePanels.controlPieces(actors.getFirst())==0)throw new IllegalStateException("No control separation");
        if(age==190&&phase<3) {
            var s=WreckSequenceEffects.get(actors.getFirst());
            if(s==null||s.airframe==null||(s.airframe.mask()&1)==0||LoosePanels.airframePieces(actors.getFirst())==0)throw new IllegalStateException("Whole wing transition missing");
        }
        if((phase==3||phase==4)&&LoosePanels.controlPieces(actors.getFirst())!=0)throw new IllegalStateException("Old/toggled controls replayed debris");
        if(age==260) {
            var s=WreckSequenceEffects.get(actors.getFirst());
            if(LoosePanels.controlPieces(actors.getFirst())!=0||AirframeDamage.missing(actors.getFirst())!=0||s!=null&&s.controlMask!=0)throw new IllegalStateException("Repair retained control loss");
            System.out.println("M8_CONTROLS repaired=true scene="+NAMES[phase]+" peak_mesh_pieces="+peak);
        }
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov event){if(ENABLED&&!actors.isEmpty())event.getCamera().setPosition(actors.getFirst().position().add(phase==2?new Vec3(-12,12,7):new Vec3(-9,9,4)));}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){if(ENABLED&&phase>=0){event.setYaw(225);event.setPitch(phase==2?31:29);event.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-controls-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_CONTROLS "+m.getString()));}}
}

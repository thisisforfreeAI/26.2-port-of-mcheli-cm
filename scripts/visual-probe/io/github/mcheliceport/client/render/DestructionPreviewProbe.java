package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.particle.VehicleAtmosphere;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Controlled cosmetic-state previews; real production renderers, never shipped. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class DestructionPreviewProbe {
    private static final int STEP=Integer.getInteger("mcheli.destructionPreview",0);
    private static final String[] NAMES=STEP==8?new String[]{"f-35a","f-15e","a-10"}:STEP==7?new String[]{"f-35a","f-15e","ac-130"}:STEP==4?new String[]{"growler","mi-24ps","an2"}:new String[]{"m1a2","ah-64","f-35a"};
    private static int tick,phase=-1,age;
    private static AircraftEntity vehicle;
    private static String capture;
    private static org.joml.Vector3f glassPoint,framePoint;
    private static int damage(AircraftEntity e){return (int)(e.getMaxHP()*(STEP==8?(age<190?.5:0):STEP==4?(age<120?.05:.9):STEP==3?.05:STEP==5?1:STEP==7?(age<140?.72:age<190?.95:0):.78));}
    private static void move(AircraftEntity e){
        e.xo=e.getX();e.yo=e.getY();e.zo=e.getZ();
        double speed=STEP==1?(phase==2?.5:phase==1?.25:.1):0;
        e.setPos(0,STEP==1&&phase>0?-55:-60,STEP==1?-20+Math.max(0,age-50)*speed:20);e.setDeltaMovement(0,0,speed);
    }
    private static final class Tank extends TankEntity {
        Tank(Minecraft mc){super(ModEntityTypes.TANK.get(),mc.level);}
        @Override public void tick(){DestructionPreviewProbe.move(this);}
        @Override public int getDamageTaken(){return DestructionPreviewProbe.damage(this);}
        @Override public boolean isSearchLightOn(){return STEP==4 || super.isSearchLightOn();}
        @Override public boolean isDestroyed(){return STEP==5&&age>40 || super.isDestroyed();}
    }
    private static final class Heli extends HeliEntity {
        Heli(Minecraft mc){super(ModEntityTypes.HELICOPTER.get(),mc.level);}
        @Override public void tick(){DestructionPreviewProbe.move(this);}
        @Override public int getDamageTaken(){return DestructionPreviewProbe.damage(this);}
        @Override public boolean isSearchLightOn(){return STEP==4 || super.isSearchLightOn();}
        @Override public boolean isDestroyed(){return STEP==5&&age>40 || super.isDestroyed();}
    }
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){DestructionPreviewProbe.move(this);}
        @Override public int getDamageTaken(){return DestructionPreviewProbe.damage(this);}
        @Override public boolean isSearchLightOn(){return STEP==4 || super.isSearchLightOn();}
        @Override public boolean isDestroyed(){return STEP==5&&age>40 || super.isDestroyed();}
    }
    private static void commands(Minecraft mc,String...c){for(String s:c)mc.player.connection.sendCommand(s);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(STEP==0)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++tick==40){
            commands(mc,"kill @e[type=!minecraft:player]","gamemode spectator",STEP==4?"time set 13000":"time set noon","weather clear","gamerule minecraft:advance_time false");
            for(int y=-60;y<4;y+=7)commands(mc,"fill -30 "+y+" -30 30 "+(y+6)+" 40 air");
            commands(mc,"fill -30 -64 -30 30 -61 40 stone","tp @s 12 -53 8 55 18");
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.ENGINE_HEAT_HAZE.set(false);
        }
        if(STEP==2&&tick==100)commands(mc,"setblock -5 -60 10 stone_slab[type=bottom]","setblock 5 -60 10 water","fill 10 -64 10 10 -58 10 air");
        if(STEP==2&&tick==120){
            double slab=io.github.mcheliceport.client.effect.FluidStains.add(mc.level,new Vec3(-4.5,-58,10.5),false);
            double water=io.github.mcheliceport.client.effect.FluidStains.add(mc.level,new Vec3(5.5,-58,10.5),false);
            double air=io.github.mcheliceport.client.effect.FluidStains.add(mc.level,new Vec3(10.5,-58,10.5),false);
            if(Math.abs(slab+59.5)>.01||Double.isFinite(water)||Double.isFinite(air))throw new IllegalStateException("Fluid terrain fit failed");
            System.out.println("M8_DESTRUCTION fluid_slab_water_air=true");
            commands(mc,"setblock -5 -60 10 air");
        }
        if(STEP==2&&tick==140){
            if(io.github.mcheliceport.client.effect.FluidStains.count()!=0)throw new IllegalStateException("Removed support retained stain");
            System.out.println("M8_DESTRUCTION fluid_removed_support=true");
        }
        if(tick<160)return;
        int next=(tick-160)/220;age=(tick-160)%220;
        if(next>=NAMES.length){mc.stop();return;}
        if(next!=phase){
            phase=next;glassPoint=null;framePoint=null;if(vehicle!=null)vehicle.discard();mc.particleEngine.clearParticles();VehicleAtmosphere.clear();VehicleImpactMarks.clear();
            vehicle=io.github.mcheliceport.content.ContentRegistries.plane().get(NAMES[phase])!=null?new Plane(mc)
                    :io.github.mcheliceport.content.ContentRegistries.heli().get(NAMES[phase])!=null?new Heli(mc):new Tank(mc);
            LoosePanels.clear();
            io.github.mcheliceport.client.effect.FluidStains.clear();
            vehicle.setId(970000+(phase==2?4:phase));vehicle.setAircraftTypeName(NAMES[phase]);vehicle.setTextureName(NAMES[phase]);vehicle.setPos(0,-60,20);
            var parts=vehicle.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(980000+phase*100+i);
            mc.level.addEntity(vehicle);System.out.println("M8_DESTRUCTION step="+STEP+" vehicle="+NAMES[phase]);
        }
        if(age==145||age==180||(STEP==4||STEP==7)&&age==95||(STEP==7||STEP==8)&&age==205||(STEP==8||STEP==5)&&age==95)capture=NAMES[phase]+"-age"+age;
        if(STEP==8){
            if(glassPoint==null&&age>50)findGlass();
            if(age==70){
                if(framePoint==null)throw new IllegalStateException("No identifiable canopy frame");
                VehicleImpactMarks.receive(new io.github.mcheliceport.net.VehicleImpactPayload(vehicle.getId(),vehicle.getUUID(),
                        framePoint.x,framePoint.y,framePoint.z,-1,0,0,.085F,false,tick));
            }
            if(age==80){
                if(VehicleImpactMarks.crackStage(vehicle)!=0||VehicleImpactMarks.stored()==0)throw new IllegalStateException("Canopy frame incorrectly treated as glass");
                System.out.println("M8_DESTRUCTION opaque_frame_paint=true");VehicleImpactMarks.clear();
            }
            if(age==90||age==125||age==160||age==170){
                if(glassPoint==null)throw new IllegalStateException("No identifiable canopy glazing");
                VehicleImpactMarks.receive(new io.github.mcheliceport.net.VehicleImpactPayload(vehicle.getId(),vehicle.getUUID(),
                        glassPoint.x,glassPoint.y,glassPoint.z,-1,0,0,.12F,false,tick));
            }
            if(age==95||age==180||age==205){
                int cracks=VehicleImpactMarks.crackStage(vehicle);
                if(age==95&&cracks!=1||age==180&&cracks!=4||age==205&&cracks!=0)throw new IllegalStateException("Incorrect glass progression/repair: "+cracks);
                System.out.println("M8_DESTRUCTION glass_age="+age+" stage="+cracks+" marks="+VehicleImpactMarks.stored());
            }
        }
        if(STEP==7&&(age==145||age==205)){
            if(age==145&&(!LoosePanels.detached(vehicle)||LoosePanels.pieces()==0||LoosePanels.pieces()>8))throw new IllegalStateException("Missing/unbounded loose panel");
            if(age==205&&LoosePanels.detached(vehicle))throw new IllegalStateException("Repair did not restore panel");
            System.out.println("M8_DESTRUCTION loose_age="+age+" detached="+LoosePanels.detached(vehicle)+" pieces="+LoosePanels.pieces());
        }
        if(STEP==6&&age==80)commands(mc,"fill 3 -60 15 3 -54 25 stone");
        if(STEP==6&&age==95)VehicleBlastScars.blast(mc.level,vehicle.position().add(6,1,0),5);
        if(STEP==6&&age==110){
            if(VehicleImpactMarks.stored()!=0)throw new IllegalStateException("Blast scars crossed solid occlusion");
            System.out.println("M8_DESTRUCTION blast_occlusion=true");
            commands(mc,"fill 3 -60 15 3 -54 25 air");
        }
        if(STEP==6&&age==120)VehicleBlastScars.blast(mc.level,vehicle.position().add(6,1,0),5);
        if(STEP==6&&age==180){
            if(VehicleImpactMarks.stored()==0||VehicleBlastScars.rays()>6)throw new IllegalStateException("Missing/unbounded blast scars");
            System.out.println("M8_DESTRUCTION blast_marks="+VehicleImpactMarks.stored()+" rays="+VehicleBlastScars.rays()+" panel_smoke="+(VehicleImpactMarks.damageOrigin(vehicle)!=null));
        }
        if(STEP==3&&(age==140||age==175)){
            for(int i=0;i<3;i++)VehicleImpactMarks.receive(new io.github.mcheliceport.net.VehicleImpactPayload(
                    vehicle.getId(),vehicle.getUUID(),2.8F,.8F+i*.5F,(i-1)*.8F,-1,0,0,.3F,true,tick*3+i));
        }
        if(STEP==3&&(age==145||age==180)){
            int fragments=io.github.mcheliceport.client.particle.VehicleShrapnel.liveCount();
            if(fragments==0||fragments>32)throw new IllegalStateException("Missing/unbounded metal fragments");
            System.out.println("M8_DESTRUCTION metal_fragments="+fragments);
        }
        if(STEP==4&&(age==95||age==145||age==180)){
            String strengths=java.util.stream.IntStream.range(0,vehicle.getAcInfo().searchLights.size())
                    .mapToObj(i->Float.toString(VehicleLightDamage.strength(vehicle,i))).collect(java.util.stream.Collectors.joining(","));
            if(strengths.isEmpty())throw new IllegalStateException("No authored lamps");
            System.out.println("M8_DESTRUCTION lamps_age="+age+" strength="+strengths);
        }
        if(STEP==5&&age==180){
            int burnAge=VehicleAtmosphere.wreckAge(vehicle);
            if(burnAge<130||burnAge>150)throw new IllegalStateException("Wrong fresh wreck age: "+burnAge);
            System.out.println("M8_DESTRUCTION wreck_age="+burnAge);
        }
        if(age==200){
            System.out.println("M8_DESTRUCTION state_count="+VehicleAtmosphere.stateCount()+" emitted="+VehicleAtmosphere.emitted());
            if(STEP==2){
                int stains=io.github.mcheliceport.client.effect.FluidStains.count();
                if(stains==0||stains>128||io.github.mcheliceport.client.effect.FluidStains.rays()>4)throw new IllegalStateException("Missing/unbounded fluid stains");
                System.out.println("M8_DESTRUCTION fluid_stains="+stains);
            }
        }
    }
    private static void findGlass(){glassPoint=canopyPoint(true);framePoint=canopyPoint(false);}
    private static org.joml.Vector3f canopyPoint(boolean transparent){
        var models=io.github.mcheliceport.client.model.AircraftModels.peek(vehicle.getAcInfo());if(models==null)return null;
        org.joml.Vector3f bestPoint=null;
        var texture=net.minecraft.resources.Identifier.fromNamespaceAndPath("mcheli","textures/planes/"+NAMES[phase]+".png");
        float best=-Float.MAX_VALUE;
        for(var group:models.body().groups){
            if(!group.name().toLowerCase(java.util.Locale.ROOT).startsWith("$canopy"))continue;var d=group.data();
            for(int t=0;t+23<d.length;t+=24){
                var a=new org.joml.Vector3f(d[t],d[t+1],d[t+2]);
                var b=new org.joml.Vector3f(d[t+8],d[t+9],d[t+10]);var c=new org.joml.Vector3f(d[t+16],d[t+17],d[t+18]);
                var normal=new org.joml.Vector3f(b).sub(a).cross(new org.joml.Vector3f(c).sub(a));
                if(normal.lengthSquared()<1e-8||Math.abs(normal.normalize().x)<.3F)continue;
                var center=a.add(b).add(c).div(3);if(center.x<.05F)continue;
                var index=io.github.mcheliceport.client.model.VehicleImpactSurface.get(group);if(index==null)return null;
                var from=new org.joml.Vector3f(center).add(2,0,0);
                var hit=index.ray(d,from,new org.joml.Vector3f(-1,0,0),4);if(hit==null)continue;
                var contact=from.add(-hit.distance(),0,0);
                var uv=io.github.mcheliceport.client.model.VehicleImpactSurface.textureUv(d,hit.triangle(),contact);if(uv==null)continue;
                Integer alpha=io.github.mcheliceport.client.model.VehicleGlassAlpha.alpha(texture,uv.x,uv.y);
                if(alpha==null)return null;if((alpha<240)!=transparent)continue;
                float score=contact.x+contact.y*.1F;
                if(score>best){best=score;bestPoint=contact;}
            }
        }
        return bestPoint;
    }
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){
        if(STEP==0||vehicle==null)return;e.setYaw(STEP==5?49:STEP==8?102:STEP==7?(phase==2?58:122):STEP==4?147:STEP==2?64:(STEP==3||STEP==6)?90:94);e.setPitch(STEP==5?18:STEP==8?16:STEP==7?12:STEP==4?10:STEP==2?12:(STEP==3||STEP==6)?20:17);e.setRoll(0);
    }
    @SubscribeEvent public static void position(ViewportEvent.ComputeFov e){
        if(STEP==0||vehicle==null)return;
        if(STEP==8&&glassPoint!=null){
            e.getCamera().setPosition(vehicle.position().add(glassPoint.x,glassPoint.y+io.github.mcheliceport.content.aircraft.AircraftInfo.GLOBAL_Y_OFFSET,glassPoint.z).add(3.6,1.4,.8));return;
        }
        if(STEP==7){
            var models=io.github.mcheliceport.client.model.AircraftModels.peek(vehicle.getAcInfo());
            if(models!=null){
                LoosePanelLook.Spec best=null;
                for(var group:models.body().groups){var s=LoosePanelLook.spec(group);if(s!=null&&(best==null||s.rank()<best.rank()))best=s;}
                if(best!=null){var c=best.center();e.getCamera().setPosition(vehicle.position().add(c.x,c.y,c.z).add(8,2,phase==2?-5:5));return;}
            }
        }
        if(STEP==2){
            var origin=VehicleAtmosphere.origins(vehicle).getFirst();
            e.getCamera().setPosition(new Vec3(origin.x,vehicle.getY(),origin.z).add(5.2,1.2,-2.5));
        }else if(STEP==5){
            var origin=VehicleAtmosphere.wreckOrigins(vehicle).getFirst();
            e.getCamera().setPosition(origin.add(7,3,-6));
        }else e.getCamera().setPosition(vehicle.position().add(STEP==4?(phase==0?new Vec3(5,2,8):new Vec3(12,5,18)):(STEP==3||STEP==6)?new Vec3(6,2.3,0):new Vec3(15,5,1)));
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null)return;var mc=Minecraft.getInstance();String name=capture;capture=null;
        Screenshot.grab(mc.gameDirectory,"m8-destruction-"+STEP+"-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,
                m->System.out.println("M8_DESTRUCTION "+m.getString()));
    }
}

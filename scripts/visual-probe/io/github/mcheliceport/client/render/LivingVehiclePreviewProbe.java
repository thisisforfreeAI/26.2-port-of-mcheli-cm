package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.particle.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Development-only controlled states rendered by production effects. Never shipped. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class LivingVehiclePreviewProbe {
    private static final int STEP=Integer.getInteger("mcheli.livingPreview",0);
    private static final String[] NAMES=STEP==10?new String[]{"t-90","m1a2","t84"}:STEP==11?new String[]{"f-35a","f-15e","a-10"}:STEP==12?new String[]{"ah-64","mh-60l_dap","mh-6"}:STEP==2?new String[]{"ah-64","mh-60l_dap","mh-6"}
            :STEP==8?new String[]{"m1a2","t-90","f-35b"}:STEP==3?new String[]{"f-35a","f-15e","a-10"}:STEP==4?new String[]{"m1a2","ah-64","t-90"}:STEP==6?new String[]{"m1a2","growler","zodiac"}:new String[]{"m1a2","ah-64","f-35a"};
    private static int ticks,phase=-1,age,lastPops,captureTick=-1;
    private static float snowAt180;private static int lastBursts,lastStrikes;
    private static AircraftEntity vehicle;private static String capture;
    private static int damage(AircraftEntity e){return (int)(e.getMaxHP()*(STEP==1||STEP>=9?(STEP>=9&&age>(STEP>=10?380:780)?0:age>40?1:.3):STEP==2?(age<100?.05:.92):STEP==3?(age<130?.5:age<190?.96:0):STEP==8&&phase==2&&age>130?.96:.05));}
    private static void held(AircraftEntity e){e.xo=e.getX();e.yo=e.getY();e.zo=e.getZ();double movement=STEP==5&&age>185?(age-185)*.12:0;e.setPos(movement,STEP==6?-59.7:-60,STEP==6?12+age*.08:(STEP==7||STEP==8)?20+age*.08:20);e.setDeltaMovement(STEP==6||STEP==7||STEP==8?new Vec3(0,0,.08):STEP==5&&age>185?new Vec3(.12,0,0):Vec3.ZERO);}
    private static final class Tank extends TankEntity {
        Tank(Minecraft mc){super(ModEntityTypes.TANK.get(),mc.level);}
        @Override public void tick(){held(this);}
        @Override public double getCurrentThrottle(){return STEP==7||STEP==8?.75:super.getCurrentThrottle();}
        @Override public boolean canUseFuel(){return STEP==7||STEP==8||super.canUseFuel();}
        @Override public int getDamageTaken(){return damage(this);}
        @Override public boolean isDestroyed(){return (STEP==1||STEP>=9)&&age>40&&(STEP<9||age<=(STEP>=10?380:780))||STEP==3&&age>130&&age<190||super.isDestroyed();}
    }
    private static final class Heli extends HeliEntity {
        Heli(Minecraft mc){super(ModEntityTypes.HELICOPTER.get(),mc.level);}
        @Override public void tick(){held(this);prevRoll=getRoll();rotationRotor=25;for(var rotor:getRotors())rotor.update(25);}
        @Override public float getRoll(){return STEP==12&&age>40&&age<=(STEP>=10?380:780)?55:super.getRoll();}
        @Override public double getCurrentThrottle(){return STEP==7||STEP==8?.75:super.getCurrentThrottle();}
        @Override public boolean canUseFuel(){return STEP==7||STEP==8||super.canUseFuel();}
        @Override public int getDamageTaken(){return damage(this);}
        @Override public boolean isDestroyed(){return (STEP==1||STEP>=9)&&age>40&&(STEP<9||age<=(STEP>=10?380:780))||STEP==3&&age>130&&age<190||super.isDestroyed();}
    }
    private static final class Boat extends io.github.mcheliceport.entity.ship.ShipEntity {
        Boat(Minecraft mc){super(ModEntityTypes.SHIP.get(),mc.level);}
        @Override public void tick(){held(this);}
        @Override public double getCurrentThrottle(){return STEP==7||STEP==8?.75:super.getCurrentThrottle();}
        @Override public boolean canUseFuel(){return STEP==7||STEP==8||super.canUseFuel();}
        @Override public int getDamageTaken(){return damage(this);}
    }
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){held(this);}
        @Override public double getCurrentThrottle(){return STEP==7||STEP==8?.75:super.getCurrentThrottle();}
        @Override public boolean canUseFuel(){return STEP==7||STEP==8||super.canUseFuel();}
        @Override public int getDamageTaken(){return damage(this);}
        @Override public boolean isDestroyed(){return (STEP==1||STEP>=9)&&age>40&&(STEP<9||age<=(STEP>=10?380:780))||STEP==3&&age>130&&age<190||super.isDestroyed();}
    }
    private static void command(Minecraft mc,String...commands){for(var c:commands)mc.player.connection.sendCommand(c);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(STEP==0)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            command(mc,"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false");
            for(int y=-60;y<4;y+=7)command(mc,"fill -30 "+y+" -30 30 "+(y+6)+" 40 air");
            command(mc,"fill -30 -64 -30 30 -61 40 stone","tp @s 12 -53 8 55 18");
            if(STEP==7)command(mc,"fill -30 -61 -30 30 -61 60 coarse_dirt");
            if(STEP==6)command(mc,"fillbiome -16 -60 8 16 -48 36 plains","fill -16 -62 -16 16 -60 40 water");
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(true);ClientConfig.VEHICLE_SMALL_MOTION.set(true);ClientConfig.VEHICLE_SURFACE_BUILDUP.set(true);ClientConfig.VEHICLE_WATER_DETAILS.set(true);ClientConfig.VEHICLE_WEATHER.set(true);ClientConfig.GPU_MODEL_BUFFERS.set(!Boolean.getBoolean("mcheli.livingCpuPreview"));
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.ENGINE_HEAT_HAZE.set(STEP==4);ClientConfig.WEAPON_HEAT.set(true);
        }
        if(ticks<160)return;int next=(ticks-160)/(STEP>=10?450:STEP==9?900:220);age=(ticks-160)%(STEP>=10?450:STEP==9?900:220);
        if(next>=NAMES.length){mc.stop();return;}
        if(phase!=next){
            phase=next;if(vehicle!=null)vehicle.discard();mc.particleEngine.clearParticles();VehicleAtmosphere.clear();VehicleWeaponHeat.clear();VehicleImpactMarks.clear();LoosePanels.clear();PanelInteriors.clear();VehicleWeather.clear();VehicleWaterEffects.clear();VehicleSurfaceBuildup.clear();SmallVehicleMotion.clear();WreckAppearance.clear();
            vehicle=ContentRegistries.plane().get(NAMES[phase])!=null?new Plane(mc):ContentRegistries.heli().get(NAMES[phase])!=null?new Heli(mc):ContentRegistries.ship().get(NAMES[phase])!=null?new Boat(mc):new Tank(mc);
            vehicle.setUUID(new java.util.UUID(0,8+phase*8));vehicle.setId(975000+phase);vehicle.setAircraftTypeName(NAMES[phase]);vehicle.setTextureName(NAMES[phase]);vehicle.setPos(0,-60,20);
            var parts=vehicle.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(985000+phase*100+i);
            for(int i=0;i<vehicle.getWeaponNum();i++){var set=vehicle.getWeapon(i);if(set!=null)set.setReserveAmmo(200);}
            mc.level.addEntity(vehicle);lastPops=0;captureTick=-1;lastBursts=lastStrikes=0;
            if(STEP==5)command(mc,"weather clear","fillbiome -16 -60 8 16 -48 36 plains");
            System.out.println("M8_LIVING stage="+STEP+" vehicle="+NAMES[phase]);
        }
        if(age==95||age==145||age==180||STEP>=9&&age==(STEP>=10?350:700)||(STEP==3||STEP==5)&&age==205)capture=NAMES[phase]+"-age"+age;
        if(STEP>=10){
            var sequence=WreckSequenceEffects.get(vehicle);
            if(sequence!=null){
                if(sequence.bursts>lastBursts){lastBursts=sequence.bursts;capture=NAMES[phase]+"-burst"+lastBursts;}
                if(sequence.strikes>lastStrikes){lastStrikes=sequence.strikes;capture=NAMES[phase]+"-strike"+lastStrikes;}
                if(age==300){
                    if(!sequence.cover)throw new IllegalStateException("Missing engine cover blowout");
                    if(STEP==10&&(!sequence.track||DestructionTimeline.turret(vehicle.getAcInfo().name,sequence.seed,sequence.heavyAmmo)!=sequence.turret))throw new IllegalStateException("Missing/incorrect tank sequence");
                    if(STEP==11&&(!sequence.wing||!sequence.tail))throw new IllegalStateException("Missing wing/tail breakup");
                    if(STEP==12&&(sequence.strikes==0||WreckSequenceEffects.rays()>2||sequence.scattered.stream().filter(n->n.startsWith("rotor")).count()!=sequence.strikes))throw new IllegalStateException("Missing/unbounded rotor strikes");
                    if(sequence.ammo>0&&sequence.bursts==0)throw new IllegalStateException("Missing interior bursts");
                    System.out.println("M8_SEQUENCE parts="+sequence.parts+" turret="+sequence.turret+" strikes="+sequence.strikes+" bursts="+sequence.bursts);
                }
            }
        }
        if(STEP>=9&&age==(STEP>=10?420:850)){
            if(WreckAppearance.removed(vehicle)!=0||LoosePanels.pieces()!=0)throw new IllegalStateException("Repair retained wreck parts: removed="+WreckAppearance.removed(vehicle)+" debris="+LoosePanels.pieces()+" dead="+vehicle.isDestroyed());
            if(STEP>=10){var sequence=WreckSequenceEffects.get(vehicle);if(sequence!=null&&sequence.parts!=0)throw new IllegalStateException("Repair retained staged parts");}
            System.out.println("M8_WRECK repair_restored=true");
        }
        if(STEP==9&&age==180){
            if(WreckAppearance.removed(vehicle)==0)throw new IllegalStateException("Missing wreck breakup");
            System.out.println("M8_WRECK removed="+WreckAppearance.removed(vehicle)+" debris="+LoosePanels.pieces());
        }
        if(STEP==8&&age==180){
            if(SmallVehicleMotion.moved(vehicle)==0)throw new IllegalStateException("Missing small part motion");
            if(phase==2&&(!LoosePanels.detached(vehicle)||PanelInteriors.cables()==0))throw new IllegalStateException("Missing exposed cables");
            System.out.println("M8_LIVING moving_parts="+SmallVehicleMotion.moved(vehicle)+" cable_segments="+PanelInteriors.cables());
        }
        if(STEP==7&&age==180){
            if(VehicleSurfaceBuildup.dirt(vehicle)<.3F||VehicleSurfaceBuildup.soot(vehicle)<.2F||VehicleSurfaceBuildup.probes()>4)throw new IllegalStateException("Missing/unbounded surface buildup");
            System.out.println("M8_LIVING dirt="+VehicleSurfaceBuildup.dirt(vehicle)+" soot="+VehicleSurfaceBuildup.soot(vehicle)+" probes="+VehicleSurfaceBuildup.probes());
        }
        if(STEP==6&&age==180){
            if(VehicleWaterEffects.wakes()==0||VehicleWaterEffects.wakes()>64||VehicleWaterEffects.reads()>12)throw new IllegalStateException("Missing/unbounded wake");
            System.out.println("M8_LIVING water_wakes="+VehicleWaterEffects.wakes()+" reads="+VehicleWaterEffects.reads());
            VehicleWaterEffects.splash(mc.level,new Vec3(vehicle.getX()+2,-58.99,vehicle.getZ()-2),.6);
        }
        if(STEP==5){
            if(age==40)command(mc,"weather rain");
            if(age==105)command(mc,"fillbiome -16 -60 8 16 -48 36 snowy_plains");
            if(age==95&&VehicleWeather.wet(vehicle)<.2F)throw new IllegalStateException("Missing wet paint");
            if(age==180){snowAt180=VehicleWeather.snow(vehicle);if(snowAt180<.4F||VehicleWeather.samples()>8)throw new IllegalStateException("Missing/unbounded snow");}
            if(age==205){if(VehicleWeather.snow(vehicle)>=snowAt180)throw new IllegalStateException("Snow did not shed");System.out.println("M8_LIVING wet="+VehicleWeather.wet(vehicle)+" parked_snow="+snowAt180+" moving_snow="+VehicleWeather.snow(vehicle));}
            if(age==209)command(mc,"weather clear","fillbiome -16 -60 8 16 -48 36 plains");
        }
        if(STEP==4){
            if(age>=50&&age<130&&age%3==0)for(int i=0;i<vehicle.getWeaponNum();i++){
                var set=vehicle.getWeapon(i);if(set==null||set.getFirstWeapon()==null)continue;
                var weapon=set.getFirstWeapon();var info=weapon.getInfo();
                if(info!=null&&!info.useHBM&&(info.type.equals("machinegun1")||info.type.equals("machinegun2")))
                    {io.github.mcheliceport.weapon.WeaponClientHooks.weaponHeat(vehicle,i,info,vehicle.position().add(weapon.getShotPos(vehicle)),vehicle.getYaw(),vehicle.getPitch());break;}
            }
            if(age==145){if(VehicleWeaponHeat.hottest(vehicle)<.7F)throw new IllegalStateException("Missing weapon heat");System.out.println("M8_LIVING weapon_heat="+VehicleWeaponHeat.hottest(vehicle));}
        }
        if(STEP==3&&(age==145||age==180||age==205)){
            if(age<190&&(!LoosePanels.detached(vehicle)||PanelInteriors.renderedVertices()==0))throw new IllegalStateException("Missing exposed interior");
            if(age==205&&LoosePanels.detached(vehicle))throw new IllegalStateException("Repair retained interior");
            System.out.println("M8_LIVING interior_age="+age+" detached="+LoosePanels.detached(vehicle)+" interior_vertices="+PanelInteriors.renderedVertices());
        }
        if(STEP==2&&(age==145||age==180)){
            if(!(vehicle instanceof HeliEntity heli)||RotorDamage.applied(heli)==0)throw new IllegalStateException("Missing rotor variant");
            System.out.println("M8_LIVING damaged_rotors="+RotorDamage.applied((HeliEntity)vehicle));
        }
        if(STEP==1){
            int pops=CookoffEffects.events();if(pops>lastPops){lastPops=pops;captureTick=ticks+1;}
            if(ticks==captureTick)capture=NAMES[phase]+"-pop"+lastPops;
            if(age==200){
                if(pops<2||pops>4||CookoffEffects.sounds()!=pops)throw new IllegalStateException("Missing/unbounded cook-off");
                if(!mc.level.getBlockState(new net.minecraft.core.BlockPos(0,-61,20)).is(net.minecraft.world.level.block.Blocks.STONE))throw new IllegalStateException("Cook-off modified terrain");
                System.out.println("M8_LIVING cookoffs="+pops+" sound_requests="+CookoffEffects.sounds()+" terrain_unchanged=true");
            }
        }
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov event){
        if(STEP==0||vehicle==null)return;
        if(STEP==12){event.getCamera().setPosition(vehicle.position().add(-8,6,5));return;}
        if(STEP==6){event.getCamera().setPosition(vehicle.position().add(8,5,-8));return;}
        if(STEP==4){event.getCamera().setPosition(VehicleWeaponHeat.hotPoint(vehicle).add(3.5,1.2,1));return;}
        Vec3 at=STEP==1?VehicleAtmosphere.wreckOrigins(vehicle).getFirst():vehicle.position().add(0,3,0);
        if(STEP==3||STEP==8&&phase==2){
            var models=io.github.mcheliceport.client.model.AircraftModels.peek(vehicle.getAcInfo());
            if(models!=null){LoosePanelLook.Spec best=null;
                for(var group:models.body().groups){var spec=LoosePanelLook.spec(group);if(spec!=null&&(best==null||spec.rank()<best.rank()))best=spec;}
                if(best!=null){var c=best.center();at=vehicle.position().add(c.x,c.y,c.z);}
            }
        }
        event.getCamera().setPosition(at.add(STEP==1?new Vec3(7,3,-6):STEP==2?new Vec3(10,10,7):STEP==3||STEP==8&&phase==2?new Vec3(4.5,2.5,3.5):new Vec3(8,3,5)));
    }
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){
        if(STEP==0||vehicle==null)return;event.setYaw(STEP==12?-122:STEP==6?45:STEP==4?106:STEP==1?49:STEP==2?125:STEP==3||STEP==8&&phase==2?128:122);event.setPitch(STEP==6?24:STEP==4?18:STEP==1?18:STEP==2?39:STEP==3||STEP==8&&phase==2?21:17);event.setRoll(0);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null)return;var mc=Minecraft.getInstance();String name=capture;capture=null;
        Screenshot.grab(mc.gameDirectory,"m8-living-"+STEP+"-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_LIVING "+m.getString()));
    }
}

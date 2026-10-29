package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.model.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.effect.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Unshipped diagnostic scenes; range screenshots separately exercise real weapon impacts. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class BlastRefinementProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeBlastRefinement");
    private static final int START=Integer.getInteger("mcheli.probeBlastStart",0),END=Integer.getInteger("mcheli.probeBlastEnd",10);
    private static final String[] NAMES={"m1a2","ah-64","f-35a","rocket","bomb","penetrator","far64","far128","detail-transition","salvo","deep-center","airburst"};
    private static int ticks,phase=-1,age,peak,peakRings;private static String capture;
    private static AircraftEntity actor;
    private static List<net.minecraft.core.BlockPos> footprint=List.of();
    private static void hold(AircraftEntity ac){ac.xo=ac.getX();ac.yo=ac.getY();ac.zo=ac.getZ();ac.setDeltaMovement(Vec3.ZERO);}
    private static final class Tank extends TankEntity {Tank(Minecraft mc){super(ModEntityTypes.TANK.get(),mc.level);}@Override public void tick(){hold(this);}}
    private static final class Heli extends HeliEntity {Heli(Minecraft mc){super(ModEntityTypes.HELICOPTER.get(),mc.level);}@Override public void tick(){hold(this);}}
    private static final class Plane extends PlaneEntity {Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}@Override public void tick(){hold(this);}}

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) throws ReflectiveOperationException {
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false","tp @s 12 -52 28 130 18","fill -24 -60 -4 24 -48 44 air","fill -24 -47 -4 24 -35 44 air","fill -24 -61 -4 24 -61 44 dirt"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.fov().set(60);mc.options.cloudStatus().set(CloudStatus.OFF);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false);ClientConfig.MODERN_EXPLOSIONS.set(false);ClientConfig.EFFECT_TARGET_FPS.set(60);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);ClientConfig.VEHICLE_SURFACE_SHADE.set(true);
            ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.ENGINE_HEAT_HAZE.set(false);ClientConfig.VEHICLE_DAMAGE_DETAILS.set(false);
            var positions=new ArrayList<net.minecraft.core.BlockPos>();
            for(int x=-14;x<=14;x++)for(int z=-14;z<=14;z++)if(x*x+z*z<=(x>0?196:64))positions.add(new net.minecraft.core.BlockPos(x,-61,20+z));
            footprint=List.copyOf(positions);
        }
        if(ticks<160)return;int next=START+(ticks-160)/200;age=(ticks-160)%200;
        if(next>=Math.min(END,NAMES.length)){System.out.println("M8_BLAST_REFINEMENT all_passed=true start="+START+" end="+END);mc.stop();return;}
        if(next!=phase){
            phase=next;if(actor!=null)actor.discard();actor=null;mc.particleEngine.clearParticles();BlastGroundEffects.clear();peak=peakRings=0;
            ClientConfig.VEHICLE_SURFACE_SHADE.set(true);
            if(phase<3){actor=phase==0?new Tank(mc):phase==1?new Heli(mc):new Plane(mc);
                actor.setId(-991000-phase);actor.setAircraftTypeName(NAMES[phase]);actor.setTextureName(NAMES[phase]);actor.setPos(0,-60,20);
                var parts=actor.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(-992000-phase*100-i);mc.level.addEntity(actor);}
        }
        if(phase<3){
            if(age==65){var model=AircraftModels.get(actor.getAcInfo().getDirectoryName(),actor.getAcInfo());
                if(model==null)throw new IllegalStateException("Model missing");
                int vertices=0,materials=0,glassVertices=0;
                var creaseBake=VehicleSurfaceShade.class.getDeclaredMethod("bake",float[].class);creaseBake.setAccessible(true);
                for(var g:model.body().groups){var shades=VehicleSurfaceShade.get(g);if(shades!=null)vertices+=shades.length;
                    if(VehicleSurfaceFinish.material(g.name())!=VehicleSurfaceFinish.Material.PAINT)materials++;
                    if(g.name().toLowerCase(java.util.Locale.ROOT).startsWith("$canopy") && shades!=null){
                        float[] creases=(float[])creaseBake.invoke(null,(Object)g.data());
                        for(int i=0;i<creases.length;i++){int at=i*8;int ordinary=VehicleSurfaceShade.tint(VehicleSurfaceShade.BASE_COLOR,
                            VehicleSurfaceFinish.shade(VehicleSurfaceFinish.Material.PAINT,creases[i],g.data()[at+5],g.data()[at+6],g.data()[at+7]));
                            if(shades[i]!=ordinary)glassVertices++;}
                    }}
                if(vertices==0)throw new IllegalStateException("Missing baked material shades");
                System.out.println("M8_BLAST_REFINEMENT vehicle="+NAMES[phase]+" baked_vertices="+vertices+" explicit_material_groups="+materials+" confirmed_glass_vertices="+glassVertices);capture=NAMES[phase]+"-shaded";}
            if(age==80){ClientConfig.VEHICLE_SURFACE_SHADE.set(false);capture=NAMES[phase]+"-unshaded";}
            return;
        }
        if(age==20)blast(mc,0,phase==3?4:12,phase==3?BlastStyle.BLAST:phase==5?BlastStyle.PENETRATOR:BlastStyle.HEAVY);
        if(phase==9&&age>=25&&age<=50&&age%5==0)for(int i=0;i<6;i++)blast(mc,(i-2.5)*3,12,BlastStyle.HEAVY);
        int owned=owned(mc),clouds=clouds(mc);peak=Math.max(peak,owned);peakRings=Math.max(peakRings,BlastGroundEffects.ringEmitted());
        if(owned>EffectTuning.smokeLimit(ClientConfig.EffectBudget.BALANCED)+EffectTuning.flashLimit(ClientConfig.EffectBudget.BALANCED))throw new IllegalStateException("Particle limit exceeded: "+owned);
        if(BlastGroundEffects.surfaceReads()>96||BlastGroundEffects.trackedFragments()>36)throw new IllegalStateException("Ground budget exceeded");
        if(age==25){
            if(phase==10 && (peakRings==0 || particles(mc).stream().noneMatch(p->p instanceof BlastDirtJetParticle)))
                throw new IllegalStateException("Missing deep-center rim dust/jets");
            if(phase==11 && peakRings!=0)throw new IllegalStateException("Airburst invented a crater rim");
            if(clouds==0)throw new IllegalStateException("Primary silhouette missing at "+NAMES[phase]);
            System.out.println("M8_BLAST_REFINEMENT scene="+NAMES[phase]+" primary_clouds="+clouds+" rings="+peakRings);}
        if(Set.of(21,23,26,32,44,70).contains(age))capture=NAMES[phase]+"-age"+(age-20);
        if(age==195){if(owned!=0)throw new IllegalStateException("Particles did not drain: "+owned);
            System.out.println("M8_BLAST_REFINEMENT passed=true scene="+NAMES[phase]+" peak_particles="+peak+" peak_rings="+peakRings+" drained=true");}
    }
    private static void blast(Minecraft mc,double x,float power,BlastStyle style){
        ClientEffects.INSTANCE.explosionEffects(mc.level,x,phase>=10?-47:-59.5,20,power,false,phase==3||phase==11?List.of():footprint,style,BlastDirection.from(new Vec3(1,-.4,.1)));
    }
    private static List<Object> particles(Minecraft mc) throws ReflectiveOperationException {
        var f=net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");f.setAccessible(true);
        var p=net.minecraft.client.particle.ParticleGroup.class.getDeclaredField("particles");p.setAccessible(true);var result=new ArrayList<Object>();
        for(var g:((Map<?,?>)f.get(mc.particleEngine)).values())result.addAll((Collection<?>)p.get(g));return result;
    }
    private static int owned(Minecraft mc) throws ReflectiveOperationException {return (int)particles(mc).stream().filter(p->p instanceof McheliParticle).count();}
    private static int clouds(Minecraft mc) throws ReflectiveOperationException {return (int)particles(mc).stream().filter(p->p instanceof BlastCloudParticle).count();}
    private static double distance(){return phase==6?64:phase==7?128:phase==8?24+Math.clamp(age-23,0,15)*3:25;}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&phase>=0)e.getCamera().setPosition(phase<3?new Vec3(13,-53,29):new Vec3(0,-54,20+distance()));}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(ENABLED&&phase>=0){e.setYaw(phase<3?125:180);e.setPitch(phase<3?12:(float)Math.toDegrees(Math.atan2(6,distance())));e.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-refinement-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_BLAST_REFINEMENT "+m.getString()));}}
}

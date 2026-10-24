package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.client.model.VehicleDetailShade;
import io.github.mcheliceport.client.model.VehicleSurfaceShade;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.effect.BlastDirection;
import io.github.mcheliceport.effect.BlastStyle;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Content-driven visual comparisons in the disposable M6Smoke world, never shipped. */
@EventBusSubscriber(modid="mcheli_ce_port", value=Dist.CLIENT)
public final class CompletionProbe {
    private static final String[] NAMES={"m1a2","ah-64","ch47","f-35a","m230","hydra70","agm114","agm158","mk82","gbu57","s-8dm","heavy12"};
    private static final BlastStyle[] STYLES={BlastStyle.CANNON,BlastStyle.BLAST,BlastStyle.PENETRATOR,
            BlastStyle.BLAST,BlastStyle.HEAVY,BlastStyle.PENETRATOR,BlastStyle.INCENDIARY,BlastStyle.HEAVY};
    private static final boolean CRATER = Boolean.getBoolean("mcheli.probeCraterScale");
    private static List<net.minecraft.core.BlockPos> affected = List.of();
    private static int tick, phase=-1, age;
    private static AircraftEntity vehicle;
    private static String capture;
    private static final class Tank extends TankEntity {
        Tank(Minecraft mc){super(ModEntityTypes.TANK.get(),mc.level);}
        @Override public void tick(){xo=getX();yo=getY();zo=getZ();}
    }
    private static final class Heli extends HeliEntity {
        Heli(Minecraft mc){super(ModEntityTypes.HELICOPTER.get(),mc.level);}
        @Override public void tick(){xo=getX();yo=getY();zo=getZ();}
    }
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){xo=getX();yo=getY();zo=getZ();}
    }
    private static void commands(Minecraft mc,String...list){for(var s:list)mc.player.connection.sendCommand(s);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("mcheli.probeCompletion"))return;
        var mc=Minecraft.getInstance(); if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++tick==40){
            commands(mc,"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear",
                    "gamerule minecraft:advance_time false","fill -16 -60 4 16 -40 40 air","fill -16 -61 4 16 -61 40 stone");
            mc.getWindow().setWindowed(1920,1080);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_TARGET_FPS.set(60);ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false);
            ClientConfig.VEHICLE_SURFACE_SHADE.set(true);ClientConfig.ENGINE_HEAT_HAZE.set(false);
            ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.VEHICLE_DAMAGE_DETAILS.set(false);
        }
        if(tick<160)return;phase=(tick-160)/160 + (Boolean.getBoolean("mcheli.probeDepthOnly") ? NAMES.length-1 : Boolean.getBoolean("mcheli.probePunch") || CRATER ? 4 : 0);age=(tick-160)%160;
        if(phase>=NAMES.length){mc.stop();return;}
        if(age==0){
            if(vehicle!=null){vehicle.discard();vehicle=null;}
            mc.particleEngine.clearParticles();BlastGroundEffects.clear();
            if(phase<4){
                vehicle=phase==0?new Tank(mc):phase==3?new Plane(mc):new Heli(mc);
                vehicle.setId(-970000-phase);vehicle.setAircraftTypeName(NAMES[phase]);vehicle.setTextureName(NAMES[phase]);
                vehicle.setPos(0,-60,phase==2?27:20);
                var parts=vehicle.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(-980000-phase*100-i);
                mc.level.addEntity(vehicle);
                commands(mc,phase==2?"tp @s 12 -51 6 31 20":phase==3?"tp @s 14 -50 -5 29 19":"tp @s 10 -53 7 38 20");
            }else commands(mc,"tp @s 0 -54 1 0 14");
            if(CRATER) commands(mc,"fill -32 -63 0 32 -61 60 "+(NAMES[phase].equals("heavy12")?"dirt":"stone"),"fill -32 -60 0 32 -55 60 air");
            System.out.println("M8_COMPLETE phase="+NAMES[phase]);
        }
        if(phase<4&&age==70){
            var model=AircraftModels.get(vehicle.getAcInfo().getDirectoryName(),vehicle.getAcInfo());
            if(model==null)throw new IllegalStateException("Model not ready: "+NAMES[phase]);
            var zones=VehicleDetailShade.zones(vehicle.getAcInfo());int hits=0;
            for(var group:model.body().body())for(int i=0;i+8<=group.data().length;i+=8)
                if(VehicleDetailShade.factor(zones,group.data()[i],group.data()[i+1],group.data()[i+2])<.9999F)hits++;
            if(zones.isEmpty()||hits==0)throw new IllegalStateException("Detail anchors miss hull: "+NAMES[phase]);
            System.out.println("M8_COMPLETE zones="+zones.size()+" detail_vertices="+hits);
            capture=NAMES[phase]+"-detail";
        }
        if(phase<4&&age==85){
            var model=AircraftModels.get(vehicle.getAcInfo().getDirectoryName(),vehicle.getAcInfo());
            VehicleSurfaceShade.prepare(model.body());capture=NAMES[phase]+"-geometry";
        }
        if(phase<4&&age==95){
            var model=AircraftModels.get(vehicle.getAcInfo().getDirectoryName(),vehicle.getAcInfo());
            VehicleSurfaceShade.prepare(model.body(),VehicleDetailShade.zones(vehicle.getAcInfo()));
        }
        if(CRATER && age==20) {
            int radius = new int[]{0,4,6,12,8,18,10,16}[phase-4];
            var blocks = new java.util.ArrayList<net.minecraft.core.BlockPos>();
            for(int z=-radius;z<=radius;z++) {
                int half=(int)Math.sqrt(radius*radius-z*z);
                if(radius==0)break;
                commands(mc,"fill "+(-half)+" -62 "+(19+z)+" "+half+" -61 "+(19+z)+" air");
                for(int x=-half;x<=half;x++)for(int y=-62;y<=-61;y++)blocks.add(new net.minecraft.core.BlockPos(x,y,19+z));
            }
            affected=List.copyOf(blocks);
            System.out.println("M8_CRATER weapon="+NAMES[phase]+" fixture_radius="+radius+" reported="+ExplosionCraterScale.footprint(0,19,affected));
        }
        if(phase>=4&&age==40){
            var weapon=ContentRegistries.weapon().get(NAMES[phase].equals("heavy12")?"mk82":NAMES[phase]);
            if(weapon==null)throw new IllegalStateException("Missing weapon "+NAMES[phase]);
            float power=NAMES[phase].equals("heavy12")?12:weapon.explosion;
            var style=BlastStyle.forWeapon(weapon,weapon.flaming);
            if(style!=STYLES[phase-4])throw new IllegalStateException("Wrong family "+NAMES[phase]+":"+style);
            System.out.println("M8_COMPLETE weapon="+NAMES[phase]+" power="+power+" family="+style
                    +" lobes="+ExplosionSequence.lobes(power,style,ClientConfig.EffectBudget.BALANCED,0)
                    +" fire_ticks="+ExplosionSequence.fireTicks(power,style));
            io.github.mcheliceport.client.sound.ExplosionAudio.play(new io.github.mcheliceport.net.ExplosionSoundPayload(0,-59.5,19,power));
            ClientEffects.INSTANCE.explosionEffects(mc.level,0,-59.5,19,power,false,CRATER?affected:List.of(),style,
                    BlastDirection.from(new Vec3(.75,-.45,1)));
        }
        if(phase>=4&&(age==41||age==43||age==46||age==60||age==90))capture=NAMES[phase]+"-age"+(age-40);
        if(Boolean.getBoolean("mcheli.probePunch") && phase>=4) {
            if(age>=110 && age<=125) for(int i=0;i<80;i++) io.github.mcheliceport.client.sound.ExplosionAudio.play(
                    new io.github.mcheliceport.net.ExplosionSoundPayload(0,-59.5,19,10));
            if(phase==NAMES.length-1) {
                if(age==130) ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.CLASSIC);
                if(age>=130 && age<=137) for(int i=0;i<80;i++) io.github.mcheliceport.client.sound.ExplosionAudio.play(
                        new io.github.mcheliceport.net.ExplosionSoundPayload(0,-59.5,19,10));
                if(age==140) ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            }
            try {
                var field=io.github.mcheliceport.client.sound.ExplosionAudio.class.getDeclaredField("pending");
                field.setAccessible(true);int queued=((io.github.mcheliceport.client.sound.ExplosionAudioQueue<?>)field.get(null)).size();
                if(queued>32 || age==150 && queued!=0) throw new IllegalStateException("Audio queue did not stay bounded/drain: "+queued);
                if(age==150)System.out.println("M8_PUNCH audio_drained="+NAMES[phase]);
            } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        if(CRATER && NAMES[phase].equals("heavy12")) {
            if(age==60)capture="heavy12-side";
            if(age==90)capture="heavy12-overhead";
        }
        if(CRATER && NAMES[phase].equals("heavy12") && age==46) {
            try {
                var groupsField=net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");groupsField.setAccessible(true);
                var groups=(java.util.Map<?,?>)groupsField.get(mc.particleEngine);
                var particlesField=net.minecraft.client.particle.ParticleGroup.class.getDeclaredField("particles");particlesField.setAccessible(true);
                var cardsField=BlastCloudParticle.class.getDeclaredField("cards");cardsField.setAccessible(true);
                var columnField=BlastDirtJetParticle.class.getDeclaredField("column");columnField.setAccessible(true);
                int crossed=0,layered=0,columns=0,wedges=0;
                for(Object group:groups.values())for(Object particle:(java.util.Queue<?>)particlesField.get(group)) {
                    if(particle instanceof BlastCloudParticle) {int c=cardsField.getInt(particle);if(c==3)crossed++;if(c==2)layered++;}
                    if(particle instanceof BlastDirtJetParticle && columnField.getBoolean(particle))columns++;
                    if(particle instanceof BlastRingParticle p && p.getGroup()==BlastDustWedges.TYPE)wedges++;
                }
                if(crossed==0 || layered==0 || columns==0 || wedges==0)
                    throw new IllegalStateException("Missing depth features: "+crossed+","+layered+","+columns+","+wedges);
                System.out.println("M8_DEPTH crossed="+crossed+" layered="+layered+" columns="+columns+" wedges="+wedges);
            } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        if(CRATER && NAMES[phase].equals("heavy12") && age==80) {
            try {
                var field=BlastGroundEffects.class.getDeclaredField("fragments");field.setAccessible(true);
                var bounced=BlastFragmentParticle.class.getDeclaredField("bounced");bounced.setAccessible(true);
                var settled=BlastFragmentParticle.class.getDeclaredField("settled");settled.setAccessible(true);
                int b=0,s=0;
                for(Object ref:(java.util.Deque<?>)field.get(null)) {
                    var particle=((java.lang.ref.WeakReference<?>)ref).get();
                    if(particle!=null) {if(bounced.getBoolean(particle))b++;if(settled.getBoolean(particle))s++;}
                }
                if(b==0 || s==0)throw new IllegalStateException("Large fragments did not bounce/settle");
                System.out.println("M8_DEPTH bounced="+b+" settled="+s);
            } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        if(BlastGroundEffects.trackedFragments()>BlastGroundLook.fragmentLiveCap(ClientConfig.EffectBudget.BALANCED)||BlastGroundEffects.surfaceReads()>96)
            throw new IllegalStateException("Weapon comparison exceeded ground budgets");
    }
    private static int depthView() {
        return CRATER && phase>=0 && phase<NAMES.length && NAMES[phase].equals("heavy12")
                ? age>=47 && age<70 ? 1 : age>=70 && age<100 ? 2 : 0 : 0;
    }
    @SubscribeEvent public static void angles(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles e) {
        int view=depthView();if(view==0)return;
        e.setYaw(view==1?90:0);e.setPitch(view==1?22:90);e.setRoll(0);
    }
    @SubscribeEvent public static void position(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov e) {
        int view=depthView();if(view==0)return;
        e.getCamera().setPosition(view==1?new Vec3(21,-48,19):new Vec3(0,-28,19));
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(!Boolean.getBoolean("mcheli.probeCompletion")||capture==null)return;
        var mc=Minecraft.getInstance();String name=capture;capture=null;
        if(name.equals("heavy12-side") || name.equals("heavy12-overhead")) {
            boolean side=name.endsWith("side");var camera=mc.gameRenderer.mainCamera();
            Vec3 expected=side?new Vec3(21,-48,19):new Vec3(0,-28,19);
            if(camera.position().distanceToSqr(expected)>.01 || Math.abs(camera.xRot()-(side?22:90))>.1
                    || Math.abs(net.minecraft.util.Mth.wrapDegrees(camera.yRot()-(side?90:0)))>.1)
                throw new IllegalStateException("Invalid depth camera: "+name+" "+camera.position());
            System.out.println("M8_DEPTH view="+name+" position="+camera.position()+" pitch="+camera.xRot()+" yaw="+camera.yRot());
        }
        Screenshot.grab(mc.gameDirectory,"m8-complete-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,
                message->System.out.println("M8_COMPLETE "+message.getString()));
    }
}

package io.github.mcheliceport.client.particle;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.config.ClientConfig;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.neoforged.fml.ModList;

/** Optional, client-only use of NTM Next's public particles; no copied code/assets or server effects. */
@net.neoforged.fml.common.EventBusSubscriber(modid="mcheli_ce_port", value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class NtmExplosionBridge {
    public enum Availability { AVAILABLE, NOT_INSTALLED, INCOMPATIBLE }
    private record Tracked(WeakReference<Particle> particle, int faces, long expires) {}
    private record Api(Constructor<?> small, Constructor<?> cloud, Constructor<?> wave,
            Method scale, Method life, Method noClip, Method spawnCloud, Method setupWave) {
        static Api load() throws ReflectiveOperationException {
            Class<?> small = Class.forName("com.hbm.particle.ParticleExplosionSmall");
            Class<?> cloud = Class.forName("com.hbm.particle.ParticleRocketFlame");
            Class<?> wave = Class.forName("com.hbm.particle.ParticleMukeWave");
            for (Class<?> type : new Class<?>[]{small, cloud, wave})
                if (!Particle.class.isAssignableFrom(type)) throw new ClassCastException(type.getName());
            return new Api(small.getConstructor(ClientLevel.class,double.class,double.class,double.class,float.class,float.class),
                    cloud.getConstructor(ClientLevel.class,double.class,double.class,double.class),
                    wave.getConstructor(ClientLevel.class,double.class,double.class,double.class),
                    cloud.getMethod("setScale",float.class),cloud.getMethod("setMaxAge",int.class),cloud.getMethod("noClip"),
                    Class.forName("com.hbm.client.ClientEffects").getMethod("spawnRocketFlame",net.minecraft.world.level.Level.class,
                            double.class,double.class,double.class,float.class,double.class,double.class,double.class,int.class),
                    wave.getMethod("setup",float.class,int.class));
        }
    }
    private static boolean checked;
    private static Api api;
    private static Availability availability = Availability.NOT_INSTALLED;
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private static final ArrayDeque<Tracked> active = new ArrayDeque<>();
    private static long tick = Long.MIN_VALUE;
    private static int emittedFaces;
    private NtmExplosionBridge() {}

    public static Availability availability() {
        if (!checked) {
            checked = true;
            if (ModList.get().isLoaded("hbm")) {
                try { api = Api.load(); availability = Availability.AVAILABLE; }
                catch (ReflectiveOperationException | LinkageError | RuntimeException e) { unavailable(e); }
            }
        }
        return availability;
    }
    private static void unavailable(Throwable error) {
        api = null; availability = Availability.INCOMPATIBLE;
        LogUtils.getLogger().warn("[McHeli/NTM Next] Native explosion particles unavailable; using Current M8", error);
    }
    private static int prepare(ClientLevel level) {
        if (world.get() != level) {
            active.clear(); world = new WeakReference<>(level); tick = Long.MIN_VALUE;
        }
        if (tick != level.getGameTime()) {tick = level.getGameTime(); emittedFaces = 0;}
        active.removeIf(t -> {
            if(t.expires<=level.getGameTime())return true;
            if(t.particle==null)return false; // Native instanced clouds are owned/ticked by NTM.
            var p=t.particle.get();return p==null || !p.isAlive();
        });
        int faces = 0;
        for (var tracked : active) faces += tracked.faces;
        return faces;
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var level=Minecraft.getInstance().level;
        if(level==null) {active.clear();world.clear();tick=Long.MIN_VALUE;emittedFaces=0;}
        else if(!active.isEmpty()) prepare(level);
    }

    /** True means handled, including load shedding; false permits the existing M8 fallback. */
    static boolean emit(ClientLevel level,double x,double y,double z,float power,float footprint) {
        if (availability() != Availability.AVAILABLE) return false;
        if (!Float.isFinite(power) || power <= 0) return true;
        var mc = Minecraft.getInstance();
        if (mc.level != level || mc.player == null || !EffectAdmission.inRange(level,x,y,z,0)) return true;
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        int pressure = EffectPerformance.pressure();
        var profile = NtmExplosionLook.profile(power, footprint);
        int live = prepare(level);
        int remaining = Math.min(NtmExplosionLook.liveFaces(quality)-live,
                NtmExplosionLook.tickFaces(quality,pressure)-emittedFaces);
        int count = Math.min(remaining/profile.faces(), NtmExplosionLook.clouds(profile,quality,pressure,
                mc.gameRenderer.mainCamera().position().distanceToSqr(x,y,z), mc.options.particles().get().ordinal()));
        if (count <= 0) return true;
        // NTM's accelerated cloud path crashed Mesa Vulkan in native validation. Keep the
        // proven NTM particle group on Vulkan; OpenGL can use its normal accelerated helper.
        boolean helper=NtmExplosionLook.acceleratedBackend(mc.getWindow().backend().getName());
        var particles = new ArrayList<Tracked>(count+1);
        var batch = new ArrayList<Particle>(count+1);
        int submitted=0;
        try {
            if(profile.small() || !helper)for(int i=0;i<count;i++) {
                Particle p;
                if(profile.small())p=(Particle)api.small.newInstance(level,x,y,z,profile.scale(),profile.speed());
                else {
                    var random=level.getRandom();p=(Particle)api.cloud.newInstance(level,x,y,z);
                    api.scale.invoke(p,profile.scale());api.life.invoke(p,70+random.nextInt(20));api.noClip.invoke(p);
                    p.setParticleSpeed(random.nextGaussian()*0.5*profile.speed(),random.nextDouble()*3*profile.speed(),
                            random.nextGaussian()*0.5*profile.speed());
                }
                batch.add(p);particles.add(new Tracked(new WeakReference<>(p),profile.faces(),level.getGameTime()+profile.life()+1));
            }
            int cost = count * profile.faces();
            if (!profile.small() && pressure < 2 && quality != ClientConfig.EffectBudget.LOW && cost < remaining) {
                Particle wave = (Particle)api.wave.newInstance(level,x,y+2,z);
                api.setupWave.invoke(wave,profile.wave(),(int)(25F*profile.wave()/45F));
                batch.add(wave); particles.add(new Tracked(new WeakReference<>(wave),1,level.getGameTime()+38)); cost++;
            }
            if(!profile.small() && helper) {
                var random=level.getRandom();
                for(int i=0;i<count;i++) {
                    int life=70+random.nextInt(20);
                    api.spawnCloud.invoke(null,level,x,y,z,profile.scale(),random.nextGaussian()*0.5*profile.speed(),
                            random.nextDouble()*3*profile.speed(),random.nextGaussian()*0.5*profile.speed(),life);
                    active.add(new Tracked(null,10,level.getGameTime()+life+1));
                    emittedFaces+=10;submitted++;
                }
                cost-=count*10;
            }
            for (Particle p : batch) mc.particleEngine.add(p);
            active.addAll(particles); emittedFaces += cost;
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            unavailable(e);
            // A helper failure after some submissions must not double the impact with a fallback.
            return submitted>0;
        }
    }
}

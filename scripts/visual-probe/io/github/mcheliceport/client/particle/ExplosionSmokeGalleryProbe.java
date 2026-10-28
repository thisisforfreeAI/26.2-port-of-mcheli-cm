package io.github.mcheliceport.client.particle;

import com.google.gson.GsonBuilder;
import io.github.mcheliceport.client.effect.BlastMarks;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.effect.BlastDirection;
import io.github.mcheliceport.effect.BlastStyle;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Named, content-driven VFX previews, not combat captures. Opt-in and never shipped. */
@EventBusSubscriber(modid="mcheli_ce_port", value=Dist.CLIENT)
public final class ExplosionSmokeGalleryProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeExplosionSmokeGallery");
    private static final String[] NAMES={"m230","bofors40mm","hydra70","agm114","agm158","mk82",
            "gbu-31","gbu57","s-8dm","cbu-72","aim9","mk46"};
    private static int ticks,phase=-1,age,peak;
    private static String capture;
    private static WeaponInfo weapon;
    private static BlastStyle style;
    private record Entry(String name,String displayName,String type,int power,float effectSize,
            int airburstPower,int underwaterPower,BlastStyle family) {}
    private static boolean conventional(WeaponInfo w) {
        return !w.useHBM && w.payloadNTM==WeaponInfo.Payload.NONE && !w.effectOnly
                && (w.requiredMod==null || !w.requiredMod.matches("(?i).*(hbm|ntm).*"));
    }
    private static float effectSize(WeaponInfo w) {
        return w.isNewExplosionBreak && w.explosionRadius>0 ? w.explosionRadius : w.explosion;
    }
    private static void command(Minecraft mc,String... commands) {
        for(String c:commands)mc.player.connection.sendCommand(c);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!ENABLED)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40) {
            command(mc,"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear",
                    "gamerule minecraft:advance_time false","tp @s 17 -51 37 135 18",
                    "fill -32 -63 -12 32 -61 52 stone",
                    "fill -20 -60 0 -1 -35 40 air","fill 0 -60 0 20 -35 40 air");
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.MODERN_EXPLOSIONS.set(false);ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false);
            ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);
            var index=ContentRegistries.weapon().values().stream().filter(ExplosionSmokeGalleryProbe::conventional)
                    .filter(w->w.explosion>0 || w.explosionInWater>0).sorted(Comparator.comparing(w->w.name))
                    .map(w->new Entry(w.name,w.displayName,w.type,w.explosion,effectSize(w),w.explosionAirburst,
                            w.explosionInWater,BlastStyle.forWeapon(w,w.flaming))).toList();
            try {
                Files.writeString(mc.gameDirectory.toPath().resolve("m8-explosion-smoke-index.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(index)+"\n");
            }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
            System.out.println("M8_SMOKE_GALLERY indexed="+index.size());
        }
        if(ticks<160)return;
        int elapsed=ticks-160,next=Math.min(11,elapsed/220);
        age=next==11 ? elapsed-11*220 : elapsed%220;
        if(next==11 && age>=500){System.out.println("M8_SMOKE_GALLERY all_passed=true");mc.stop();return;}
        if(next!=phase) {
            phase=next;peak=0;mc.particleEngine.clearParticles();BlastMarks.clear();BlastGroundEffects.clear();
            weapon=ContentRegistries.weapon().get(NAMES[phase]);
            if(weapon==null || !conventional(weapon))throw new IllegalStateException("Invalid conventional weapon "+NAMES[phase]);
            style=BlastStyle.forWeapon(weapon,weapon.flaming);
            if(phase==11)command(mc,"fill -18 -61 2 18 -60 38 water");
            System.out.println("M8_SMOKE_GALLERY weapon="+weapon.name+" type="+weapon.type+" profile="+style
                    +" size="+effectSize(weapon)+" airburst="+weapon.explosionAirburst+" underwater="+weapon.explosionInWater);
        }
        if(age==30) {
            boolean water=phase==11,airburst=phase==10;
            float size=water?weapon.explosionInWater:airburst?weapon.explosionAirburst:effectSize(weapon);
            ClientEffects.INSTANCE.explosionEffects(mc.level,0,water?-60.4:airburst?-51:-59.5,20,
                    size,water,List.of(),style,BlastDirection.from(new Vec3(.75,-.45,1)));
        }
        int total=Integer.parseInt(mc.particleEngine.countParticles().replaceAll(".*T ",""));peak=Math.max(peak,total);
        if(total>EffectTuning.smokeLimit(ClientConfig.EffectBudget.BALANCED)+EffectTuning.flashLimit(ClientConfig.EffectBudget.BALANCED)+32)
            throw new IllegalStateException("Gallery particle cap exceeded");
        int second=style==BlastStyle.CANNON?12:style==BlastStyle.INCENDIARY?60:35;
        if(age==38 || age==30+second)capture=weapon.name+"-age"+(age-30)+(style==BlastStyle.CANNON&&age==42?"-close":"");
        if(age==(phase==11?490:210)) {
            int remaining=ownedParticles(mc);
            if(remaining!=0)throw new IllegalStateException("Particles did not drain: "+weapon.name+" remaining="+remaining);
            System.out.println("M8_SMOKE_GALLERY drain=true weapon="+weapon.name+" peak="+peak);
        }
    }
    private static int ownedParticles(Minecraft mc) {
        try {
            var field=net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");field.setAccessible(true);
            var particles=net.minecraft.client.particle.ParticleGroup.class.getDeclaredField("particles");particles.setAccessible(true);int count=0;
            for(var group:((java.util.Map<?,?>)field.get(mc.particleEngine)).values())
                for(var particle:(java.util.Collection<?>)particles.get(group))if(particle instanceof McheliParticle)count++;
            return count;
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov event) {
        if(ENABLED&&phase>=0)event.getCamera().setPosition(style==BlastStyle.CANNON&&age>=42
                ?new Vec3(5,-56.5,25):new Vec3(17,-51,37));
    }
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event) {
        if(ENABLED&&phase>=0){event.setYaw(135);event.setPitch(18);event.setRoll(0);}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;
            Screenshot.grab(mc.gameDirectory,"m8-explosion-smoke-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,
                    m->System.out.println("M8_SMOKE_GALLERY "+m.getString()));}
    }
}

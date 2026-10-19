package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.effect.BlastMarks;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.BlastDirection;
import io.github.mcheliceport.effect.BlastStyle;
import java.util.List;
import java.util.ArrayList;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.client.render.BulletRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Opt-in real-renderer checks of the five blast-motion additions, excluded from production builds. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class BlastMotionProbe {
    private static final String[] NAMES = {"bomb", "rocket-east", "rocket-west", "incendiary", "ground-flash",
            "airburst", "water", "low", "marks-off", "stress", "tracer-day", "tracer-night", "tracer-classic", "agm158", "rocket-low", "agm158-low"};
    private static final boolean MISSILES = Boolean.getBoolean("mcheli.probeMissiles");
    private static final int[] MISSILE_PHASES = {1, 13, 14, 15};
    private static final int LENGTH = 150;
    private static final int OFFSET = Boolean.getBoolean("mcheli.probeMotionTracersOnly") ? 10 : 0;
    private static final List<HeldBullet> bullets = new ArrayList<>();
    private static final class HeldBullet extends BulletEntity {
        HeldBullet(Minecraft mc) { super(ModEntityTypes.BULLET.get(), mc.level); }
        @Override public void tick() { this.xo = this.getX(); this.yo = this.getY(); this.zo = this.getZ(); }
    }
    private static int tick, peakFlash, peakFragments, peakParticles;
    private static String capture;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!MISSILES && !Boolean.getBoolean("mcheli.probeBlastMotion")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        tick++;
        if (tick == 40) {
            commands(mc, "kill @e[type=!minecraft:player]", "gamemode spectator", "weather clear",
                    "gamerule minecraft:advance_time false", "fill -24 -60 6 24 -50 48 air",
                    "fill -24 -49 6 24 -40 48 air", "fill -24 -64 6 24 -61 48 stone");
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.getWindow().setWindowed(1920, 1080);
            mc.options.enableVsync().set(false); mc.options.framerateLimit().set(120);
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);
            ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false);
        }
        if (tick < 160) return;
        int phase = phase(), age = (tick - 160) % LENGTH;
        if (phase >= NAMES.length) { mc.stop(); return; }
        if (age == 0) {
            for (var bullet : bullets) bullet.discard(); bullets.clear();
            ClientConfig.EFFECT_QUALITY.set(phase == 12 ? EffectQuality.CLASSIC : EffectQuality.ENHANCED);
            mc.particleEngine.clearParticles(); BlastMarks.clear(); BlastGroundEffects.clear();
            peakFlash = peakFragments = peakParticles = 0;
            ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.EFFECT_BUDGET.set(phase == 7 || phase >= 14 ? ClientConfig.EffectBudget.LOW : ClientConfig.EffectBudget.BALANCED);
            ClientConfig.BLAST_MARKS.set(phase != 8);
            commands(mc, "fill -24 -60 6 24 -58 48 air", "fill -24 -61 6 24 -61 48 grass_block",
                    phase == 3 || phase == 4 || phase == 8 || phase == 11 ? "time set midnight" : "time set noon",
                    "tp @s -6 -54 7 -25 14");
            if (phase == 4) commands(mc, "fill -3 -60 20 3 -60 20 stone_slab[type=bottom]",
                    "fill -2 -60 22 2 -60 22 stone");
            if (phase == 6) commands(mc, "fill -24 -60 6 24 -59 48 water");
            if (phase == 0) checkParticles(mc);
            System.out.println("M8_MOTION phase=" + NAMES[phase]);
        }
        if (age == 30) {
            if (phase == 4 || phase == 8) BlastMarks.add(mc.level, 0, -59.4, 20, 12, BlastStyle.HEAVY);
            else if (phase < 10 || phase >= 13) blast(mc, phase);
            else bullets(mc, phase);
        }
        if (phase == 9 && age > 30 && age < 75 && age % 5 == 0) {
            for (int i = 0; i < 12; i++) ClientEffects.INSTANCE.explosionEffects(mc.level, (i % 4 - 1.5) * 4,
                    -59.5, 18 + i / 4 * 4, 12, false, List.of(), BlastStyle.HEAVY);
        }
        if (age == 34 || age == 39 || age == 46 || age == 65) capture = NAMES[phase] + "-age" + (age - 30);
        peakFragments = Math.max(peakFragments, BlastGroundEffects.trackedFragments());
        peakParticles = Math.max(peakParticles, Integer.parseInt(mc.particleEngine.countParticles().replaceAll(".*T ", "")));
        if (BlastGroundEffects.surfaceReads() > 96 || BlastGroundEffects.emittedFragments() > 4
                || BlastGroundEffects.trackedFragments() > 24) throw new IllegalStateException("Ground budget exceeded");
        if (age == 130) {
            if ((phase == 4 || phase == 8) && peakFlash == 0) throw new IllegalStateException("Terrain flash never rendered");
            if ((phase == 5 || phase == 6 || phase == 7 || phase >= 14) && (peakFlash > 0 || peakFragments > 0))
                throw new IllegalStateException("Airburst/water/Low ground effect leak");
            if (phase == 8 && BlastMarks.markCount() != 0) throw new IllegalStateException("Disabled scorch left a persistent mark");
            if (BlastMarks.flashQuadCount() != 0) throw new IllegalStateException("Flash outlived its initial impulse");
            System.out.println("M8_MOTION result=" + NAMES[phase] + " flash_faces=" + peakFlash
                    + " fragments=" + peakFragments + " particles=" + peakParticles);
        }
    }

    private static int phase() {
        int index = (tick - 160) / LENGTH;
        return MISSILES ? index < MISSILE_PHASES.length ? MISSILE_PHASES[index] : NAMES.length : index + OFFSET;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void bullets(Minecraft mc, int phase) {
        int tracers = 0;
        for (int i = 0; i < 8; i++) {
            var bullet = new HeldBullet(mc);
            bullet.setId(-70000 - phase * 100 - i);
            bullet.setUUID(new java.util.UUID(0, i));
            bullet.setName("m134");
            bullet.isBomblet = 0;
            if (bullet.getInfo() == null) throw new IllegalStateException("Missing stock bullet fixture");
            bullet.setPos(-3 + i % 4 * 2, -56 + i / 4 * 1.3, 20);
            bullet.setYRot(-90); bullet.yRotO = -90;
            bullet.setDeltaMovement(8, 0, 0); bullet.prevMotionX = 8;
            mc.level.addEntity(bullet); bullets.add(bullet);
            BulletRenderer renderer = (BulletRenderer) mc.getEntityRenderDispatcher().getRenderer(bullet);
            var state = renderer.createRenderState(); renderer.extractRenderState(bullet, state, 0);
            if (state.groups == null) throw new IllegalStateException("Bullet model missing");
            if (state.tracer) tracers++;
        }
        if (tracers != (phase == 12 ? 0 : 2)) throw new IllegalStateException("Unexpected sparse tracer selection: " + tracers);
        System.out.println("M8_MOTION tracer_selection=" + tracers + "/8 classic=" + (phase == 12));
    }

    private static void blast(Minecraft mc, int phase) {
        BlastStyle style = phase == 1 || phase == 2 || phase >= 13 ? BlastStyle.BLAST : phase == 3 ? BlastStyle.INCENDIARY : BlastStyle.HEAVY;
        var direction = BlastDirection.from(new Vec3(phase == 1 || phase >= 13 ? 1 : phase == 2 ? -1 : 0, -0.4, 0));
        float power = phase == 1 || phase == 2 || phase == 14 ? 4 : 12;
        if (phase == 13 || phase == 15) {
            var info = io.github.mcheliceport.content.ContentRegistries.weapon().get("agm158");
            if (info == null || info.explosion != 10) throw new IllegalStateException("Missing stock AGM-158 fixture");
            power = info.explosion; style = BlastStyle.forWeapon(info, false);
            if (ExplosionSequence.smokeLed(power, style)) throw new IllegalStateException("AGM-158 lost its heavy warhead profile");
        }
        ClientEffects.INSTANCE.explosionEffects(mc.level, 0, phase == 5 ? -42 : -59.5, 20,
                power, phase == 6, List.of(), style, direction);
    }

    private static void checkParticles(Minecraft mc) {
        if (!McheliParticle.sprite(BlastDirtJetParticle.TEXTURE).contents().name().equals(BlastDirtJetParticle.TEXTURE))
            throw new IllegalStateException("Missing jet sprite");
        var p = new BlastDirtJetParticle(mc.level, 0, -58, 20, 0.3, 0.5, 0, 3, 0.5F, 0.4F, 0.3F, 999, 0.8F);
        if (!p.getParticleLimit().equals(EffectAdmission.limit(false))) throw new IllegalStateException("Jet bypassed shared particle limit");
        for (int i = 0; i < 28; i++) p.tick();
        if (p.isAlive() || p.getQuadSize(0) > 3) throw new IllegalStateException("Jet life/radius unbounded");
        var fire = new BlastCloudParticle(mc.level, 0, -59, 20, 0, 0, 0, 4, 100, 52, 0.9F, 2, true);
        for (int i = 0; i < 128; i++) fire.tick();
        if (fire.isAlive()) throw new IllegalStateException("Lingering fire exceeded cloud lifetime");
    }
    private static void commands(Minecraft mc, String... commands) {
        for (String command : commands) mc.player.connection.sendCommand(command);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if ((!MISSILES && !Boolean.getBoolean("mcheli.probeBlastMotion")) || tick < 160) return;
        peakFlash = Math.max(peakFlash, BlastMarks.flashQuadCount());
        if (BlastMarks.flashQuadCount() > 64) throw new IllegalStateException("Terrain flash budget exceeded");
        if (capture == null) return;
        var mc = Minecraft.getInstance(); String name = capture; capture = null;
        Screenshot.grab(mc.gameDirectory, "m8-motion-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("M8_MOTION " + message.getString()));
    }
}

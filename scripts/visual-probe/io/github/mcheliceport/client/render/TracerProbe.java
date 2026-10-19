package io.github.mcheliceport.client.render;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Moving stock-model tracer checks; included only by the opt-in init script. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class TracerProbe {
    private static final String[] NAMES = {"side-day", "side-night", "rear-day", "rear-night", "front-day", "distant", "classic"};
    private static final int LENGTH = 120;
    private static final List<HeldBullet> bullets = new ArrayList<>();
    private static int tick, sequence, peak;
    private static String capture;

    private static final class HeldBullet extends BulletEntity {
        private int age;
        HeldBullet(Minecraft mc) { super(ModEntityTypes.BULLET.get(), mc.level); }
        @Override public void tick() {
            this.xo = this.getX(); this.yo = this.getY(); this.zo = this.getZ();
            this.setPos(this.position().add(this.getDeltaMovement().normalize().scale(0.5)));
            if (++this.age >= 32) this.discard();
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeTracers")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        tick++;
        if (tick == 40) {
            commands(mc, "kill @e[type=!minecraft:player]", "gamemode spectator", "weather clear",
                    "gamerule minecraft:advance_time false");
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.getWindow().setWindowed(1920, 1080);
            mc.options.enableVsync().set(false); mc.options.framerateLimit().set(120);
            ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);
            ClientConfig.ADAPTIVE_EFFECTS.set(false);
        }
        if (tick < 160) return;
        int phase = (tick - 160) / LENGTH, age = (tick - 160) % LENGTH;
        if (phase >= NAMES.length) { mc.stop(); return; }
        if (age == 0) {
            for (var b : bullets) b.discard(); bullets.clear(); peak = 0;
            mc.particleEngine.clearParticles();
            ClientConfig.EFFECT_QUALITY.set(phase == 6 ? EffectQuality.CLASSIC : EffectQuality.ENHANCED);
            commands(mc, phase == 1 || phase == 3 ? "time set midnight" : "time set noon",
                    phase == 5 ? "tp @s 0 -53 -60 0 0" : "tp @s 0 -53 5 0 0");
            System.out.println("M8_TRACER phase=" + NAMES[phase]);
        }
        bullets.removeIf(b -> !b.isAlive());
        if (age >= 20 && age <= 80 && age % 20 == 0) spawn(mc, phase);
        peak = Math.max(peak, bullets.size());
        if (bullets.size() > 16) throw new IllegalStateException("Unbounded tracer fixture");
        if (age == 28 || age == 48) capture = NAMES[phase] + "-age" + age;
        if (age == 115) {
            if (!bullets.isEmpty()) throw new IllegalStateException("Bullet fixtures did not drain");
            System.out.println("M8_TRACER result=" + NAMES[phase] + " peak_bullets=" + peak + " drained=true");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void spawn(Minecraft mc, int phase) {
        Vec3 motion = phase == 2 || phase == 3 ? new Vec3(0, 0, 8)
                : phase == 4 ? new Vec3(0, 0, -8) : new Vec3(8, 0, 0);
        int selected = 0;
        for (int i = 0; i < 8; i++) {
            var b = new HeldBullet(mc); b.setId(-300000 - sequence); b.setUUID(new UUID(0, sequence++));
            b.setName("m134"); b.isBomblet = 0;
            boolean alongView = phase >= 2 && phase <= 4;
            b.setPos(alongView && i % 4 == 0 ? 0.5 : -3 + i % 4 * 2,
                    (alongView ? -51.4 : -54) + i / 4 * 1.3, 20);
            b.setYRot(phase == 2 || phase == 3 ? 0 : phase == 4 ? 180 : -90); b.yRotO = b.getYRot();
            b.setDeltaMovement(motion); b.prevMotionX = motion.x; b.prevMotionY = motion.y; b.prevMotionZ = motion.z;
            mc.level.addEntity(b); bullets.add(b);
            BulletRenderer renderer = (BulletRenderer) mc.getEntityRenderDispatcher().getRenderer(b);
            var state = renderer.createRenderState(); renderer.extractRenderState(b, state, 0);
            if (state.groups == null) throw new IllegalStateException("Missing stock M134 model");
            if (state.tracer) selected++;
        }
        if (selected != (phase == 6 ? 0 : 2)) throw new IllegalStateException("Sparse tracer selection changed");
    }

    private static void commands(Minecraft mc, String... commands) {
        for (String c : commands) mc.player.connection.sendCommand(c);
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeTracers") || capture == null) return;
        var mc = Minecraft.getInstance(); String name = capture; capture = null;
        Screenshot.grab(mc.gameDirectory, "m8-tracer-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("M8_TRACER " + message.getString()));
    }
}

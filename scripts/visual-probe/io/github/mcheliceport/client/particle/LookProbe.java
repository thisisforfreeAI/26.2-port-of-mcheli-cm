package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.ParticleParam;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Opt-in lighting/fire regression scene in the disposable M6Smoke world copy. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class LookProbe {
    private static final String[] SCENES = {"day-classic", "day-lit", "night-lit", "room-dark", "room-torch",
            "room-classic", "water-damage", "sparks"};
    private static int tick;
    private static String capture;
    private static SmokeParticle sample;
    private static final BlockPos AT = new BlockPos(-3, -57, 17);

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeLook")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        tick++;
        if (tick == 40) {
            mc.getWindow().setWindowed(Integer.getInteger("mcheli.probeWidth", 1920), Integer.getInteger("mcheli.probeHeight", 1080));
            commands(mc, "kill @e[type=!minecraft:player]", "gamemode spectator", "tp @s 0 -55 4 0 8",
                    "weather clear", "gamerule minecraft:advance_time false");
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(120);
            ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);
        }
        if (tick < 160) return;
        int phase = (tick - 160) / 220;
        int age = (tick - 160) % 220;
        if (phase >= SCENES.length) { mc.stop(); return; }
        if (age == 0) {
            if (mc.getWindow().getWidth() != 1920 || mc.getWindow().getHeight() != 1080) {
                throw new IllegalStateException("Look probe requires 1920x1080");
            }
            mc.particleEngine.clearParticles();
            ClientConfig.EFFECT_STYLE.set(phase == 0 || phase == 5 ? ClientConfig.EffectStyle.CLASSIC : ClientConfig.EffectStyle.POLISHED);
            commands(mc, "fill -9 -60 9 9 -49 27 air", phase == 2 || phase == 7 ? "time set midnight" : "time set noon");
            if (phase >= 3 && phase <= 5) {
                commands(mc, "fill -8 -60 10 8 -50 26 stone hollow", "fill -7 -59 10 7 -51 10 tinted_glass",
                        "tp @s 0 -55 11 0 8");
                if (phase == 4) commands(mc, "setblock -3 -58 18 stone", "setblock -3 -57 18 torch");
            }
            if (phase < 3 || phase >= 6) commands(mc, "tp @s 0 -55 4 0 8");
            if (phase == 6) commands(mc, "fill -6 -59 14 0 -56 20 water");
            sample = new SmokeParticle(mc.level, AT.getX(), AT.getY(), AT.getZ(), 0, 0, 0);
            sample.enhanced = phase != 0 && phase != 5;
            sample.setParticleMaxAge(10000);
            sample.motionYUpAge = 2;
            System.out.println("M8_LOOK phase=" + SCENES[phase]);
        }
        if (age < 60) return;
        if (phase != 7 && age % 3 == 0) {
            for (int i = 0; i < 2; i++) {
                ParticleParam p = new ParticleParam("smoke", i == 0 ? -3 : 3, -57, 17, 0, 0.06, 0, 11);
                p.age = 60;
                p.r = p.g = p.b = 0.5F;
                // Cold at x=-3, critical damage at x=3; water phase also tests hot hints while submerged.
                if (i == 1 || phase == 6) p.setDamageHeat(10, 100);
                ClientEffects.INSTANCE.spawnParticle(mc.level, p);
            }
        }
        sample.tick();
        if (age == 160) {
            int actual = sample.getLightCoords(0);
            int ambient = LightCoordsUtil.getLightCoords(mc.level, AT);
            int expected = sample.enhanced ? ambient : SmokeParticle.SKY_LIGHT;
            if (actual != expected) throw new IllegalStateException("Smoke light mismatch " + actual + " != " + expected);
            if (phase == 3 && actual != 0) throw new IllegalStateException("Dark room has leaked light " + actual);
            if (phase == 4 && (LightCoordsUtil.block(actual) <= 0 || LightCoordsUtil.sky(actual) != 0)) {
                throw new IllegalStateException("Torch room did not retain torch light " + actual);
            }
            System.out.println("M8_LOOK light=" + SCENES[phase] + " sky=" + LightCoordsUtil.sky(actual)
                    + " block=" + LightCoordsUtil.block(actual) + " particles=" + mc.particleEngine.countParticles());
            capture = SCENES[phase];
        }
        if (phase == 7 && age == 180) {
            ClientEffects.INSTANCE.explosionEffects(mc.level, 0, -57, 17, 4, false, List.of(AT));
        }
        if (phase == 7 && age == 182) capture = "explosion-sparks";
        if (phase == 7 && age == 200) {
            // A separate small burst makes the restrained spark shape visible away from fireball smoke.
            ClientEffects.spawnExplosionSparks(mc.level, 0, -55, 8, 4);
        }
        if (phase == 7 && age == 202) capture = "isolated-sparks";
    }

    private static void commands(Minecraft mc, String... commands) {
        for (String command : commands) mc.player.connection.sendCommand(command);
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        String name = capture;
        capture = null;
        Screenshot.grab(mc.gameDirectory, "m8-look-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("M8_LOOK " + message.getString()));
    }
}

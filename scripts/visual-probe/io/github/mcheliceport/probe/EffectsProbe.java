package io.github.mcheliceport.probe;

import io.github.mcheliceport.client.particle.ClientEffects;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.effect.ParticleParam;
import io.github.mcheliceport.entity.flare.FlareEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Opt-in scene in an isolated copy of a flat test world. Not part of the mod. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class EffectsProbe {
    private static int tick;
    private static long lastFrame;
    private static final List<Double> frames = new ArrayList<>();
    private static String capture;
    private static String phaseName = "warmup";

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        tick++;
        if (tick == 40) {
            var atlas = mc.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.PARTICLES);
            for (String name : List.of("smoke", "splash", "flash")) {
                var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("mcheli_ce_port", "m8_" + name);
                System.out.println("M8_PROBE sprite=" + atlas.getSprite(id));
                if (!atlas.getSprite(id).contents().name().equals(id)) {
                    throw new IllegalStateException("Missing probe sprite " + id);
                }
            }
            for (String command : List.of("kill @e[type=!minecraft:player]", "gamemode spectator",
                    "time set midnight", "weather clear", "gamerule minecraft:advance_time false",
                    "tp @s 0 -55 -10 0 8", "fill 8 -60 10 15 -60 20 minecraft:water")) {
                mc.player.connection.sendCommand(command);
            }
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(120);
        }
        if (tick < 160) return;
        int phase = (tick - 160) / 360;
        int age = (tick - 160) % 360;
        if (phase >= 7) {
            mc.stop();
            return;
        }
        if (age == 0) {
            mc.particleEngine.clearParticles();
            frames.clear();
            lastFrame = 0;
            boolean classic = phase == 0 || phase == 2 || phase == 5;
            phaseName = switch (phase) {
                case 0 -> "classic";
                case 1 -> "polished";
                case 2 -> "combat-classic";
                case 3 -> "combat-polished";
                case 4 -> "combat-low";
                case 5 -> "day-classic";
                default -> "day-polished";
            };
            ClientConfig.EFFECT_STYLE.set(classic ? ClientConfig.EffectStyle.CLASSIC : ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_QUALITY.set(phase == 4 ? ClientConfig.EffectQuality.LOW : ClientConfig.EffectQuality.BALANCED);
            if (phase == 5) mc.player.connection.sendCommand("time set noon");
            System.out.println("M8_PROBE phase=" + phaseName);
        }
        Random rand = new Random(age);
        int copies = phase >= 2 && phase <= 4 ? 12 : 1;
        for (int copy = 0; copy < copies; copy++) {
            double offset = copy == 0 ? 0 : 25 + copy * 4;
            if (age % 2 == 0) {
                ParticleParam smoke = new ParticleParam("smoke", -9, -58.5, 14 + offset,
                        .015, .12, 0, 11);
                smoke.age = 70;
                smoke.diffusible = true;
                smoke.r = .75F; smoke.g = .77F; smoke.b = .8F;
                ClientEffects.INSTANCE.spawnParticle(mc.level, smoke);
            }
            if (age % 45 == 0) {
                for (int i = 0; i < 45; i++) {
                    double angle = rand.nextDouble() * Math.PI * 2;
                    ParticleParam smoke = new ParticleParam("explode", -2 + rand.nextDouble() * 2 - 1,
                            -58 + rand.nextDouble(), 15 + offset + rand.nextDouble() * 2 - 1,
                            Math.cos(angle) * .15, rand.nextDouble() * .22, Math.sin(angle) * .15, 9);
                    smoke.age = 45 + rand.nextInt(25);
                    smoke.diffusible = true;
                    smoke.r = .6F; smoke.g = .55F; smoke.b = .5F;
                    ClientEffects.INSTANCE.spawnParticle(mc.level, smoke);
                }
            }
            if (age % 10 == 0) {
                ClientEffects.INSTANCE.spawnMuzzleFlash(mc.level, 4, -56.7, 14 + offset,
                        .9F, 1, 1, 1, 1, 4);
            }
            for (int i = 0; i < 2; i++) {
                ParticleParam trail = new ParticleParam("smoke", 7 + i * .18, -55.5 - i * .23,
                        17 + offset, 0, .015, 0, 4);
                trail.age = 30;
                trail.r = .8F; trail.g = .8F; trail.b = .85F;
                ClientEffects.INSTANCE.spawnParticle(mc.level, trail);
            }
            if (age % 6 == 0) {
                ParticleParam splash = new ParticleParam("splash", 12 + rand.nextDouble() * 2 - 1,
                        -58.7, 15 + offset, (rand.nextDouble() - .5) * .15,
                        .65 + rand.nextDouble() * .15, 0, 7);
                splash.age = 45;
                ClientEffects.INSTANCE.spawnParticle(mc.level, splash);
            }
        }
        // A real client flare exercises the renderer and its stock trail.
        if (age == 180) {
            FlareEntity flare = new FlareEntity(mc.level, 7, -55, 17, .005, .02, 0, 4, 0);
            flare.setId(-2100 - phase);
            mc.level.addEntity(flare);
        }
        if (age == 202) capture = phaseName;
        if (phase == 6 && age == 225) {
            ClientEffects.INSTANCE.explosionEffects(mc.level, -2, -58, 15, 4, false,
                    List.of(net.minecraft.core.BlockPos.containing(-2, -59, 15)));
        }
        if (phase == 6 && age == 227) capture = "day-explosion";
        if (age == 359 && !frames.isEmpty()) {
            Collections.sort(frames);
            double median = frames.get(frames.size() / 2);
            double p95 = frames.get((int) ((frames.size() - 1) * .95));
            System.out.printf(java.util.Locale.ROOT,
                    "M8_PROBE result=%s frames=%d median_ms=%.2f p95_ms=%.2f particles=%s%n",
                    phaseName, frames.size(), median, p95, mc.particleEngine.countParticles());
        }
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (tick < 160 || mc.level == null) return;
        long now = System.nanoTime();
        if ((tick - 160) % 360 >= 100 && lastFrame != 0) frames.add((now - lastFrame) / 1_000_000.0);
        lastFrame = now;
        if (capture != null) {
            String name = capture;
            capture = null;
            Screenshot.grab(mc.gameDirectory, "m8-" + name + ".png",
                    mc.gameRenderer.mainRenderTarget(), 1, message -> System.out.println("M8_PROBE " + message.getString()));
        }
    }
}

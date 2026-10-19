package io.github.mcheliceport.client.effect;

import io.github.mcheliceport.client.particle.ClientEffects;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.BlastStyle;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Repeatable terrain workload and moving stock-bullet fixture; never part of a normal build. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class TerrainBudgetProbe {
    private static final String[] NAMES = {"flat", "crater", "steps", "airburst", "overlap", "disabled", "moving-fire"};
    private static final int LENGTH = 180;
    private static final boolean BASELINE = Boolean.getBoolean("mcheli.probeTerrainBaseline");
    private static final List<HeldBullet> bullets = new ArrayList<>();
    private static final StringBuilder csv = new StringBuilder("phase,tick,cpu_ms,reads,rays,checks,pending,active,tiles,flash_faces,bullets\n");
    private static int tick, sequence, peakReads, peakRays, peakChecks, peakPending, peakActive, peakTiles, peakFlash, peakBullets;
    private static String capture;

    private static final class HeldBullet extends BulletEntity {
        HeldBullet(Minecraft mc) { super(ModEntityTypes.BULLET.get(), mc.level); }
        @Override public void tick() {
            this.xo = this.getX(); this.yo = this.getY(); this.zo = this.getZ();
            this.setPos(this.getX() + 1, this.getY(), this.getZ());
            if (++this.tickCount >= 16) this.discard();
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeTerrainBudget")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        tick++;
        if (tick == 40) {
            commands(mc, "kill @e[type=!minecraft:player]", "gamemode spectator", "weather clear", "time set noon",
                    "gamerule minecraft:advance_time false", "fill -24 -60 6 24 -50 48 air",
                    "fill -24 -49 6 24 -40 48 air", "fill -24 -64 6 24 -61 48 stone");
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.getWindow().setWindowed(1920, 1080);
            mc.options.enableVsync().set(false); mc.options.framerateLimit().set(120);
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);
            ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);
            ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false);
        }
        if (tick < 160) return;
        int phase = (tick - 160) / LENGTH, age = (tick - 160) % LENGTH;
        if (phase >= NAMES.length) {
            try { java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("m8-terrain-" + (BASELINE ? "before" : "after") + ".csv"), csv); }
            catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            mc.stop(); return;
        }
        if (age == 0) {
            for (var bullet : bullets) bullet.discard(); bullets.clear();
            mc.particleEngine.clearParticles(); BlastMarks.clear();
            peakReads = peakRays = peakChecks = peakPending = peakActive = peakTiles = peakFlash = peakBullets = 0;
            ClientConfig.BLAST_MARKS.set(phase != 5);
            commands(mc, "fill -24 -64 6 24 -61 48 stone", "fill -24 -61 6 24 -61 48 grass_block",
                    "fill -24 -60 6 24 -58 48 air", "tp @s -6 -54 7 -25 14", "time set noon");
            if (phase == 1) commands(mc, "fill -4 -61 16 4 -61 24 air", "fill -2 -62 18 2 -61 22 air");
            if (phase == 2) commands(mc, "fill -4 -60 19 4 -60 19 stone_slab[type=bottom]", "fill -3 -60 23 3 -60 23 stone");
            if (phase == 6) commands(mc, "tp @s 0 -51 -5 0 16", "time set midnight");
            System.out.println("M8_TERRAIN phase=" + NAMES[phase] + " baseline=" + BASELINE);
        }
        if (age == 30) {
            if (phase == 4 || phase == 5) for (int i = 0; i < 64; i++) mark(mc, (i % 3 - 1) * 6, -59.5, 14 + i / 3 % 3 * 6);
            else if (phase != 6) mark(mc, 0, phase == 3 ? -42 : phase == 2 ? -58.8 : -59.5, 20);
        }
        if (phase == 0 && age == 85 && BlastMarks.quadCount() != 144) throw new IllegalStateException("Large footprint changed: " + BlastMarks.quadCount());
        if (phase == 0 && age == 90) commands(mc, "fill -7 -61 13 7 -61 27 air");
        if (phase == 0 && age == 120 && BlastMarks.markCount() != 0) throw new IllegalStateException("Destroyed surface retained its mark");
        if (phase == 4 && age > 30 && age <= 78 && age % 6 == 0) for (int i = 0; i < 9; i++) mark(mc, (i % 3 - 1) * 6, -59.5, 14 + i / 3 * 6);
        if (phase == 6 && age >= 30 && age < 130) {
            bullets.removeIf(b -> !b.isAlive());
            for (int i = 0; i < 4; i++) {
                var b = new HeldBullet(mc); b.setId(-65000 - sequence); b.setUUID(new java.util.UUID(0, sequence++));
                b.setName("m134"); b.isBomblet = 0;
                b.setPos(-10, -54 + i % 2 * 0.5, 18 + i * 3); b.setYRot(-90); b.yRotO = -90;
                b.setDeltaMovement(8, 0, 0); b.prevMotionX = 8;
                mc.level.addEntity(b); bullets.add(b);
            }
            if (age % 10 == 0) {
                ClientEffects.INSTANCE.explosionEffects(mc.level, -6, -59.5, 20, 12, false, List.of(), BlastStyle.HEAVY);
                ClientEffects.INSTANCE.explosionEffects(mc.level, 6, -59.5, 24, 4, false, List.of(), BlastStyle.BLAST);
            }
        }
        peakReads = Math.max(peakReads, BlastMarks.projectionReads()); peakRays = Math.max(peakRays, BlastMarks.projectionRays());
        peakChecks = Math.max(peakChecks, BlastMarks.maintenanceChecks()); peakPending = Math.max(peakPending, BlastMarks.pendingCount());
        peakActive = Math.max(peakActive, BlastMarks.activeCount()); peakTiles = Math.max(peakTiles, BlastMarks.quadCount());
        peakBullets = Math.max(peakBullets, bullets.size());
        if (!BASELINE && (BlastMarks.projectionReads() > 256 || BlastMarks.projectionRays() > 32
                || BlastMarks.maintenanceChecks() > 40 || BlastMarks.activeCount() > 2)) throw new IllegalStateException("Terrain tick budget exceeded");
        if (BlastMarks.pendingCount() > 16 || BlastMarks.quadCount() > 384 || bullets.size() > 64) throw new IllegalStateException("Unbounded stored work");
        if (age >= 20 && age < 150) csv.append(NAMES[phase]).append(',').append(age).append(',').append(BlastMarks.tickNanos() / 1_000_000.0)
                .append(',').append(BlastMarks.projectionReads()).append(',').append(BlastMarks.projectionRays()).append(',')
                .append(BlastMarks.maintenanceChecks()).append(',').append(BlastMarks.pendingCount()).append(',')
                .append(BlastMarks.activeCount()).append(',').append(BlastMarks.quadCount()).append(',').append(BlastMarks.flashQuadCount())
                .append(',').append(bullets.size()).append('\n');
        if (age == 36 || age == 50 || age == 100) capture = NAMES[phase] + "-age" + age;
        if (age == 165) {
            if (phase == 4 && peakTiles > 288) throw new IllegalStateException("Staging increased the flat overlap draw footprint");
            if (phase == 3 && (BlastMarks.markCount() > 0 || BlastMarks.pendingCount() > 0 || BlastMarks.activeCount() > 0)) throw new IllegalStateException("Airburst work never drained");
            if (phase == 5 && (BlastMarks.markCount() > 0 || BlastMarks.pendingCount() > 0 || BlastMarks.activeCount() > 0)) throw new IllegalStateException("Expired flash retained work");
            if ((phase == 1 || phase == 2) && BlastMarks.quadCount() < 100) throw new IllegalStateException("Stepped/crater footprint missing");
            System.out.println("M8_TERRAIN result=" + NAMES[phase] + " reads=" + peakReads + " rays=" + peakRays + " checks=" + peakChecks
                    + " pending=" + peakPending + " active=" + peakActive + " tiles=" + peakTiles + " flash=" + peakFlash + " bullets=" + peakBullets);
        }
    }
    private static void mark(Minecraft mc, double x, double y, double z) { BlastMarks.add(mc.level, x, y, z, 12, BlastStyle.HEAVY); }
    private static void commands(Minecraft mc, String... commands) { for (String c : commands) mc.player.connection.sendCommand(c); }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeTerrainBudget") || tick < 160) return;
        peakFlash = Math.max(peakFlash, BlastMarks.flashQuadCount());
        if (BlastMarks.flashQuadCount() > 64) throw new IllegalStateException("Flash face ceiling exceeded");
        if (capture == null) return;
        var mc = Minecraft.getInstance(); String name = capture; capture = null;
        Screenshot.grab(mc.gameDirectory, "m8-terrain-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("M8_TERRAIN " + message.getString()));
    }
}

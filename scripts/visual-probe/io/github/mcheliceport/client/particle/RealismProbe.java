package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.model.VehicleSurfaceShade;
import io.github.mcheliceport.client.render.AircraftRenderState;
import io.github.mcheliceport.client.render.AircraftRenderer;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.BlastStyle;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Disposable M6Smoke-world checks. Never included in release jars. */
@EventBusSubscriber(modid = "mcheli_ce_port", value = Dist.CLIENT)
public final class RealismProbe {
    private static final String[] NAMES = {"shadow-balanced", "shadow-high", "shadow-disabled", "classic",
            "shade-disabled", "night-blast", "roof-blast", "split-ground", "rock-fragments", "sand-jets", "far-shadow"};
    private static int tick, phase = -1, age;
    private static String capture;
    private static HeldTank tank;
    private static final class HeldTank extends TankEntity {
        HeldTank(Minecraft mc) { super(ModEntityTypes.TANK.get(), mc.level); }
        @Override public void tick() { xo = getX(); yo = getY(); zo = getZ(); }
        @Override public int getFuel() { return 1000; }
    }
    private static void commands(Minecraft mc, String... commands) {
        for (var command : commands) mc.player.connection.sendCommand(command);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeRealism")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (++tick == 40) {
            commands(mc, "kill @e[type=!minecraft:player]", "gamemode spectator", "weather clear",
                    "gamerule minecraft:advance_time false", "fill -16 -60 4 16 -40 32 air",
                    "fill -16 -61 4 16 -61 32 stone");
            mc.getWindow().setWindowed(1920, 1080);
            mc.options.enableVsync().set(false); mc.options.framerateLimit().set(120);
            mc.options.entityShadows().set(true);
            ClientConfig.EFFECT_TARGET_FPS.set(Integer.getInteger("mcheli.probeTargetFps", 60));
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        }
        if (tick < 160) return;
        phase = (tick - 160) / 160; age = (tick - 160) % 160;
        if (phase >= NAMES.length) { mc.stop(); return; }
        if (age == 0) {
            if (tank != null) { tank.discard(); tank = null; }
            mc.particleEngine.clearParticles(); BlastGroundEffects.clear();
            ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.ENGINE_HEAT_HAZE.set(false);
            ClientConfig.EFFECT_QUALITY.set(phase == 3 ? EffectQuality.CLASSIC : EffectQuality.ENHANCED);
            ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(phase == 1 ? ClientConfig.EffectBudget.HIGH : ClientConfig.EffectBudget.BALANCED);
            ClientConfig.VEHICLE_CONTACT_SHADOWS.set(phase != 2);
            ClientConfig.VEHICLE_SURFACE_SHADE.set(phase != 4);
            ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false);
            commands(mc, "time set " + (phase == 5 ? "midnight" : "noon"),
                    "fill -16 -60 4 16 -40 32 air", "fill -16 -61 4 16 -61 32 stone",
                    "tp @s 8 -55 7 45 24");
            if (phase < 5 || phase == 10) {
                tank = new HeldTank(mc); tank.setId(-950000 - phase);
                tank.setAircraftTypeName("m1a2"); tank.setTextureName("m1a2"); tank.setPos(0, -60, 15);
                var parts = tank.getParts(); for (int i = 0; i < parts.length; i++) parts[i].setId(-960000 - phase * 100 - i);
                if (phase == 10) commands(mc, "tp @s 8 -55 -10 16 12");
                mc.level.addEntity(tank);
            } else {
                commands(mc, "tp @s 0 -56 5 0 8");
                if (phase == 6) commands(mc, "fill -7 -52 10 7 -52 24 stone");
                if (phase == 9) commands(mc, "fill -16 -61 4 16 -61 32 sand");
                if (phase == 7) commands(mc, "fill -16 -61 4 -1 -61 32 sand", "fill 0 -61 4 16 -61 32 grass_block");
            }
            System.out.println("M8_REALISM phase=" + NAMES[phase]);
        }
        if (age == 50 && phase >= 5 && phase < 10) {
            if (phase >= 7) {
                BlastGroundEffects.emit(mc.level, 0, -59.5, 17, 12, BlastStyle.HEAVY);
                int expected = phase == 8 ? 4 : 2;
                if (BlastGroundEffects.emittedFragments() != expected)
                    throw new IllegalStateException("Wrong material fragments: " + BlastGroundEffects.emittedFragments());
                System.out.println("M8_REALISM fragments=" + BlastGroundEffects.emittedFragments()
                        + " ring=" + BlastGroundEffects.ringEmitted() + " reads=" + BlastGroundEffects.surfaceReads());
            } else ClientEffects.INSTANCE.explosionEffects(mc.level, 0, -59.5, 17, 12, false, List.of(), BlastStyle.HEAVY);
        }
        if (age == 56 && phase >= 7 && phase < 10) capture = NAMES[phase] + "-early";
        if (age == 75) {
            if (phase < 5 || phase == 10) checkTank(mc);
            else {
                // A delayed lobe still has amber block illumination after its hidden stagger.
                var puff = new BlastCloudParticle(mc.level, 0, -59, 17, 0, 0, 0, 1, 60, 0, 0.9F, 8, false, 12);
                for (int i = 0; i < 9; i++) puff.tick();
                int blockLight = (puff.getLightCoords(0) >> 4) & 15;
                if (blockLight < 6) throw new IllegalStateException("Delayed amber light expired: " + blockLight);
                System.out.println("M8_REALISM delayed_block_light=" + blockLight + " ground_reads=" + BlastGroundEffects.surfaceReads());
            }
            capture = NAMES[phase];
        }
    }
    @SuppressWarnings("unchecked") private static void checkTank(Minecraft mc) {
        var renderer = (AircraftRenderer<AircraftEntity>) (Object) mc.getEntityRenderDispatcher().getRenderer(tank);
        AircraftRenderState state = renderer.createRenderState(tank, 1);
        float expected = phase == 1 ? 3 : phase == 2 || phase == 3 || phase == 10 ? 2 : 2.5F;
        if (state.shadowRadius != expected || (phase == 10 ? !state.shadowPieces.isEmpty() : state.shadowPieces.isEmpty()) || state.draws.isEmpty())
            throw new IllegalStateException("Missing tank/shadow: " + state.shadowRadius + "/" + state.shadowPieces.size());
        if (state.surfaceShade != (phase != 3 && phase != 4)) throw new IllegalStateException("Shade toggle failed");
        long baked = state.draws.stream().flatMap(d -> d.groups().stream()).filter(g -> VehicleSurfaceShade.get(g) != null).count();
        if (baked == 0) throw new IllegalStateException("Tank has no baked colors");
        System.out.println("M8_REALISM radius=" + state.shadowRadius + " shadow_pieces=" + state.shadowPieces.size()
                + " surface_shade=" + state.surfaceShade + " baked_groups=" + baked);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("mcheli.probeRealism") || capture == null) return;
        var mc = Minecraft.getInstance(); String name = capture; capture = null;
        Screenshot.grab(mc.gameDirectory, "m8-realism-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> System.out.println("M8_REALISM " + message.getString()));
    }
}

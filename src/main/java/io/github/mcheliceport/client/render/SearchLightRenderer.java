package io.github.mcheliceport.client.render;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.SearchLightBeam;
import io.github.mcheliceport.util.McheliMath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/** Additive, two-sided stock cones; no GL calls, custom shaders or world light-engine updates. */
public final class SearchLightRenderer {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("mcheli_ce_port", "pipeline/searchlight"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();
    /** Additive position-colour triangles; the laser designator's beam uses it too. */
    static final RenderType TYPE = RenderType.create("mcheli_searchlight",
            RenderSetup.builder(PIPELINE).createRenderSetup());
    private SearchLightRenderer() { }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) { event.registerPipeline(PIPELINE); }

    public static void submit(List<SearchLightBeam> beams, PoseStack stack, SubmitNodeCollector collector) {
        if (beams.isEmpty()) { return; }
        List<SearchLightBeam> snapshot = List.copyOf(beams);
        collector.submitCustomGeometry(stack, TYPE, (pose, buffer) -> {
            for (var beam : snapshot) {
                var p = pose.copy(); p.mulPose(beam.pose());
                float radius = beam.light().width / 2;
                for (int i = 0; i < 24; i++) {
                    buffer.addVertex(p, 0, 0, 0).setColor(dim(beam.light().colorStart, beam.strength()*beam.scatter()));
                    for (int n = i; n <= i + 1; n++) {
                        double angle = Math.toRadians(n * 15);
                        buffer.addVertex(p, (float) Math.sin(angle) * radius, beam.light().height,
                                (float) Math.cos(angle) * radius).setColor(dim(beam.light().colorEnd, beam.strength()*beam.scatter()));
                    }
                }
            }
        });
    }

    private static int dim(int color, float strength) {
        return ((int) ((color >>> 24) * strength) << 24) | (color & 0xFFFFFF);
    }

    public static void registerModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() { },
                (LivingEntity entity, LivingEntityRenderState state) -> {
                    var mc = Minecraft.getInstance();
                    if (mc.level == null) { return; }
                    Vec3 target = entity.getPosition(state.partialTick);
                    int block = LightCoordsUtil.block(state.lightCoords);
                    for (var beam : lights(state.partialTick)) {
                        block = Math.max(block, beam.beam.blockLight(target.subtract(beam.position)));
                        if (block == 15) { break; }
                    }
                    state.lightCoords = LightCoordsUtil.pack(block, LightCoordsUtil.sky(state.lightCoords));
                });
    }

    private record WorldBeam(Vec3 position, SearchLightBeam beam) { }
    private static final List<WorldBeam> FRAME_LIGHTS = new ArrayList<>();
    private static boolean extracted;
    private static Object level;
    public static void beginFrame() { extracted = false; FRAME_LIGHTS.clear(); level = null; }
    /** Enumerate tracked aircraft once per extracted frame, never once per living entity. */
    private static List<WorldBeam> lights(float partial) {
        var mc = Minecraft.getInstance();
        if (extracted && level == mc.level) { return FRAME_LIGHTS; }
        extracted = true; level = mc.level; FRAME_LIGHTS.clear();
        if (mc.level == null) { return FRAME_LIGHTS; }
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof AircraftEntity ac) || !ac.isSearchLightOn() || ac.getAcInfo() == null) { continue; }
            float[] ypr = McheliMath.slerpOrientationYPR(ac.yRotO, ac.xRotO, ac.prevRoll,
                    ac.getYaw(), ac.getPitch(), ac.getRoll(), partial);
            for (int lamp = 0; lamp < ac.getAcInfo().searchLights.size(); lamp++) {
                var light = ac.getAcInfo().searchLights.get(lamp);
                float strength = VehicleLightDamage.strength(ac, lamp);
                if (strength <= 0 || !SearchLightBeam.usable(light)) { continue; }
                FRAME_LIGHTS.add(new WorldBeam(ac.getPosition(partial), SearchLightBeam.of(light,
                        ypr[0], ypr[1], ypr[2], ac.searchLightYaw(partial), ac.searchLightPitch(partial),
                        Mth.lerp(partial, ac.previousSteeringYaw, ac.steeringYaw)).dim(strength)));
            }
        }
        return FRAME_LIGHTS;
    }
}

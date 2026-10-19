package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * The client half of upstream's long-distance patch ({@code EntityRendererHook#renderFarPass}): a
 * vehicle the server sends from past the render distance (see {@code LongDistanceTracking}) sits in
 * a chunk this client has not loaded, where vanilla neither ticks nor draws entities. Those
 * vehicles are ticked here, so they keep moving, and drawn without fog, as upstream's far pass did.
 */
public final class FarVehicles {

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "pipeline/far_vehicle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "core/far_vehicle"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();
    private static final Function<Identifier, RenderType> FAR = Util.memoize(texture -> RenderType.create(
            "mcheli_ce_port_far_vehicle",
            RenderSetup.builder(PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .sortOnUpload()
                    .createRenderSetup()));

    private FarVehicles() {
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PIPELINE);
    }

    /** The vehicle render type without fog. */
    public static RenderType renderType(Identifier texture) {
        return FAR.apply(texture);
    }

    /** Upstream's far-pass test: farther from the camera than the render distance. */
    public static boolean isFar(double distanceSqr) {
        double renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0;
        return distanceSqr > renderDistance * renderDistance;
    }

    /** A vehicle in a chunk the client has not loaded, in view. */
    public static boolean visibleOutsideLoadedChunks(Entity entity, Frustum frustum, double camX, double camY,
            double camZ) {
        // Vanilla ticks exactly the entities in chunks the client has loaded.
        if (!(entity instanceof AircraftEntity) || !(entity.level() instanceof ClientLevel level)
                || level.isTickingEntity(entity)) {
            return false;
        }
        return Minecraft.getInstance().getEntityRenderDispatcher().shouldRender(entity, frustum, camX, camY, camZ);
    }

    /** Ticks the vehicles vanilla does not tick, those in chunks the client has not loaded. */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) {
            return;
        }
        List<AircraftEntity> idle = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof AircraftEntity vehicle && !vehicle.isRemoved() && !vehicle.isPassenger()
                    && !level.isTickingEntity(vehicle) && !level.tickRateManager().isEntityFrozen(vehicle)) {
                idle.add(vehicle);
            }
        }
        for (AircraftEntity vehicle : idle) {
            level.guardEntityTick(level::tickNonPassenger, vehicle);
        }
    }
}

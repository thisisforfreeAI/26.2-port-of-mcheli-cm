package io.github.mcheliceport.client.render;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.world.entity.Entity;
import io.github.mcheliceport.shim.client.event.ClientTickEvent;

/**
 * The client half of upstream's long-distance patch ({@code EntityRendererHook#renderFarPass}): a
 * vehicle the server sends from past the render distance (see {@code LongDistanceTracking}) sits in
 * a chunk this client has not loaded, where vanilla neither ticks nor draws entities. Those
 * vehicles are ticked here, so they keep moving, and drawn without fog, as upstream's far pass did.
 */
public final class FarVehicles {
    private static final Function<ResourceLocation, RenderType> FAR = RenderType::entityTranslucentCull;

    private FarVehicles() {
    }


    /** The vehicle render type without fog. */
    public static RenderType renderType(ResourceLocation texture) {
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
                || ((io.github.mcheliceport.mixin.client.ClientLevelAccessor) level).mcheli$tickingEntities().contains(entity)) {
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
                    && !((io.github.mcheliceport.mixin.client.ClientLevelAccessor) level).mcheli$tickingEntities().contains(vehicle) && !level.tickRateManager().isEntityFrozen(vehicle)) {
                idle.add(vehicle);
            }
        }
        for (AircraftEntity vehicle : idle) {
            level.guardEntityTick(level::tickNonPassenger, vehicle);
        }
    }
}

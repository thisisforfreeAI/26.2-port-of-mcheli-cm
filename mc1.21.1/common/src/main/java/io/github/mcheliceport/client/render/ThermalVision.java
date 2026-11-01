package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.AircraftClientEvents;
import io.github.mcheliceport.client.camera.GltdCameraClient;
import io.github.mcheliceport.entity.aircraft.AircraftCamera;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.gltd.GltdEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import io.github.mcheliceport.shim.client.event.ClientBus;
import io.github.mcheliceport.shim.client.event.RenderLivingEvent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;

/**
 * Thermal vision's white-hot entities: upstream's {@code MCH_TextureManagerDummy}, which bound its
 * plain white {@code textures/test.png} in place of every entity texture while the rider's camera
 * was in thermal vision, except for the vehicle being ridden. The thermal post effect then turns
 * those bright grey shapes white and darkens the rest.
 *
 * <p>Living entities are submitted again through a collector that swaps the texture of each model
 * for the white one; held items keep theirs, as upstream's item renderer bound through its own
 * texture manager. McHeli vehicles swap their texture in {@link AircraftRenderer}.
 */
public final class ThermalVision {

    public static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("mcheli", "textures/test.png");

    private static boolean resubmitting;

    private ThermalVision() {
    }

    public static void register() {
        ClientBus.addListener(RenderLivingEvent.Pre.class, ThermalVision::onPre);
    }

    /** Upstream {@code CameraHandler.cameraMode == 2}: the local rider's camera is in thermal vision. */
    public static boolean active() {
        return cameraMode(Minecraft.getInstance().player) == AircraftCamera.MODE_THERMALVISION;
    }

    /** Upstream {@code MouseInputHandler#getCameraMode}: the ridden vehicle's camera, else the GLTD's. */
    static int cameraMode(@Nullable LocalPlayer player) {
        AircraftEntity aircraft = AircraftClientEvents.riddenAircraft(player);
        if (aircraft != null) {
            return aircraft.getCameraMode(player);
        }
        GltdEntity gltd = GltdCameraClient.riddenGltd();
        return gltd != null ? gltd.camera.getMode(0) : AircraftCamera.MODE_NORMAL;
    }

    /** Whether an entity is drawn white: all but the vehicle being ridden, as upstream. */
    public static boolean drawsWhite(Entity entity) {
        return active() && entity != AircraftClientEvents.riddenAircraft(Minecraft.getInstance().player);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onPre(RenderLivingEvent.Pre event) {
        if (resubmitting || event.isCanceled() || !active() || !drawsWhite(event.getEntity())) {
            return;
        }
        event.setCanceled(true);
        resubmitting = true;
        try {
            MultiBufferSource source = event.getMultiBufferSource();
            MultiBufferSource white = type -> source.getBuffer(white(type));
            ((LivingEntityRenderer) event.getRenderer()).render(event.getEntity(), event.getYaw(),
                    event.getPartialTick(), event.getPoseStack(), white, event.getPackedLight());
        } finally {
            resubmitting = false;
        }
    }

    /** The white render type for a model's; 1.21.1 render types do not say their texture, so all go cut out. */
    static RenderType white(RenderType original) {
        if (original.isOutline()) {
            return original;
        }
        return RenderType.entityCutoutNoCull(WHITE);
    }
}

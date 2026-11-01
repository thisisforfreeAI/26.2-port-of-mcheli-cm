package io.github.mcheliceport.client.render.baked;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.client.render.AircraftPartPoses;
import io.github.mcheliceport.shim.client.SubmitNodeCollector;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * 26.2 draws vehicles from GPU buffers baked once per model, with its own shader. That path needs
 * 26.2's render pipeline API, so on 1.21.1 vehicles always take the immediate path.
 */
public final class BakedVehicles {

    private BakedVehicles() {
    }

    public static boolean enabled() {
        return false;
    }

    public static void register() {
    }

    public static boolean submit(SubmitNodeCollector collector, PoseStack poseStack, ResourceLocation texture,
            List<AircraftPartPoses.Draw> draws, int color, int light, boolean flat, boolean surfaceShade, boolean far,
            @Nullable Object lightProbe, boolean realistic) {
        return false;
    }
}

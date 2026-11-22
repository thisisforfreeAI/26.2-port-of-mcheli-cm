package io.github.mcheliceport.mixin;

import io.github.mcheliceport.client.render.FarVehicles;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pushes the camera's far plane out while a vehicle is beyond it. Vanilla clips everything past four
 * times the render distance, so with Distant Horizons (short vanilla distance, terrain kilometres
 * away) a jet vanished in front of the far terrain. Upstream used a second 3,000-block projection.
 * The culling frustum and the projection are both built from this value after it is set.
 */
@Mixin(Camera.class)
abstract class FarVehicleDepthMixin {
    @Shadow private float depthFar;

    @Inject(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;depthFar:F",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void mcheli$farVehicleDepth(DeltaTracker deltaTracker, CallbackInfo ci) {
        this.depthFar = FarVehicles.depthFar(this.depthFar);
    }
}

package io.github.mcheliceport.fabric.mixin.client;

import io.github.mcheliceport.shim.client.event.CalculateDetachedCameraDistanceEvent;
import io.github.mcheliceport.shim.client.event.ClientBus;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** NeoForge's {@code CalculateDetachedCameraDistanceEvent}: the third-person camera distance. */
@Mixin(Camera.class)
abstract class CameraMixin {
    @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float mcheli$distance(float distance) {
        return ClientBus.post(new CalculateDetachedCameraDistanceEvent(distance)).getDistance();
    }
}

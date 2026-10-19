package io.github.mcheliceport.mixin;

import io.github.mcheliceport.client.render.FarVehicles;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Upstream's far-plane pass: vanilla draws an entity only in a compiled section, so a vehicle sent
 * from past the render distance would never show.
 */
@Mixin(LevelExtractor.class)
abstract class FarVehicleVisibilityMixin {
    @Inject(method = "isEntityVisible", at = @At("RETURN"), cancellable = true)
    private void mcheli$farVehicle(Entity entity, Frustum frustum, double camX, double camY, double camZ,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && FarVehicles.visibleOutsideLoadedChunks(entity, frustum, camX, camY, camZ)) {
            cir.setReturnValue(true);
        }
    }
}

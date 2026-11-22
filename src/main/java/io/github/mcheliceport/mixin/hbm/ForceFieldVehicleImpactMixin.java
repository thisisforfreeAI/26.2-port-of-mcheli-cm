package io.github.mcheliceport.mixin.hbm;

import io.github.mcheliceport.compat.hbm.ForceFieldImpact;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NTM Next's force field emitter: McHeli vehicles crossing the shield deal it no damage (see
 * {@link ForceFieldImpact}). {@code @Pseudo}, so nothing happens when NTM Next is not installed;
 * {@code HbmGameTests} checks that it applies when it is.
 */
@Pseudo
@Mixin(targets = "com.hbm.blockentity.machine.BlockEntityForceField")
abstract class ForceFieldVehicleImpactMixin {
    @Inject(method = "impact(Lnet/minecraft/world/entity/Entity;)I", at = @At("HEAD"), cancellable = true)
    private void mcheli$noVehicleImpact(Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (ForceFieldImpact.isVehicle(entity)) {
            cir.setReturnValue(0);
        }
    }
}

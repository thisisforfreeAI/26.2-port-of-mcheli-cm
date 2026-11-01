package io.github.mcheliceport.fabricmixin;

import io.github.mcheliceport.platform.fabric.FabricParts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Attacks and interactions name a hit box by its entity id: resolve it as NeoForge does. */
@Mixin(ServerLevel.class)
abstract class ServerLevelPartsMixin {

    @Inject(method = "getEntityOrPart", at = @At("RETURN"), cancellable = true)
    private void mcheli$findPart(int id, CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() == null) {
            Entity part = FabricParts.find((ServerLevel) (Object) this, id);
            if (part != null) {
                cir.setReturnValue(part);
            }
        }
    }
}

package io.github.mcheliceport.mixin;

import io.github.mcheliceport.shim.PartEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Attacks and interactions aimed at a hit box part find it by id, as with the dragon's parts. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelPartsMixin {

    @Inject(method = "getEntityOrPart", at = @At("RETURN"), cancellable = true)
    private void mcheli$findPart(int id, CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() == null) {
            Entity part = PartEntities.get((ServerLevel) (Object) this, id);
            if (part != null) {
                cir.setReturnValue(part);
            }
        }
    }
}

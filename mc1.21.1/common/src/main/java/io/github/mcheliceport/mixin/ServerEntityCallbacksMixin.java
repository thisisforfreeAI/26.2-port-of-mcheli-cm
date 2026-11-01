package io.github.mcheliceport.mixin;

import io.github.mcheliceport.shim.PartEntities;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps {@link PartEntities} in step with the server level's tracked entities. */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class ServerEntityCallbacksMixin {

    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void mcheli$start(Entity entity, CallbackInfo ci) {
        PartEntities.onTrackingStart(entity);
    }

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void mcheli$end(Entity entity, CallbackInfo ci) {
        PartEntities.onTrackingEnd(entity);
    }
}

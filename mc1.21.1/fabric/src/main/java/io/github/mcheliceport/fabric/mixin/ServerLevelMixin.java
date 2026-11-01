package io.github.mcheliceport.fabric.mixin;

import io.github.mcheliceport.platform.event.ModEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's EntityTickEvent.Post and EntityJoinLevelEvent on the server. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "tickNonPassenger", at = @At("TAIL"))
    private void mcheli$tickPost(Entity entity, CallbackInfo ci) {
        ModEvents.entityTickPost(entity);
    }

    @Inject(method = "tickPassenger", at = @At("TAIL"))
    private void mcheli$passengerTickPost(Entity vehicle, Entity passenger, CallbackInfo ci) {
        ModEvents.entityTickPost(passenger);
    }

    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void mcheli$join(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ModEvents.entityJoin(entity, (ServerLevel) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}

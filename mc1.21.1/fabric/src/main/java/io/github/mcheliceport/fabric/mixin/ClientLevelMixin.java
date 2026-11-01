package io.github.mcheliceport.fabric.mixin;

import io.github.mcheliceport.platform.event.ModEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's EntityTickEvent.Post and EntityJoinLevelEvent on the client. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(method = "tickNonPassenger", at = @At("TAIL"))
    private void mcheli$tickPost(Entity entity, CallbackInfo ci) {
        ModEvents.entityTickPost(entity);
    }

    @Inject(method = "tickPassenger", at = @At("TAIL"))
    private void mcheli$passengerTickPost(Entity vehicle, Entity passenger, CallbackInfo ci) {
        ModEvents.entityTickPost(passenger);
    }

    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void mcheli$join(Entity entity, CallbackInfo ci) {
        if (ModEvents.entityJoin(entity, (ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }
}

package io.github.mcheliceport.fabricmixin;

import io.github.mcheliceport.platform.GameEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's {@code EntityTickEvent.Post} on the server: after an entity (or a passenger) ticked. */
@Mixin(ServerLevel.class)
abstract class ServerLevelEntityTickMixin {

    @Inject(method = "tickNonPassenger", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;tick()V", shift = At.Shift.AFTER))
    private void mcheli$afterTick(Entity entity, CallbackInfo ci) {
        GameEvents.fire(GameEvents.ENTITY_TICK_POST, entity);
    }

    @Inject(method = "tickPassenger", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;rideTick()V", shift = At.Shift.AFTER))
    private void mcheli$afterRideTick(Entity vehicle, Entity passenger, CallbackInfo ci) {
        GameEvents.fire(GameEvents.ENTITY_TICK_POST, passenger);
    }
}

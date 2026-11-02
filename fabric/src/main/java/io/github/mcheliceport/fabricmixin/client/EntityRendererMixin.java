package io.github.mcheliceport.fabricmixin.client;

import io.github.mcheliceport.platform.ClientEvents;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's {@code RenderNameTagEvent.CanRender} (a listener may hide a name tag) and its render
 * state modifiers for living entities.
 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin<T extends Entity> {

    @Inject(method = "shouldShowName", at = @At("RETURN"), cancellable = true)
    private void mcheli$nameTag(T entity, double distanceSquared, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            var tag = new ClientEvents.NameTag(entity);
            ClientEvents.fire(ClientEvents.NAME_TAG, tag);
            if (tag.hidden()) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("RETURN"))
    private void mcheli$livingState(T entity, float partialTick,
            CallbackInfoReturnable<net.minecraft.client.renderer.entity.state.EntityRenderState> cir) {
        if ((Object) this instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?>
                && entity instanceof net.minecraft.world.entity.LivingEntity living
                && cir.getReturnValue() instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
            ClientEvents.LIVING_RENDER_STATE.forEach(listener -> listener.accept(living, state, partialTick));
        }
    }
}

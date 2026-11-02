package io.github.mcheliceport.fabricmixin.client;

import io.github.mcheliceport.platform.ClientEvents;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's {@code RenderNameTagEvent.CanRender}: a listener may hide a name tag. */
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
}

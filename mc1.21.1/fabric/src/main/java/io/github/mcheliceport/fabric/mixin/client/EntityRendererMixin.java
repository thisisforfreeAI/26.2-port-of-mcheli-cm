package io.github.mcheliceport.fabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.shim.client.event.ClientBus;
import io.github.mcheliceport.shim.client.event.RenderNameTagEvent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's {@code RenderNameTagEvent}, as far as the port uses it: hiding a name tag. */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void mcheli$nameTag(Entity entity, Component name, PoseStack pose, MultiBufferSource buffers, int light,
            float partialTick, CallbackInfo ci) {
        Boolean can = ClientBus.post(new RenderNameTagEvent(entity, name)).canRender();
        if (Boolean.FALSE.equals(can)) {
            ci.cancel();
        }
    }
}

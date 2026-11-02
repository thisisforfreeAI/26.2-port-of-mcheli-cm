package io.github.mcheliceport.mixin.client;

import io.github.mcheliceport.client.render.FarVehicles;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Upstream's far-plane pass: vanilla draws an entity only in a compiled section, so a vehicle sent
 * from past the render distance would never show. 1.21.1 tests the section right after
 * {@code shouldRender} in the entity loop, so the entity is remembered from that call.
 */
@Mixin(LevelRenderer.class)
abstract class FarVehicleVisibilityMixin {
    @Unique private Entity mcheli$entity;
    @Unique private Frustum mcheli$frustum;
    @Unique private double mcheli$x, mcheli$y, mcheli$z;

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
    private boolean mcheli$remember(EntityRenderDispatcher dispatcher, Entity entity, Frustum frustum,
            double x, double y, double z) {
        this.mcheli$entity = entity;
        this.mcheli$frustum = frustum;
        this.mcheli$x = x;
        this.mcheli$y = y;
        this.mcheli$z = z;
        return dispatcher.shouldRender(entity, frustum, x, y, z);
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiled(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean mcheli$farVehicle(LevelRenderer renderer, BlockPos pos) {
        if (renderer.isSectionCompiled(pos)) {
            return true;
        }
        Entity entity = this.mcheli$entity;
        return entity != null && this.mcheli$frustum != null
                && FarVehicles.visibleOutsideLoadedChunks(entity, this.mcheli$frustum, this.mcheli$x, this.mcheli$y, this.mcheli$z);
    }
}

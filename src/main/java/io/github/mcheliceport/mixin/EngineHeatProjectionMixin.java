package io.github.mcheliceport.mixin;

import io.github.mcheliceport.client.render.EngineHeat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Reads the final world projection, including view bob/hurt/nausea, without changing it. */
@Mixin(GameRenderer.class)
public abstract class EngineHeatProjectionMixin {
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 0)
    private Matrix4f mcheli$captureHeatProjection(Matrix4f projection) {
        EngineHeat.captureProjection(projection);
        return projection;
    }
}

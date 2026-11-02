package io.github.mcheliceport.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.mcheliceport.client.render.EngineHeat;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Draws the engine heat haze right after the level, with the level's own matrices. */
@Mixin(GameRenderer.class)
abstract class GameRendererLevelMixin {
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void mcheli$engineHeat(LevelRenderer renderer, DeltaTracker delta, boolean blockOutline, Camera camera,
            GameRenderer gameRenderer, LightTexture light, Matrix4f modelView, Matrix4f projection,
            Operation<Void> original) {
        original.call(renderer, delta, blockOutline, camera, gameRenderer, light, modelView, projection);
        EngineHeat.render(modelView, projection);
    }
}

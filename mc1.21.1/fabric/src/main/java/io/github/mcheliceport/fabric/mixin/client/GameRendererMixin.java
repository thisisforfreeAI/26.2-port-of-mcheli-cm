package io.github.mcheliceport.fabric.mixin.client;

import io.github.mcheliceport.shim.client.event.ClientBus;
import io.github.mcheliceport.shim.client.event.RenderFrameEvent;
import io.github.mcheliceport.shim.client.event.RenderHandEvent;
import io.github.mcheliceport.shim.client.event.ViewportEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's frame, FOV, camera angle and hand events. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Shadow @Final private Camera mainCamera;

    @Inject(method = "render", at = @At("HEAD"))
    private void mcheli$framePre(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        ClientBus.post(new RenderFrameEvent.Pre(delta));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mcheli$framePost(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        ClientBus.post(new RenderFrameEvent.Post(delta));
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void mcheli$fov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        double fov = cir.getReturnValueD();
        double changed = ClientBus.post(new ViewportEvent.ComputeFov(camera, partialTick, fov, useFovSetting)).getFOV();
        if (changed != fov) {
            cir.setReturnValue(changed);
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V"))
    private void mcheli$cameraAngles(DeltaTracker delta, CallbackInfo ci) {
        Camera camera = this.mainCamera;
        var event = ClientBus.post(new ViewportEvent.ComputeCameraAngles(camera,
                delta.getGameTimeDeltaPartialTick(true), camera.getYRot(), camera.getXRot(), 0.0F));
        float yaw = event.getYaw();
        float pitch = event.getPitch();
        float roll = event.getRoll();
        if (yaw == camera.getYRot() && pitch == camera.getXRot() && roll == 0.0F) {
            return;
        }
        CameraAccessor access = (CameraAccessor) camera;
        access.mcheli$setRotation(yaw, pitch);
        if (roll != 0.0F) {
            // NeoForge's three-angle Camera#setRotation.
            access.mcheli$rotation().rotationYXZ((float) Math.PI - yaw * Mth.DEG_TO_RAD, -pitch * Mth.DEG_TO_RAD,
                    -roll * Mth.DEG_TO_RAD);
            new Vector3f(0.0F, 0.0F, -1.0F).rotate(access.mcheli$rotation(), access.mcheli$forwards());
            new Vector3f(0.0F, 1.0F, 0.0F).rotate(access.mcheli$rotation(), access.mcheli$up());
            new Vector3f(-1.0F, 0.0F, 0.0F).rotate(access.mcheli$rotation(), access.mcheli$left());
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void mcheli$hand(Camera camera, float partialTick, Matrix4f projection, CallbackInfo ci) {
        if (ClientBus.post(new RenderHandEvent()).isCanceled()) {
            ci.cancel();
        }
    }
}

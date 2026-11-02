package io.github.mcheliceport.fabricmixin.client;

import io.github.mcheliceport.platform.ClientEvents;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's {@code ViewportEvent.ComputeCameraAngles} (with camera roll, which vanilla's camera
 * lacks), {@code ViewportEvent.ComputeFov} and {@code CalculateDetachedCameraDistanceEvent}.
 */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    @Final
    private static Vector3fc FORWARDS;
    @Shadow
    @Final
    private static Vector3fc UP;
    @Shadow
    @Final
    private static Vector3fc LEFT;
    @Shadow
    @Final
    private Quaternionf rotation;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;
    @Shadow
    private Entity entity;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    /** The entity-aligned rotation (not the minecart one): listeners may change it, and roll it. */
    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1))
    private void mcheli$cameraAngles(Camera camera, float yaw, float pitch, float partialTicks) {
        var angles = new ClientEvents.CameraAngles(camera, partialTicks, yaw, pitch, 0);
        ClientEvents.fire(ClientEvents.CAMERA_ANGLES, angles);
        setRotation(angles.getYaw(), angles.getPitch());
        float roll = angles.getRoll();
        if (roll != 0) {
            float rad = (float) (Math.PI / 180.0);
            rotation.rotationYXZ((float) Math.PI - angles.getYaw() * rad, -angles.getPitch() * rad, -roll * rad);
            FORWARDS.rotate(rotation, forwards);
            UP.rotate(rotation, up);
            LEFT.rotate(rotation, left);
        }
    }

    @ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float mcheli$detachedDistance(float distance) {
        var event = new ClientEvents.DetachedDistance(distance);
        ClientEvents.fire(ClientEvents.DETACHED_CAMERA_DISTANCE, event);
        return event.getDistance();
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void mcheli$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        var fov = new ClientEvents.Fov((Camera) (Object) this, partialTicks, cir.getReturnValueF());
        ClientEvents.fire(ClientEvents.COMPUTE_FOV, fov);
        cir.setReturnValue((float) fov.getFOV());
    }
}

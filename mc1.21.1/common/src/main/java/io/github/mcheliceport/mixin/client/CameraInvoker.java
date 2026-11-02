package io.github.mcheliceport.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Moves the camera, as 26.2's public {@code Camera#setPosition}. */
@Mixin(Camera.class)
public interface CameraInvoker {
    @Invoker("setPosition")
    void mcheli$setPosition(Vec3 position);
}

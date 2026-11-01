package io.github.mcheliceport.fabric.mixin.client;

import net.minecraft.client.Camera;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** What NeoForge's camera angle event sets: yaw, pitch and roll. */
@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setRotation")
    void mcheli$setRotation(float yaw, float pitch);

    @Accessor("rotation")
    Quaternionf mcheli$rotation();

    @Accessor("forwards")
    Vector3f mcheli$forwards();

    @Accessor("up")
    Vector3f mcheli$up();

    @Accessor("left")
    Vector3f mcheli$left();
}

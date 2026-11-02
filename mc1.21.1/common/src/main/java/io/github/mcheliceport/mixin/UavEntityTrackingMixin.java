package io.github.mcheliceport.mixin;

import io.github.mcheliceport.entity.uav.UavRemoteSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Only active UAV operators gain an additional nearby entity-tracking origin. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class UavEntityTrackingMixin {
    @Shadow @Final private Entity entity;
    @ModifyVariable(method = "updatePlayer", at = @At("STORE"), ordinal = 0)
    private Vec3 mcheli$cameraTrackingDelta(Vec3 ordinary, ServerPlayer player) {
        return UavRemoteSession.trackingDelta(ordinary, entity, player);
    }
}

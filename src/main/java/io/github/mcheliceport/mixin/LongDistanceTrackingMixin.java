package io.github.mcheliceport.mixin;

import io.github.mcheliceport.entity.aircraft.LongDistanceTracking;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Upstream's {@code MixinEntityTrackerEntry}: vehicles are tracked to their full range. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class LongDistanceTrackingMixin {
    @Shadow @Final private Entity entity;

    @Redirect(method = "updatePlayer", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"))
    private int mcheli$trackingRange(int trackingRange, int viewRange) {
        return LongDistanceTracking.visibleRange(this.entity, trackingRange, viewRange);
    }

    @Redirect(method = "updatePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"))
    private boolean mcheli$forceWatch(ChunkMap chunkMap, ServerPlayer player, int chunkX, int chunkZ) {
        return LongDistanceTracking.applies(this.entity) || chunkMap.isChunkTracked(player, chunkX, chunkZ);
    }
}

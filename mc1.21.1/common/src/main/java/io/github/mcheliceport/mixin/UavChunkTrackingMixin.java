package io.github.mcheliceport.mixin;

import io.github.mcheliceport.entity.uav.UavChunkView;
import io.github.mcheliceport.entity.uav.UavStationEntity;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla recomputes its body-only view every tick; an active session owns its two windows. */
@Mixin(ChunkMap.class)
abstract class UavChunkTrackingMixin {
    @Inject(method = "updateChunkTracking", at = @At("HEAD"), cancellable = true)
    private void mcheli$keepRemoteView(ServerPlayer player, CallbackInfo ci) {
        if (player.getChunkTrackingView() instanceof UavChunkView
                && player.getVehicle() instanceof UavStationEntity station && station.streamingFor(player)) {
            ci.cancel();
        }
    }
}

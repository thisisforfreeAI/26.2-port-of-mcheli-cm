package io.github.mcheliceport.fabric.mixin;

import io.github.mcheliceport.wingman.block.WingmanMarkerBlockEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's BlockEntity#onChunkUnloaded, for the wingman markers. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

    @Inject(method = "clearAllBlockEntities", at = @At("HEAD"))
    private void mcheli$chunkUnloaded(CallbackInfo ci) {
        for (Map.Entry<BlockPos, BlockEntity> entry : ((LevelChunk) (Object) this).getBlockEntities().entrySet()) {
            if (entry.getValue() instanceof WingmanMarkerBlockEntity marker) {
                marker.onChunkUnloaded();
            }
        }
    }
}

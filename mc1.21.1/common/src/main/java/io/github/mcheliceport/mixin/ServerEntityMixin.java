package io.github.mcheliceport.mixin;

import io.github.mcheliceport.net.SpawnDataPayload;
import io.github.mcheliceport.shim.ComplexSpawn;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends a {@link ComplexSpawn} entity's spawn data right after its spawn bundle, on the same
 * connection, so the client has the entity by the time the data arrives (NeoForge's complex spawn).
 */
@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {

    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "addPairing", at = @At("TAIL"))
    private void mcheli$sendSpawnData(ServerPlayer player, CallbackInfo ci) {
        if (this.entity instanceof ComplexSpawn spawn) {
            SpawnDataPayload.send(player, this.entity, spawn);
        }
    }
}

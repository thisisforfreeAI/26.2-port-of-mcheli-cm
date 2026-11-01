package io.github.mcheliceport.fabricmixin;

import io.github.mcheliceport.platform.fabric.SpawnDataPayload;
import io.github.mcheliceport.platform.loader.SpawnDataEntity;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
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
 * Sends a {@link SpawnDataEntity}'s extra spawn data right after its add-entity packet, in the same
 * bundle, as NeoForge does for {@code IEntityWithComplexSpawn}.
 */
@Mixin(ServerEntity.class)
abstract class ServerEntitySpawnDataMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "sendPairingData", at = @At(value = "INVOKE",
            target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V", ordinal = 0, shift = At.Shift.AFTER))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mcheli$sendSpawnData(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> broadcast,
            CallbackInfo ci) {
        if (this.entity instanceof SpawnDataEntity spawnData) {
            broadcast.accept((Packet) ServerPlayNetworking.createClientboundPacket(
                    SpawnDataPayload.of(spawnData, player.registryAccess())));
        }
    }
}

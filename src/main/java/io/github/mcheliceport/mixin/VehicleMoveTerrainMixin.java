package io.github.mcheliceport.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.LoadedTerrain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * After a pilot's vehicle move, vanilla checks the vehicle's old and new boxes against the blocks
 * there. When those chunks are still generating the check waits for them on the server thread, so
 * for McHeli vehicles it is skipped until the terrain is loaded (see {@link LoadedTerrain}).
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class VehicleMoveTerrainMixin {
    @WrapOperation(method = "handleMoveVehicle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isEntityCollidingWithAnythingNew(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;DDD)Z"))
    private boolean mcheli$skipUnloadedTerrain(ServerGamePacketListenerImpl self, LevelReader level, Entity vehicle, AABB oldAABB,
            double newX, double newY, double newZ, Operation<Boolean> original) {
        if (vehicle instanceof AircraftEntity && vehicle.level() != null) {
            AABB newAABB = vehicle.getBoundingBox().move(newX - vehicle.getX(), newY - vehicle.getY(), newZ - vehicle.getZ());
            if (!LoadedTerrain.isReady(vehicle.level(), newAABB.minmax(oldAABB))) {
                return false;
            }
        }
        return original.call(self, level, vehicle, oldAABB, newX, newY, newZ);
    }

    /** The rejected-move check of the old box: no collision there means the move is sent back. */
    @WrapOperation(method = "handleMoveVehicle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean mcheli$skipUnloadedOldBox(ServerLevel level, Entity vehicle, AABB box, Operation<Boolean> original) {
        if (vehicle instanceof AircraftEntity && !LoadedTerrain.isReady(level, box)) {
            return true;
        }
        return original.call(level, vehicle, box);
    }
}

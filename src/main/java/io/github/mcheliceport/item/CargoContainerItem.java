package io.github.mcheliceport.item;

import io.github.mcheliceport.entity.container.CargoContainerEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** Upstream's five-block block/fluid placement ray, including the container's half-block offset. */
public class CargoContainerItem extends Item {
    public CargoContainerItem(Properties properties) { super(properties); }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var eye = player.getEyePosition();
        var reach = player.getViewVector(1).scale(5);
        var hit = level.clip(new ClipContext(eye, eye.add(reach), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() == HitResult.Type.MISS) { return InteractionResult.PASS; }
        for (var other : level.getEntities(player, player.getBoundingBox().expandTowards(reach).inflate(1))) {
            if (other.isPickable() && other.getBoundingBox().inflate(other.getPickRadius()).contains(eye)) {
                return InteractionResult.FAIL;
            }
        }
        var pos = hit.getBlockPos();
        var cargo = new CargoContainerEntity(ModEntityTypes.CONTAINER.get(), level);
        cargo.snapTo(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, AircraftItem.snapYaw(player.getYRot()), 0);
        if (!level.noCollision(cargo, cargo.getBoundingBox().deflate(0.1))) { return InteractionResult.FAIL; }
        if (!level.isClientSide()) {
            if (!level.addFreshEntity(cargo)) { return InteractionResult.FAIL; }
            player.getItemInHand(hand).consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}

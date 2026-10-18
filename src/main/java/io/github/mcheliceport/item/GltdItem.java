package io.github.mcheliceport.item;

import io.github.mcheliceport.entity.gltd.GltdEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The ground laser target designator item (upstream {@code MCH_ItemGLTD}): use it on a block
 * within five blocks to set the designator up there, facing the way the player faces.
 */
public class GltdItem extends Item {

    private static final double REACH = 5.0;

    public GltdItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(REACH)), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }
        // Upstream: nothing is placed while the player's eyes are inside another entity.
        AABB around = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);
        for (Entity entity : level.getEntities(player, around)) {
            if (entity.isPickable() && entity.getBoundingBox().inflate(entity.getPickRadius()).contains(eye)) {
                return InteractionResult.FAIL;
            }
        }
        BlockPos pos = hit.getBlockPos();
        GltdEntity gltd = ModEntityTypes.GLTD.get().create(level, net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
        if (gltd == null) {
            return InteractionResult.FAIL;
        }
        gltd.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, player.getYRot(), 0.0F);
        if (!level.noCollision(gltd, gltd.getBoundingBox().deflate(0.1))) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            gltd.updateCameraPosition(true);
            level.addFreshEntity(gltd);
        }
        player.getItemInHand(hand).consume(1, player);
        return InteractionResult.SUCCESS;
    }
}

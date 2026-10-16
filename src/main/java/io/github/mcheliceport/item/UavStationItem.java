package io.github.mcheliceport.item;

import io.github.mcheliceport.entity.uav.UavStationEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** Upstream's quarter-turn console placement, consuming only a successful server spawn. */
public class UavStationItem extends Item {
    private final boolean small;
    public UavStationItem(Properties properties, boolean small) { super(properties); this.small = small; }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isSpectator()) { return InteractionResult.PASS; }
        var eye = player.getEyePosition(); var reach = player.getViewVector(1).scale(5);
        var hit = level.clip(new ClipContext(eye, eye.add(reach), ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != HitResult.Type.BLOCK) { return InteractionResult.PASS; }
        if (!player.mayUseItemAt(hit.getBlockPos(), hit.getDirection(), player.getItemInHand(hand))) { return InteractionResult.FAIL; }
        var station = new UavStationEntity(ModEntityTypes.UAV_STATION.get(), level);
        var pos = hit.getBlockPos();
        float yaw = (float) (Math.floor((net.minecraft.util.Mth.positiveModulo(player.getYRot(), 360) + 45) / 90) * 90 - 180);
        station.snapTo(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, yaw, 0);
        station.setSmall(small); station.initOffsets();
        if (!level.noCollision(station, station.getBoundingBox().deflate(.1))
                || !level.getEntities(player, station.getBoundingBox(), e -> e.isPickable()).isEmpty()) { return InteractionResult.FAIL; }
        if (!level.isClientSide()) {
            if (!level.addFreshEntity(station)) { return InteractionResult.FAIL; }
            player.getItemInHand(hand).consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}

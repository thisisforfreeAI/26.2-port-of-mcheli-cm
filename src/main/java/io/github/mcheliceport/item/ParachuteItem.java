package io.github.mcheliceport.item;

import io.github.mcheliceport.entity.parachute.ParachuteEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Right-click while airborne to deploy upstream's type-1 parachute. */
public class ParachuteItem extends Item {
    public ParachuteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.getVehicle() != null || player.onGround() || !player.isAlive()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            ParachuteEntity chute = ParachuteEntity.deploy(server, player, 1,
                    player.position().add(0.5, 3.5, 0.5), player.getDeltaMovement());
            if (chute == null) {
                return InteractionResult.FAIL;
            }
            player.getItemInHand(hand).consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}

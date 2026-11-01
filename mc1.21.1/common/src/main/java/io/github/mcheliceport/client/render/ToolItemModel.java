package io.github.mcheliceport.client.render;

import io.github.mcheliceport.item.WrenchItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 26.2 draws the hand tools' 3D models in the hand through a custom item model type. 1.21.1 has no
 * item model types, so for now the tools keep their flat item models; the wrench's swing state is
 * kept for when a 1.21.1 item renderer takes over.
 */
public final class ToolItemModel {

    private ToolItemModel() {
    }

    public static final class WrenchSwing {
        private static int count;
        private static int prev;

        private WrenchSwing() {
        }

        /** Client tick: upstream {@code onUsingTick} and {@code onPlayerStoppedUsing}. */
        public static void tick() {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || !player.isUsingItem() || !(player.getUseItem().getItem() instanceof WrenchItem)) {
                count = 0;
                prev = 0;
                return;
            }
            if (WrenchItem.aimedAircraft(player) != null) {
                prev = count;
                count = count <= 0 ? 16 : count - 1;
            }
        }

        static float smooth() {
            float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            int i = Math.abs(count - 8);
            int j = Math.abs(prev - 8);
            return j + (i - j) * partial;
        }
    }
}

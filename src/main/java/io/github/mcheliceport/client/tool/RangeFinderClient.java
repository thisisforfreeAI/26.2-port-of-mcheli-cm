package io.github.mcheliceport.client.tool;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.mcheliceport.client.input.ModKeyMappings;
import io.github.mcheliceport.item.RangeFinderItem;
import io.github.mcheliceport.net.RangeFinderSpotPayload;
import io.github.mcheliceport.weapon.WeaponClientHooks;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The client side of the rangefinder: upstream's {@code MCH_ItemRangeFinder} statics and
 * {@code MCH_ClientToolTickHandler#onTick_ItemRangeFinder}. After eight ticks of use the scope
 * comes up at x2 zoom; the attack key spots (or marks the point, in mode 2), the zoom key zooms in
 * to x10, the weapon-mode key zooms out to x1.2 and the flare key cycles the modes. These are the
 * vehicle keys (Z, X and V by default), read here although the player is not riding.
 */
public final class RangeFinderClient {

    public static final int MODE_VEHICLES = 0;
    public static final int MODE_MOBS = 1;
    public static final int MODE_POINT = 2;

    private static int cooldown;
    private static boolean continueUsing;
    private static float zoom = 2.0F;
    private static int mode;
    private static final Edge SPOT = new Edge();
    private static final Edge MODE = new Edge();

    private RangeFinderClient() {
    }

    public static int cooldown() {
        return cooldown;
    }

    public static float zoom() {
        return zoom;
    }

    public static int mode() {
        return mode;
    }

    /** Upstream {@code isUsingScope}: used for more than eight ticks, until released. */
    public static boolean isUsingScope(Player player) {
        return player.isUsingItem() && player.getTicksUsingItem() > 8 || continueUsing;
    }

    /** Upstream {@code entityJoinWorldEvent}: mobs mode in single player, players and vehicles otherwise. */
    public static void onLogin() {
        Minecraft mc = Minecraft.getInstance();
        mode = mc.hasSingleplayerServer() ? MODE_MOBS : MODE_VEHICLES;
        stop();
        cooldown = 0;
    }

    private static void start() {
        zoom = 2.0F;
        continueUsing = true;
    }

    private static void stop() {
        continueUsing = false;
    }

    /** Client tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (cooldown > 0) {
            cooldown--;
        }
        boolean keysActive = player != null && mc.gui.screen() == null;
        boolean spotDown = SPOT.update(keysActive && physicallyDown(mc.options.keyAttack));
        boolean modeDown = MODE.update(keysActive && physicallyDown(ModKeyMappings.FLARE));
        ItemStack stack = player != null ? player.getMainHandItem() : ItemStack.EMPTY;
        boolean holding = stack.getItem() instanceof RangeFinderItem;
        if (holding) {
            boolean using = player.isUsingItem() && player.getTicksUsingItem() > 8 && RangeFinderItem.canUse(player);
            if (!continueUsing && using) {
                start();
            }
            if (using) {
                if (spotDown) {
                    spot(player, stack);
                }
                if (keysActive && physicallyDown(ModKeyMappings.ZOOM) && zoom < 10.0F) {
                    zoom = Math.min(10.0F, zoom + zoom / 10.0F);
                    WeaponClientHooks.playSound("zoom", 0.05F, 1.0F);
                }
                if (keysActive && physicallyDown(ModKeyMappings.SWITCH_WEAPON_MODE) && zoom > 1.2F) {
                    zoom = Math.max(1.2F, zoom - zoom / 10.0F);
                    WeaponClientHooks.playSound("zoom", 0.05F, 0.9F);
                }
                if (modeDown) {
                    WeaponClientHooks.playSound("lockon", 1.0F, 0.9F);
                    mode = (mode + 1) % 3;
                    if (mc.hasSingleplayerServer() && mode == MODE_VEHICLES) {
                        mode = MODE_MOBS;
                    }
                }
            }
        }
        // Upstream stopped on release (onPlayerStoppedUsing) or when the rangefinder left the hand.
        if (continueUsing && (!holding || player == null || !player.isUsingItem())) {
            stop();
        }
    }

    /** Upstream {@code spotEntity}: asks the server to spot or mark, at most every 60 ticks. */
    private static void spot(LocalPlayer player, ItemStack stack) {
        if (cooldown != 0 || player.getTicksUsingItem() <= 8) {
            return;
        }
        if (mode == MODE_POINT) {
            cooldown = RangeFinderItem.SPOT_COOLDOWN;
            ClientPacketDistributor.sendToServer(new RangeFinderSpotPayload(0));
        } else if (RangeFinderItem.hasCharge(stack)) {
            cooldown = RangeFinderItem.SPOT_COOLDOWN;
            ClientPacketDistributor.sendToServer(new RangeFinderSpotPayload(
                    mode == MODE_VEHICLES ? RangeFinderItem.MODE_VEHICLES_FILTER : RangeFinderItem.MODE_MOBS_FILTER));
        } else {
            WeaponClientHooks.playSound("ng", 1.0F, 1.0F);
        }
    }

    /** Upstream {@code W_Reflection.setCameraZoom}: the scope narrows the view. */
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && continueUsing && RangeFinderItem.canUse(player) && isUsingScope(player)) {
            event.setFOV(event.getFOV() / zoom);
        }
    }

    /** Whether the key bound to a mapping is held, whatever the mapping's context. */
    public static boolean physicallyDown(@Nullable KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) {
            return false;
        }
        InputConstants.Key key = mapping.getKey();
        var window = Minecraft.getInstance().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(window, key.getValue());
    }

    /** Upstream {@code MCH_Key#isKeyDown}: true on the tick a key goes down. */
    private static final class Edge {
        private boolean before;

        boolean update(boolean now) {
            boolean down = now && !this.before;
            this.before = now;
            return down;
        }
    }
}

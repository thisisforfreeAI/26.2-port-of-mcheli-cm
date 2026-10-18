package io.github.mcheliceport.item;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.multiplay.Spotting;
import io.github.mcheliceport.util.McheliSounds;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The laser rangefinder (upstream {@code MCH_ItemRangeFinder}): hold use to look through the
 * scope, which shows the range to the block in view; the attack key spots, for the player's team,
 * the players and vehicles or the mobs in view, or marks the block in view. Each spot uses one of
 * its ten charges (redstone recharges it); marking a point is free.
 *
 * <p>The scope, zoom and modes are client-side ({@code RangeFinderClient}); spots reach the
 * server as a {@code RangeFinderSpotPayload}, handled by {@link #handleSpotRequest}.
 */
public class RangeFinderItem extends Item {

    /** Upstream's client cooldown between spots, in ticks. */
    public static final int SPOT_COOLDOWN = 60;
    /** The server's cooldown, a little shorter than the client's so honest clients are never refused. */
    private static final int SERVER_COOLDOWN = SPOT_COOLDOWN - 10;
    /** Upstream's spot cone half-angle factor ({@code 20.0F}). */
    private static final float SPOT_ANGLE = 20.0F;
    /** Mobs stay marked 60 seconds; players and vehicles for {@code rangeFinderSpotTime}. */
    private static final int MOB_SPOT_TIME = 60;
    /** The filters of upstream's three modes: players and vehicles, mobs, and (0) mark point. */
    public static final int MODE_VEHICLES_FILTER = Spotting.FILTER_PLAYERS | Spotting.FILTER_GROUND
            | Spotting.FILTER_HELIS | Spotting.FILTER_PLANES;
    public static final int MODE_MOBS_FILTER = Spotting.FILTER_MOBS | Spotting.FILTER_MONSTERS;

    private static final Map<UUID, Long> LAST_SPOT = new ConcurrentHashMap<>();

    public RangeFinderItem(Properties properties) {
        super(properties);
    }

    /**
     * Upstream {@code canUse}: the rangefinder is in the main hand and the player is not flying a
     * vehicle; in a seat, only if the seat is not a gunner's and has no weapon selected.
     */
    public static boolean canUse(@Nullable Player player) {
        if (player == null || !(player.getMainHandItem().getItem() instanceof RangeFinderItem)) {
            return false;
        }
        if (player.getVehicle() instanceof AircraftEntity) {
            return false;
        }
        if (player.getVehicle() instanceof SeatEntity seat) {
            AircraftEntity ac = seat.getParent();
            return ac == null || !ac.isGunnerMode(player)
                    && ac.weapons().getWeaponIDBySeatID(ac.getSeatIdByEntity(player)) < 0;
        }
        return true;
    }

    /** Whether the rangefinder has a charge left to spot with. */
    public static boolean hasCharge(ItemStack stack) {
        return stack.getDamageValue() < stack.getMaxDamage();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (canUse(player)) {
            player.startUsingItem(hand);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    /**
     * Upstream {@code PacketRequestSpotEnemy#onReceive}: filter 0 marks the block in view;
     * otherwise, with a charge left, uses one (unless {@code rangeFinderConsume} is off or the
     * player is in creative) and spots. Plays "pi" on success, "ng" otherwise. Returns success.
     */
    public static boolean handleSpotRequest(ServerPlayer player, int targetFilter) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof RangeFinderItem)) {
            return false;
        }
        long now = player.level().getGameTime();
        Long last = LAST_SPOT.get(player.getUUID());
        if (last != null && now - last >= 0 && now - last < SERVER_COOLDOWN) {
            return false;
        }
        LAST_SPOT.put(player.getUUID(), now);
        Vec3 eyes = new Vec3(player.getX(), player.getEyeY(), player.getZ());
        boolean success = false;
        if (targetFilter == 0) {
            success = Spotting.markPoint(player, eyes);
        } else if (hasCharge(held)) {
            if (ServerConfig.RANGE_FINDER_CONSUME.get() && !player.getAbilities().instabuild) {
                // 1.12 items broke past their maximum damage, so the last charge leaves an empty
                // rangefinder to recharge rather than breaking it.
                held.setDamageValue(held.getDamageValue() + 1);
            }
            int time = (targetFilter & ~MODE_MOBS_FILTER) == 0 ? MOB_SPOT_TIME : ServerConfig.RANGE_FINDER_SPOT_TIME.get();
            success = Spotting.spotEntity(player, null, eyes, targetFilter, ServerConfig.RANGE_FINDER_SPOT_DIST.get(),
                    time, SPOT_ANGLE);
        }
        McheliSounds.playAt(player, success ? "pi" : "ng", 1.0F, 1.0F);
        return success;
    }

    /** Forgets a player's cooldown (tests). */
    public static void resetCooldown(Player player) {
        LAST_SPOT.remove(player.getUUID());
    }
}

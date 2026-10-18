package io.github.mcheliceport.item;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.gunner.GunnerEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.util.McheliSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code MCH_ItemSpawnGunner}: used on a free seat within five blocks it seats a gunner
 * of the player's team there; used on a gunner it takes the gunner off.
 */
public class SpawnGunnerItem extends Item {

    private static final double REACH = 5.0;

    private final int targetType;

    public SpawnGunnerItem(int targetType, Properties properties) {
        super(properties);
        this.targetType = targetType;
    }

    public int targetType() {
        return this.targetType;
    }

    /**
     * Upstream's search (shared by the item and {@code MCH_GuiSpawnGunner}): the nearest gunner on
     * the player's line of sight within five blocks, else the nearest seat (or the gunner on it),
     * else the nearest vehicle that is not a UAV (or the gunner flying it).
     */
    public static @Nullable Entity findTarget(Player player) {
        Level level = player.level();
        Vec3 from = player.getEyePosition();
        // Upstream used the body's rotation, not the head's.
        Vec3 to = from.add(Vec3.directionFromRotation(player.getXRot(), player.getYRot()).scale(REACH));
        AABB area = player.getBoundingBox().inflate(REACH);
        Entity target = null;
        for (GunnerEntity gunner : level.getEntitiesOfClass(GunnerEntity.class, area)) {
            if (hit(gunner, from, to) && closer(player, gunner, target)) {
                target = gunner;
            }
        }
        if (target != null) {
            return target;
        }
        for (SeatEntity seat : level.getEntitiesOfClass(SeatEntity.class, area)) {
            if (seat.getParent() != null && seat.getParent().getAcInfo() != null && hit(seat, from, to)
                    && closer(player, seat, target)) {
                target = seat.getRider() instanceof GunnerEntity gunner ? gunner : seat;
            }
        }
        if (target != null) {
            return target;
        }
        for (AircraftEntity ac : level.getEntitiesOfClass(AircraftEntity.class, area)) {
            if (!ac.isUAV() && ac.getAcInfo() != null && hit(ac, from, to) && closer(player, ac, target)) {
                target = ac.getPilot() instanceof GunnerEntity gunner ? gunner : ac;
            }
        }
        return target;
    }

    private static boolean hit(Entity entity, Vec3 from, Vec3 to) {
        return entity.getBoundingBox().clip(from, to).isPresent();
    }

    private static boolean closer(Player player, Entity entity, @Nullable Entity target) {
        return target == null || player.distanceToSqr(entity) < player.distanceToSqr(target);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Entity target = findTarget(player);
        if (target instanceof GunnerEntity gunner) {
            gunner.interact(player, hand, Vec3.ZERO);
            return InteractionResult.SUCCESS;
        }
        if (target == null) {
            if (!level.isClientSide()) {
                player.sendSystemMessage(Component.literal("Right click to seat."));
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            GunnerEntity gunner = new GunnerEntity(ModEntityTypes.GUNNER.get(), level);
            gunner.snapTo(target.getX(), target.getY(), target.getZ(),
                    ((Mth.floor(player.getYRot() * 4.0F / 360.0F + 0.5) & 3) - 1) * 90, 0.0F);
            gunner.isCreative = player.isCreative();
            gunner.targetType = this.targetType;
            gunner.ownerUUID = player.getUUID().toString();
            PlayerTeam team = level.getScoreboard().getPlayersTeam(player.getScoreboardName());
            if (team != null) {
                gunner.setTeamName(team.getName());
            }
            level.addFreshEntity(gunner);
            gunner.startRiding(target);
            McheliSounds.playAt(gunner, "wrench", 1.0F, 3.0F);
            AircraftEntity ac = target instanceof AircraftEntity aircraft ? aircraft : ((SeatEntity) target).getParent();
            player.sendSystemMessage(Component.literal("The gunner was put on ")
                    .append(Component.literal(ac.getAcInfo().displayName).withStyle(ChatFormatting.GOLD))
                    .append(" seat " + (ac.getSeatIdByEntity(gunner) + 1) + " by ")
                    .append(PlayerTeam.formatNameForTeam(player.getTeam(), player.getDisplayName())));
        }
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}

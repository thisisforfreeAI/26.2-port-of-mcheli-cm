package io.github.mcheliceport.item;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.entity.PlaceholderEntity;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.chain.ChainEntity;
import io.github.mcheliceport.entity.parachute.ParachuteEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.entity.vehicle.VehicleEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/** Reusable chain: first click selects the trailer, second selects the tug; click a link end to disconnect. */
public class ChainItem extends Item {
    public ChainItem(Properties properties) { super(properties); }
    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class, event -> {
            if (event.getItemStack().getItem() instanceof ChainItem) {
                interact(event.getEntity(), event.getItemStack(), event.getTarget());
                event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true);
            }
        });
    }
    public static boolean eligible(Entity entity) {
        return !entity.isRemoved() && entity.isAlive() && !entity.isPassenger()
                && !(entity instanceof LivingEntity || entity instanceof ItemEntity || entity instanceof ExperienceOrb
                || entity instanceof Projectile || entity instanceof ChainEntity || entity instanceof SeatEntity
                || entity instanceof ParachuteEntity || entity instanceof PlaceholderEntity
                || entity instanceof io.github.mcheliceport.entity.rappel.RappellingEntity
                || entity instanceof net.neoforged.neoforge.entity.PartEntity<?>)
                && !(entity instanceof AircraftEntity aircraft && aircraft.isDestroyed())
                && !(entity instanceof VehicleEntity && CommonConfig.FIX_VEHICLE_AT_PLACED_POINT.get());
    }
    /** Vanilla client-driven entities have no trailer-authority switch; never fight their driver. */
    public static boolean canBeTowed(Entity entity) {
        return eligible(entity) && (entity instanceof AircraftEntity || !entity.isClientAuthoritative());
    }
    public static void interact(Player player, ItemStack stack, Entity target) {
        Entity hit = target;
        if (target instanceof io.github.mcheliceport.entity.aircraft.AircraftHitBox box) { target = box.getParent(); }
        if (!(player.level() instanceof ServerLevel level) || !(stack.getItem() instanceof ChainItem)
                || player.isSpectator() || !player.isAlive() || target.level() != level || !eligible(target)
                || !player.isWithinEntityInteractionRange(hit.getBoundingBox(), 1) || !player.hasLineOfSight(hit)) { return; }
        var existing = ChainEntity.attached(target);
        if (existing != null) {
            existing.discard(); select(stack, null); message(player, "disconnected"); return;
        }
        Entity first = selected(stack, level);
        if (first == null || !canBeTowed(first) || ChainEntity.linked(first)) {
            if (!canBeTowed(target)) { return; }
            select(stack, target); ChainEntity.sound(target, "chain"); message(player, "selected"); return;
        }
        if (first == target) { return; }
        double distance = target.distanceTo(first);
        if (distance < 2 || distance > 16) { message(player, "range"); return; }
        var chain = new ChainEntity(ModEntityTypes.CHAIN.get(), level);
        chain.setPos(first.position().add(target.position()).scale(0.5)); chain.setLength((int) distance);
        if (!level.addFreshEntity(chain)) { return; }
        if (!chain.connect(first, target)) { chain.discard(); return; }
        ChainEntity.sound(target, "chain_ct"); select(stack, null); message(player, "connected");
    }
    private static void message(Player player, String key) {
        if (player instanceof ServerPlayer server) {
            server.sendSystemMessage(Component.translatable("message.mcheli_ce_port.chain." + key), true);
        }
    }
    public static @Nullable Entity selected(ItemStack stack, ServerLevel level) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.getStringOr("TowedEntityDimension", "").equals(level.dimension().identifier().toString())) { return null; }
        try { return level.getEntity(UUID.fromString(tag.getStringOr("TowedEntityUUID", ""))); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    public static void select(ItemStack stack, @Nullable Entity entity) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (entity == null) {
                tag.remove("TowedEntityUUID"); tag.remove("TowedEntityDimension");
            } else {
                tag.putString("TowedEntityUUID", entity.getUUID().toString());
                tag.putString("TowedEntityDimension", entity.level().dimension().identifier().toString());
            }
        });
    }
}

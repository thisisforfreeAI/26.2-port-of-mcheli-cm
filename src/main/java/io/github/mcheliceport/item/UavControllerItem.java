package io.github.mcheliceport.item;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.uav.UavStationEntity;
import io.github.mcheliceport.registry.ModDataComponents;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Shared physical interaction for the pairing tool and the handheld UAV tablet. */
public class UavControllerItem extends Item {
    private final boolean tablet;
    public UavControllerItem(Properties properties, boolean tablet) { super(properties); this.tablet = tablet; }
    public static int maxPairs() { return ServerConfig.SPEC.isLoaded() ? ServerConfig.MAX_PAIRED_UAVS.get() : 5; }
    public static boolean allowsNormal() { return ServerConfig.SPEC.isLoaded() && ServerConfig.TABLET_NORMAL_UAVS.get(); }
    public static UavTabletData data(ItemStack stack) {
        var data = stack.get(ModDataComponents.UAV_TABLET.get());
        if (data == null) { data = new UavTabletData(UUID.randomUUID(), java.util.List.of()); stack.set(ModDataComponents.UAV_TABLET.get(), data); }
        return data;
    }
    public InteractionResult onAircraft(Player player, InteractionHand hand, AircraftEntity ac) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) { return InteractionResult.FAIL; }
        if (ac.level().isClientSide()) { return InteractionResult.SUCCESS; }
        if (!ac.isUAV() || ac.getAcInfo() == null || ac.getAcInfo().isTargetDrone || ac.isDestroyed() || ac.isRemoved()) {
            return message(player, "Use a live, remotely controllable UAV.");
        }
        if (ac.uavStation() != null || ac.isPassenger() || ac.isTowed()) { return message(player, "Disconnect the UAV before pairing."); }
        var stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            var owner = ac.pairedController();
            if (tablet && (owner == null || !owner.equals(data(stack).id()))) { return message(player, "UAV is not paired to this tablet."); }
            if (owner != null && ac.level() instanceof ServerLevel sl && sl.getEntity(owner) instanceof UavStationEntity s) { s.releaseIdlePairing(ac); }
            if (tablet) {
                var d = data(stack); var ids = new ArrayList<>(d.uavs()); ids.remove(ac.getUUID());
                stack.set(ModDataComponents.UAV_TABLET.get(), new UavTabletData(d.id(), ids));
            }
            ac.setPairedController(null);
            return message(player, "UAV pairing released.");
        }
        if (!tablet) {
            stack.set(ModDataComponents.UAV_BOUND.get(), ac.getUUID());
            return message(player, "UAV selected. Right-click a station to pair.");
        }
        if (!allowsNormal() && !ac.getAcInfo().isSmallUAV) { return message(player, "This tablet accepts small UAVs only."); }
        var d = data(stack);
        if (ac.pairedController() != null && !ac.pairedController().equals(d.id())) { return message(player, "Release its old pairing with Shift + pairing device."); }
        var ids = new ArrayList<>(d.uavs());
        if (!ids.contains(ac.getUUID())) {
            if (ids.size() >= maxPairs()) { return message(player, "Tablet pairing list is full."); }
            ids.add(ac.getUUID());
        }
        stack.set(ModDataComponents.UAV_TABLET.get(), new UavTabletData(d.id(), ids)); ac.setPairedController(d.id());
        return message(player, "UAV paired to tablet. Right-click air to open.");
    }
    public InteractionResult onStation(Player player, InteractionHand hand, UavStationEntity station) {
        if (tablet) { return InteractionResult.PASS; }
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) { return InteractionResult.FAIL; }
        if (station.level().isClientSide()) { return InteractionResult.SUCCESS; }
        var stack = player.getItemInHand(hand); var id = stack.get(ModDataComponents.UAV_BOUND.get());
        if (id == null) { return message(player, "Right-click a UAV with the device first."); }
        var ac = ((ServerLevel) station.level()).getEntity(id);
        if (!(ac instanceof AircraftEntity aircraft)) { return message(player, "Selected UAV is not loaded in this dimension."); }
        if (station.pair(player, aircraft)) { stack.remove(ModDataComponents.UAV_BOUND.get()); }
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!tablet || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || player.isSpectator() || player.isPassenger()) { return InteractionResult.PASS; }
        if (!level.isClientSide()) {
            var stack = player.getItemInHand(hand); var d = data(stack);
            var s = new UavStationEntity(ModEntityTypes.UAV_STATION.get(), level);
            s.initTablet(player, hand, stack, d);
            if (!level.addFreshEntity(s)) { return InteractionResult.FAIL; }
            if (!player.startRiding(s)) { s.discard(); return InteractionResult.FAIL; }
            s.positionRider(player); s.openMenu(player);
        }
        return InteractionResult.SUCCESS;
    }
    private static InteractionResult message(Player player, String text) { if (player instanceof net.minecraft.server.level.ServerPlayer sp) { sp.sendSystemMessage(Component.literal(text), true); } return InteractionResult.SUCCESS; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> text, TooltipFlag flag) {
        if (tablet) {
            var d = stack.get(ModDataComponents.UAV_TABLET.get());
            text.accept(Component.literal("Paired UAVs: " + (d == null ? 0 : d.uavs().size())));
            text.accept(Component.literal("Right-click UAV: pair; air: open tablet"));
            text.accept(Component.literal("Shift + UAV: unpair this tablet"));
        } else {
            var id = stack.get(ModDataComponents.UAV_BOUND.get());
            text.accept(Component.literal(id == null ? "Right-click UAV, then station to pair" : "Selected UAV: " + id.toString().substring(0, 8)));
            text.accept(Component.literal("Shift + UAV: release idle pairing"));
        }
    }
}

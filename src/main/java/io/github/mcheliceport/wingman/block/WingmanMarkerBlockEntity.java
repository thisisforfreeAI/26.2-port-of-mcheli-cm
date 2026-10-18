package io.github.mcheliceport.wingman.block;

import io.github.mcheliceport.registry.ModBlocks;
import io.github.mcheliceport.wingman.registry.MarkerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Upstream's {@code WingmanMarkerTileEntity}: the marker's ID, its base and, for a parking spot,
 * the heading to park in. The marker type itself is the block state's {@link WingmanMarkerBlock#TYPE}.
 */
public class WingmanMarkerBlockEntity extends BlockEntity {

    private String markerId = "";
    private String baseId = "";
    /** Parking spots only: -1 any, 0 north, 1 east, 2 south, 3 west. */
    private int parkingHeading = -1;
    private boolean unloading;

    public WingmanMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.WINGMAN_MARKER_ENTITY.get(), pos, state);
    }

    public MarkerType getMarkerType() {
        return this.getBlockState().getValue(WingmanMarkerBlock.TYPE);
    }

    public String getMarkerId() {
        return this.markerId;
    }

    public String getBaseId() {
        return this.baseId;
    }

    public int getParkingHeading() {
        return this.parkingHeading;
    }

    /**
     * Upstream {@code PacketMarkerUpdate}: the new type, ID, base (none for a base) and heading
     * (parking spots only), recorded in the registry.
     */
    public void apply(MarkerType type, String id, String base, int heading) {
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        this.markerId = id;
        this.baseId = type == MarkerType.BASE ? "" : base;
        this.parkingHeading = type == MarkerType.PARKING ? heading : -1;
        this.setChanged();
        if (this.getMarkerType() != type) {
            level.setBlock(this.worldPosition, this.getBlockState().setValue(WingmanMarkerBlock.TYPE, type), 3);
        }
        MarkerRegistry.register(level, this.info());
    }

    public MarkerRegistry.MarkerInfo info() {
        return new MarkerRegistry.MarkerInfo(this.worldPosition, this.getMarkerType(), this.markerId, this.baseId,
                this.parkingHeading);
    }

    /**
     * Upstream {@code onBlockAdded}: a new marker takes an ID of its type ({@code p_1}, {@code wp_2}...)
     * and tells a player within 16 blocks. Every marker is recorded in the registry when it loads.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        boolean placed = this.markerId.isEmpty();
        if (placed) {
            this.markerId = WingmanMarkerBlock.autoId(level, this.getMarkerType());
            this.setChanged();
        }
        MarkerRegistry.register(level, this.info());
        if (placed) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(this.worldPosition.getX(), this.worldPosition.getY(),
                        this.worldPosition.getZ()) < 16 * 16) {
                    player.sendSystemMessage(Component.literal("§7Marker placed: "
                            + this.getMarkerType().displayName() + " §7id=§e" + this.markerId));
                    break;
                }
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.unloading = true;
    }

    /**
     * Upstream {@code breakBlock}: the marker leaves the registry when its block goes, however it
     * goes (the {@code /setblock} command skips the block's removal hooks), but not when its chunk unloads.
     */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (!this.unloading && this.level instanceof ServerLevel level) {
            MarkerRegistry.unregister(level, this.worldPosition);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.markerId = input.getStringOr("markerId", "");
        this.baseId = input.getStringOr("baseId", "");
        this.parkingHeading = input.getIntOr("parkingHeading", -1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("markerId", this.markerId);
        output.putString("baseId", this.baseId);
        output.putInt("parkingHeading", this.parkingHeading);
    }
}

package io.github.mcheliceport.wingman.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The wingman marker block, which marks bases, runways, helipads and waypoints for AI wingmen.
 *
 * <p>Upstream ({@code WingmanMarkerBlock}) kept the marker type in a block entity and only showed
 * it through the block's model. The port stores it in the block state. The marker screens, the
 * block entity data (marker and base IDs, parking heading) and the wingman AI come in milestone
 * M6b; until then every placed marker is a waypoint.
 */
public class WingmanMarkerBlock extends Block {

    public static final EnumProperty<MarkerType> TYPE = EnumProperty.create("type", MarkerType.class);

    public WingmanMarkerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TYPE, MarkerType.WAYPOINT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TYPE);
    }
}

package io.github.mcheliceport.block;

import io.github.mcheliceport.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * M10c, a port addition: a repair pad tile, one sixteenth of a block high. Nine of them in a 3x3
 * make a pad that services a vehicle standing still on it ({@code groundcrew.GroundCrew}).
 */
public class RepairPadBlock extends Block {

    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0 / 16.0, 1.0);

    public RepairPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public static boolean isPad(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.REPAIR_PAD.get());
    }

    /** Whether {@code center} is the middle of a full 3x3 of pad tiles. */
    public static boolean isPadCenter(BlockGetter level, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!isPad(level, center.offset(dx, 0, dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The centre tile of a full pad under {@code feet} (within a block of its column and two blocks
     * down), or null. A vehicle anywhere over the middle tile counts.
     */
    public static @Nullable BlockPos padUnder(BlockGetter level, Vec3 feet) {
        BlockPos base = BlockPos.containing(feet.x, feet.y + 0.5, feet.z);
        for (int dy = 0; dy >= -2; dy--) {
            BlockPos p = base.offset(0, dy, 0);
            if (isPad(level, p)) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos c = p.offset(dx, 0, dz);
                        if (isPadCenter(level, c) && Math.abs(feet.x - (c.getX() + 0.5)) <= 1.5
                                && Math.abs(feet.z - (c.getZ() + 0.5)) <= 1.5) {
                            return c;
                        }
                    }
                }
                return null;
            }
        }
        return null;
    }
}

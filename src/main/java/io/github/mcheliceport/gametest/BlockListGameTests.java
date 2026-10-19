package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.weapon.BlockList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** Config block lists keep their answers per block, and still answer the same after tags reload. */
final class BlockListGameTests {

    private BlockListGameTests() {
    }

    static void register() {
        ModGameTests.test("block_list_answers", 1, BlockListGameTests::answers);
    }

    private static void answers(GameTestHelper helper) {
        BlockList list = BlockList.of(List.of("minecraft:stone", "#minecraft:logs"));
        for (int pass = 0; pass < 2; pass++) {
            check(helper, list.contains(Blocks.STONE.defaultBlockState()), "a listed block is in the list");
            check(helper, list.contains(Blocks.OAK_LOG.defaultBlockState()), "a block of a listed tag is in the list");
            check(helper, !list.contains(Blocks.DIRT.defaultBlockState()), "dirt is not");
            BlockList.clearCaches();
        }
        helper.succeed();
    }
}

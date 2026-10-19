package io.github.mcheliceport.weapon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A config list of blocks: block IDs, or block tags written with a leading {@code #}. Upstream
 * listed 1.12 block names ({@code MCH_Config#setBlockListFromString}).
 */
public final class BlockList {

    private static final Map<List<? extends String>, BlockList> PARSED = new WeakHashMap<>();

    private final List<Block> blocks = new ArrayList<>();
    private final List<TagKey<Block>> tags = new ArrayList<>();
    /**
     * Answers per block, as tag lookups are costly and a tank asks for every block it touches each
     * tick. Cleared when tags reload ({@link #clearCaches}).
     */
    private final Map<Block, Boolean> answers = new ConcurrentHashMap<>();

    private BlockList(List<? extends String> entries) {
        for (String entry : entries) {
            String s = entry.trim();
            if (s.startsWith("#")) {
                Identifier id = Identifier.tryParse(s.substring(1));
                if (id != null) {
                    this.tags.add(TagKey.create(Registries.BLOCK, id));
                }
            } else if (!s.isEmpty()) {
                Identifier id = Identifier.tryParse(s);
                if (id != null) {
                    BuiltInRegistries.BLOCK.getOptional(id).ifPresent(this.blocks::add);
                }
            }
        }
    }

    /** The parsed list for a config value, parsed once per config load. */
    public static synchronized BlockList of(List<? extends String> entries) {
        return PARSED.computeIfAbsent(entries, BlockList::new);
    }

    public boolean contains(BlockState state) {
        return this.answers.computeIfAbsent(state.getBlock(), this::lookUp);
    }

    private boolean lookUp(Block block) {
        if (this.blocks.contains(block)) {
            return true;
        }
        for (TagKey<Block> tag : this.tags) {
            if (block.builtInRegistryHolder().is(tag)) {
                return true;
            }
        }
        return false;
    }

    /** Forgets every list's answers, for when block tags change. */
    public static synchronized void clearCaches() {
        for (BlockList list : PARSED.values()) {
            list.answers.clear();
        }
    }
}

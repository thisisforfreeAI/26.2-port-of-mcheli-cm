package io.github.mcheliceport.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/** Fixed-memory cosmetic reservoir. Uses no world RNG and never changes block iteration order. */
public final class BlastTerrainSample {
    public static final int LIMIT = 256;
    private final List<BlockPos> positions = new ArrayList<>();
    private long seen;

    public void offer(BlockPos pos) {
        long n=++seen;
        if(positions.size()<LIMIT){positions.add(pos.immutable());return;}
        long hash=n+0x9E3779B97F4A7C15L;
        hash=(hash^(hash>>>30))*0xBF58476D1CE4E5B9L;
        hash=(hash^(hash>>>27))*0x94D049BB133111EBL;
        hash^=hash>>>31;
        long slot=Long.remainderUnsigned(hash,n);
        if(slot<LIMIT)positions.set((int)slot,pos.immutable());
    }
    /** Protection callbacks may leave sampled blocks intact: omit those from visual crater sizing. */
    public List<BlockPos> snapshot(Predicate<BlockPos> removed) {
        return positions.stream().filter(removed).toList();
    }
}
